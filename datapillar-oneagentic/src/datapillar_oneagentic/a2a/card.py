"""
AgentCard - Agent 自描述

实现 A2A 协议的 AgentCard 规范，描述 Agent 的能力、技能、认证要求等。
AgentCard 通常暴露在 /.well-known/agent-card.json 端点。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class AgentSkill:
    """
    Agent 技能描述

    描述 Agent 能做什么，帮助其他 Agent 决定是否委派任务。

    属性：
    - id: 技能唯一标识
    - name: 技能名称
    - description: 技能描述
    - tags: 标签列表
    - examples: 示例输入
    """

    id: str
    """技能唯一标识"""

    name: str
    """技能名称"""

    description: str = ""
    """技能描述"""

    tags: list[str] = field(default_factory=list)
    """标签列表"""

    examples: list[str] = field(default_factory=list)
    """示例输入"""

    def to_dict(self) -> dict[str, Any]:
        """转换为字典"""
        return {
            "id": self.id,
            "name": self.name,
            "description": self.description,
            "tags": self.tags,
            "examples": self.examples,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> AgentSkill:
        """从字典创建"""
        return cls(
            id=data.get("id", ""),
            name=data.get("name", ""),
            description=data.get("description", ""),
            tags=data.get("tags", []),
            examples=data.get("examples", []),
        )


@dataclass
class AgentCard:
    """
    Agent 自描述卡片

    遵循 A2A 协议规范，描述 Agent 的身份、能力、认证要求等。
    通常暴露在 /.well-known/agent-card.json 端点。

    属性：
    - name: Agent 名称
    - description: Agent 描述
    - url: Agent 服务 URL
    - version: 版本号
    - skills: 技能列表
    - supported_modes: 支持的交互模式
    - auth_required: 是否需要认证
    - auth_schemes: 支持的认证方式

    使用示例：
    ```python
    card = AgentCard(
        name="数据分析师",
        description="擅长数据分析和报表生成",
        url="https://api.example.com/agent",
        skills=[
            AgentSkill(
                id="data_analysis",
                name="数据分析",
                description="分析数据并生成洞察",
            ),
        ],
    )
    ```
    """

    name: str
    """Agent 名称"""

    description: str = ""
    """Agent 描述"""

    url: str = ""
    """Agent 服务 URL"""

    version: str = "1.0.0"
    """版本号"""

    skills: list[AgentSkill] = field(default_factory=list)
    """技能列表"""

    supported_modes: list[str] = field(default_factory=lambda: ["streaming", "sync"])
    """支持的交互模式：streaming, sync, async"""

    auth_required: bool = False
    """是否需要认证"""

    auth_schemes: list[str] = field(default_factory=list)
    """支持的认证方式：api_key, bearer, oauth2"""

    metadata: dict[str, Any] = field(default_factory=dict)
    """额外元数据"""

    def to_dict(self) -> dict[str, Any]:
        """转换为字典（用于 JSON 序列化）"""
        return {
            "name": self.name,
            "description": self.description,
            "url": self.url,
            "version": self.version,
            "skills": [s.to_dict() for s in self.skills],
            "supported_modes": self.supported_modes,
            "auth_required": self.auth_required,
            "auth_schemes": self.auth_schemes,
            "metadata": self.metadata,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> AgentCard:
        """从字典创建"""
        skills_data = data.get("skills", [])
        skills = [AgentSkill.from_dict(s) for s in skills_data]

        return cls(
            name=data.get("name", ""),
            description=data.get("description", ""),
            url=data.get("url", ""),
            version=data.get("version", "1.0.0"),
            skills=skills,
            supported_modes=data.get("supported_modes", ["streaming", "sync"]),
            auth_required=data.get("auth_required", False),
            auth_schemes=data.get("auth_schemes", []),
            metadata=data.get("metadata", {}),
        )

    def get_skill(self, skill_id: str) -> AgentSkill | None:
        """根据 ID 获取技能"""
        for skill in self.skills:
            if skill.id == skill_id:
                return skill
        return None

    def has_skill(self, skill_id: str) -> bool:
        """检查是否有某个技能"""
        return self.get_skill(skill_id) is not None
