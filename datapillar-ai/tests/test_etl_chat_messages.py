from langchain_core.messages import HumanMessage, SystemMessage

from src.modules.etl.utils.chat_messages import build_system_human_messages


def test_build_system_human_messages_roles():
    messages = build_system_human_messages("系统指令", "用户输入")
    assert isinstance(messages[0], SystemMessage)
    assert isinstance(messages[1], HumanMessage)
    assert messages[0].content == "系统指令"
    assert messages[1].content == "用户输入"

