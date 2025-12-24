"""
指标 AI 治理服务
"""

import json
import logging
import re

from langchain_core.messages import SystemMessage, HumanMessage

from src.infrastructure.llm.client import call_llm
from src.modules.governance.metric.schemas import (
    AIFillRequest, AIFillResponse,
    AICheckRequest, AICheckResponse,
    SemanticIssue, IssueSeverity,
    MetricType,
)

logger = logging.getLogger(__name__)


ATOMIC_FILL_PROMPT = """你是指标治理专家，根据用户描述和数据列信息生成原子指标，必须返回JSON。

## 度量列（用于计算）
{measure_columns}

## 过滤列（用于 WHERE 条件）
{filter_columns}

## 可选项
- 数据类型: {data_types}
- 单位: {units}
- 词根: {word_roots}

## 要求
1. name（中文）、code（英文）、calculationFormula 三者语义必须一致
2. code 格式：词根_词根_聚合词，如 ORDER_AMOUNT_SUM
3. calculationFormula 使用度量列生成聚合公式，如 SUM(amount)
4. 如果有过滤列，在公式中添加 WHERE 条件，如 SUM(amount) WHERE status = 'paid'
5. dataType 根据度量列类型推断，unit 从可选项中选择
6. comment 准确描述业务含义

返回 JSON：
```json
{{
  "name": "中文名称",
  "code": "UPPER_CODE",
  "type": "ATOMIC",
  "dataType": "数据类型",
  "unit": "单位",
  "calculationFormula": "聚合公式 WHERE 过滤条件",
  "comment": "业务描述"
}}
```"""


DERIVED_FILL_PROMPT = """你是指标治理专家，根据用户描述生成派生指标，必须返回JSON。

## 基础指标
{base_metric}

## 已选修饰符
{modifiers}

## 可选项
- 数据类型: {data_types}
- 单位: {units}
- 词根: {word_roots}
- 可用修饰符: {available_modifiers}

## 要求
1. 派生指标 = 基础指标 + 修饰符（时间周期、维度过滤等）
2. code 格式：基础指标code_修饰符code，如 ORDER_AMOUNT_SUM_DAILY
3. calculationFormula 格式：{{基础指标code}} WHERE 修饰条件
4. name 和 comment 要体现派生含义

返回 JSON：
```json
{{
  "name": "中文名称",
  "code": "UPPER_CODE",
  "type": "DERIVED",
  "dataType": "数据类型",
  "unit": "单位",
  "calculationFormula": "{{基础指标}} WHERE 条件",
  "comment": "业务描述"
}}
```"""


COMPOSITE_FILL_PROMPT = """你是指标治理专家，根据用户描述生成复合指标，必须返回JSON。

## 可用指标
{metrics}

## 运算类型
{operation}

## 可选项
- 数据类型: {data_types}
- 单位: {units}
- 词根: {word_roots}

## 要求
1. 复合指标 = 多个指标的算术运算（加减乘除）
2. calculationFormula 格式：({{指标A}} - {{指标B}}) / {{指标C}} * 100
3. 复合指标通常产生比率、占比等，单位常为 RATIO
4. name 和 comment 要体现运算含义

返回 JSON：
```json
{{
  "name": "中文名称",
  "code": "UPPER_CODE",
  "type": "COMPOSITE",
  "dataType": "数据类型",
  "unit": "单位",
  "calculationFormula": "({{指标A}} - {{指标B}}) / {{指标C}}",
  "comment": "业务描述"
}}
```"""


CHECK_SYSTEM_PROMPT = """你是指标治理专家，检查表单的语义一致性，必须返回JSON。

表单：
{form}

检查规则（严格执行）：
1. code 格式必须是 词根_词根_聚合词，如 ORDER_AMOUNT_SUM
   - 只允许大写字母和下划线，不允许数字
   - 最后一段必须是聚合词（SUM/AVG/MAX/MIN/COUNT等）
   - 前面的词根必须是有意义的英文单词
2. code 中的聚合词必须与 calculationFormula 中的聚合函数一致
3. name（中文）必须与 code（英文）语义对应
4. comment 必须有值且能准确描述 calculationFormula 的业务含义

返回 JSON：
```json
{{
  "valid": true/false,
  "issues": [
    {{"field": "字段名", "severity": "error|warning", "message": "问题描述"}}
  ],
  "suggestions": {{"字段名": "建议值"}}
}}
```"""


class MetricAIService:
    """指标 AI 治理服务"""

    async def fill(self, request: AIFillRequest) -> AIFillResponse:
        """AI 填写表单"""
        llm = call_llm(enable_json_mode=True)

        # 根据指标类型生成不同的 prompt
        system_prompt = self._build_fill_prompt(request)

        response = await llm.ainvoke([
            SystemMessage(content=system_prompt),
            HumanMessage(content=request.user_input),
        ])

        result = self._parse_json(response.content)
        logger.info(f"AI Fill: {request.context.metric_type} -> {result.get('code')}")

        return AIFillResponse(**result)

    def _build_fill_prompt(self, request: AIFillRequest) -> str:
        """构建填写 prompt"""
        ctx = request.context
        opts = ctx.form_options

        # 公共选项
        data_types = ", ".join(opts.data_types)
        units = ", ".join(opts.units)
        word_roots = ", ".join([f"{r.code}({r.name})" for r in opts.word_roots])

        if ctx.metric_type == MetricType.ATOMIC:
            payload = ctx.get_atomic_payload()
            measure_cols = "\n".join([
                f"- {c.name} ({c.type}): {c.comment or '无注释'}"
                for c in (payload.measure_columns if payload else [])
            ]) or "无"
            filter_cols = "\n".join([
                f"- {c.name} ({c.type}): 可选值 {[v.label for v in c.values]}"
                for c in (payload.filter_columns if payload else [])
            ]) or "无"

            return ATOMIC_FILL_PROMPT.format(
                measure_columns=measure_cols,
                filter_columns=filter_cols,
                data_types=data_types,
                units=units,
                word_roots=word_roots,
            )

        elif ctx.metric_type == MetricType.DERIVED:
            payload = ctx.get_derived_payload()
            base_metric = payload.base_metric.code if payload else "未指定"
            modifiers = ", ".join(payload.modifiers) if payload else "无"
            available_modifiers = ", ".join([f"{m.code}({m.name})" for m in opts.modifiers])

            return DERIVED_FILL_PROMPT.format(
                base_metric=base_metric,
                modifiers=modifiers,
                data_types=data_types,
                units=units,
                word_roots=word_roots,
                available_modifiers=available_modifiers,
            )

        else:  # COMPOSITE
            payload = ctx.get_composite_payload()
            metrics = ", ".join(payload.metrics) if payload else "无"
            operation = payload.operation if payload else "divide"

            return COMPOSITE_FILL_PROMPT.format(
                metrics=metrics,
                operation=operation,
                data_types=data_types,
                units=units,
                word_roots=word_roots,
            )

    async def check(self, request: AICheckRequest) -> AICheckResponse:
        """AI 检查语义"""
        llm = call_llm(enable_json_mode=True)

        form_json = request.form.model_dump_json(by_alias=True, indent=2)
        system_prompt = CHECK_SYSTEM_PROMPT.format(form=form_json)

        response = await llm.ainvoke([
            SystemMessage(content=system_prompt),
            HumanMessage(content="验证这个表单是否符合规范"),
        ])

        result = self._parse_json(response.content)
        logger.info(f"AI Check: {request.form.code} -> valid={result.get('valid')}")

        issues = [
            SemanticIssue(
                field=i["field"],
                severity=IssueSeverity(i["severity"]),
                message=i["message"]
            )
            for i in result.get("issues", [])
        ]

        return AICheckResponse(
            valid=result.get("valid", False),
            issues=issues,
            suggestions=result.get("suggestions", {}),
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
