from langchain_core.messages import HumanMessage, SystemMessage


def build_messages(system_prompt: str, user_input: str) -> list[SystemMessage | HumanMessage]:
    return [SystemMessage(content=system_prompt), HumanMessage(content=user_input)]
