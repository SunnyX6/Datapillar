"""
执行模式枚举

定义团队的执行策略。
"""

from enum import Enum


class Process(str, Enum):
    """
    执行模式

    控制团队内 Agent 的协作方式。
    """

    SEQUENTIAL = "sequential"
    """
    顺序执行

    按 agents 列表顺序依次执行：
    - Agent 1 执行完毕 → Agent 2 → Agent 3 → ...
    - 每个 Agent 的输出自动作为下一个的上下文
    - 适用于明确的流水线场景（分析→设计→开发→审核）
    """

    DYNAMIC = "dynamic"
    """
    动态执行

    Agent 自主决定是否委派：
    - 从第一个 Agent 开始
    - Agent 判断自己能否完成，不能则委派给其他 Agent
    - 通过 can_delegate_to 配置约束委派范围
    - 适用于灵活的协作场景
    """

    HIERARCHICAL = "hierarchical"
    """
    层级执行

    Manager Agent 协调分配任务：
    - Manager 接收用户请求，规划任务
    - Manager 将任务分配给合适的 Agent
    - Agent 执行完毕后汇报给 Manager
    - Manager 汇总结果或继续分配
    - 适用于复杂的多任务场景
    """

    MAPREDUCE = "mapreduce"
    """
    MapReduce 执行

    任务分解 → 并行执行 → 统一汇总：
    - Planner 拆分独立任务并分配 Agent
    - Map 阶段并行执行任务
    - Reducer 汇总结果生成最终交付（默认使用最后一个 Agent 的 schema）
    - 适用于可并行化且需要汇总的场景
    """

    REACT = "react"
    """
    ReAct 模式（规划-执行-反思）

    智能规划与反思循环：
    - Controller 根据用户目标生成任务计划
    - 按计划调度 Agent 执行任务
    - 执行后反思结果，决定下一步（继续/重试/重规划/结束）
    - 适用于复杂目标、需要动态调整的场景
    """
