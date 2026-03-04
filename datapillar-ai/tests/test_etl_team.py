"""
ETL Team integration testing

Verify：
1. Team creation and configuration
2. Agent Specifications and Deliverables Schema
3. Delegation scope is automatically set in dynamic mode
"""

from unittest.mock import AsyncMock, MagicMock, patch

import pytest
from datapillar_oneagentic import DatapillarConfig, Process
from datapillar_oneagentic.core.agent import get_agent_spec

from src.modules.etl.agents import create_etl_team
from src.modules.etl.agents.analyst_agent import AnalysisOutput, AnalystAgent
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
    """test ETL Team creation"""

    def test_create_team_success(self, etl_team):
        assert etl_team.name == "ETL smart team"
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
    """test Agent Specifications and Tool Configuration"""

    def test_analyst_spec(self):
        spec = get_agent_spec(AnalystAgent)
        assert spec is not None
        assert spec.name == "Demand Analyst"
        assert spec.deliverable_schema is AnalysisOutput
        tool_names = {tool.name for tool in spec.tools}
        assert tool_names == {"get_knowledge_navigation", "search_tables", "get_table_detail"}

    def test_catalog_spec(self):
        spec = get_agent_spec(CatalogAgent)
        assert spec is not None
        assert spec.name == "Metadata Specialist"
        assert spec.deliverable_schema is CatalogOutput
        tool_names = {tool.name for tool in spec.tools}
        assert tool_names == {
            "get_knowledge_navigation",
            "list_catalogs",
            "list_schemas",
            "list_tables",
            "search_tables",
            "search_columns",
            "get_table_detail",
            "get_table_lineage",
            "get_lineage_sql",
        }

    def test_architect_spec(self):
        spec = get_agent_spec(ArchitectAgent)
        assert spec is not None
        assert spec.name == "data architect"
        assert spec.deliverable_schema is WorkflowOutput
        tool_names = {tool.name for tool in spec.tools}
        assert tool_names == {"get_knowledge_navigation", "get_table_lineage", "list_component"}

    def test_developer_spec(self):
        spec = get_agent_spec(DeveloperAgent)
        assert spec is not None
        assert spec.name == "Data development engineer"
        assert spec.deliverable_schema is WorkflowOutput
        tool_names = {tool.name for tool in spec.tools}
        assert tool_names == {
            "get_knowledge_navigation",
            "get_table_detail",
            "get_table_lineage",
            "get_lineage_sql",
        }

    def test_reviewer_spec(self):
        spec = get_agent_spec(ReviewerAgent)
        assert spec is not None
        assert spec.name == "code reviewer"
        assert spec.deliverable_schema is ReviewResult
        tool_names = {tool.name for tool in spec.tools}
        assert tool_names == {"get_knowledge_navigation"}
