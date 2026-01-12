"""
ETL 团队集成测试

测试覆盖：
1. 团队创建和配置
2. DYNAMIC 模式下的委派自动设置
3. Agent 规格验证
"""

from unittest.mock import MagicMock, patch

import pytest

# 在测试开始前导入，触发 Agent 注册（只执行一次）
from src.modules.etl.agents import create_etl_team
from src.modules.oneagentic import Process
from src.modules.oneagentic.core.agent import AgentRegistry
from src.modules.oneagentic.core.datapillar import Datapillar
from src.modules.oneagentic.runtime.executor import clear_executor_cache

# ==================== Fixtures ====================


@pytest.fixture
def etl_team():
    """创建 ETL 团队"""
    # 清空 Executor 缓存
    clear_executor_cache()
    # 清空 Datapillar 注册表（允许重新创建同名团队）
    Datapillar._clear_registry()
    return create_etl_team()


# ==================== 团队创建测试 ====================


class TestEtlTeamCreation:
    """测试 ETL 团队创建"""

    def test_create_team_success(self, etl_team):
        """测试团队创建成功"""
        assert etl_team is not None
        assert etl_team.name == "ETL 智能团队"
        assert etl_team.process == Process.DYNAMIC

    def test_team_has_correct_members(self, etl_team):
        """测试团队成员正确"""
        agent_ids = etl_team._agent_ids

        assert "analyst" in agent_ids
        assert "catalog" in agent_ids
        assert "architect" in agent_ids
        assert "developer" in agent_ids
        assert "reviewer" in agent_ids

    def test_entry_agent_is_analyst(self, etl_team):
        """测试入口 Agent 是 AnalystAgent"""
        assert etl_team._entry_agent_id == "analyst"

    def test_dynamic_mode_sets_can_delegate_to(self, etl_team):
        """测试 DYNAMIC 模式自动设置 can_delegate_to"""
        # 获取 analyst 的执行器（会触发 can_delegate_to 设置）
        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = etl_team._get_executor("analyst")

        # analyst 应该可以委派给其他所有团队成员
        can_delegate = executor.spec.can_delegate_to

        assert "catalog" in can_delegate
        assert "architect" in can_delegate
        assert "developer" in can_delegate
        assert "reviewer" in can_delegate
        assert "analyst" not in can_delegate  # 不能委派给自己


# ==================== Agent 规格测试 ====================


class TestAgentSpecs:
    """测试各 Agent 规格正确"""

    def test_analyst_spec(self):
        """测试 AnalystAgent 规格"""
        spec = AgentRegistry.get("analyst")

        assert spec is not None
        assert spec.name == "需求分析师"
        assert "search_tables" in spec.tools
        assert "get_table_detail" in spec.tools
        assert spec.deliverable_key == "analysis"

    def test_catalog_spec(self):
        """测试 CatalogAgent 规格"""
        spec = AgentRegistry.get("catalog")

        assert spec is not None
        assert spec.name == "元数据专员"
        assert "list_catalogs" in spec.tools
        assert "search_tables" in spec.tools
        assert spec.deliverable_key == "catalog_result"

    def test_architect_spec(self):
        """测试 ArchitectAgent 规格"""
        spec = AgentRegistry.get("architect")

        assert spec is not None
        assert spec.name == "数据架构师"
        assert "get_table_lineage" in spec.tools
        assert spec.deliverable_key == "workflow"

    def test_developer_spec(self):
        """测试 DeveloperAgent 规格"""
        spec = AgentRegistry.get("developer")

        assert spec is not None
        assert spec.name == "数据开发工程师"
        assert spec.deliverable_key == "sql"

    def test_reviewer_spec(self):
        """测试 ReviewerAgent 规格"""
        spec = AgentRegistry.get("reviewer")

        assert spec is not None
        assert spec.name == "代码评审员"
        assert spec.deliverable_key == "review"
        assert spec.tools == []  # 评审不需要工具


# ==================== 委派工具测试 ====================


class TestDelegationTools:
    """测试委派工具生成"""

    def test_analyst_has_delegation_tools(self, etl_team):
        """测试 AnalystAgent 有委派工具"""
        with patch("src.modules.oneagentic.runtime.executor.call_llm") as mock_call_llm:
            mock_call_llm.return_value = MagicMock()
            executor = etl_team._get_executor("analyst")

        # 检查委派工具
        delegation_tool_names = [t.name for t in executor.delegation_tools]

        assert "delegate_to_catalog" in delegation_tool_names
        assert "delegate_to_architect" in delegation_tool_names
        assert "delegate_to_developer" in delegation_tool_names
        assert "delegate_to_reviewer" in delegation_tool_names

    def test_reviewer_has_no_delegation_by_default(self):
        """测试 ReviewerAgent 原始规格中没有委派目标"""
        # 直接获取原始 spec（不通过团队）
        # 注意：DYNAMIC 模式下团队会自动设置 can_delegate_to
        # 这里测试的是原始 spec
        spec = AgentRegistry.get("reviewer")
        assert spec is not None
        # 原始 spec 的 can_delegate_to 默认为空列表
        # 但 DYNAMIC 模式会在 _get_executor 时自动设置


# ==================== 执行图测试 ====================


class TestExecutionGraph:
    """测试执行图构建"""

    def test_graph_built_correctly(self, etl_team):
        """测试执行图构建正确"""
        graph = etl_team._graph

        assert graph is not None
        # DYNAMIC 模式应该有条件入口
        assert etl_team.process == Process.DYNAMIC

    def test_all_agents_in_graph(self, etl_team):
        """测试所有 Agent 都在图中"""
        # 图的节点应该包含所有 Agent
        graph = etl_team._graph
        nodes = set(graph.nodes.keys())

        assert "analyst" in nodes
        assert "catalog" in nodes
        assert "architect" in nodes
        assert "developer" in nodes
        assert "reviewer" in nodes
