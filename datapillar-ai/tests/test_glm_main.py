"""
GLM 主测文件（只测 GLM 行为，不掺杂 Neo4j/GraphRAG/Agent 流程）。

目标：
- 复现/确认 GLM 在不同调用方式下的返回形态（普通对话 / function calling 结构化输出）
- 粗略量化耗时：直接调用 vs 走项目封装（语义缓存会额外触发 embedding）

运行方式：
- 只跑本文件（推荐打开 -s 看打印）：
  UV_CACHE_DIR=$PWD/.uv_cache XDG_CACHE_HOME=$PWD/.cache \
  RUN_GLM_MAIN_TEST=1 uv run pytest -q tests/test_glm_main.py -s
"""

from __future__ import annotations

import os
import time

import pytest
from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field


def _require_enabled() -> None:
    if os.getenv("RUN_GLM_MAIN_TEST") != "1":
        pytest.skip("跳过 GLM 主测：请设置环境变量 RUN_GLM_MAIN_TEST=1")


def _build_api_base(raw_base: str | None) -> str:
    base = (raw_base or "https://open.bigmodel.cn/api/paas/v4").rstrip("/")
    if base.endswith("/chat/completions"):
        return base
    return f"{base}/chat/completions"


class PingOutput(BaseModel):
    """最小结构化输出，用来验证 function calling 是否可用。"""

    ok: bool = Field(..., description="固定返回 true")
    echo: str = Field(..., description="原样回显用户输入的一小段")


@pytest.mark.asyncio
async def test_glm_main_smoke_and_timing() -> None:
    _require_enabled()

    from langchain_community.chat_models import ChatZhipuAI

    from src.infrastructure.llm.client import call_llm
    from src.infrastructure.llm.embeddings import UnifiedEmbedder
    from src.infrastructure.llm.model_manager import model_manager

    model = model_manager.default_chat_model()
    assert model is not None, "未找到默认 chat 模型（检查 MySQL ai_model 表）"
    assert (
        model.provider.lower() == "glm"
    ), f"当前默认 chat provider 不是 glm，而是 {model.provider!r}"

    api_base = _build_api_base(model.base_url)

    print("\n" + "=" * 80)
    print("【GLM 主测】模型信息（不会输出 api_key）")
    print("=" * 80)
    print(f"provider={model.provider}, model={model.model_name}, api_base={api_base}")

    messages = [
        SystemMessage(content="你是一个严格的助手。"),
        HumanMessage(content="你好，返回一句话：你是谁？"),
    ]

    # 0) embedding 单次耗时（解释 call_llm 为什么慢）
    embedder = UnifiedEmbedder()
    t0 = time.perf_counter()
    _ = embedder.embed_query("glm latency probe")
    t1 = time.perf_counter()
    print("\n" + "-" * 80)
    print(f"[Embedding] 单次 embed_query 耗时: {(t1 - t0):.3f}s")

    # 1) 直接调用 ChatZhipuAI（纯 chat/completions）
    llm = ChatZhipuAI(
        api_key=model.api_key,
        api_base=api_base,
        model=model.model_name,
        temperature=0.0,
        streaming=False,
    )

    t0 = time.perf_counter()
    resp = await llm.ainvoke(messages)
    t1 = time.perf_counter()
    text = (getattr(resp, "content", "") or "").strip()
    print("\n" + "-" * 80)
    print(f"[ChatZhipuAI] 普通对话耗时: {(t1 - t0):.3f}s")
    print(f"[ChatZhipuAI] content(前200): {text[:200]}")
    assert text, "普通对话未返回 content（疑似响应异常）"

    # 2) 直接调用 ChatZhipuAI 的结构化输出（function calling）
    # include_raw=False 时，返回值直接是 Pydantic 对象；解析失败会抛异常。
    structured = llm.with_structured_output(PingOutput, method="function_calling")
    t0 = time.perf_counter()
    out = await structured.ainvoke(
        [
            SystemMessage(content="只允许通过工具调用返回结构化结果。"),
            HumanMessage(content="回显：foo"),
        ]
    )
    t1 = time.perf_counter()
    print("\n" + "-" * 80)
    print(f"[ChatZhipuAI] function_calling 耗时: {(t1 - t0):.3f}s")
    print(f"[ChatZhipuAI] parsed={out!r}")
    assert isinstance(out, PingOutput)
    assert out.ok is True
    assert "foo" in out.echo

    # 3) 走项目封装 call_llm（包含：弹性包装 + 语义缓存 lookup(embedding)）
    llm_wrapped = call_llm(temperature=0.0, output_schema=PingOutput)
    t0 = time.perf_counter()
    wrapped_out = await llm_wrapped.ainvoke(
        [
            SystemMessage(content="只允许通过工具调用返回结构化结果。"),
            HumanMessage(content="回显：bar"),
        ]
    )
    t1 = time.perf_counter()
    print("\n" + "-" * 80)
    print(f"[call_llm] 结构化输出总耗时（含语义缓存 lookup）: {(t1 - t0):.3f}s")
    print(f"[call_llm] output_type={type(wrapped_out)} value={wrapped_out!r}")
    assert isinstance(wrapped_out, PingOutput), "call_llm(output_schema=...) 未返回 Pydantic 对象"


@pytest.mark.asyncio
async def test_glm_main_analysis_result_output_raw() -> None:
    """
    用 AnalysisResultOutput 做一次 structured output，并打印 raw/tool_calls/arguments。

    目的：
    - 观察 GLM 在 function calling 下是否会输出 null / 字符串化 JSON 等不守约情况
    - 不做自愈，不做重试，只做一次请求的观测
    """
    _require_enabled()

    from langchain_community.chat_models import ChatZhipuAI

    from src.infrastructure.llm.model_manager import model_manager

    # 注意：这里用“更严格”的本地 Schema（不带项目里的容错 validator），便于暴露模型的类型不守约问题。
    class StrictStep(BaseModel):
        step_id: str
        step_name: str
        description: str
        input_tables: list[str]
        output_table: str | None = None
        depends_on: list[str] = Field(default_factory=list)

    class StrictDataTarget(BaseModel):
        table_name: str
        write_mode: str = "overwrite"
        partition_by: list[str] = Field(default_factory=list)
        description: str | None = None

    class StrictAmbiguity(BaseModel):
        question: str
        context: str | None = None
        options: list[str] = Field(default_factory=list)

    class StrictAnalysisResultOutput(BaseModel):
        summary: str
        steps: list[StrictStep] = Field(default_factory=list)
        final_target: StrictDataTarget | None = None
        ambiguities: list[StrictAmbiguity] = Field(default_factory=list)
        confidence: float = Field(default=0.5, ge=0.0, le=1.0)

    model = model_manager.default_chat_model()
    assert model is not None, "未找到默认 chat 模型（检查 MySQL ai_model 表）"
    assert (
        model.provider.lower() == "glm"
    ), f"当前默认 chat provider 不是 glm，而是 {model.provider!r}"

    api_base = _build_api_base(model.base_url)
    llm = ChatZhipuAI(
        api_key=model.api_key,
        api_base=api_base,
        model=model.model_name,
        temperature=0.0,
        streaming=False,
    )

    structured = llm.with_structured_output(
        StrictAnalysisResultOutput,
        method="function_calling",
        include_raw=True,
    )

    # 使用复杂场景，增加触发“类型不守约”（null / 字符串化 JSON 等）的概率
    user_query = """
把订单主表 t_ord_main 和订单明细表 t_ord_dtl 关联，清洗后写入 order_detail_clean，要求：
1. 过滤掉已取消的订单
2. 只保留支付宝和微信支付的订单
3. 关联用户表获取用户等级和VIP标记
""".strip()
    out = await structured.ainvoke(
        [
            SystemMessage(
                content=(
                    "你必须通过工具调用返回结构化结果，不要输出自然语言解释。"
                    "请严格遵守字段类型：list 不能为 null；对象字段必须输出对象。"
                )
            ),
            HumanMessage(content=user_query),
        ]
    )

    print("\n" + "=" * 80)
    print("【GLM 观测】StrictAnalysisResultOutput structured output（include_raw=True）")
    print("=" * 80)
    assert isinstance(out, dict), f"include_raw=True 预期返回 dict，实际: {type(out)}"
    raw = out.get("raw")
    parsed = out.get("parsed")
    parsing_error = out.get("parsing_error")
    print(f"parsing_error={parsing_error!r}")
    if raw is not None:
        import json

        tool_calls = getattr(raw, "additional_kwargs", {}).get("tool_calls")
        print(f"raw.tool_calls={tool_calls}")
        if tool_calls and isinstance(tool_calls, list):
            first = tool_calls[0] if tool_calls else None
            func = first.get("function") if isinstance(first, dict) else None
            name = func.get("name") if isinstance(func, dict) else None
            args = func.get("arguments") if isinstance(func, dict) else None
            print(f"first.function.name={name}")
            print(f"first.function.arguments(raw)={args}")
            if isinstance(args, str):
                try:
                    print("first.function.arguments(parsed_json)=")
                    print(json.dumps(json.loads(args), ensure_ascii=False, indent=2))
                except json.JSONDecodeError as e:
                    print(f"arguments 不是合法 JSON：{e}")

    print(f"parsed(type)={type(parsed)} value={parsed!r}")
    if parsing_error is None:
        assert isinstance(parsed, StrictAnalysisResultOutput)


@pytest.mark.asyncio
async def test_glm_main_developer_sql_output_raw() -> None:
    """
    用 DeveloperSqlOutput 做一次 structured output，并打印 raw/tool_calls/arguments。

    目的：
    - 验证 DeveloperAgent 切换为 with_structured_output 后，GLM 返回的 tool_calls 形态
    - 不依赖任何 ETL 工具，不跑 Neo4j
    """
    _require_enabled()

    from langchain_community.chat_models import ChatZhipuAI

    from src.infrastructure.llm.model_manager import model_manager
    from src.modules.etl.schemas.developer import DeveloperSqlOutput

    model = model_manager.default_chat_model()
    assert model is not None, "未找到默认 chat 模型（检查 MySQL ai_model 表）"
    assert (
        model.provider.lower() == "glm"
    ), f"当前默认 chat provider 不是 glm，而是 {model.provider!r}"

    api_base = _build_api_base(model.base_url)
    llm = ChatZhipuAI(
        api_key=model.api_key,
        api_base=api_base,
        model=model.model_name,
        temperature=0.0,
        streaming=False,
    )

    structured = llm.with_structured_output(
        DeveloperSqlOutput,
        method="function_calling",
    )

    out = await structured.ainvoke(
        [
            SystemMessage(
                content=(
                    "你必须通过工具调用返回结构化结果（DeveloperSqlOutput）。"
                    "只返回字段 sql，不要任何解释。"
                    "sql 必须是可执行的 Spark SQL/Hive SQL。"
                )
            ),
            HumanMessage(content="生成一个最简单的可执行SQL：从常量生成一行两列，列名为 a,b。"),
        ]
    )

    print("\n" + "=" * 80)
    print("【GLM 观测】DeveloperSqlOutput structured output（include_raw=False）")
    print("=" * 80)
    print(f"parsed(type)={type(out)} value={out!r}")
    assert isinstance(out, DeveloperSqlOutput)
    assert isinstance(out.sql, str) and out.sql.strip()


@pytest.mark.asyncio
async def test_glm_via_call_llm_complex_structured_output() -> None:  # noqa: C901
    """
    用 call_llm 测试复杂结构化输出 + 工具调用。

    场景：多表关联的 ETL 需求分析，包含：
    - 嵌套对象（StrictStep 列表）
    - 可选字段（final_target）
    - 工具调用（get_table_detail, get_table_lineage）
    """
    _require_enabled()

    import json

    from langchain_core.messages import ToolMessage
    from langchain_core.tools import tool
    from pydantic import field_validator

    from src.infrastructure.llm.client import call_llm
    from src.infrastructure.llm.model_manager import model_manager

    # 复杂 Schema：模拟真实 ETL 分析结果
    class ColumnMapping(BaseModel):
        """列映射"""

        source_column: str = Field(..., description="源列名")
        target_column: str = Field(..., description="目标列名")
        transformation: str = Field(default="direct", description="转换类型: direct/cast/expr")
        expression: str | None = Field(
            default=None, description="转换表达式（当 transformation=expr 时）"
        )

    class StrictStep(BaseModel):
        """ETL 步骤"""

        step_id: str = Field(..., description="步骤ID，如 step_1")
        step_name: str = Field(..., description="步骤名称")
        description: str = Field(..., description="步骤描述")
        input_tables: list[str] = Field(default_factory=list, description="输入表列表")
        output_table: str | None = Field(default=None, description="输出表（临时表或最终表）")
        is_temp_table: bool = Field(default=False, description="是否为临时表")
        join_conditions: list[str] = Field(default_factory=list, description="JOIN 条件列表")
        filter_conditions: list[str] = Field(default_factory=list, description="过滤条件列表")
        column_mappings: list[ColumnMapping] = Field(default_factory=list, description="列映射关系")
        depends_on: list[str] = Field(default_factory=list, description="依赖的步骤ID列表")

        @field_validator(
            "input_tables", "join_conditions", "filter_conditions", "depends_on", mode="before"
        )
        @classmethod
        def coerce_list(cls, v):
            """容错：null -> 空列表，字符串 -> 尝试解析"""
            if v is None:
                return []
            if isinstance(v, str):
                try:
                    parsed = json.loads(v)
                    return parsed if isinstance(parsed, list) else [v]
                except json.JSONDecodeError:
                    return [v]
            return v

        @field_validator("column_mappings", mode="before")
        @classmethod
        def coerce_column_mappings(cls, v):
            """容错：字符串化的列映射列表"""
            if v is None:
                return []
            if isinstance(v, str):
                try:
                    return json.loads(v)
                except json.JSONDecodeError:
                    return []
            return v

    class DataTarget(BaseModel):
        """数据目标表"""

        catalog: str = Field(default="default", description="目录")
        schema_name: str = Field(..., description="Schema 名称")
        table_name: str = Field(..., description="表名")
        write_mode: str = Field(default="overwrite", description="写入模式: overwrite/append")
        partition_by: list[str] = Field(default_factory=list, description="分区字段")
        description: str | None = Field(default=None, description="表描述")

        @field_validator("partition_by", mode="before")
        @classmethod
        def coerce_list(cls, v):
            if v is None:
                return []
            if isinstance(v, str):
                try:
                    parsed = json.loads(v)
                    return parsed if isinstance(parsed, list) else [v]
                except json.JSONDecodeError:
                    return [v]
            return v

    class Ambiguity(BaseModel):
        """需求歧义点"""

        question: str = Field(..., description="问题")
        context: str | None = Field(default=None, description="上下文")
        options: list[str] = Field(default_factory=list, description="可选答案")
        priority: str = Field(default="medium", description="优先级: high/medium/low")

        @field_validator("options", mode="before")
        @classmethod
        def coerce_list(cls, v):
            if v is None:
                return []
            if isinstance(v, str):
                try:
                    parsed = json.loads(v)
                    return parsed if isinstance(parsed, list) else [v]
                except json.JSONDecodeError:
                    return [v]
            return v

    class ComplexAnalysisResult(BaseModel):
        """复杂 ETL 分析结果"""

        summary: str = Field(..., description="需求摘要")
        steps: list[StrictStep] = Field(default_factory=list, description="ETL 步骤列表")
        final_target: DataTarget | None = Field(default=None, description="最终目标表")
        ambiguities: list[Ambiguity] = Field(default_factory=list, description="需求歧义点列表")
        confidence: float = Field(default=0.5, ge=0.0, le=1.0, description="置信度")
        recommended_engine: str = Field(default="spark", description="推荐执行引擎")

        @field_validator("steps", mode="before")
        @classmethod
        def coerce_steps(cls, v):
            """容错：字符串化的步骤列表"""
            if v is None:
                return []
            if isinstance(v, str):
                try:
                    return json.loads(v)
                except json.JSONDecodeError:
                    return []
            return v

        @field_validator("final_target", mode="before")
        @classmethod
        def coerce_final_target(cls, v):
            """容错：字符串化的目标表对象"""
            if v is None:
                return None
            if isinstance(v, str):
                try:
                    return json.loads(v)
                except json.JSONDecodeError:
                    return None
            return v

        @field_validator("ambiguities", mode="before")
        @classmethod
        def coerce_ambiguities(cls, v):
            """容错：字符串化的歧义列表"""
            if v is None:
                return []
            if isinstance(v, str):
                try:
                    return json.loads(v)
                except json.JSONDecodeError:
                    return []
            return v

    # 定义工具
    @tool
    def get_table_detail(catalog: str, schema: str, table: str) -> str:
        """获取表结构详情"""
        mock_tables = {
            "t_ord_main": {
                "columns": [
                    {"name": "order_id", "type": "bigint", "comment": "订单ID"},
                    {"name": "user_id", "type": "bigint", "comment": "用户ID"},
                    {
                        "name": "order_status",
                        "type": "string",
                        "comment": "订单状态: created/paid/shipped/completed/cancelled",
                    },
                    {
                        "name": "pay_method",
                        "type": "string",
                        "comment": "支付方式: alipay/wechat/card/cash",
                    },
                    {"name": "total_amount", "type": "decimal(18,2)", "comment": "订单总金额"},
                    {"name": "create_time", "type": "timestamp", "comment": "创建时间"},
                ],
            },
            "t_ord_dtl": {
                "columns": [
                    {"name": "detail_id", "type": "bigint", "comment": "明细ID"},
                    {"name": "order_id", "type": "bigint", "comment": "订单ID"},
                    {"name": "product_id", "type": "bigint", "comment": "商品ID"},
                    {"name": "quantity", "type": "int", "comment": "数量"},
                    {"name": "unit_price", "type": "decimal(18,2)", "comment": "单价"},
                    {"name": "discount", "type": "decimal(5,2)", "comment": "折扣"},
                ],
            },
            "t_user": {
                "columns": [
                    {"name": "user_id", "type": "bigint", "comment": "用户ID"},
                    {"name": "user_name", "type": "string", "comment": "用户名"},
                    {"name": "user_level", "type": "int", "comment": "用户等级: 1-5"},
                    {"name": "is_vip", "type": "boolean", "comment": "是否VIP"},
                    {"name": "register_time", "type": "timestamp", "comment": "注册时间"},
                ],
            },
            "t_product": {
                "columns": [
                    {"name": "product_id", "type": "bigint", "comment": "商品ID"},
                    {"name": "product_name", "type": "string", "comment": "商品名称"},
                    {"name": "category_id", "type": "bigint", "comment": "类目ID"},
                    {"name": "brand", "type": "string", "comment": "品牌"},
                ],
            },
        }
        table_info = mock_tables.get(table)
        if table_info:
            return json.dumps(
                {"status": "success", "table": f"{catalog}.{schema}.{table}", **table_info},
                ensure_ascii=False,
            )
        return json.dumps({"status": "error", "message": f"表 {table} 不存在"}, ensure_ascii=False)

    @tool
    def get_table_lineage(catalog: str, schema: str, table: str, direction: str = "both") -> str:
        """获取表血缘关系"""
        mock_lineage = {
            "order_detail_clean": {
                "upstream": ["t_ord_main", "t_ord_dtl", "t_user"],
                "downstream": ["dws_order_daily", "ads_user_order_stats"],
            },
        }
        lineage = mock_lineage.get(table, {"upstream": [], "downstream": []})
        return json.dumps(
            {"status": "success", "table": table, "direction": direction, "lineage": lineage},
            ensure_ascii=False,
        )

    model = model_manager.default_chat_model()
    assert model is not None, "未找到默认 chat 模型"

    print("\n" + "=" * 80)
    print("【call_llm + 复杂结构化输出 + 工具调用】")
    print("=" * 80)
    print(f"provider={model.provider}, model={model.model_name}")

    # 使用 call_llm 获取 LLM
    llm = call_llm(temperature=0.0)

    # 绑定工具
    tools = [get_table_detail, get_table_lineage]
    llm_with_tools = llm.bind_tools(tools)

    # 复杂 ETL 需求
    user_query = """
请分析以下复杂 ETL 需求：

将订单主表(t_ord_main)、订单明细表(t_ord_dtl)、用户表(t_user)、商品表(t_product)进行关联，生成订单宽表 order_detail_clean，要求：

1. 关联逻辑：
   - t_ord_main 与 t_ord_dtl 通过 order_id 关联
   - 关联 t_user 获取用户等级和VIP标记
   - 关联 t_product 获取商品名称和品牌

2. 过滤条件：
   - 过滤已取消的订单（order_status != 'cancelled'）
   - 只保留支付宝和微信支付的订单（pay_method in ('alipay', 'wechat')）
   - 只保留VIP用户的订单

3. 字段处理：
   - 计算订单明细金额 = quantity * unit_price * (1 - discount)
   - 添加订单日期字段（从 create_time 提取）

4. 输出要求：
   - 写入目标表 dwd.order_detail_clean
   - 按 order_date 分区
   - 使用 overwrite 模式

请先调用工具获取表结构信息，然后给出完整的分析结果。
""".strip()

    messages = [
        SystemMessage(
            content=(
                "你是 Datapillar 的 AnalystAgent（需求分析师）。\n"
                "你必须先调用工具获取表结构信息，然后通过工具调用返回结构化分析结果。\n"
                "请严格遵守字段类型：list 不能为 null；嵌套对象必须是对象而非字符串。\n"
                "所有步骤必须包含具体的 join_conditions 和 filter_conditions。"
            )
        ),
        HumanMessage(content=user_query),
    ]

    # 第一轮：让 LLM 调用工具
    print("\n--- 第一轮：工具调用 ---")
    response1 = await llm_with_tools.ainvoke(messages)
    messages.append(response1)

    tool_calls = getattr(response1, "tool_calls", [])
    print(f"tool_calls count={len(tool_calls)}")

    if tool_calls:
        for tc in tool_calls:
            print(f"  调用工具: {tc.get('name')}({tc.get('args')})")
            # 执行工具
            tool_name = tc.get("name")
            tool_args = tc.get("args", {})
            if tool_name == "get_table_detail":
                result = get_table_detail.invoke(tool_args)
            elif tool_name == "get_table_lineage":
                result = get_table_lineage.invoke(tool_args)
            else:
                result = json.dumps({"status": "error", "message": f"未知工具: {tool_name}"})
            messages.append(ToolMessage(content=result, tool_call_id=tc.get("id")))
            print(f"    结果: {result[:100]}...")

    # 第二轮：获取结构化输出
    print("\n--- 第二轮：结构化输出 ---")
    structured_llm = llm.with_structured_output(
        ComplexAnalysisResult,
        method="function_calling",
        include_raw=True,
    )

    result = await structured_llm.ainvoke(messages)

    assert isinstance(result, dict), f"include_raw=True 预期返回 dict，实际: {type(result)}"

    raw = result.get("raw")
    parsed = result.get("parsed")
    parsing_error = result.get("parsing_error")

    print(f"parsing_error={parsing_error!r}")

    if parsing_error:
        print(f"❌ 解析失败: {parsing_error}")
        # 打印原始 tool_calls 便于调试
        if raw:
            raw_tool_calls = getattr(raw, "tool_calls", [])
            if raw_tool_calls:
                for tc in raw_tool_calls:
                    print(f"  raw tool_call: {tc.get('name')}")
                    print(f"    args: {json.dumps(tc.get('args'), ensure_ascii=False, indent=2)}")
        pytest.fail(f"结构化输出解析失败: {parsing_error}")

    assert isinstance(parsed, ComplexAnalysisResult), f"解析结果类型错误: {type(parsed)}"

    print("\n✅ 结构化输出成功！")
    print(f"summary: {parsed.summary[:100]}...")
    print(f"steps count: {len(parsed.steps)}")
    print(f"final_target: {parsed.final_target}")
    print(f"ambiguities count: {len(parsed.ambiguities)}")
    print(f"confidence: {parsed.confidence}")
    print(f"recommended_engine: {parsed.recommended_engine}")

    # 验证关键字段
    assert parsed.summary, "summary 不能为空"
    assert len(parsed.steps) >= 3, f"预期至少 3 个步骤，实际 {len(parsed.steps)}"
    assert parsed.final_target is not None, "final_target 不能为空"
    assert (
        parsed.final_target.table_name == "order_detail_clean"
    ), f"目标表名错误: {parsed.final_target.table_name}"

    # 打印详细步骤
    print("\n--- 步骤详情 ---")
    for step in parsed.steps:
        print(f"\n[{step.step_id}] {step.step_name}")
        print(f"  描述: {step.description}")
        print(f"  输入表: {step.input_tables}")
        print(f"  输出表: {step.output_table} (临时表: {step.is_temp_table})")
        if step.join_conditions:
            print(f"  JOIN 条件: {step.join_conditions}")
        if step.filter_conditions:
            print(f"  过滤条件: {step.filter_conditions}")
        if step.column_mappings:
            print(f"  列映射: {len(step.column_mappings)} 个")
        print(f"  依赖: {step.depends_on}")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s", "--tb=short"])
