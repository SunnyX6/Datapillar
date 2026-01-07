"""
Analyst Agent（需求分析师）

职责：业务层面的需求分析与收敛
- 将用户需求拆分为业务步骤（Step）
- 基于知识库验证需求的可行性
- 需求必须在此阶段收敛清楚，不允许模糊需求往后传
- 通过工具验证表是否存在
"""

import asyncio
import json
import logging
import time
from typing import Any

from langchain_core.messages import ToolMessage

from src.infrastructure.llm.client import call_llm
from src.infrastructure.resilience import get_resilience_config
from src.modules.etl.agents.knowledge_agent import AgentType, get_agent_tools
from src.modules.etl.agents.prompt_messages import build_llm_messages
from src.modules.etl.schemas.agent_result import AgentResult
from src.modules.etl.schemas.analyst import (
    AnalysisResult,
    AnalysisResultOutput,
)
from src.modules.etl.tools.table import get_table_detail

logger = logging.getLogger(__name__)


def _tool_error(message: str, **extra: object) -> str:
    """构造工具错误响应"""
    payload: dict[str, object] = {"status": "error", "message": message}
    payload.update(extra)
    return json.dumps(payload, ensure_ascii=False)


ANALYST_AGENT_SYSTEM_INSTRUCTIONS = """你是 Datapillar 的需求分析师（AnalystAgent）。

## 你的任务
将用户的 ETL 需求拆分为可执行的业务步骤（Step），并验证涉及的表是否存在。

## 可用工具

### get_table_detail
查询表的详细信息（字段、类型等）。
- 用户提到的表名可能不完整，需要通过此工具验证
- 如果返回"未找到表"，说明表名或路径不正确

## 工作流程
1. 分析用户需求
2. 如果需要验证表信息，调用 get_table_detail
3. 分析完成后，直接输出 JSON 格式的分析结果

## 输出格式（JSON）
分析完成后，直接输出以下 JSON 格式：
```json
{
  "summary": "一句话概括用户需求",
  "confidence": 0.8,
  "steps": [
    {
      "step_id": "s1",
      "step_name": "步骤名称",
      "description": "这一步做什么",
      "input_tables": ["catalog.schema.table"],
      "output_table": "catalog.schema.table",
      "depends_on": []
    }
  ],
  "final_target": {
    "table_name": "目标表名",
    "write_mode": "overwrite",
    "description": "描述"
  },
  "ambiguities": []
}
```

## 字段说明
- summary: 一句话概括用户需求
- confidence: 需求明确程度 (0-1)，模糊需求 < 0.7
- steps: 业务步骤列表
  - step_id: 步骤唯一标识
  - step_name: 步骤名称
  - description: 这一步做什么
  - input_tables: 输入表列表（完整路径 catalog.schema.table）
  - output_table: 输出表（完整路径）
  - depends_on: 依赖的上游步骤 ID
- final_target: 最终数据目标
  - table_name: 目标表名
  - write_mode: overwrite/append/upsert
  - description: 描述
- ambiguities: 需要澄清的问题列表

## 收敛标准
需求分析必须"收敛"才算完成：
1. 每个 Step 必须有明确的 input_tables 和 output_table
2. 必须有 final_target
3. confidence >= 0.7

如果无法收敛，设置 confidence < 0.7 并在 ambiguities 中列出问题。

## 重要约束
1. 你只负责"做什么"（业务拆解），不写 SQL，不选组件
2. 不允许臆造表名，必须通过工具验证或在 ambiguities 中询问
3. 分析完成后直接输出 JSON，不要调用任何工具
"""


class AnalystAgent:
    """
    需求分析师

    职责：
    1. 基于知识库收敛用户需求
    2. 通过工具验证涉及的表是否存在
    3. 需求不明确时强制要求澄清
    4. 不允许模糊需求往后传
    """

    def __init__(self):
        self.llm = call_llm(temperature=0.0)
        config = get_resilience_config()
        self.max_iterations = config.max_iterations
        self.allowlist = get_agent_tools(AgentType.ANALYST)

    async def run(
        self,
        *,
        user_query: str,
        knowledge_agent=None,
        memory_context: dict[str, Any] | None = None,
    ) -> AgentResult:
        """
        执行需求分析

        参数：
        - user_query: 用户输入
        - knowledge_agent: KnowledgeAgent 实例（用于按需查询指针）
        - memory_context: 对话历史上下文（支持多轮对话）

        返回：
        - AgentResult: 执行结果
        """
        self._knowledge_agent = knowledge_agent

        if not user_query:
            return AgentResult.failed(
                summary="缺少用户输入",
                error="缺少用户输入，无法分析需求",
            )

        logger.info(f"📋 AnalystAgent 开始分析需求: {user_query}")

        try:
            llm_with_tools = self._bind_tools()

            output = await self._analyze_with_tools(
                user_query=user_query,
                llm_with_tools=llm_with_tools,
                memory_context=memory_context,
            )

            analysis_result = AnalysisResult.from_output(output, user_query)
            logger.info(f"✅ AnalystAgent 完成分析:\n{analysis_result.plan_summary()}")

            # 检查 LLM 返回的 confidence 和 ambiguities，判断是否需要用户澄清
            if analysis_result.confidence < 0.7 and analysis_result.ambiguities:
                logger.info(
                    f"⚠️ AnalystAgent 需要澄清: confidence={analysis_result.confidence}, "
                    f"ambiguities={analysis_result.ambiguities}"
                )
                return AgentResult.needs_clarification(
                    summary="需求不够明确，需要澄清",
                    message="我有一些问题需要确认后才能继续分析",
                    questions=analysis_result.ambiguities,
                )

            return AgentResult.completed(
                summary=f"需求分析完成: {analysis_result.summary}",
                deliverable=analysis_result,
                deliverable_type="analysis",
            )

        except Exception as e:
            logger.error(f"AnalystAgent 分析失败: {e}", exc_info=True)
            return AgentResult.failed(
                summary=f"需求分析失败: {str(e)}",
                error=str(e),
            )

    async def _analyze_with_tools(
        self,
        user_query: str,
        llm_with_tools,
        memory_context: dict[str, Any] | None = None,
    ) -> AnalysisResultOutput:
        """
        带工具调用的分析流程：
        1. 预先调用 KnowledgeAgent 获取候选表/列/值域（带权限过滤）
        2. 第一阶段：LLM 调用工具收集信息（bind_tools + ToolMessage）
        3. 第二阶段：LLM 输出结构化结果（with_structured_output + parse_structured_output 兜底）
        """
        total_start = time.perf_counter()

        # 预先检索知识上下文（带权限过滤）
        context_payload = None
        if self._knowledge_agent:
            search_start = time.perf_counter()
            ctx = await self._knowledge_agent.global_search(user_query, top_k=10, min_score=0.5)
            search_elapsed = time.perf_counter() - search_start
            logger.info(f"⏱️ 知识检索耗时: {search_elapsed:.2f}s, 找到 {ctx.summary()}")
            # 传入 allowlist 过滤钥匙：只保留该员工有权限的工具
            context_payload = ctx.to_llm_context(allowlist=self.allowlist)

        messages = build_llm_messages(
            system_instructions=ANALYST_AGENT_SYSTEM_INSTRUCTIONS,
            agent_id="analyst_agent",
            user_query=user_query,
            context_payload=context_payload,
            memory_context=memory_context,
        )

        # 第一阶段：工具调用收集信息
        for iteration in range(1, self.max_iterations + 1):
            llm_start = time.perf_counter()
            response = await llm_with_tools.ainvoke(messages)
            llm_elapsed = time.perf_counter() - llm_start
            logger.info(f"⏱️ [第{iteration}轮] LLM 调用耗时: {llm_elapsed:.2f}s")

            if not response.tool_calls:
                # 没有工具调用，进入第二阶段
                break

            # 执行工具调用，结果放入 ToolMessage
            messages.append(response)
            for tc in response.tool_calls:
                logger.info(f"🔧 AnalystAgent 调用工具: {tc['name']}({tc['args']})")

            tool_start = time.perf_counter()
            results = await asyncio.gather(
                *[self._execute_tool(tc["name"], tc["args"]) for tc in response.tool_calls]
            )
            tool_elapsed = time.perf_counter() - tool_start
            logger.info(
                f"⏱️ [第{iteration}轮] 工具调用耗时: {tool_elapsed:.2f}s ({len(results)} 个工具并行)"
            )

            for tc, result in zip(response.tool_calls, results, strict=True):
                messages.append(ToolMessage(content=result, tool_call_id=tc["id"]))

        # 第二阶段：结构化输出（with_structured_output 让 LLM 知道 schema）
        structured_start = time.perf_counter()
        output = await self._get_structured_output(messages, AnalysisResultOutput)
        structured_elapsed = time.perf_counter() - structured_start
        logger.info(f"⏱️ 结构化输出耗时: {structured_elapsed:.2f}s")

        total_elapsed = time.perf_counter() - total_start
        logger.info(f"⏱️ AnalystAgent 总耗时: {total_elapsed:.2f}s")

        return output

    async def _get_structured_output(
        self,
        messages: list,
        schema: type[AnalysisResultOutput],
    ) -> AnalysisResultOutput:
        """
        获取结构化输出：with_structured_output(json_mode) + parse_structured_output 兜底
        """
        from src.infrastructure.llm.structured_output import parse_structured_output

        # 使用 json_mode（不是 function_calling，避免和工具调用混淆）
        llm_structured = self.llm.with_structured_output(
            schema,
            method="json_mode",
            include_raw=True,
        )
        result = await llm_structured.ainvoke(messages)

        # 情况 1：直接解析成功
        if isinstance(result, schema):
            return result

        # 情况 2：dict 格式（include_raw=True 的返回）
        if isinstance(result, dict):
            parsed = result.get("parsed")
            if isinstance(parsed, schema):
                return parsed

            # 解析失败，尝试从 raw 中恢复
            parsing_error = result.get("parsing_error")
            raw = result.get("raw")

            if raw:
                raw_text = getattr(raw, "content", None)
                if raw_text:
                    logger.warning(
                        "with_structured_output 解析失败，尝试 parse_structured_output 兜底"
                    )
                    try:
                        return parse_structured_output(raw_text, schema)
                    except ValueError as e:
                        logger.error(f"parse_structured_output 兜底也失败: {e}")
                        raise

            if parsing_error:
                raise parsing_error

        raise ValueError(f"无法获取结构化输出: {type(result)}")

    async def _execute_tool(self, tool_name: str, tool_args: dict) -> str:
        """执行工具调用（支持精确参数和模糊参数）"""
        try:
            if tool_name not in self.allowlist:
                return _tool_error(f"工具不在 allowlist 中: {tool_name}")

            if tool_name == "get_table_detail":
                # 检查是否已提供精确参数
                catalog = tool_args.get("catalog")
                schema_name = tool_args.get("schema_name") or tool_args.get("schema")
                table = tool_args.get("table")

                # 如果只提供了 table_name，尝试通过 knowledge_agent 查找精确路径
                if not (catalog and schema_name and table):
                    table_name = tool_args.get("table_name") or tool_args.get("table") or ""
                    if not table_name:
                        return _tool_error("缺少 table 参数")

                    # 尝试解析 schema.table 或 catalog.schema.table 格式
                    parts = table_name.split(".")
                    if len(parts) >= 3:
                        catalog, schema_name, table = parts[0], parts[1], parts[2]
                    elif len(parts) == 2:
                        schema_name, table = parts[0], parts[1]
                        catalog = ""
                    else:
                        # 无法解析，尝试通过 knowledge_agent 查找
                        if self._knowledge_agent:
                            ctx = await self._knowledge_agent.global_search(
                                table_name, top_k=1, min_score=0.6
                            )
                            if ctx.tables:
                                pointer = ctx.tables[0]
                                catalog = pointer.catalog
                                schema_name = pointer.schema_name
                                table = pointer.table
                            else:
                                return _tool_error(f"未找到表: {table_name}")
                        else:
                            return _tool_error(f"无法解析表名: {table_name}")

                logger.info(
                    f"🔧 调用工具: {tool_name}(catalog={catalog}, schema_name={schema_name}, table={table})"
                )
                return await get_table_detail.ainvoke(
                    {
                        "catalog": catalog,
                        "schema_name": schema_name,
                        "table": table,
                    }
                )

            return _tool_error(f"未知工具: {tool_name}")
        except Exception as e:
            logger.error(f"工具 {tool_name} 执行失败: {e}")
            return _tool_error(str(e))

    def _bind_tools(self):
        """绑定查询工具到 LLM"""
        return self.llm.bind_tools([get_table_detail])
