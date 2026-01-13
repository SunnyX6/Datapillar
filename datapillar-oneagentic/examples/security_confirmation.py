"""
MCP 和 A2A 安全确认机制示例

本示例展示：
1. 如何配置安全确认回调
2. ConfirmationRequest 的完整信息结构
3. 命令行交互确认示例
4. Web 应用确认示例（伪代码）
5. 根据风险等级自定义确认策略

运行命令：
    uv run python examples/security_confirmation.py
"""

import asyncio
import logging
from typing import Any

from datapillar_oneagentic.security import (
    ConfirmationRequest,
    configure_security,
    reset_security_config,
    NoConfirmationCallbackError,
    UserRejectedError,
)
from datapillar_oneagentic.mcp import (
    MCPServerStdio,
    MCPToolkit,
)
from datapillar_oneagentic.a2a import (
    A2AConfig,
    create_a2a_tool,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


# ============================================================================
# 第一部分：确认回调函数示例
# ============================================================================


def cli_confirmation_simple(request: ConfirmationRequest) -> bool:
    """
    简单的命令行确认

    直接使用 to_display_string() 展示信息
    """
    print(request.to_display_string())
    response = input("\n确认执行？(y/N): ").strip().lower()
    return response == "y"


def cli_confirmation_with_risk_check(request: ConfirmationRequest) -> bool:
    """
    带风险等级检查的命令行确认

    - low/medium: 简单确认
    - high: 需要输入 'yes'
    - critical: 需要输入 'YES I UNDERSTAND'
    """
    print("\n" + "=" * 60)
    print(f"⚠️  危险操作确认 - 风险等级: {request.risk_level.upper()}")
    print("=" * 60)

    # 展示基本信息
    print(f"\n操作类型: {request.operation_type}")
    print(f"名称: {request.name}")
    print(f"描述: {request.description}")
    print(f"来源: {request.source}")

    # 展示参数
    print("\n调用参数:")
    for key, value in request.parameters.items():
        value_str = str(value)
        if len(value_str) > 80:
            value_str = value_str[:80] + "..."
        print(f"  {key}: {value_str}")

    # 展示警告
    if request.warnings:
        print("\n风险警告:")
        for warning in request.warnings:
            print(f"  ⚠️  {warning}")

    # 展示元数据（可选）
    if request.metadata:
        print("\n元数据:")
        for key, value in request.metadata.items():
            print(f"  {key}: {value}")

    print("=" * 60)

    # 根据风险等级决定确认方式
    if request.risk_level == "low":
        response = input("\n按 Enter 继续，输入 'n' 取消: ").strip().lower()
        return response != "n"

    elif request.risk_level == "medium":
        response = input("\n确认执行？(y/N): ").strip().lower()
        return response == "y"

    elif request.risk_level == "high":
        response = input("\n⚠️ 高风险操作！请输入 'yes' 确认: ").strip().lower()
        return response == "yes"

    else:  # critical
        print("\n🚨 极高风险操作！")
        print("此操作可能造成不可逆的影响。")
        response = input("请输入 'YES I UNDERSTAND' 确认: ").strip()
        return response == "YES I UNDERSTAND"


def auto_approve_with_logging(request: ConfirmationRequest) -> bool:
    """
    自动批准（仅用于测试环境）

    记录所有操作但自动批准
    """
    logger.warning(
        f"[AUTO-APPROVE] {request.operation_type}: {request.name}\n"
        f"  风险等级: {request.risk_level}\n"
        f"  参数: {request.parameters}\n"
        f"  来源: {request.source}"
    )
    return True


def policy_based_confirmation(request: ConfirmationRequest) -> bool:
    """
    基于策略的确认

    - 白名单工具自动批准
    - 特定来源自动批准
    - 其他需要人工确认
    """
    # 白名单工具（自动批准）
    ALLOWED_TOOLS = {"read_file", "list_directory", "get_weather"}

    # 可信来源（自动批准）
    TRUSTED_SOURCES = {"https://internal.company.com"}

    # 检查白名单
    if request.name in ALLOWED_TOOLS:
        logger.info(f"[POLICY] 白名单工具，自动批准: {request.name}")
        return True

    # 检查可信来源
    if any(request.source.startswith(src) for src in TRUSTED_SOURCES):
        logger.info(f"[POLICY] 可信来源，自动批准: {request.source}")
        return True

    # 低风险操作自动批准
    if request.risk_level == "low":
        logger.info(f"[POLICY] 低风险操作，自动批准: {request.name}")
        return True

    # 其他需要人工确认
    return cli_confirmation_with_risk_check(request)


# ============================================================================
# 第二部分：Web 应用确认示例（伪代码）
# ============================================================================


class WebConfirmationHandler:
    """
    Web 应用确认处理器

    通过 WebSocket 推送确认请求到前端，等待用户响应
    """

    def __init__(self, websocket_manager: Any, timeout: float = 60.0):
        self.websocket_manager = websocket_manager
        self.timeout = timeout
        self.pending_requests: dict[str, asyncio.Future] = {}

    def __call__(self, request: ConfirmationRequest) -> bool:
        """
        同步回调接口

        注意：实际 Web 应用中可能需要异步实现
        """
        import uuid

        request_id = str(uuid.uuid4())

        # 构建前端需要的数据
        payload = {
            "type": "security_confirmation",
            "request_id": request_id,
            "operation_type": request.operation_type,
            "name": request.name,
            "description": request.description,
            "parameters": request.parameters,
            "risk_level": request.risk_level,
            "warnings": request.warnings,
            "source": request.source,
            "metadata": request.metadata,
            # 前端展示用
            "display_string": request.to_display_string(),
        }

        # 推送到前端（伪代码）
        # self.websocket_manager.broadcast(payload)

        # 等待用户响应（伪代码）
        # try:
        #     future = asyncio.get_event_loop().create_future()
        #     self.pending_requests[request_id] = future
        #     result = asyncio.wait_for(future, timeout=self.timeout)
        #     return result
        # except asyncio.TimeoutError:
        #     return False
        # finally:
        #     self.pending_requests.pop(request_id, None)

        logger.info(f"[WEB] 推送确认请求到前端: {request_id}")
        logger.info(f"[WEB] Payload: {payload}")

        # 模拟：这里返回 True 表示用户确认
        return True

    def handle_user_response(self, request_id: str, confirmed: bool) -> None:
        """
        处理前端用户响应

        前端通过 WebSocket 发送确认结果时调用
        """
        if request_id in self.pending_requests:
            self.pending_requests[request_id].set_result(confirmed)


# ============================================================================
# 第三部分：完整使用示例
# ============================================================================


async def demo_mcp_with_confirmation():
    """
    演示 MCP 工具的安全确认流程
    """
    print("\n" + "=" * 60)
    print("演示：MCP 工具安全确认")
    print("=" * 60)

    # 配置安全确认回调
    configure_security(
        require_confirmation=True,
        confirmation_callback=cli_confirmation_with_risk_check,
    )

    try:
        # 创建 MCP 服务器配置
        # 注意：这里使用 filesystem server 作为示例
        # 实际运行需要安装: npx -y @modelcontextprotocol/server-filesystem
        servers = [
            MCPServerStdio(
                command="npx",
                args=["-y", "@modelcontextprotocol/server-filesystem", "/tmp"],
            ),
        ]

        # 创建 MCP 工具
        # 危险工具（如 delete_file）会触发确认
        async with MCPToolkit(servers) as toolkit:
            tools = toolkit.get_tools()

            print(f"\n已加载 {len(tools)} 个 MCP 工具")
            for tool in tools:
                print(f"  - {tool.name}: {tool.description[:50]}...")

    except Exception as e:
        logger.error(f"MCP 演示失败: {e}")
        print(f"\n提示：MCP 演示需要安装 Node.js 和 npx")

    finally:
        reset_security_config()


async def demo_a2a_with_confirmation():
    """
    演示 A2A 远程调用的安全确认流程
    """
    print("\n" + "=" * 60)
    print("演示：A2A 远程调用安全确认")
    print("=" * 60)

    # 配置安全确认回调
    configure_security(
        require_confirmation=True,
        confirmation_callback=cli_confirmation_with_risk_check,
    )

    try:
        # 创建 A2A 配置
        a2a_config = A2AConfig(
            endpoint="https://api.example.com/.well-known/agent.json",
            require_confirmation=True,  # 默认就是 True
        )

        # 创建 A2A 工具
        a2a_tool = create_a2a_tool(a2a_config, name="call_remote_analyst")

        print(f"\n已创建 A2A 工具: {a2a_tool.name}")
        print(f"描述: {a2a_tool.description}")

        # 模拟调用（会触发确认）
        print("\n模拟调用 A2A 工具...")
        try:
            result = await a2a_tool.ainvoke({
                "task": "分析最近一周的销售数据",
                "context": "重点关注华东地区",
            })
            print(f"结果: {result}")
        except UserRejectedError:
            print("用户拒绝了操作")
        except NoConfirmationCallbackError as e:
            print(f"配置错误: {e}")
        except Exception as e:
            # A2A 调用可能因为网络等原因失败
            print(f"调用失败（预期）: {e}")

    finally:
        reset_security_config()


async def demo_no_callback_error():
    """
    演示未配置回调时的错误
    """
    print("\n" + "=" * 60)
    print("演示：未配置回调时的错误处理")
    print("=" * 60)

    # 配置：需要确认但不提供回调
    configure_security(
        require_confirmation=True,
        confirmation_callback=None,  # 故意不配置
    )

    try:
        a2a_config = A2AConfig(
            endpoint="https://api.example.com/.well-known/agent.json",
        )
        a2a_tool = create_a2a_tool(a2a_config)

        # 调用会抛出 NoConfirmationCallbackError
        await a2a_tool.ainvoke({"task": "测试任务"})

    except NoConfirmationCallbackError as e:
        print(f"\n✅ 正确捕获错误: NoConfirmationCallbackError")
        print(f"错误信息: {e}")

    finally:
        reset_security_config()


async def demo_disable_confirmation():
    """
    演示禁用确认（仅限测试环境）
    """
    print("\n" + "=" * 60)
    print("演示：禁用确认（仅限测试环境）")
    print("=" * 60)

    # 方式一：完全禁用确认
    configure_security(require_confirmation=False)
    print("已禁用安全确认（不推荐用于生产环境）")

    reset_security_config()

    # 方式二：自动批准但记录日志
    configure_security(
        require_confirmation=True,
        confirmation_callback=auto_approve_with_logging,
    )
    print("已配置自动批准（带日志记录）")

    reset_security_config()


# ============================================================================
# 第四部分：ConfirmationRequest 结构说明
# ============================================================================


def show_confirmation_request_structure():
    """
    展示 ConfirmationRequest 的完整结构
    """
    print("\n" + "=" * 60)
    print("ConfirmationRequest 结构说明")
    print("=" * 60)

    # 创建示例请求
    example_request = ConfirmationRequest(
        operation_type="mcp_tool",
        name="delete_file",
        description="删除指定路径的文件",
        parameters={
            "path": "/tmp/important_data.txt",
        },
        risk_level="high",
        warnings=[
            "此工具可能执行破坏性操作（删除、修改数据）",
            "此操作不可撤销，重复执行可能产生不同结果",
        ],
        source="MCPClient(stdio://npx @mcp/server-filesystem)",
        metadata={
            "tool_title": "Delete File",
            "annotations": {
                "destructive_hint": True,
                "idempotent_hint": False,
                "open_world_hint": False,
                "read_only_hint": False,
            },
        },
    )

    print("\n字段说明：")
    print(f"  operation_type: {example_request.operation_type}")
    print("    - 'mcp_tool': MCP 工具调用")
    print("    - 'a2a_delegate': A2A 远程 Agent 调用")

    print(f"\n  name: {example_request.name}")
    print("    - 工具或 Agent 的名称")

    print(f"\n  description: {example_request.description}")
    print("    - 工具或 Agent 的描述")

    print(f"\n  parameters: {example_request.parameters}")
    print("    - 完整的调用参数")

    print(f"\n  risk_level: {example_request.risk_level}")
    print("    - 'low': 低风险（只读操作）")
    print("    - 'medium': 中风险（可能修改数据）")
    print("    - 'high': 高风险（破坏性操作）")
    print("    - 'critical': 极高风险（破坏性 + 外部网络）")

    print(f"\n  warnings: {example_request.warnings}")
    print("    - 风险警告列表")

    print(f"\n  source: {example_request.source}")
    print("    - MCP: 服务器地址")
    print("    - A2A: Agent endpoint")

    print(f"\n  metadata: {example_request.metadata}")
    print("    - MCP: tool_title, annotations")
    print("    - A2A: endpoint, require_confirmation, fail_fast")

    print("\n" + "-" * 60)
    print("to_display_string() 输出：")
    print("-" * 60)
    print(example_request.to_display_string())


# ============================================================================
# 主函数
# ============================================================================


async def main():
    """运行所有演示"""
    print("=" * 60)
    print("MCP 和 A2A 安全确认机制示例")
    print("=" * 60)

    # 展示 ConfirmationRequest 结构
    show_confirmation_request_structure()

    # 演示未配置回调时的错误
    await demo_no_callback_error()

    # 演示禁用确认
    await demo_disable_confirmation()

    # 以下演示需要实际的 MCP/A2A 服务
    # await demo_mcp_with_confirmation()
    # await demo_a2a_with_confirmation()

    print("\n" + "=" * 60)
    print("演示完成")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
