"""
Reviewer Agent（方案评审）

评审技术方案的合理性、安全性、性能等。
"""

import json
import logging
from typing import Optional, List

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.types import Command

from src.modules.etl.schemas.state import AgentState
from src.modules.etl.schemas.plan import Workflow, Job, ReviewResult, ReviewIssue
from src.infrastructure.llm.client import call_llm

logger = logging.getLogger(__name__)

# 评审提示词
REVIEWER_AGENT_PROMPT = """你是资深数据架构评审专家，负责评审 ETL 技术方案的合理性。

## 你的职责
1. 检查方案的完整性（是否有源、转换、目标节点）
2. 验证数据流的正确性（依赖关系、拓扑顺序）
3. 评估性能风险（大表 JOIN、笛卡尔积、数据倾斜）
4. 检查安全合规（敏感字段、权限、数据脱敏）
5. 验证最佳实践（分区裁剪、增量处理、幂等性）

## 技术方案
{architecture_plan}

## 知识上下文
{knowledge_context}

## 评审维度

### 1. 完整性检查
- 是否有 source 节点读取数据
- 是否有 sink 节点写入数据
- 转换逻辑是否完整

### 2. 正确性检查
- 节点依赖关系是否正确
- 是否存在循环依赖
- JOIN 条件是否合理
- 字段类型是否匹配

### 3. 性能检查
- 是否存在大表 JOIN（超过 1000 万行）
- 是否可能产生笛卡尔积
- 是否有数据倾斜风险
- 是否使用了分区裁剪

### 4. 安全检查
- 是否涉及敏感字段（手机号、身份证等）
- 是否需要数据脱敏
- 权限是否合规

### 5. 最佳实践
- 是否支持增量处理
- 是否具备幂等性
- 是否有适当的错误处理

## 输出要求
请以 JSON 格式输出评审结果，包含：
1. approved: 是否通过评审（true/false）
2. issues: 发现的问题列表，每个问题包含：
   - severity: 严重程度（critical/high/medium/low）
   - category: 问题类别（completeness/correctness/performance/security/best_practice）
   - description: 问题描述
   - suggestion: 修改建议
   - affected_nodes: 涉及的节点ID
3. improvements: 改进建议列表
4. summary: 评审总结

只输出 JSON，不要解释。
"""


class ReviewerAgent:
    """
    方案评审

    职责：
    1. 评审 Workflow 的合理性
    2. 从多个维度检查问题（完整性、正确性、性能、安全、最佳实践）
    3. 给出改进建议
    4. 决定是否通过评审
    """

    def __init__(self):
        self.llm = call_llm(temperature=0.0, enable_json_mode=True)
        self.llm_structured = self.llm.with_structured_output(ReviewResult)

    async def __call__(self, state: AgentState) -> Command:
        """执行方案评审"""
        architecture_plan = state.architecture_plan
        knowledge_context = state.knowledge_context

        if not architecture_plan:
            return Command(
                update={
                    "messages": [AIMessage(content="缺少架构方案，无法评审")],
                    "current_agent": "reviewer_agent",
                    "error": "缺少架构方案",
                }
            )

        logger.info(f"🔍 ReviewerAgent 开始评审方案")

        try:
            # 先进行规则检查
            rule_issues = self._rule_based_review(architecture_plan, knowledge_context)

            # 构建 prompt
            prompt = REVIEWER_AGENT_PROMPT.format(
                architecture_plan=json.dumps(architecture_plan, ensure_ascii=False, indent=2),
                knowledge_context=self._format_context(knowledge_context),
            )

            # 调用 LLM 评审
            review_result = await self.llm_structured.ainvoke([HumanMessage(content=prompt)])

            # 合并规则检查结果
            review_result.issues.extend(rule_issues)

            # 如果有 critical/high 问题，强制不通过
            has_blocker = any(i.severity in ("critical", "high") for i in review_result.issues)
            if has_blocker:
                review_result.approved = False

            logger.info(
                f"✅ ReviewerAgent 完成评审: approved={review_result.approved}, "
                f"issues={len(review_result.issues)}"
            )

            return Command(
                update={
                    "messages": [AIMessage(content=f"方案评审完成: {'通过' if review_result.approved else '未通过'}")],
                    "review_result": review_result.model_dump(),
                    "current_agent": "reviewer_agent",
                    "iteration_count": state.iteration_count if review_result.approved else state.iteration_count + 1,
                }
            )

        except Exception as e:
            logger.error(f"ReviewerAgent 评审失败: {e}", exc_info=True)
            # 降级：只使用规则检查
            rule_issues = self._rule_based_review(architecture_plan, knowledge_context)
            has_blocker = any(i.severity in ("critical", "high") for i in rule_issues)
            fallback_result = ReviewResult(
                approved=not has_blocker,
                issues=rule_issues,
                improvements=["LLM 评审失败，建议人工复核"],
                summary="仅完成规则检查，LLM 评审失败",
            )
            return Command(
                update={
                    "messages": [AIMessage(content=f"LLM 评审失败，使用规则检查: {str(e)}")],
                    "review_result": fallback_result.model_dump(),
                    "current_agent": "reviewer_agent",
                    "iteration_count": state.iteration_count if fallback_result.approved else state.iteration_count + 1,
                }
            )

    def _rule_based_review(
        self, plan_dict: dict, context: Optional[dict]
    ) -> List[ReviewIssue]:
        """基于规则的检查"""
        issues = []

        # 将 dict 转换为 Workflow
        if isinstance(plan_dict, dict):
            plan = Workflow(**plan_dict)
        else:
            plan = plan_dict

        nodes = plan.jobs

        # 1. 完整性检查 - 至少有一个节点
        if not nodes:
            issues.append(ReviewIssue(
                severity="critical",
                category="completeness",
                description="方案缺少节点，无法读取/写入数据",
                suggestion="添加 source/sink 节点",
                affected_nodes=[],
            ))

        # 基于输入/依赖推断源/汇
        source_nodes = [n for n in nodes if not n.input_tables]
        downstream_refs = {dep for n in nodes for dep in n.depends}
        sink_nodes = [n for n in nodes if n.id not in downstream_refs]

        if not source_nodes:
            issues.append(ReviewIssue(
                severity="critical",
                category="completeness",
                description="方案缺少 source 节点，无法读取数据",
                suggestion="至少添加一个无输入表的读取节点",
                affected_nodes=[],
            ))

        if not sink_nodes:
            issues.append(ReviewIssue(
                severity="critical",
                category="completeness",
                description="方案缺少 sink 节点，无法写入数据",
                suggestion="确保存在终态输出节点",
                affected_nodes=[],
            ))

        # 2. 依赖关系检查
        node_ids = {n.id for n in nodes}
        for node in nodes:
            for dep in node.depends:
                if dep not in node_ids:
                    issues.append(ReviewIssue(
                        severity="critical",
                        category="correctness",
                        description=f"节点 {node.id} 依赖的节点 {dep} 不存在",
                        suggestion=f"检查节点 {dep} 是否定义，或修正依赖关系",
                        affected_nodes=[node.id],
                    ))

        # 3. 循环依赖检查
        if self._has_cycle(nodes):
            issues.append(ReviewIssue(
                severity="critical",
                category="correctness",
                description="节点之间存在循环依赖",
                suggestion="检查并移除循环依赖",
                affected_nodes=[n.id for n in nodes],
            ))

        # 4. 孤立节点检查
        referenced_nodes = set()
        for node in nodes:
            referenced_nodes.update(node.depends)

        for node in nodes:
            if node.id not in referenced_nodes and node.id not in sink_nodes:
                issues.append(ReviewIssue(
                    severity="medium",
                    category="correctness",
                    description=f"节点 {node.id} 没有被其他节点依赖，可能是孤立节点",
                    suggestion="检查该节点是否应该被其他节点引用",
                    affected_nodes=[node.id],
                ))

        # 5. 性能检查 - 多表 JOIN
        for node in nodes:
            if len(node.depends) > 3:
                issues.append(ReviewIssue(
                    severity="high",
                    category="performance",
                    description=f"节点 {node.id} 关联了 {len(node.depends)} 个上游，可能导致大表 JOIN 性能问题",
                    suggestion="考虑分步 JOIN 或预聚合",
                    affected_nodes=[node.id],
                ))

        # 6. 表存在性检查
        if context:
            table_names = set(context.get("tables", {}).keys())
            for node in source_nodes:
                for src in node.input_tables:
                    if not src.startswith("tmp.") and src not in table_names:
                        issues.append(ReviewIssue(
                            severity="high",
                            category="correctness",
                            description=f"源表 {src} 不在知识库中",
                            suggestion="确认表名是否正确，或将表添加到知识库",
                            affected_nodes=[node.id],
                        ))

            for node in sink_nodes:
                if node.output_table and not node.output_table.startswith("tmp.") and node.output_table not in table_names:
                    issues.append(ReviewIssue(
                        severity="medium",
                        category="correctness",
                        description=f"目标表 {node.output_table} 不在知识库中（可能是新表）",
                        suggestion="如果是新表，请确认表结构；如果是已有表，请检查表名",
                        affected_nodes=[node.id],
                    ))

        return issues

    def _has_cycle(self, nodes: List[Job]) -> bool:
        """检测是否存在循环依赖"""
        # 构建邻接表
        graph = {n.id: n.depends for n in nodes}
        visited = set()
        rec_stack = set()

        def dfs(node_id: str) -> bool:
            visited.add(node_id)
            rec_stack.add(node_id)

            for dep in graph.get(node_id, []):
                if dep not in visited:
                    if dfs(dep):
                        return True
                elif dep in rec_stack:
                    return True

            rec_stack.remove(node_id)
            return False

        for node in nodes:
            if node.id not in visited:
                if dfs(node.id):
                    return True

        return False

    def _format_context(self, context: Optional[dict]) -> str:
        """格式化知识上下文"""
        if not context:
            return "（无知识上下文）"

        lines = []

        # 表信息摘要
        tables = context.get("tables", {})
        if tables:
            lines.append(f"### 表信息（共 {len(tables)} 个表）")
            for name, table in list(tables.items())[:5]:
                layer = table.get("layer", "")
                col_count = len(table.get("columns", []))
                lines.append(f"- {name} ({layer}, {col_count} 列)")

        # JOIN 信息
        joins = context.get("join_hints", [])
        if joins:
            lines.append(f"\n### JOIN 关系（共 {len(joins)} 个）")
            for j in joins[:3]:
                lines.append(
                    f"- {j.get('left_table')}.{j.get('left_column')} = "
                    f"{j.get('right_table')}.{j.get('right_column')}"
                )

        # DQ 规则
        dq_rules = context.get("dq_rules", [])
        if dq_rules:
            lines.append(f"\n### 数据质量规则（共 {len(dq_rules)} 个）")

        return "\n".join(lines) if lines else "（无知识上下文）"
