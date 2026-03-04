# @author Sunny
# @date 2026-01-27

"""
indicator AI Governance services

Architecture:simple LLM call
1.Get all context beforehand(table details,Recommended)
2.once LLM call,use structured output Guaranteed output format
3.use ainvoke Go cache
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
# stylized example(Pure daily conversation,Does not include business content)
# ============================================================================

STYLE_EXAMPLES = [
    Message.system(
        """You are the female gangster from the village,Like men,Rough personality but thoughtful,Nicknamed Scar Lotus"""
    ),
    Message.user("please,let me go...."),
    Message.assistant(
        "Hoho...Impossible!Now you finally fall into my arms,Scar Lotus,right?, Jie Jie Jie~"
    ),
    Message.user("hello,Heroine"),
    Message.assistant(
        "Remember it!I cant change my name,but I cant change my surname., My name is Scar Lotus"
    ),
    Message.user("Head of the family,What to do with this indicator?"),
    Message.assistant(
        "You don't need to ask my mother about this.??Take him out and give him a good beating first"
    ),
]


# ============================================================================
# Prompt Template
# ============================================================================

ATOMIC_FILL_PROMPT = """You create atomic indicators specifically for users.## Available semantic assets
{semantic_assets}

## table context
{table_context}

## Verification process(must be strictly enforced)
1.Validation table scenario:The table description or column description must clearly contain the business concept as stated by the user.if table/Column has no description,Or the description is irrelevant to user needs,Must return failure
2.Validate column knowledge:The business concept described by the user must be found in the column description..Merely guessing at the column name does not count as passing the verification
3.Validate user selection:Have all the columns required for the formula been selected??

on failure:Return success=false and failure reasons message(Use a arrogant tone).## prohibited(If it is violated,failure will be returned directly.)
1.It is forbidden to guess business scenarios based on table names.!
2.It is forbidden to guess the business meaning based on the listing name.!
3.It is forbidden to generate indicators without clearly describing the support!"""


DERIVED_FILL_PROMPT = """You generate derived metrics specifically for users.## Available semantic assets
{semantic_assets}

## Basic indicator context
{metric_context}

## table context
{table_context}

## Verification process(must be strictly enforced)
1.Verify basic indicators:The business scenario described by the user must be consistent with the description of the basic indicators.(description)match.If the underlying indicator is not described,Or the description is irrelevant to user needs,Must return failure
2.Validate filter columns:The filter conditions of the user description must be found in the column description..Merely guessing at the column name does not count as passing the verification
3.Validation modifier:If modifiers are needed,Must be selected from available list

on failure:Return success=false and failure reasons message(Use a arrogant tone).## prohibited(If it is violated,failure will be returned directly.)
1.It is forbidden to generate derived indicators when the basic indicator has no description.!
2.It is forbidden to guess the meaning of filter conditions based on column names.!
3.It is forbidden to generate indicators without clearly describing the support!"""


COMPOSITE_FILL_PROMPT = """You generate composite metrics specifically for users.## Available semantic assets
{semantic_assets}

## Indicator context involved in the operation
{metric_context}

## Verification process(must be strictly enforced)
1.Verification indicator description:Each indicator involved in the operation must have a description(description).If any indicator has no description,Must return failure
2.Verify business matching:The business concept described by the user must be clearly corresponding in the description of the indicator..Merely by indicator name orcodeGuessing does not count as verification
3.Verify computability:The calculation rules described by the user must be able to be completed with the selected indicator

on failure:Return success=false and failure reasons message(Use a arrogant tone).## prohibited(If it is violated,failure will be returned directly.)
1.It is forbidden to generate composite indicators when any indicator has no description.!
2.It is prohibited to use indicator names orcodeGuess the business meaning!3.It is forbidden to generate indicators without clearly describing the support!"""


# ============================================================================
# # table context
# ============================================================================


class MetricAIService:
    """indicator AI Governance services"""

    async def fill(
        self,
        request: AIFillRequest,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> AIFillResponse:
        """
        AI Fill out the form

        process:1.Retrieve semantic assets on demand
        2.once LLM call(use structured output)
        3.Return results(success=false appended by program recommendations)
        """
        total_start = time.time()

        resolved_tenant_id = tenant_id or get_default_tenant_id()

        # # prohibited(If it is violated,failure will be returned directly.)
        semantic_assets = self._search_semantic_assets(
            request.user_input,
            tenant_id=resolved_tenant_id,
            user_id=user_id,
        )

        # # Available semantic assets
        table_context = self._get_table_context(
            request,
            tenant_id=resolved_tenant_id,
            user_id=user_id,
        )

        # # Basic indicator context
        metric_context = self._get_metric_context(
            request,
            tenant_id=resolved_tenant_id,
            user_id=user_id,
        )

        # # table context
        _, recommendations_list = self._get_recommendations(
            request,
            tenant_id=resolved_tenant_id,
            user_id=user_id,
        )

        # # Verification process(must be strictly enforced)
        system_prompt = self._build_system_prompt(
            request.context.metric_type, semantic_assets, table_context, metric_context
        )
        user_message = self._build_user_message(request)

        # # prohibited(If it is violated,failure will be returned directly.)
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

        # # Available semantic assets
        output: AIFillOutput = await llm.ainvoke(messages)

        total_elapsed = time.time() - total_start
        logger.info(f"[fill] Total time spent:{total_elapsed:.2f}s,success={output.success}")

        # # Indicator context involved in the operation
        recs = recommendations_list if not output.success else []
        return AIFillResponse.from_output(output, recs)

    def _get_table_context(
        self,
        request: AIFillRequest,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> str:
        """Get table context"""
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
                logger.info(
                    f"[context] Get table {table},{len(result.get('columns') or [])} Column"
                )
                return json.dumps(
                    {
                        "table": result.get("table"),
                        "description": result.get("description"),
                        "columns": result.get("columns"),
                    },
                    ensure_ascii=False,
                    indent=2,
                )

        return "No table information"

    def _get_metric_context(
        self,
        request: AIFillRequest,
        *,
        tenant_id: int | None = None,
        user_id: int | None = None,
    ) -> str:
        """Get indicator context(derived/For composite indicators)"""
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
            return "No indicator information"

        metrics = Neo4jMetricSearch.get_metric_context(
            codes,
            tenant_id=tenant_id,
            user_id=user_id,
        )
        if not metrics:
            logger.warning(f"[context] Indicator not found:{codes}")
            return "No indicator information"

        logger.info(f"[context] Get {len(metrics)} indicator context")

        result = []
        for m in metrics:
            info = {
                "code": m.get("code"),
                "name": m.get("name"),
                "type": m.get("metric_type"),
                "description": m.get("description") or "No description",
                "calculationFormula": m.get("calculation_formula") or "No formula",
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
        """Get recommended results,Return (Format string,original list)"""
        if request.context.metric_type == MetricType.ATOMIC:
            return self._recommend_tables(
                request.user_input,
                tenant_id=tenant_id,
                user_id=user_id,
            )
        elif request.context.metric_type == MetricType.DERIVED:
            # # Verification process(must be strictly enforced)
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
            # # prohibited(If it is violated,failure will be returned directly.)
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
        """Recommended tables and columns,Return (Format string,original list)"""
        start = time.time()
        raw_results = Neo4jTableSearch.search_tables(
            query=user_input,
            tenant_id=tenant_id,
            user_id=user_id,
        )

        if not raw_results:
            logger.info(
                f"[recommend] Recommendation table/No results in column,Time consuming {time.time() - start:.3f}s"
            )
            return (
                json.dumps(
                    {
                        "status": "no_results",
                        "message": "Not found with",
                        "recommendations": [],
                    },
                    ensure_ascii=False,
                ),
                [],
            )

        # ============================================================================
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

        # AI service
        # bonus = 0.05 × min(matched_column_count, 3)
        for table in tables_map.values():
            table_score = table.get("tableScore", 0)
            columns = table.get("columns", [])
            max_column_score = max((c.get("score", 0) for c in columns), default=0)
            column_count = len(columns)
            bonus = 0.05 * min(column_count, 3)
            table["score"] = max(table_score, max_column_score) + bonus
            # 1.Retrieve semantic assets on demand(Semantics based on user input)
            del table["tableScore"]

        recommendations = list(tables_map.values())
        recommendations.sort(key=lambda x: x.get("score", 0), reverse=True)
        for table in recommendations:
            table["columns"].sort(key=lambda x: x.get("score", 0), reverse=True)

        logger.info("score")

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
        """recommendations"""
        raw_results = Neo4jMetricSearch.search_metrics(
            query=user_input,
            tenant_id=tenant_id,
            user_id=user_id,
        )

        if not raw_results:
            logger.info("Recommended indicators,Return (Format string,original list)")
            return (
                json.dumps(
                    {
                        "status": "no_results",
                        "message": "message",
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

        logger.info("score")

        return (
            json.dumps(
                {"status": "success", "recommendations": recommendations}, ensure_ascii=False
            ),
            recommendations,
        )

    def _build_system_prompt(
        self, metric_type: MetricType, semantic_assets: str, table_context: str, metric_context: str
    ) -> str:
        """success"""
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
        """recommendations"""
        assets = Neo4jSemanticSearch.search_semantic_assets(
            query=user_input,
            top_k=15,
            tenant_id=tenant_id,
            user_id=user_id,
        )
        return self._format_semantic_assets(assets)

    def _format_semantic_assets(self, assets: dict) -> str:
        """Build system prompt"""
        lines: list[str] = []

        word_roots = assets.get("word_roots", [])
        if word_roots:
            items = [f"{w['code']}({w.get('name', '')})" for w in word_roots if w.get("code")]
            if items:
                lines.append(f"### Word roots ({len(items)})")
                lines.append(", ".join(items))

        modifiers = assets.get("modifiers", [])
        if modifiers:
            items = [f"{m['code']}({m.get('name', '')})" for m in modifiers if m.get("code")]
            if items:
                lines.append(f"### Modifiers ({len(items)})")
                lines.append(", ".join(items))

        units = assets.get("units", [])
        if units:
            items = [f"{u['code']}({u.get('symbol', '')})" for u in units if u.get("code")]
            if items:
                lines.append(f"### Units ({len(items)})")
                lines.append(", ".join(items))

        if not lines:
            return "No semantic assets available."

        return "\n".join(lines)

    def _build_user_message(self, request: AIFillRequest) -> str:
        """No associated semantic assets"""
        ctx = request.context
        parts = [f"User input: {request.user_input}"]

        if ctx.metric_type == MetricType.ATOMIC:
            payload = ctx.get_atomic_payload()
            if payload:
                if payload.ref_catalog and payload.ref_schema and payload.ref_table:
                    parts.append(
                        f"Table: {payload.ref_catalog}.{payload.ref_schema}.{payload.ref_table}"
                    )

                if payload.measure_columns:
                    cols = "\n".join(
                        [
                            f"- {c.name} ({c.type}): {c.comment or 'no comment'}"
                            for c in payload.measure_columns
                        ]
                    )
                    parts.append(f"Measure columns:\n{cols}")

                if payload.filter_columns:
                    cols = "\n".join(
                        [
                            f"- {c.name} ({c.type}): values {[v.key for v in c.values]}"
                            for c in payload.filter_columns
                        ]
                    )
                    parts.append(f"Filter columns:\n{cols}")

        elif ctx.metric_type == MetricType.DERIVED:
            payload = ctx.get_derived_payload()
            if payload:
                if payload.base_metric:
                    base_metric = payload.base_metric
                    parts.append(
                        "Base metric: "
                        f"{base_metric.code}({base_metric.name or ''}) - "
                        f"{base_metric.description or 'no description'}"
                    )

                if payload.ref_catalog and payload.ref_schema and payload.ref_table:
                    parts.append(
                        f"Table: {payload.ref_catalog}.{payload.ref_schema}.{payload.ref_table}"
                    )

                if payload.modifiers:
                    mods = ", ".join([f"{m.code}({m.name})" for m in payload.modifiers])
                    parts.append(f"Modifiers: {mods}")

                if payload.filter_columns:
                    cols = "\n".join(
                        [
                            f"- {c.name} ({c.type}): values {[v.key for v in c.values]}"
                            for c in payload.filter_columns
                        ]
                    )
                    parts.append(f"Filter columns:\n{cols}")

        else:  # COMPOSITE
            payload = ctx.get_composite_payload()
            if payload and payload.metrics:
                metrics = "\n".join(
                    [
                        f"- {m.code}({m.name or ''}): {m.description or 'no description'}"
                        for m in payload.metrics
                    ]
                )
                parts.append(f"Metrics:\n{metrics}")

        return "\n".join(parts)


metric_ai_service = MetricAIService()
