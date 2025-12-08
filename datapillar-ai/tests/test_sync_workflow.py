"""
测试多智能体工作流 - 简单的同步任务

测试场景：从 MySQL 同步数据到 Hive
"""

import requests
import json
import uuid
from datetime import datetime


def login():
    """登录获取认证 Cookie"""
    url = "http://localhost:7000/data-builder-auth/api/auth/login"
    payload = {
        "username": "sunny",
        "password": "123456asd"
    }

    response = requests.post(url, json=payload)
    response.raise_for_status()

    # 从响应中获取 cookies
    cookies = response.cookies
    print(f"✅ 登录成功，用户: sunny")
    print(f"📝 Cookies: {dict(cookies)}")
    return cookies


def test_sync_workflow(cookies):
    """测试同步工作流生成（完整流程）"""
    url = "http://localhost:5000/api/agent/workflow/sse"

    # 生成唯一的 session ID
    session_id = str(uuid.uuid4())

    # ========== 第一阶段：首次请求（到中断点）==========
    print(f"\n{'='*60}")
    print(f"🚀 阶段 1：首次请求（获取推荐数据）")
    print(f"{'='*60}")
    print(f"📋 Session ID: {session_id}")
    print(f"📝 用户输入: 从 MySQL 的 mysql_order 表同步数据到 Hive 的 ods_order 表，执行全量同步")
    print(f"{'='*60}\n")

    payload = {
        "sessionId": session_id,
        "userInput": "从 MySQL 的 mysql_order 表同步数据到 Hive 的 ods_order 表，执行全量同步",
        "resumeValue": None
    }

    response = requests.post(
        url,
        json=payload,
        cookies=cookies,
        headers={
            "Content-Type": "application/json",
            "Accept": "text/event-stream"
        },
        stream=True
    )

    response.raise_for_status()

    print(f"⏳ 接收流式响应...\n")

    # 解析 SSE 流，获取推荐数据
    event_count = 0
    recommended_data = None

    for line in response.iter_lines():
        if line:
            line_str = line.decode('utf-8')

            if line_str.startswith('data: '):
                event_count += 1
                data_str = line_str[6:]

                try:
                    event_data = json.loads(data_str)
                    event_type = event_data.get('eventType', 'unknown')
                    title = event_data.get('title', 'System')
                    description = event_data.get('description', '')
                    status = event_data.get('status', '')

                    icon_map = {
                        'session_started': '🎬',
                        'agent_thinking': '🤔',
                        'call_tool': '🔧',
                        'plan': '📋',
                        'code': '💻',
                        'session_interrupted': '⏸️',
                        'session_completed': '✅',
                        'session_error': '❌'
                    }
                    icon = icon_map.get(event_type, '📡')

                    print(f"{icon} [{event_count}] {title} - {event_type}")
                    print(f"   状态: {status}")
                    print(f"   描述: {description}")

                    response_data = event_data.get('response', {})
                    if response_data and response_data.get('data'):
                        data = response_data['data']

                        if event_type == 'session_interrupted':
                            recommended_data = data.get('recommendedData', {})
                            print(f"   ⏸️ 收到推荐数据:")
                            print(f"      - 源表: {recommended_data.get('source_table')}")
                            print(f"      - 目标表: {recommended_data.get('target_table')}")
                            mappings = recommended_data.get('column_mappings', [])
                            print(f"      - 列映射数量: {len(mappings)}")
                            print(f"\n      📋 详细映射（前5条）:")
                            for i, mapping in enumerate(mappings[:5], 1):
                                print(f"         {i}. {mapping.get('source_column')} → {mapping.get('target_column')}")

                    print()

                    if event_type in ['session_completed', 'session_error', 'session_interrupted']:
                        break

                except json.JSONDecodeError as e:
                    print(f"⚠️  JSON 解析失败: {e}")

    print(f"{'='*60}")
    print(f"✅ 阶段 1 完成，共接收 {event_count} 个事件")
    print(f"{'='*60}\n")

    if not recommended_data:
        print("❌ 未收到推荐数据，测试失败")
        return

    # ========== 第二阶段：用户确认并恢复执行 ==========
    print(f"\n{'='*60}")
    print(f"🔄 阶段 2：用户确认并恢复执行")
    print(f"{'='*60}")
    print(f"📋 Session ID: {session_id} (相同)")
    print(f"✅ 用户确认推荐数据（不修改）")
    print(f"{'='*60}\n")

    # 用户确认推荐数据（保持不变）
    payload_resume = {
        "sessionId": session_id,
        "userInput": None,  # 恢复执行时不需要新输入
        "resumeValue": recommended_data  # 用户确认的数据
    }

    response2 = requests.post(
        url,
        json=payload_resume,
        cookies=cookies,
        headers={
            "Content-Type": "application/json",
            "Accept": "text/event-stream"
        },
        stream=True
    )

    response2.raise_for_status()

    print(f"⏳ 接收流式响应...\n")

    event_count2 = 0
    for line in response2.iter_lines():
        if line:
            line_str = line.decode('utf-8')

            if line_str.startswith('data: '):
                event_count2 += 1
                data_str = line_str[6:]

                try:
                    event_data = json.loads(data_str)
                    event_type = event_data.get('eventType', 'unknown')
                    title = event_data.get('title', 'System')
                    description = event_data.get('description', '')
                    status = event_data.get('status', '')

                    icon_map = {
                        'session_started': '🎬',
                        'agent_thinking': '🤔',
                        'call_tool': '🔧',
                        'plan': '📋',
                        'code': '💻',
                        'session_interrupted': '⏸️',
                        'session_completed': '✅',
                        'session_error': '❌'
                    }
                    icon = icon_map.get(event_type, '📡')

                    print(f"{icon} [{event_count2}] {title} - {event_type}")
                    print(f"   状态: {status}")
                    print(f"   描述: {description}")

                    response_data = event_data.get('response', {})
                    if response_data and response_data.get('data'):
                        data = response_data['data']

                        if event_type == 'plan':
                            print(f"   📋 执行计划:")
                            print(f"      - 工作流名称: {data.get('workflowName', 'N/A')}")
                            print(f"      - 总步骤: {data.get('totalSteps', 'N/A')}")
                            steps = data.get('steps', [])
                            for i, step in enumerate(steps, 1):
                                print(f"      {i}. {step.get('stepName', 'N/A')}")

                        elif event_type == 'code':
                            print(f"   💻 工作流配置:")
                            print(f"      - 工作流名称: {data.get('workflowName', 'N/A')}")
                            print(f"      - 描述: {data.get('description', 'N/A')}")
                            nodes = data.get('nodes', [])
                            print(f"      - 节点数量: {len(nodes)}")
                            for node in nodes:
                                node_data = node.get('data', {})
                                print(f"         · {node_data.get('label', 'N/A')} ({node.get('type', 'N/A')})")

                    print()

                    if event_type in ['session_completed', 'session_error']:
                        break

                except json.JSONDecodeError as e:
                    print(f"⚠️  JSON 解析失败: {e}")

    print(f"{'='*60}")
    print(f"🏁 阶段 2 完成，共接收 {event_count2} 个事件")
    print(f"{'='*60}\n")

    print(f"\n{'='*60}")
    print(f"🎉 完整流程测试完成！")
    print(f"   - 阶段 1 事件数: {event_count}")
    print(f"   - 阶段 2 事件数: {event_count2}")
    print(f"   - 总事件数: {event_count + event_count2}")
    print(f"{'='*60}\n")


def main():
    """主函数"""
    try:
        # 1. 登录获取认证
        cookies = login()

        # 2. 测试工作流生成
        test_sync_workflow(cookies)

    except requests.exceptions.RequestException as e:
        print(f"❌ 请求失败: {e}")
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()
