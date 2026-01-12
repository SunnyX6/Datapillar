"""
KnowledgeStore - 知识仓库

管理所有知识的注册、获取、归档、销毁。

设计原则：
- 知识分层：公司 > 领域 > Agent
- 生命周期：Agent 知识与 Agent 生命周期绑定
- 按需获取：根据 AgentSpec.knowledge_domains 获取
- 不污染：知识只注入 Context，不进 Blackboard

使用示例：
```python
# 注册公司知识
KnowledgeStore.register_domain(data_analysis_methodology)

# Agent 入职时注册知识
KnowledgeStore.register_agent_knowledge("analyst", [my_contribution])

# 按需获取知识
knowledge = KnowledgeStore.get_knowledge(
    domains=["data_analysis"],
    agent_id="analyst",
)
```
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import datetime
from typing import Any

from datapillar_oneagentic.knowledge.domain import (
    AgentKnowledgeContribution,
    KnowledgeDomain,
    KnowledgeLevel,
)

logger = logging.getLogger(__name__)


@dataclass
class KnowledgeQuery:
    """知识查询结果"""

    domain_id: str
    """领域 ID"""

    name: str
    """领域名称"""

    level: KnowledgeLevel
    """知识层级"""

    content: str
    """知识内容"""

    source: str
    """来源（公司/领域名/Agent ID）"""


@dataclass
class ArchivedKnowledge:
    """归档的知识"""

    domain: KnowledgeDomain
    """原始知识"""

    archived_at: datetime
    """归档时间"""

    reason: str
    """归档原因"""


class KnowledgeStore:
    """
    知识仓库

    管理所有知识的注册、获取、归档、销毁。

    知识分层：
    - L1: 公司知识（COMPANY）- 永久存在
    - L2: 领域知识（DOMAIN）- 领域存在则存在
    - L3: Agent 知识（AGENT）- 与 Agent 生命周期绑定
    """

    # 全局单例存储
    _domains: dict[str, KnowledgeDomain] = {}
    _archived: dict[str, ArchivedKnowledge] = {}

    # ==================== 注册/注销 ====================

    @classmethod
    def register_domain(cls, domain: KnowledgeDomain) -> None:
        """
        注册知识领域

        Args:
            domain: 知识领域
        """
        if domain.domain_id in cls._domains:
            logger.warning(f"知识领域 {domain.domain_id} 已存在，将被覆盖")

        cls._domains[domain.domain_id] = domain
        logger.info(
            f"知识注册: {domain.name} ({domain.domain_id}), "
            f"层级: {domain.level.value}"
        )

    @classmethod
    def register_domains(cls, domains: list[KnowledgeDomain]) -> None:
        """批量注册知识领域"""
        for domain in domains:
            cls.register_domain(domain)

    @classmethod
    def unregister_domain(cls, domain_id: str) -> KnowledgeDomain | None:
        """
        注销知识领域

        Args:
            domain_id: 领域 ID

        Returns:
            被注销的领域，不存在则返回 None
        """
        domain = cls._domains.pop(domain_id, None)
        if domain:
            logger.info(f"知识注销: {domain.name} ({domain_id})")
        return domain

    # ==================== Agent 知识管理 ====================

    @classmethod
    def register_agent_knowledge(
        cls,
        agent_id: str,
        contributions: list[AgentKnowledgeContribution],
    ) -> None:
        """
        注册 Agent 带来的知识

        Agent 入职时调用，知识与 Agent 生命周期绑定。

        Args:
            agent_id: Agent ID
            contributions: 知识贡献列表
        """
        for contribution in contributions:
            domain = contribution.to_domain(agent_id)
            cls.register_domain(domain)

        logger.info(f"Agent {agent_id} 带来 {len(contributions)} 条知识")

    @classmethod
    def archive_agent_knowledge(cls, agent_id: str, reason: str = "Agent 注销") -> int:
        """
        归档 Agent 知识

        Agent 注销时调用，知识归档但不删除，可用于审计或恢复。

        Args:
            agent_id: Agent ID
            reason: 归档原因

        Returns:
            归档的知识数量
        """
        archived_count = 0
        to_archive = []

        for domain_id, domain in cls._domains.items():
            if domain.level == KnowledgeLevel.AGENT and domain.owner_agent_id == agent_id:
                to_archive.append(domain_id)

        for domain_id in to_archive:
            domain = cls._domains.pop(domain_id)
            cls._archived[domain_id] = ArchivedKnowledge(
                domain=domain,
                archived_at=datetime.now(),
                reason=reason,
            )
            archived_count += 1

        if archived_count > 0:
            logger.info(f"Agent {agent_id} 的 {archived_count} 条知识已归档")

        return archived_count

    @classmethod
    def delete_agent_knowledge(cls, agent_id: str) -> int:
        """
        销毁 Agent 知识

        彻底删除知识，不可恢复。

        Args:
            agent_id: Agent ID

        Returns:
            删除的知识数量
        """
        deleted_count = 0
        to_delete = []

        for domain_id, domain in cls._domains.items():
            if domain.level == KnowledgeLevel.AGENT and domain.owner_agent_id == agent_id:
                to_delete.append(domain_id)

        for domain_id in to_delete:
            cls._domains.pop(domain_id)
            deleted_count += 1

        if deleted_count > 0:
            logger.info(f"Agent {agent_id} 的 {deleted_count} 条知识已销毁")

        return deleted_count

    @classmethod
    def restore_agent_knowledge(cls, agent_id: str) -> int:
        """
        恢复归档的 Agent 知识

        Args:
            agent_id: Agent ID

        Returns:
            恢复的知识数量
        """
        restored_count = 0
        to_restore = []

        for domain_id, archived in cls._archived.items():
            if archived.domain.owner_agent_id == agent_id:
                to_restore.append(domain_id)

        for domain_id in to_restore:
            archived = cls._archived.pop(domain_id)
            cls._domains[domain_id] = archived.domain
            restored_count += 1

        if restored_count > 0:
            logger.info(f"Agent {agent_id} 的 {restored_count} 条知识已恢复")

        return restored_count

    # ==================== 知识获取 ====================

    @classmethod
    def get_knowledge(
        cls,
        domains: list[str],
        agent_id: str | None = None,
        max_tokens: int = 4000,
        include_agent_knowledge: bool = True,
    ) -> dict[str, Any]:
        """
        获取知识（按需）

        根据请求的领域获取知识，组装成可注入 Prompt 的格式。

        Args:
            domains: 需要的知识领域 ID 列表
            agent_id: 请求者 Agent ID（用于获取 Agent 自己的知识）
            max_tokens: 最大 token 数（粗略估算，1 token ≈ 2 字符）
            include_agent_knowledge: 是否包含 Agent 知识

        Returns:
            {
                "domains": {domain_id: content, ...},
                "summary": "知识摘要",
                "total_chars": 字符数,
            }
        """
        result_domains: dict[str, str] = {}
        total_chars = 0
        max_chars = max_tokens * 2

        for domain_id in domains:
            if domain_id in cls._domains:
                domain = cls._domains[domain_id]

                if domain.level == KnowledgeLevel.AGENT:
                    if not include_agent_knowledge:
                        continue
                    if domain.owner_agent_id != agent_id:
                        continue

                content = domain.to_prompt()
                if total_chars + len(content) <= max_chars:
                    result_domains[domain_id] = content
                    total_chars += len(content)
                else:
                    summary = domain.summary(max_length=500)
                    result_domains[domain_id] = f"## {domain.name}\n\n{summary}\n\n（知识已截断）"
                    total_chars += len(summary) + 50
                    logger.warning(f"知识 {domain_id} 因超出限制被截断")

        if agent_id and include_agent_knowledge:
            for domain_id, domain in cls._domains.items():
                if (
                    domain.level == KnowledgeLevel.AGENT
                    and domain.owner_agent_id == agent_id
                    and domain_id not in result_domains
                ):
                    content = domain.to_prompt()
                    if total_chars + len(content) <= max_chars:
                        result_domains[domain_id] = content
                        total_chars += len(content)

        return {
            "domains": result_domains,
            "summary": f"已加载 {len(result_domains)} 个知识领域",
            "total_chars": total_chars,
        }

    @classmethod
    def get_domain(cls, domain_id: str) -> KnowledgeDomain | None:
        """获取单个知识领域"""
        return cls._domains.get(domain_id)

    @classmethod
    def list_domains(
        cls,
        level: KnowledgeLevel | None = None,
        agent_id: str | None = None,
    ) -> list[KnowledgeDomain]:
        """
        列出知识领域

        Args:
            level: 过滤层级
            agent_id: 过滤所有者

        Returns:
            知识领域列表
        """
        result = []
        for domain in cls._domains.values():
            if level and domain.level != level:
                continue
            if agent_id and domain.owner_agent_id != agent_id:
                continue
            result.append(domain)
        return result

    @classmethod
    def search_domains(cls, keyword: str) -> list[KnowledgeDomain]:
        """
        搜索知识领域

        Args:
            keyword: 关键词

        Returns:
            匹配的知识领域列表
        """
        keyword_lower = keyword.lower()
        result = []
        for domain in cls._domains.values():
            if (
                keyword_lower in domain.name.lower()
                or keyword_lower in domain.content.lower()
                or any(keyword_lower in tag.lower() for tag in domain.tags)
            ):
                result.append(domain)
        return result

    # ==================== 工具方法 ====================

    @classmethod
    def clear(cls) -> None:
        """清空所有知识（主要用于测试）"""
        cls._domains.clear()
        cls._archived.clear()
        logger.info("知识仓库已清空")

    @classmethod
    def stats(cls) -> dict[str, int]:
        """获取知识统计"""
        stats = {
            "total": len(cls._domains),
            "company": 0,
            "domain": 0,
            "agent": 0,
            "archived": len(cls._archived),
        }
        for domain in cls._domains.values():
            stats[domain.level.value] += 1
        return stats
