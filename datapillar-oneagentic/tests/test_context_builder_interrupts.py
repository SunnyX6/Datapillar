"""ContextBuilder interrupt extraction tests."""

from __future__ import annotations

from dataclasses import dataclass

from datapillar_oneagentic.context import ContextBuilder


@dataclass(slots=True)
class _MockInterrupt:
    value: object
    ns: list[str] | None = None
    id: str | None = None


@dataclass(slots=True)
class _MockTask:
    name: str | None = None
    node: str | None = None
    interrupts: list[_MockInterrupt] | None = None


@dataclass(slots=True)
class _MockSnapshot:
    tasks: list[_MockTask]


def test_extract_interrupts_uses_namespace_for_internal_task_name() -> None:
    snapshot = _MockSnapshot(
        tasks=[
            _MockTask(
                name="__interrupt__",
                interrupts=[
                    _MockInterrupt(
                        value={"message": "need input"},
                        ns=["analyst:0"],
                        id="interrupt-1",
                    )
                ],
            )
        ]
    )

    interrupts = ContextBuilder.extract_interrupts(snapshot)

    assert interrupts == [
        {
            "agent_id": "analyst",
            "payload": {"message": "need input"},
            "interrupt_id": "interrupt-1",
        }
    ]


def test_extract_interrupts_prefers_task_name_when_valid() -> None:
    snapshot = _MockSnapshot(
        tasks=[
            _MockTask(
                name="analyst",
                interrupts=[_MockInterrupt(value="payload", ns=["__interrupt__:0"], id="abc")],
            )
        ]
    )

    interrupts = ContextBuilder.extract_interrupts(snapshot)

    assert interrupts[0]["agent_id"] == "analyst"
    assert interrupts[0]["interrupt_id"] == "abc"
