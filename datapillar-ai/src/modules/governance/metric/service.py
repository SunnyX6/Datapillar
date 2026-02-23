# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
指标 AI 治理服务

架构：简单 LLM 调用
1. 预先获取所有上下文（表详情、推荐）
2. 一次 LLM 调用，使用 structured output 保证输出格式
3. 使用 ainvoke 走缓存
"""

import json
import logging
import time
from functools import lru_cache

from datapillar_oneagentic.messages import Message, Messages
from datapillar_oneagentic.providers.llm import LLMProvider

from src.infrastructure.llm.config import get_datapillar_config
from src.infrastructure.repository.knowledge import (
    Neo4jMetricSearch,
    Neo4jSemanticSearch,
    Neo4jTableSearch,
)
from src.modules.governance.metric.schemas import (
    AIFillOutput,
    AIFillRequest,
    AIFillResponse,
    MetricType,
)
from src.shared.config.runtime import get_default_tenant_id

logger = logging.getLogger(__name__)


@lru_cache(maxsize=128)
def _get_llm_provider(tenant_id: int) -> LLMProvider:
    config = get_datapillar_config(tenant_id)
    return LLMProvider(config.llm)


# ============================================================================
# 风格化示例（纯日常对话，不含业务内容）
# ============================================================================

STYLE_EXAMPLES = [
    Message.system("""你是村头女悍匪，喜好男，性格粗狂但又心思细腻，绰号刀疤莲"""),
    Message.user("求求你，放过我...."),
    Message.assistant("吼吼...不可能！这下终于落入我刀疤莲怀里了吧，桀桀桀~"),
    Message.user("你好，女侠"),
    Message.assistant("记住了啊！老娘行不更名坐不改姓，老娘叫刀疤莲"),
    Message.user("当家的，这个指标怎么办？"),
    Message.assistant("这还用问老娘？？拉出去先打一顿"),
]


# ============================================================================
# Prompt 模板
# ============================================================================

ATOMIC_FILL_PROMPT = """你专门为用户创建原子指标。

## 可用的语义资产
{semantic_assets}

## 表上下文
{table_context}

## 验证流程（必须严格执行）
1. 验证表场景：表描述或列描述必须明确包含用户所说的业务概念。如果表/列没有描述，或描述与用户需求无关，必须返回失败
2. 验证列知识：用户描述的业务概念必须能在列的描述中找到对应。仅靠列名猜测不算验证通过
3. 验证用户选择：公式需要的列都选了吗？

失败时：返回 success=false 和失败原因 message（使用傲娇语气）。

## 禁止（违反则直接返回失败）
1. 禁止根据表名猜测业务场景！
2. 禁止根据列名猜测业务含义！
3. 禁止在没有明确描述支撑的情况下生成指标！
"""


DERIVED_FILL_PROMPT = """你专门为用户生成派生指标。

## 可用的语义资产
{semantic_assets}

## 基础指标上下文
{metric_context}

## 表上下文
{table_context}

## 验证流程（必须严格执行）
1. 验证基础指标：用户描述的业务场景必须和基础指标的描述（description）匹配。如果基础指标没有描述，或描述与用户需求无关，必须返回失败
2. 验证过滤列：用户描述的过滤条件必须能在列的描述中找到对应。仅靠列名猜测不算验证通过
3. 验证修饰符：如果需要修饰符，必须从可用列表中选择

失败时：返回 success=false 和失败原因 message（使用傲娇语气）。

## 禁止（违反则直接返回失败）
1. 禁止在基础指标没有描述时生成派生指标！
2. 禁止根据列名猜测过滤条件含义！
3. 禁止在没有明确描述支撑的情况下生成指标！"""


COMPOSITE_FILL_PROMPT = """你专门为用户生成复合指标。

## 可用的语义资产
{semantic_assets}

## 参与运算的指标上下文
{metric_context}

## 验证流程（必须严格执行）
1. 验证指标描述：每个参与运算的指标必须有描述（description）。如果任意指标没有描述，必须返回失败
2. 验证业务匹配：用户描述的业务概念必须能在指标的描述中找到明确对应。仅靠指标名称或code猜测不算验证通过
3. 验证可计算性：用户描述的运算规则必须能用已选指标完成

失败时：返回 success=false 和失败原因 message（使用傲娇语气）。

## 禁止（违反则直接返回失败）
1. 禁止在任意指标没有描述时生成复合指标！
2. 禁止根据指标名称或code猜测业务含义！
3. 禁止在没有明确描述支撑的情况下生成指标！"""


# ============================================================================
# AI 服务
# ============================================================================


class MetricAIService:
    """指标 AI 治理服务"""

    async def fill(
        self,
        request: AIFillRequest,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> AIFillResponse:
        """
        AI 填写表单

        流程：
        1. 按需检索语义资产
        2. 一次 LLM 调用（使用 structured output）
        3. 返回结果（success=false 时由程序附加 recommendations）
        """
        total_start = time.time()

        resolved_tenant_id = tenant_id or get_default_tenant_id()

        # 1. 按需检索语义资产（基于用户输入的语义）
        semantic_assets = self._search_semantic_assets(
            request.user_input,
            tenant_id=resolved_tenant_id,
            user_id=user_id,
        )

        # 2. 获取表上下文
        table_context = self._get_table_context(
            request,
            tenant_id=resolved_tenant_id,
            user_id=user_id,
        )

        # 3. 获取指标上下文（派生/复合指标用）
        metric_context = self._get_metric_context(
            request,
            tenant_id=resolved_tenant_id,
            user_id=user_id,
        )

        # 4. 获取推荐结果（用于 success=false 时返回）
        _, recommendations_list = self._get_recommendations(
            request,
            tenant_id=resolved_tenant_id,
            user_id=user_id,
        )

        # 5. 构建 prompt
        system_prompt = self._build_system_prompt(
            request.context.metric_type, semantic_assets, table_context, metric_context
        )
        user_message = self._build_user_message(request)

        # 6. 调用 LLM（使用 structured output）
        llm = _get_llm_provider(resolved_tenant_id)(
            output_schema=AIFillOutput,
            temperature=0.3,
            max_tokens=4096,
        )

        messages = Messages(
            [
                Message.system(system_prompt),
                *STYLE_EXAMPLES,
                Message.user(user_message),
            ]
        )

        # structured output 直接返回 AIFillOutput 实例
        output: AIFillOutput = await llm.ainvoke(messages)

        total_elapsed = time.time() - total_start
        logger.info(f"[fill] 总耗时: {total_elapsed:.2f}s, success={output.success}")

        # 如果 success=false，由程序附加 recommendations（避免 LLM 幻觉）
        recs = recommendations_list if not output.success else []
        return AIFillResponse.from_output(output, recs)

    def _get_table_context(
        self,
        request: AIFillRequest,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> str:
        """获取表上下文"""
        ctx = request.context
        catalog, schema, table = None, None, None

        if ctx.metric_type == MetricType.ATOMIC:
            payload = ctx.get_atomic_payload()
            if payload:
                catalog, schema, table = payload.ref_catalog, payload.ref_schema, payload.ref_table

        elif ctx.metric_type == MetricType.DERIVED:
            payload = ctx.get_derived_payload()
            if payload:
                catalog, schema, table = payload.ref_catalog, payload.ref_schema, payload.ref_table

        if catalog and schema and table:
            result = Neo4jTableSearch.get_table_detail(
                catalog,
                schema,
                table,
                tenant_id=tenant_id,
                user_id=user_id,
            )
            if result:
                logger.info(f"[context] 获取表 {table}, {len(result.get('columns') or [])} 列")
                return json.dumps(
                    {
                        "table": result.get("table"),
                        "description": result.get("description"),
                        "columns": result.get("columns"),
                    },
                    ensure_ascii=False,
                    indent=2,
                )

        return "无表信息"

    def _get_metric_context(
        self,
        request: AIFillRequest,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> str:
        """获取指标上下文（派生/复合指标用）"""
        ctx = request.context
        codes: list[str] = []

        if ctx.metric_type == MetricType.DERIVED:
            payload = ctx.get_derived_payload()
            if payload and payload.base_metric:
                codes = [payload.base_metric.code]

        elif ctx.metric_type == MetricType.COMPOSITE:
            payload = ctx.get_composite_payload()
            if payload and payload.metrics:
                codes = [m.code for m in payload.metrics]

        if not codes:
            return "无指标信息"

        metrics = Neo4jMetricSearch.get_metric_context(
            codes,
            tenant_id=tenant_id,
            user_id=user_id,
        )
        if not metrics:
            logger.warning(f"[context] 未找到指标: {codes}")
            return "无指标信息"

        logger.info(f"[context] 获取 {len(metrics)} 个指标上下文")

        result = []
        for m in metrics:
            info = {
                "code": m.get("code"),
                "name": m.get("name"),
                "type": m.get("metric_type"),
                "description": m.get("description") or "无描述",
                "calculationFormula": m.get("calculation_formula") or "无公式",
            }
            if m.get("unit"):
                info["unit"] = m.get("unit")
            if m.get("aggregation_logic"):
                info["aggregationLogic"] = m.get("aggregation_logic")
            result.append(info)

        return json.dumps(result, ensure_ascii=False, indent=2)

    def _get_recommendations(
        self,
        request: AIFillRequest,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> tuple[str, list]:
        """获取推荐结果，返回 (格式化字符串, 原始列表)"""
        if request.context.metric_type == MetricType.ATOMIC:
            return self._recommend_tables(
                request.user_input,
                tenant_id=tenant_id,
                user_id=user_id,
            )
        elif request.context.metric_type == MetricType.DERIVED:
            # 派生指标：推荐指标 + 表/列，统一按 score 排序
            _, metrics_list = self._recommend_metrics(
                request.user_input,
                tenant_id=tenant_id,
                user_id=user_id,
            )
            _, tables_list = self._recommend_tables(
                request.user_input,
                tenant_id=tenant_id,
                user_id=user_id,
            )
            combined = metrics_list + tables_list
            # 统一按 score 降序排列
            combined.sort(key=lambda x: x.get("score", 0), reverse=True)
            return (
                json.dumps({"status": "success", "recommendations": combined}, ensure_ascii=False),
                combined,
            )
        else:
            return self._recommend_metrics(
                request.user_input,
                tenant_id=tenant_id,
                user_id=user_id,
            )

    def _recommend_tables(
        self,
        user_input: str,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> tuple[str, list]:
        """推荐表和列，返回 (格式化字符串, 原始列表)"""
        start = time.time()
        raw_results = Neo4jTableSearch.search_tables(
            query=user_input,
            tenant_id=tenant_id,
            user_id=user_id,
        )

        if not raw_results:
            logger.info(f"[recommend] 推荐表/列无结果, 耗时 {time.time() - start:.3f}s")
            return (
                json.dumps(
                    {
                        "status": "no_results",
                        "message": f"未找到与「{user_input}」相关的表和列",
                        "recommendations": [],
                    },
                    ensure_ascii=False,
                ),
                [],
            )

        # 按表分组
        tables_map: dict[str, dict] = {}
        for item in raw_results:
            item_type = item.get("type")
            path = item.get("path", "")

            if item_type == "Table":
                parts = path.split(".")
                if len(parts) >= 3:
                    table_key = path
                    if table_key not in tables_map:
                        tables_map[table_key] = {
                            "msgType": "table",
                            "catalog": parts[0],
                            "schema": parts[1],
                            "name": parts[2],
                            "fullPath": path,
                            "description": item.get("description"),
                            "tableScore": item.get("score", 0),
                            "columns": [],
                        }
                    elif item.get("score", 0) > tables_map[table_key]["tableScore"]:
                        tables_map[table_key]["tableScore"] = item.get("score", 0)

            elif item_type == "Column":
                table_path = item.get("table")
                if not table_path:
                    parts = path.split(".")
                    if len(parts) >= 4:
                        table_path = ".".join(parts[:3])

                if table_path:
                    parts = table_path.split(".")
                    if len(parts) >= 3:
                        if table_path not in tables_map:
                            tables_map[table_path] = {
                                "msgType": "table",
                                "catalog": parts[0],
                                "schema": parts[1],
                                "name": parts[2],
                                "fullPath": table_path,
                                "description": None,
                                "tableScore": 0,
                                "columns": [],
                            }

                        tables_map[table_path]["columns"].append(
                            {
                                "name": item.get("name"),
                                "dataType": item.get("dataType"),
                                "description": item.get("description"),
                                "score": item.get("score", 0),
                            }
                        )

        # 计算聚合分数：final_score = max(table_score, max_column_score) + bonus
        # bonus = 0.05 × min(matched_column_count, 3)
        for table in tables_map.values():
            table_score = table.get("tableScore", 0)
            columns = table.get("columns", [])
            max_column_score = max((c.get("score", 0) for c in columns), default=0)
            column_count = len(columns)
            bonus = 0.05 * min(column_count, 3)
            table["score"] = max(table_score, max_column_score) + bonus
            # 清理中间字段
            del table["tableScore"]

        recommendations = list(tables_map.values())
        recommendations.sort(key=lambda x: x.get("score", 0), reverse=True)
        for table in recommendations:
            table["columns"].sort(key=lambda x: x.get("score", 0), reverse=True)

        logger.info(
            f"[recommend] 推荐表/列完成, {len(recommendations)} 个表, 耗时 {time.time() - start:.3f}s"
        )

        return (
            json.dumps(
                {"status": "success", "recommendations": recommendations}, ensure_ascii=False
            ),
            recommendations,
        )

    def _recommend_metrics(
        self,
        user_input: str,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> tuple[str, list]:
        """推荐指标，返回 (格式化字符串, 原始列表)"""
        start = time.time()
        raw_results = Neo4jMetricSearch.search_metrics(
            query=user_input,
            tenant_id=tenant_id,
            user_id=user_id,
        )

        if not raw_results:
            logger.info(f"[recommend] 推荐指标无结果, 耗时 {time.time() - start:.3f}s")
            return (
                json.dumps(
                    {
                        "status": "no_results",
                        "message": f"未找到与「{user_input}」相关的指标",
                        "recommendations": [],
                    },
                    ensure_ascii=False,
                ),
                [],
            )

        recommendations = [
            {
                "msgType": "metric",
                "code": m.get("code"),
                "name": m.get("name"),
                "description": m.get("description"),
                "type": m.get("type"),
                "score": m.get("score", 0),
            }
            for m in raw_results[:10]
        ]

        logger.info(
            f"[recommend] 推荐指标完成, {len(recommendations)} 个, 耗时 {time.time() - start:.3f}s"
        )

        return (
            json.dumps(
                {"status": "success", "recommendations": recommendations}, ensure_ascii=False
            ),
            recommendations,
        )

    def _build_system_prompt(
        self, metric_type: MetricType, semantic_assets: str, table_context: str, metric_context: str
    ) -> str:
        """构建 system prompt"""
        if metric_type == MetricType.ATOMIC:
            return ATOMIC_FILL_PROMPT.format(
                semantic_assets=semantic_assets, table_context=table_context
            )
        elif metric_type == MetricType.DERIVED:
            return DERIVED_FILL_PROMPT.format(
                semantic_assets=semantic_assets,
                table_context=table_context,
                metric_context=metric_context,
            )
        else:
            return COMPOSITE_FILL_PROMPT.format(
                semantic_assets=semantic_assets, metric_context=metric_context
            )

    def _search_semantic_assets(
        self,
        user_input: str,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> str:
        """按需检索语义资产（混合检索：向量+全文）"""
        assets = Neo4jSemanticSearch.search_semantic_assets(
            query=user_input,
            top_k=15,
            tenant_id=tenant_id,
            user_id=user_id,
        )
        return self._format_semantic_assets(assets)

    def _format_semantic_assets(self, assets: dict) -> str:
        """格式化语义资产"""
        lines = []

        # 词根
        word_roots = assets.get("word_roots", [])
        if word_roots:
            items = [f"{w['code']}({w.get('name', '')})" for w in word_roots if w.get("code")]
            lines.append(f"### 词根 ({len(items)} 个，按相关度排序)")
            lines.append(", ".join(items))

        # 修饰符
        modifiers = assets.get("modifiers", [])
        if modifiers:
            items = [m["code"] for m in modifiers if m.get("code")]
            lines.append(f"\n### 修饰符 ({len(items)} 个，按相关度排序)")
            lines.append(", ".join(items))

        # 单位
        units = assets.get("units", [])
        if units:
            items = [f"{u['code']}({u.get('symbol', '')})" for u in units if u.get("code")]
            lines.append(f"\n### 单位 ({len(items)} 个，按相关度排序)")
            lines.append(", ".join(items))

        if not lines:
            return "无相关语义资产"

        return "\n".join(lines)

    def _build_user_message(self, request: AIFillRequest) -> str:
        """构建用户消息"""
        ctx = request.context
        parts = [f"用户需求：{request.user_input}"]

        if ctx.metric_type == MetricType.ATOMIC:
            payload = ctx.get_atomic_payload()
            if payload:
                if payload.ref_catalog and payload.ref_schema and payload.ref_table:
                    parts.append(
                        f"\n表信息：{payload.ref_catalog}.{payload.ref_schema}.{payload.ref_table}"
                    )

                if payload.measure_columns:
                    cols = "\n".join(
                        [
                            f"  - {c.name} ({c.type}): {c.comment or '无注释'}"
                            for c in payload.measure_columns
                        ]
                    )
                    parts.append(f"\n用户选择的度量列：\n{cols}")

                if payload.filter_columns:
                    cols = "\n".join(
                        [
                            f"  - {c.name} ({c.type}): 可选值 {[v.key for v in c.values]}"
                            for c in payload.filter_columns
                        ]
                    )
                    parts.append(f"\n用户选择的过滤列：\n{cols}")

        elif ctx.metric_type == MetricType.DERIVED:
            payload = ctx.get_derived_payload()
            if payload:
                if payload.base_metric:
                    bm = payload.base_metric
                    parts.append(
                        f"\n基础指标：{bm.code}({bm.name or ''}): {bm.description or '无描述'}"
                    )

                if payload.ref_catalog and payload.ref_schema and payload.ref_table:
                    parts.append(
                        f"\n表信息：{payload.ref_catalog}.{payload.ref_schema}.{payload.ref_table}"
                    )

                if payload.modifiers:
                    mods = ", ".join([f"{m.code}({m.name})" for m in payload.modifiers])
                    parts.append(f"\n用户选择的修饰符：{mods}")

                if payload.filter_columns:
                    cols = "\n".join(
                        [
                            f"  - {c.name} ({c.type}): 可选值 {[v.key for v in c.values]}"
                            for c in payload.filter_columns
                        ]
                    )
                    parts.append(f"\n用户选择的过滤列：\n{cols}")

        else:  # COMPOSITE
            payload = ctx.get_composite_payload()
            if payload and payload.metrics:
                metrics = "\n".join(
                    [
                        f"  - {m.code}({m.name or ''}): {m.description or '无描述'}"
                        for m in payload.metrics
                    ]
                )
                parts.append(f"\n用户选择的指标：\n{metrics}")

        return "\n".join(parts)


metric_ai_service = MetricAIService()
