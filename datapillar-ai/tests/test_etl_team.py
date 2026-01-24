"""
ETL 团队集成测试

验证：
1. 团队创建和配置
2. Agent 规格与交付物 Schema
3. 动态模式下委派范围自动设置
"""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from datapillar_oneagentic import DatapillarConfig, Process
from datapillar_oneagentic.core.agent import get_agent_spec

from src.modules.etl.agents import create_etl_team
from src.modules.etl.agents.analyst_agent import AnalystAgent, AnalysisOutput
from src.modules.etl.agents.architect_agent import ArchitectAgent
from src.modules.etl.agents.catalog_agent import CatalogAgent, CatalogOutput
from src.modules.etl.agents.developer_agent import DeveloperAgent
from src.modules.etl.agents.reviewer_agent import ReviewerAgent
from src.modules.etl.schemas.review import ReviewResult
from src.modules.etl.schemas.workflow import WorkflowOutput


@pytest.fixture
def _llm_stub():
    llm = MagicMock()
    llm.ainvoke = AsyncMock()
    return llm


@pytest.fixture
def etl_team(_llm_stub):
    config = DatapillarConfig(llm={"api_key": "test-key", "model": "gpt-4o"})
    with patch(
        "datapillar_oneagentic.providers.llm.llm.LLMProvider.__call__",
        return_value=_llm_stub,
    ):
        yield create_etl_team(config=config, namespace="test_etl")


class TestEtlTeamCreation:
    """测试 ETL 团队创建"""

    def test_create_team_success(self, etl_team):
        assert etl_team.name == "ETL 智能团队"
        assert etl_team.process == Process.DYNAMIC
        assert etl_team.namespace == "test_etl"

    def test_team_has_correct_members(self, etl_team):
        agent_ids = set(etl_team._agent_ids)
        assert agent_ids == {"analyst", "catalog", "architect", "developer", "reviewer"}

    def test_entry_agent_is_analyst(self, etl_team):
        assert etl_team._entry_agent_id == "analyst"

    def test_dynamic_mode_sets_can_delegate_to(self, etl_team):
        executor = etl_team._get_executor("analyst")
        expected = set(etl_team._agent_ids) - {"analyst"}
        assert set(executor.spec.can_delegate_to) == expected


class TestAgentSpecs:
    """测试 Agent 规格与工具配置"""

    def test_analyst_spec(self):
        spec = get_agent_spec(AnalystAgent)
        assert spec is not None
        assert spec.name == "需求分析师"
        assert spec.deliverable_schema is AnalysisOutput
        tool_names = {tool.name for tool in spec.tools}
        assert tool_names == {"search_tables", "get_table_detail"}

    def test_catalog_spec(self):
        spec = get_agent_spec(CatalogAgent)
        assert spec is not None
        assert spec.name == "元数据专员"
        assert spec.deliverable_schema is CatalogOutput
        tool_names = {tool.name for tool in spec.tools}
        assert tool_names == {
            "count_catalogs",
            "count_schemas",
            "count_tables",
            "list_catalogs",
            "list_schemas",
            "list_tables",
            "search_tables",
            "search_columns",
            "get_table_detail",
            "get_table_lineage",
        }

    def test_architect_spec(self):
        spec = get_agent_spec(ArchitectAgent)
        assert spec is not None
        assert spec.name == "数据架构师"
        assert spec.deliverable_schema is WorkflowOutput
        tool_names = {tool.name for tool in spec.tools}
        assert tool_names == {"get_table_lineage", "search_tables", "list_component"}

    def test_developer_spec(self):
        spec = get_agent_spec(DeveloperAgent)
        assert spec is not None
        assert spec.name == "数据开发工程师"
        assert spec.deliverable_schema is WorkflowOutput
        tool_names = {tool.name for tool in spec.tools}
        assert tool_names == {
            "get_table_detail",
            "get_table_lineage",
            "get_lineage_sql",
            "search_tables",
        }

    def test_reviewer_spec(self):
        spec = get_agent_spec(ReviewerAgent)
        assert spec is not None
        assert spec.name == "代码评审员"
        assert spec.deliverable_schema is ReviewResult
        assert spec.tools == []
