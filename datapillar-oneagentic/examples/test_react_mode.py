"""
测试 ReAct 模式（规划-执行-反思）

场景：数据分析任务
1. Planner 规划任务步骤
2. 按计划执行 Agent
3. Reflector 评估结果，决定继续/重试/结束

使用 GLM-4.7 真实 API
"""

import asyncio
import os
from pydantic import BaseModel, Field

from datapillar_oneagentic import (
    agent,
    tool,
    Datapillar,
    Process,
    AgentContext,
    datapillar_configure,
)


# === GLM 配置 ===
GLM_API_KEY = os.environ.get("GLM_API_KEY", "da90d1098b0d4126848881f56ee2197c.B77DUfAuh4To29o7")
GLM_MODEL = "glm-4.7"


# === 工具定义 ===

@tool
def fetch_sales_data(period: str) -> str:
    """获取销售数据

    Args:
        period: 时间周期，如 "2024-Q1", "2024-01"

    Returns:
        销售数据摘要
    """
    # 模拟数据
    data = {
        "2024-Q1": "Q1销售额: 1000万, 订单数: 5000, 客单价: 2000元",
        "2024-Q2": "Q2销售额: 1200万, 订单数: 5500, 客单价: 2182元",
        "2024-Q3": "Q3销售额: 1500万, 订单数: 6000, 客单价: 2500元",
    }
    return data.get(period, f"未找到 {period} 的数据")


@tool
def analyze_trend(data_summary: str) -> str:
    """分析数据趋势

    Args:
        data_summary: 数据摘要

    Returns:
        趋势分析结果
    """
    return f"趋势分析结果: 基于'{data_summary[:50]}...'，销售呈上升趋势，Q3环比增长25%"


@tool
def generate_report(analysis: str, format: str = "markdown") -> str:
    """生成报告

    Args:
        analysis: 分析结果
        format: 报告格式 (markdown/html)

    Returns:
        报告内容
    """
    return f"""
# 销售分析报告

## 分析结论
{analysis}

## 建议
1. 继续保持当前增长势头
2. 关注客单价提升
3. 优化库存管理

---
报告生成时间: 2024-10-01
"""


# === Agent 定义 ===

class DataFetchResult(BaseModel):
    """数据获取结果"""
    data_summary: str = Field(description="数据摘要")
    period: str = Field(description="数据周期")


class AnalysisResult(BaseModel):
    """分析结果"""
    trend: str = Field(description="趋势描述")
    insights: list[str] = Field(description="关键洞察")
    confidence: float = Field(description="置信度 0-1")


class ReportResult(BaseModel):
    """报告结果"""
    report: str = Field(description="报告内容")
    format: str = Field(description="报告格式")


@agent(
    id="data_fetcher",
    name="数据获取员",
    description="从数据源获取原始数据",
    tools=["fetch_sales_data"],
    deliverable_schema=DataFetchResult,
    temperature=0.0,
    max_steps=5,
)
class DataFetcherAgent:
    """数据获取 Agent"""

    SYSTEM_PROMPT = """你是数据获取专家，负责从数据源获取原始数据。

## 工作流程
1. 理解用户需要什么时间段的数据
2. 使用 fetch_sales_data 工具获取数据
3. 整理数据摘要

## 输出格式
{
  "data_summary": "数据摘要内容",
  "period": "数据周期如2024-Q1"
}"""

    async def run(self, ctx: AgentContext) -> DataFetchResult:
        messages = ctx.build_messages(self.SYSTEM_PROMPT)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_output(messages)


@agent(
    id="data_analyst",
    name="数据分析师",
    description="分析数据趋势和规律",
    tools=["analyze_trend"],
    deliverable_schema=AnalysisResult,
    temperature=0.3,
    max_steps=5,
)
class DataAnalystAgent:
    """数据分析 Agent"""

    SYSTEM_PROMPT = """你是数据分析专家，负责分析数据趋势。

## 上游数据
{upstream_data}

## 工作流程
1. 分析上游获取的数据
2. 使用 analyze_trend 工具进行趋势分析
3. 总结关键洞察

## 输出格式
{{
  "trend": "趋势描述",
  "insights": ["洞察1", "洞察2"],
  "confidence": 0.85
}}"""

    async def run(self, ctx: AgentContext) -> AnalysisResult:
        # 获取上游数据
        upstream = await ctx.get_deliverable("data_fetcher")
        if upstream:
            upstream_data = f"数据摘要: {upstream.get('data_summary', '')}\n周期: {upstream.get('period', '')}"
        else:
            upstream_data = "无上游数据"

        prompt = self.SYSTEM_PROMPT.format(upstream_data=upstream_data)
        messages = ctx.build_messages(prompt)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_output(messages)


@agent(
    id="report_generator",
    name="报告生成器",
    description="生成分析报告",
    tools=["generate_report"],
    deliverable_schema=ReportResult,
    temperature=0.0,
    max_steps=3,
)
class ReportGeneratorAgent:
    """报告生成 Agent"""

    SYSTEM_PROMPT = """你是报告生成专家，负责生成分析报告。

## 分析结果
{analysis_result}

## 工作流程
1. 根据分析结果生成报告
2. 使用 generate_report 工具

## 输出格式
{{
  "report": "报告内容",
  "format": "markdown"
}}"""

    async def run(self, ctx: AgentContext) -> ReportResult:
        # 获取上游分析结果
        upstream = await ctx.get_deliverable("data_analyst")
        if upstream:
            analysis_result = f"趋势: {upstream.get('trend', '')}\n洞察: {upstream.get('insights', [])}"
        else:
            analysis_result = "无分析结果"

        prompt = self.SYSTEM_PROMPT.format(analysis_result=analysis_result)
        messages = ctx.build_messages(prompt)
        messages = await ctx.invoke_tools(messages)
        return await ctx.get_output(messages)


def create_react_team() -> Datapillar:
    """创建 ReAct 模式的数据分析团队"""
    return Datapillar(
        namespace="react_demo",
        name="数据分析团队(ReAct)",
        agents=[DataFetcherAgent, DataAnalystAgent, ReportGeneratorAgent],
        process=Process.REACT,  # 使用 ReAct 模式
        enable_share_context=True,
        verbose=True,
    )


async def main():
    """主函数"""
    # 配置 LLM
    datapillar_configure(
        llm={
            "provider": "glm",
            "model": GLM_MODEL,
            "api_key": GLM_API_KEY,
        },
    )

    print("=" * 60)
    print("🧠 ReAct 模式测试")
    print("   模型:", GLM_MODEL)
    print("   流程: 规划 → 执行 → 反思 → 继续/结束")
    print("=" * 60)

    team = create_react_team()

    query = "分析2024年Q1到Q3的销售数据趋势，生成分析报告"
    print(f"\n📝 用户需求: {query}\n")
    print("-" * 60)

    session_id = "react_test_001"

    try:
        async for event in team.stream(query=query, session_id=session_id):
            event_type = event.get("event")

            if event_type == "start":
                print(f"🚀 开始执行")
                print(f"   入口 Agent: {event['data'].get('entry_agent')}")

            elif event_type == "agent":
                agent_id = event["data"].get("agent_id")
                status = event["data"].get("status")
                print(f"\n📍 Agent 执行: {agent_id}")
                print(f"   状态: {status}")
                if event["data"].get("error"):
                    print(f"   错误: {event['data']['error']}")

            elif event_type == "result":
                print("\n" + "=" * 60)
                print("📦 最终结果:")
                deliverables = event["data"].get("deliverables", {})
                for key, value in deliverables.items():
                    print(f"\n[{key}]")
                    if isinstance(value, dict):
                        for k, v in value.items():
                            v_str = str(v)[:100] + "..." if len(str(v)) > 100 else str(v)
                            print(f"  {k}: {v_str}")
                    else:
                        print(f"  {value}")
                print(f"\n⏱️ 耗时: {event['data'].get('duration_ms')}ms")

            elif event_type == "error":
                print(f"\n❌ 错误: {event['data'].get('detail')}")

    except Exception as e:
        print(f"\n❌ 执行异常: {e}")
        import traceback
        traceback.print_exc()

    print("\n" + "=" * 60)
    print("✨ ReAct 模式测试完成")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
