"""
知识领域定义

KnowledgeDomain 定义一个知识领域的结构：
- domain_id: 领域唯一标识
- name: 领域名称
- level: 知识层级（公司/领域/Agent）
- content: 知识内容（方法论，不是数据）
- owner_agent_id: 所有者（Agent 知识才有）

设计原则：
- 知识内容必须是方法论/模式/规范
- 禁止包含具体数据（表名、字段名等）
- 应该引导 Agent 使用工具获取实时数据
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from typing import Any

logger = logging.getLogger(__name__)


class KnowledgeLevel(Enum):
    """
    知识层级

    - COMPANY: 公司知识（永久存在）
    - DOMAIN: 领域知识（领域存在则存在）
    - AGENT: Agent 知识（与 Agent 生命周期绑定）
    """

    COMPANY = "company"
    DOMAIN = "domain"
    AGENT = "agent"


@dataclass
class KnowledgeDomain:
    """
    知识领域

    使用示例：
    ```python
    etl_methodology = KnowledgeDomain(
        domain_id="etl_methodology",
        name="ETL 方法论",
        level=KnowledgeLevel.COMPANY,
        content='''
        ## ETL 需求分析方法论

        1. 先确认数据源（调用 list_catalogs 工具）
        2. 理解表结构（调用 get_table_detail 工具）
        3. 分析血缘关系（调用 get_table_lineage 工具）

        注意：所有表名、字段名必须通过工具实时获取
        ''',
    )
    ```
    """

    domain_id: str
    """领域唯一标识"""

    name: str
    """领域名称（显示用）"""

    level: KnowledgeLevel
    """知识层级"""

    content: str
    """知识内容（方法论，不是数据）"""

    owner_agent_id: str | None = None
    """所有者 Agent ID（仅 AGENT 级别知识需要）"""

    tags: list[str] = field(default_factory=list)
    """标签（用于检索）"""

    version: str = "1.0.0"
    """版本号"""

    created_at: datetime = field(default_factory=datetime.now)
    """创建时间"""

    updated_at: datetime = field(default_factory=datetime.now)
    """更新时间"""

    metadata: dict[str, Any] = field(default_factory=dict)
    """额外元数据"""

    def __post_init__(self):
        """校验知识领域"""
        if not self.domain_id:
            raise ValueError("domain_id 不能为空")
        if not self.name:
            raise ValueError("name 不能为空")
        if not self.content:
            raise ValueError("content 不能为空")

        # AGENT 级别知识必须有所有者
        if self.level == KnowledgeLevel.AGENT and not self.owner_agent_id:
            raise ValueError("AGENT 级别知识必须指定 owner_agent_id")

        # 校验知识内容是否符合规范
        warnings = self._validate_content()
        for warning in warnings:
            logger.warning(f"知识 {self.domain_id} 可能存在问题: {warning}")

    def _validate_content(self) -> list[str]:
        """
        校验知识内容是否符合规范

        检查是否包含可能是具体数据的内容
        """
        warnings = []
        content_lower = self.content.lower()

        # 检查是否包含类似表名的模式（如 xxx.xxx.xxx）
        table_pattern = r'\b[a-z_]+\.[a-z_]+\.[a-z_]+\b'
        if re.search(table_pattern, content_lower):
            matches = re.findall(table_pattern, content_lower)
            suspicious = [m for m in matches if not m.startswith(('e.g.', 'i.e.'))]
            if suspicious:
                warnings.append(
                    f"内容可能包含具体表名: {suspicious[:3]}... "
                    "知识应该只包含方法论，具体表名应通过工具获取"
                )

        # 检查是否包含 SQL 语句
        sql_keywords = ['select ', 'insert ', 'update ', 'delete ', 'create table']
        for keyword in sql_keywords:
            if keyword in content_lower:
                warnings.append(
                    f"内容包含 SQL 语句（{keyword.strip()}），"
                    "如果是示例 SQL 请确保不包含具体表名"
                )
                break

        return warnings

    def to_prompt(self) -> str:
        """转换为可注入 Prompt 的格式"""
        return f"""## {self.name}

{self.content}
"""

    def summary(self, max_length: int = 200) -> str:
        """获取知识摘要"""
        content = self.content.strip()
        if len(content) <= max_length:
            return content
        return content[:max_length] + "..."


@dataclass
class AgentKnowledgeContribution:
    """
    Agent 知识贡献

    Agent 注册时可以带来的知识
    """

    domain_id: str
    """领域 ID（将自动加上 agent_id 前缀）"""

    name: str
    """知识名称"""

    content: str
    """知识内容"""

    tags: list[str] = field(default_factory=list)
    """标签"""

    def to_domain(self, agent_id: str) -> KnowledgeDomain:
        """转换为 KnowledgeDomain"""
        return KnowledgeDomain(
            domain_id=f"{agent_id}:{self.domain_id}",
            name=self.name,
            level=KnowledgeLevel.AGENT,
            content=self.content,
            owner_agent_id=agent_id,
            tags=self.tags,
        )
