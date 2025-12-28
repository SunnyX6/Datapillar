"""
指标 AI 治理服务
"""

import json
import logging
import re

from langchain_core.messages import SystemMessage, HumanMessage

from src.infrastructure.llm.client import call_llm
from src.infrastructure.repository import SemanticRepository
from src.modules.governance.metric.schemas import (
    AIFillRequest, AIFillResponse,
    MetricType, WordRoot, Modifier,
)

logger = logging.getLogger(__name__)


ATOMIC_FILL_PROMPT = """你是指标治理专家，根据用户描述和数据表/列信息上下文生成原子指标。

## 表上下文知识
{table_context}

## 列值域知识（列的取值只能从值域里面获取，禁止捏造）
{available_value_domains}

## 用户选择度量列
{measure_columns}

## 用户选择过滤列（如果有值，优先使用）
{filter_columns}

## 语义资产
- 数据类型: {data_types}
- 单位: {units}
- 词根: {word_roots}
- 聚合函数: 如SUM,COUNT等常见的sql函数

## 输出规则
只有当知识上下文满足需求遵循以下规则时填写，否则除了warning字段，其他字段全部置空
1. 根据用户需求和度量列生成指标
2. 公式中涉及"可用列值域"中的列时，值只能从列出的选项中选择
3. 词根必须从词根列表中匹配，填入 wordRoots
4. measureColumns 和 filterColumns 返回公式中实际使用到的列名数组

## 输出 JSON
```json
{{
  "name": "中文名称",
  "wordRoots": ["词根列表中匹配的code"],
  "aggregation": "聚合函数",
  "modifiersSelected": [],
  "type": "ATOMIC",
  "dataType": "数据类型",
  "unit": "单位code或null",
  "calculationFormula": "计算公式，如sum(xxx) where xxx in ('xxx')",
  "comment": "业务描述",
  "measureColumns": ["公式中使用的度量列名列表"],
  "filterColumns": ["公式中使用的过滤列名列表"],
  "warning": "问题说明或null"
}}
```

## 严格约束
1. 必须且只能依赖上下文回答问题，禁止自行推测或编造
2. warning 有值时，其他所有字段必须为空；warning 为 null 时，正常填写其他字段
3. 所有的回答都必须基于已知表的上下文和列值域知识，如果这两项无法满足，直接向用户澄清问题
"""


DERIVED_FILL_PROMPT = """你是指标治理专家，根据用户描述生成派生指标，必须返回JSON。

## 基础指标（原子指标）
{base_metric}

## 已选修饰符
{modifiers_selected}
{optional_context}
## 可选项
- 数据类型: {data_types}
- 单位: {units}
- 可用修饰符（必须从此列表选择）: {available_modifiers}

## 输出规则
1. 派生指标 = 基础指标 + 修饰符（时间周期、维度过滤等）
2. modifiersSelected（修饰符）必须从修饰符列表中选择
3. calculationFormula 格式：{{基础指标code}} WHERE 过滤条件
4. 如果有列值域知识，过滤条件中的值只能从列出的选项中选择
5. name 和 comment 要体现派生含义
6. wordRoots 留空，因为派生指标继承原子指标的词根
7. filterColumns 返回公式中实际使用到的过滤列名数组

## 输出 JSON
```json
{{
  "name": "中文名称",
  "wordRoots": [],
  "aggregation": "",
  "modifiersSelected": ["MTD"],
  "type": "DERIVED",
  "dataType": "数据类型",
  "unit": "单位code或null",
  "calculationFormula": "{{AMOUNT_SUM}} WHERE date >= DATE_TRUNC('month', CURRENT_DATE)",
  "comment": "业务描述",
  "filterColumns": ["公式中使用的过滤列名列表"],
  "warning": "问题说明或null"
}}
```

## 严格约束
1. 必须且只能依赖上下文回答问题，禁止自行推测或编造
2. warning 有值时，其他所有字段必须为空；warning 为 null 时，正常填写其他字段
"""


COMPOSITE_FILL_PROMPT = """你是指标治理专家，根据用户描述生成复合指标，必须返回JSON。

## 用户选择的指标
{metrics}

## 可选项
- 数据类型: {data_types}
- 单位: {units}
- 词根: {word_roots}

## 输出规则
1. 复合指标 = 多个指标的算术运算（加减乘除）
2. calculationFormula 格式：({{指标A}} - {{指标B}}) / {{指标C}} * 100
3. 复合指标通常产生比率、占比等
4. name 和 comment 要体现运算含义
5. wordRoots 从词根列表中选择

## 输出 JSON
```json
{{
  "name": "中文名称",
  "wordRoots": ["PROFIT", "RATE"],
  "aggregation": "",
  "modifiersSelected": [],
  "type": "COMPOSITE",
  "dataType": "数据类型",
  "unit": "单位code或null",
  "calculationFormula": "({{REVENUE}} - {{COST}}) / {{REVENUE}} * 100",
  "comment": "业务描述",
  "warning": "问题说明或null"
}}
```

## 严格约束
1. 必须且只能依赖上下文回答问题，禁止自行推测或编造
2. warning 有值时，其他所有字段必须为空；warning 为 null 时，正常填写其他字段
"""


class MetricAIService:
    """指标 AI 治理服务"""

    async def fill(self, request: AIFillRequest) -> AIFillResponse:
        """AI 填写表单"""
        llm = call_llm(enable_json_mode=True)

        # 获取语义元数据：前端传了就用，没传就查 Neo4j
        semantic_data = await self._load_semantic_data(request.context.form_options)

        # 查询表的上下文作为知识背景（原子指标和派生指标都可能有）
        table_context = None
        ref_catalog, ref_schema, ref_table = None, None, None

        if request.context.metric_type == MetricType.ATOMIC:
            payload = request.context.get_atomic_payload()
            if payload:
                ref_catalog, ref_schema, ref_table = payload.ref_catalog, payload.ref_schema, payload.ref_table
        elif request.context.metric_type == MetricType.DERIVED:
            payload = request.context.get_derived_payload()
            if payload:
                ref_catalog, ref_schema, ref_table = payload.ref_catalog, payload.ref_schema, payload.ref_table

        if ref_catalog and ref_schema and ref_table:
            table_context = await SemanticRepository.get_table_context(
                ref_catalog, ref_schema, ref_table
            )
            if table_context:
                logger.info(f"获取表上下文: {ref_catalog}.{ref_schema}.{ref_table}")

        # 根据指标类型生成不同的 prompt
        system_prompt = self._build_fill_prompt(request, semantic_data, table_context)

        response = await llm.ainvoke([
            SystemMessage(content=system_prompt),
            HumanMessage(content=request.user_input),
        ])

        result = self._parse_json(response.content)
        logger.info(f"AI Fill: {request.context.metric_type} -> {result.get('code')}")

        # 强制校验：warning 有值时，其他字段置空
        if result.get("warning"):
            result = {"warning": result["warning"]}

        return AIFillResponse(**result)

    async def _load_semantic_data(self, form_options) -> dict:
        """
        获取语义元数据：前端传了就用，没传就查 Neo4j

        Args:
            form_options: 前端传递的表单选项

        Returns:
            包含 word_roots, modifiers, units 的字典
        """
        # 词根：前端传了就用，没传就查 Neo4j
        if form_options.word_roots:
            word_roots = form_options.word_roots
        else:
            roots = await SemanticRepository.get_word_roots(limit=200)
            word_roots = [
                WordRoot(code=r.code, name=r.name or r.code)
                for r in roots
            ]

        # 修饰符：前端传了就用，没传就查 Neo4j
        if form_options.modifiers:
            modifiers = form_options.modifiers
        else:
            mods = await SemanticRepository.get_modifiers(limit=200)
            modifiers = [
                Modifier(code=m.code, name=m.description or m.code)
                for m in mods
            ]

        # 单位：前端传了就用，没传就查 Neo4j
        if form_options.units:
            units = form_options.units
        else:
            unit_list = await SemanticRepository.get_units(limit=200)
            units = [u.code for u in unit_list]

        data = {
            "word_roots": word_roots,
            "modifiers": modifiers,
            "units": units,
        }

        logger.debug(
            f"语义元数据: "
            f"{len(data['word_roots'])} 词根, "
            f"{len(data['modifiers'])} 修饰符, "
            f"{len(data['units'])} 单位"
        )

        return data

    def _build_fill_prompt(self, request: AIFillRequest, semantic_data: dict, table_context=None) -> str:
        """构建填写 prompt"""
        ctx = request.context
        opts = ctx.form_options

        # 公共选项
        data_types = ", ".join(opts.data_types)
        units = ", ".join(semantic_data["units"])
        word_roots = ", ".join([f"{r.code}({r.name})" for r in semantic_data["word_roots"]])

        if ctx.metric_type == MetricType.ATOMIC:
            payload = ctx.get_atomic_payload()
            measure_cols = "\n".join([
                f"- {c.name} ({c.type}): {c.comment or '无注释'}"
                for c in (payload.measure_columns if payload else [])
            ]) or "无"

            # 用户选择的过滤列
            filter_cols = "\n".join([
                f"- {c.name} ({c.type}): {[v.key for v in c.values]}"
                for c in (payload.filter_columns if payload else [])
            ]) or "无"

            # 提取可用值域（优先用户选择，否则用表上下文）
            value_domains = []
            if payload and payload.filter_columns:
                # 用户选择的过滤列
                for c in payload.filter_columns:
                    values_str = ", ".join([f"{v.key}({v.label})" for v in c.values])
                    value_domains.append(f"- {c.name}: {values_str}")
            elif table_context and table_context.columns:
                # 表上下文中的值域
                for col in table_context.columns:
                    vd = col.get("valueDomain")
                    if vd and vd.get("items"):
                        items_str = vd.get("items")
                        values = []
                        for item in items_str.split(","):
                            if ":" in item:
                                k, v = item.split(":", 1)
                                values.append(f"{k.strip()}({v.strip()})")
                            else:
                                values.append(item.strip())
                        value_domains.append(f"- {col.get('name')}: {', '.join(values)}")
            available_value_domains = "\n".join(value_domains) or "无"

            # 格式化表上下文
            if table_context:
                table_ctx_str = f"{table_context.catalog}.{table_context.schema}.{table_context.table}"
                if table_context.description:
                    table_ctx_str += f" - {table_context.description}"
            else:
                table_ctx_str = "无"

            return ATOMIC_FILL_PROMPT.format(
                table_context=table_ctx_str,
                measure_columns=measure_cols,
                filter_columns=filter_cols,
                available_value_domains=available_value_domains,
                data_types=data_types,
                units=units,
                word_roots=word_roots,
            )

        elif ctx.metric_type == MetricType.DERIVED:
            payload = ctx.get_derived_payload()

            # 基础指标上下文
            if payload and payload.base_metric:
                bm = payload.base_metric
                base_metric_str = f"- {bm.code}({bm.name or ''}): {bm.description or '无描述'}"
            else:
                base_metric_str = "未指定"

            # 已选修饰符上下文
            if payload and payload.modifiers:
                modifiers_selected = "\n".join([
                    f"- {m.code}({m.name})" for m in payload.modifiers
                ])
            else:
                modifiers_selected = "无"

            # 可用修饰符列表
            available_modifiers = ", ".join([f"{m.code}({m.name})" for m in semantic_data["modifiers"]])

            # 可选上下文：表上下文、列值域、过滤列（只有用户选择了表才有）
            optional_parts = []
            if table_context:
                # 表上下文
                table_ctx_str = f"{table_context.catalog}.{table_context.schema}.{table_context.table}"
                if table_context.description:
                    table_ctx_str += f" - {table_context.description}"
                optional_parts.append(f"\n## 表上下文\n{table_ctx_str}")

                # 列值域（优先用户选择的过滤列，否则用表上下文）
                value_domains = []
                if payload and payload.filter_columns:
                    for c in payload.filter_columns:
                        values_str = ", ".join([f"{v.key}({v.label})" for v in c.values])
                        value_domains.append(f"- {c.name}: {values_str}")
                elif table_context.columns:
                    for col in table_context.columns:
                        vd = col.get("valueDomain")
                        if vd and vd.get("items"):
                            items_str = vd.get("items")
                            values = []
                            for item in items_str.split(","):
                                if ":" in item:
                                    k, v = item.split(":", 1)
                                    values.append(f"{k.strip()}({v.strip()})")
                                else:
                                    values.append(item.strip())
                            value_domains.append(f"- {col.get('name')}: {', '.join(values)}")
                if value_domains:
                    optional_parts.append(f"\n## 列值域知识\n" + "\n".join(value_domains))

                # 用户选择的过滤列
                if payload and payload.filter_columns:
                    filter_cols = "\n".join([
                        f"- {c.name} ({c.type}): {[v.key for v in c.values]}"
                        for c in payload.filter_columns
                    ])
                    optional_parts.append(f"\n## 用户选择过滤列\n{filter_cols}")

            optional_context = "\n".join(optional_parts) + "\n" if optional_parts else ""

            return DERIVED_FILL_PROMPT.format(
                base_metric=base_metric_str,
                modifiers_selected=modifiers_selected,
                optional_context=optional_context,
                data_types=data_types,
                units=units,
                available_modifiers=available_modifiers,
            )

        else:  # COMPOSITE
            payload = ctx.get_composite_payload()

            # 用户选择的指标上下文
            if payload and payload.metrics:
                metrics_str = "\n".join([
                    f"- {m.code}({m.name or ''}): {m.description or '无描述'}"
                    for m in payload.metrics
                ])
            else:
                metrics_str = "无"

            return COMPOSITE_FILL_PROMPT.format(
                metrics=metrics_str,
                data_types=data_types,
                units=units,
                word_roots=word_roots,
            )

    def _parse_json(self, content: str) -> dict:
        """解析 JSON"""
        match = re.search(r'```json\s*([\s\S]*?)\s*```', content)
        if match:
            return json.loads(match.group(1))

        match = re.search(r'\{[\s\S]*\}', content)
        if match:
            return json.loads(match.group())

        raise ValueError(f"无法解析 JSON: {content[:500]}")


metric_ai_service = MetricAIService()
