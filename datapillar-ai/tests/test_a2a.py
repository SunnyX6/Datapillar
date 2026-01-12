"""
A2A 模块测试
"""

import pytest

from src.modules.oneagentic.a2a import (
    A2AClient,
    A2AConfig,
    AgentCard,
    AgentSkill,
    APIKeyAuth,
    BearerAuth,
    create_a2a_tool,
)


class TestA2AConfig:
    """A2A 配置测试"""

    def test_basic_config(self):
        """基本配置"""
        config = A2AConfig(
            endpoint="https://api.example.com/.well-known/agent-card.json",
        )
        assert config.endpoint == "https://api.example.com/.well-known/agent-card.json"
        assert config.timeout == 120
        assert config.max_turns == 10

    def test_config_with_auth(self):
        """带认证的配置"""
        config = A2AConfig(
            endpoint="https://api.example.com",
            auth=APIKeyAuth(api_key="sk-xxx", header_name="X-API-Key"),
        )
        headers = config.auth.to_headers()
        assert headers == {"X-API-Key": "sk-xxx"}

    def test_bearer_auth(self):
        """Bearer 认证"""
        auth = BearerAuth(token="my-token")
        headers = auth.to_headers()
        assert headers == {"Authorization": "Bearer my-token"}

    def test_invalid_endpoint(self):
        """无效端点"""
        with pytest.raises(ValueError, match="endpoint 不能为空"):
            A2AConfig(endpoint="")

        with pytest.raises(ValueError, match="必须是 HTTP"):
            A2AConfig(endpoint="ftp://example.com")

    def test_invalid_timeout(self):
        """无效超时"""
        with pytest.raises(ValueError, match="timeout 必须大于 0"):
            A2AConfig(endpoint="https://example.com", timeout=0)


class TestAgentCard:
    """AgentCard 测试"""

    def test_basic_card(self):
        """基本卡片"""
        card = AgentCard(
            name="数据分析师",
            description="擅长数据分析",
            url="https://api.example.com",
        )
        assert card.name == "数据分析师"
        assert card.version == "1.0.0"

    def test_card_with_skills(self):
        """带技能的卡片"""
        card = AgentCard(
            name="数据分析师",
            skills=[
                AgentSkill(
                    id="data_analysis",
                    name="数据分析",
                    description="分析数据并生成洞察",
                    tags=["data", "analysis"],
                ),
            ],
        )
        assert len(card.skills) == 1
        assert card.has_skill("data_analysis")
        assert not card.has_skill("unknown")

    def test_card_serialization(self):
        """序列化/反序列化"""
        card = AgentCard(
            name="测试Agent",
            description="测试用",
            skills=[
                AgentSkill(id="test", name="测试技能"),
            ],
        )
        data = card.to_dict()
        restored = AgentCard.from_dict(data)

        assert restored.name == card.name
        assert restored.description == card.description
        assert len(restored.skills) == 1
        assert restored.skills[0].id == "test"


class TestA2AClient:
    """A2A Client 测试"""

    def test_client_init(self):
        """客户端初始化"""
        config = A2AConfig(endpoint="https://api.example.com")
        client = A2AClient(config)
        # 未连接时 _http_client 为 None
        assert client._http_client is None

    def test_get_base_url(self):
        """获取基础URL"""
        config = A2AConfig(endpoint="https://api.example.com/.well-known/agent-card.json")
        client = A2AClient(config)
        base_url = client._get_base_url()
        assert base_url == "https://api.example.com"


class TestA2ATool:
    """A2A 工具测试"""

    def test_create_tool(self):
        """创建工具"""
        config = A2AConfig(endpoint="https://api.example.com")
        tool = create_a2a_tool(config, name="test_delegate")
        assert tool.name == "test_delegate"
        assert "委派" in tool.description


class TestA2AServer:
    """A2A Server 测试"""

    @pytest.fixture(autouse=True)
    def setup(self):
        """每个测试前清理"""
        from src.modules.oneagentic.core.datapillar import Datapillar
        from src.modules.oneagentic.runtime.executor import clear_executor_cache

        clear_executor_cache()
        Datapillar._clear_registry()

    def test_agent_card_no_teams(self):
        """无团队时的 AgentCard"""
        from fastapi import FastAPI
        from fastapi.testclient import TestClient

        from src.modules.oneagentic.a2a.server import a2a_router

        app = FastAPI()
        app.include_router(a2a_router)
        client = TestClient(app)

        response = client.get("/.well-known/agent-card.json")
        assert response.status_code == 200

        data = response.json()
        assert data["name"] == "Datapillar A2A Gateway"
        assert data["skills"] == []  # 无团队

    def test_agent_card_with_team(self):
        """有团队时的 AgentCard"""
        from fastapi import FastAPI
        from fastapi.testclient import TestClient

        from src.modules.etl.agents import create_etl_team
        from src.modules.oneagentic.a2a.server import a2a_router

        # 创建团队
        team = create_etl_team()

        app = FastAPI()
        app.include_router(a2a_router)
        client = TestClient(app)

        response = client.get("/.well-known/agent-card.json")
        assert response.status_code == 200

        data = response.json()
        assert data["name"] == "Datapillar A2A Gateway"
        assert len(data["skills"]) == 1
        assert data["skills"][0]["name"] == "ETL 智能团队"
        assert data["skills"][0]["id"] == team.team_id

    def test_list_teams(self):
        """列出团队"""
        from fastapi import FastAPI
        from fastapi.testclient import TestClient

        from src.modules.etl.agents import create_etl_team
        from src.modules.oneagentic.a2a.server import a2a_router

        team = create_etl_team()

        app = FastAPI()
        app.include_router(a2a_router)
        client = TestClient(app)

        response = client.get("/a2a/teams")
        assert response.status_code == 200

        data = response.json()
        assert data["count"] == 1
        assert data["teams"][0]["name"] == "ETL 智能团队"
        assert data["teams"][0]["team_id"] == team.team_id

    def test_send_task_no_team(self):
        """发送任务但未指定团队"""
        from fastapi import FastAPI
        from fastapi.testclient import TestClient

        from src.modules.oneagentic.a2a.server import a2a_router

        app = FastAPI()
        app.include_router(a2a_router)
        client = TestClient(app)

        response = client.post(
            "/a2a/tasks/send",
            json={
                "message": {"role": "user", "content": "测试任务"},
                "metadata": {},
            },
        )
        assert response.status_code == 200

        data = response.json()
        assert data["status"] == "failed"
        assert "未指定目标团队" in data["error"]

    def test_send_task_team_not_found(self):
        """发送任务但团队不存在"""
        from fastapi import FastAPI
        from fastapi.testclient import TestClient

        from src.modules.oneagentic.a2a.server import a2a_router

        app = FastAPI()
        app.include_router(a2a_router)
        client = TestClient(app)

        response = client.post(
            "/a2a/tasks/send",
            json={
                "message": {"role": "user", "content": "测试任务"},
                "metadata": {"team_name": "不存在的团队"},
            },
        )
        assert response.status_code == 200

        data = response.json()
        assert data["status"] == "failed"
        assert "不存在" in data["error"]

    def test_send_task_single_team_auto_route(self):
        """只有一个团队时自动路由"""
        from unittest.mock import AsyncMock, patch

        from fastapi import FastAPI
        from fastapi.testclient import TestClient

        from src.modules.etl.agents import create_etl_team
        from src.modules.oneagentic.a2a.server import a2a_router

        team = create_etl_team()

        app = FastAPI()
        app.include_router(a2a_router)
        client = TestClient(app)

        # Mock kickoff 避免真实 LLM 调用
        with patch.object(team, "kickoff", new_callable=AsyncMock) as mock_kickoff:
            from src.modules.oneagentic.core.datapillar import DatapillarResult

            mock_kickoff.return_value = DatapillarResult(
                success=True,
                output=None,
                summary="任务完成",
            )

            response = client.post(
                "/a2a/tasks/send",
                json={
                    "message": {"role": "user", "content": "测试任务"},
                    "metadata": {},  # 不指定团队，自动路由
                },
            )
            assert response.status_code == 200

            data = response.json()
            assert data["status"] == "completed"
            assert data["result"] == "任务完成"

            mock_kickoff.assert_called_once()
