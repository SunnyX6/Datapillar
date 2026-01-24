"""
Datapillar OneAgentic 配置系统

支持多种配置方式（优先级从高到低）：
1. 配置文件（toml/yaml/json）
2. 环境变量
3. 代码直接传入
4. 代码默认值

配置文件示例（config.toml）：
```toml
[llm]
model = "gpt-4o"
timeout_seconds = 120
retry.max_retries = 3
circuit_breaker.failure_threshold = 5
rate_limit.default.rpm = 60

[embedding]
model = "text-embedding-3-small"

[agent]
max_steps = 50
```

环境变量示例（敏感信息）：
```bash
export DATAPILLAR_LLM_API_KEY="sk-xxx"
export DATAPILLAR_EMBEDDING_API_KEY="sk-xxx"
```

代码配置示例：
```python
from datapillar_oneagentic import DatapillarConfig

config = DatapillarConfig(
    llm={"api_key": "sk-xxx", "model": "gpt-4o"},
    agent={"max_steps": 50},
)
```
"""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, PydanticBaseSettingsSource, SettingsConfigDict

from datapillar_oneagentic.core.config import AgentConfig, ContextConfig
from datapillar_oneagentic.experience.config import LearningConfig
from datapillar_oneagentic.knowledge.config import KnowledgeConfig
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig, LLMConfig
from datapillar_oneagentic.storage.config import VectorStoreConfig

logger = logging.getLogger(__name__)


# === 配置文件源 ===


def _find_config_file() -> Path | None:
    """查找配置文件（按优先级）"""
    search_paths = [
        Path.cwd(),  # 当前目录
        Path.cwd() / "config",  # config 子目录
        Path.home() / ".config" / "datapillar",  # 用户配置目录
    ]
    extensions = [".toml", ".yaml", ".yml", ".json"]
    names = ["datapillar", "config"]

    for path in search_paths:
        for name in names:
            for ext in extensions:
                file = path / f"{name}{ext}"
                if file.exists():
                    return file
    return None


def _load_config_file(file_path: Path) -> dict[str, Any]:
    """根据扩展名加载配置文件"""
    suffix = file_path.suffix.lower()
    content = file_path.read_text(encoding="utf-8")

    if suffix == ".toml":
        try:
            import tomllib
        except ImportError:
            import tomli as tomllib
        return tomllib.loads(content)

    elif suffix in (".yaml", ".yml"):
        try:
            import yaml
            return yaml.safe_load(content) or {}
        except ImportError:
            logger.warning("PyYAML 未安装，跳过 YAML 配置文件")
            return {}

    elif suffix == ".json":
        return json.loads(content)

    else:
        logger.warning(f"不支持的配置文件格式: {suffix}")
        return {}


class FileConfigSource(PydanticBaseSettingsSource):
    """配置文件源（支持 toml/yaml/json）"""

    def __init__(self, settings_cls: type[BaseSettings], config_file: Path | None = None):
        super().__init__(settings_cls)
        self._config_file = config_file or _find_config_file()
        self._file_data: dict[str, Any] = {}
        if self._config_file and self._config_file.exists():
            try:
                self._file_data = _load_config_file(self._config_file)
            except Exception as e:
                logger.warning(f"加载配置文件失败: {self._config_file}, 错误: {e}")

    def get_field_value(self, field: Any, field_name: str) -> tuple[Any, str, bool]:
        value = self._file_data.get(field_name)
        return value, field_name, False

    def __call__(self) -> dict[str, Any]:
        return self._file_data


# === 主配置类 ===

class DatapillarConfig(BaseSettings):
    """
    框架全局配置

    支持三种配置方式：
    1. 配置文件（toml/yaml/json）
    2. 环境变量（DATAPILLAR_ 前缀）
    3. 代码传入
    """

    model_config = SettingsConfigDict(
        env_prefix="DATAPILLAR_",
        # 使用单下划线作为嵌套分隔符，并限制只拆分一层：
        # 例：DATAPILLAR_LLM_API_KEY -> llm.api_key
        # 如果不限制 max_split，API_KEY 会被拆成 api.key，直接导致配置失败。
        env_nested_delimiter="_",
        env_nested_max_split=1,
        extra="allow",
    )

    # 配置文件路径（可选，不指定则自动查找）
    config_file: Path | None = Field(default=None, exclude=True)

    llm: LLMConfig = Field(default_factory=LLMConfig)
    """LLM 配置"""

    embedding: EmbeddingConfig = Field(default_factory=EmbeddingConfig)
    """Embedding 配置"""

    context: ContextConfig = Field(default_factory=ContextConfig)
    """上下文配置"""

    vector_store: VectorStoreConfig = Field(default_factory=VectorStoreConfig)
    """向量数据库配置（知识/经验共用）"""

    agent: AgentConfig = Field(default_factory=AgentConfig)
    """Agent 执行配置"""

    learning: LearningConfig = Field(default_factory=LearningConfig)
    """经验学习配置"""

    knowledge: KnowledgeConfig = Field(default_factory=KnowledgeConfig)
    """知识配置（含 base_config）"""

    verbose: bool = Field(default=False, description="是否输出详细日志")
    """详细日志开关"""

    log_level: str = Field(default="INFO", description="日志级别")
    """日志级别"""

    @classmethod
    def settings_customise_sources(
        cls,
        settings_cls: type[BaseSettings],
        init_settings: PydanticBaseSettingsSource,
        env_settings: PydanticBaseSettingsSource,
        dotenv_settings: PydanticBaseSettingsSource,
        file_secret_settings: PydanticBaseSettingsSource,
    ) -> tuple[PydanticBaseSettingsSource, ...]:
        """
        自定义配置源优先级（前者覆盖后者）

        优先级从高到低：
        1. 配置文件（FileConfigSource）- 最高优先级
        2. 环境变量（env_settings）
        3. 代码传入（init_settings）- 最低优先级
        """
        init_data = init_settings()
        config_file = init_data.get("config_file")
        return (
            FileConfigSource(
                settings_cls,
                Path(config_file) if config_file else None,
            ),  # 配置文件（最高优先级）
            env_settings,  # 环境变量
            init_settings,  # 代码传入（最低优先级）
        )

    def is_llm_configured(self) -> bool:
        """检查 LLM 是否已配置"""
        return self.llm.is_configured()

    def is_embedding_configured(self) -> bool:
        """检查 Embedding 是否已配置"""
        return self.embedding.is_configured()

    @model_validator(mode="after")
    def _inherit_knowledge_embedding(self) -> "DatapillarConfig":
        """
        知识模块默认复用全局 embedding 配置，避免用户重复配置两套 embedding。

        规则：
        - 如果 knowledge.base_config.embedding 没有显式配置（api_key/model 都为空）
        - 且全局 embedding 已配置
        则将全局 embedding 复制到 knowledge.base_config.embedding。
        """
        if (
            self.knowledge.base_config.embedding.api_key is None
            and self.knowledge.base_config.embedding.model is None
            and self.embedding.is_configured()
        ):
            self.knowledge.base_config.embedding = self.embedding.model_copy(deep=True)
        return self

    def validate_llm(self) -> None:
        """验证 LLM 配置"""
        if not self.is_llm_configured():
            raise ConfigurationError(
                "LLM 未配置！请通过配置文件、环境变量或代码配置 LLM。\n"
                "配置文件示例（config.toml）：\n"
                "  [llm]\n"
                "  model = \"gpt-4o\"\n\n"
                "环境变量示例：\n"
                "  export DATAPILLAR_LLM_API_KEY=\"sk-xxx\"\n"
                "  export DATAPILLAR_LLM_MODEL=\"gpt-4o\""
            )

    def validate_embedding(self) -> None:
        """验证 Embedding 配置"""
        if not self.is_embedding_configured():
            raise ConfigurationError(
                "Embedding 未配置！使用经验学习功能需要配置 Embedding。\n"
                "配置文件示例（config.toml）：\n"
                "  [embedding]\n"
                "  model = \"text-embedding-3-small\"\n\n"
                "环境变量示例：\n"
                "  export DATAPILLAR_EMBEDDING_API_KEY=\"sk-xxx\"\n"
                "  export DATAPILLAR_EMBEDDING_MODEL=\"text-embedding-3-small\""
            )


class ConfigurationError(Exception):
    """配置错误异常"""

    pass


def datapillar_configure(
    config_file: str | Path | None = None,
    **kwargs,
) -> DatapillarConfig:
    """
    配置框架

    优先级（从高到低）：
    1. 配置文件（config.toml）
    2. 环境变量（DATAPILLAR_ 前缀）
    3. 代码传入（**kwargs）
    4. 默认值

    参数：
        config_file: 配置文件路径（可选，支持 toml/yaml/json）
        **kwargs: 配置项（作为默认值，会被配置文件和环境变量覆盖）

    示例（显式传递给 Datapillar）：
    ```python
    # 方式1：指定配置文件（最高优先级）
    config = datapillar_configure(config_file="config.toml")

    # 方式2：代码直接配置（作为默认值）
    config = datapillar_configure(
        llm={"api_key": "sk-xxx", "model": "gpt-4o"},
        agent={"max_steps": 50},
    )

    # 方式3：混合使用（配置文件会覆盖代码传入）
    config = datapillar_configure(
        config_file="config.toml",
        llm={"api_key": "sk-default"},  # 如果配置文件有 api_key，会被覆盖
    )
    ```
    """
    config = DatapillarConfig(
        config_file=Path(config_file) if config_file else None,
        **kwargs,
    )

    # 设置日志级别
    if config.verbose:
        logging.getLogger("datapillar_oneagentic").setLevel(logging.DEBUG)
    else:
        logging.getLogger("datapillar_oneagentic").setLevel(
            getattr(logging, config.log_level.upper(), logging.INFO)
        )

    # 日志输出配置状态
    llm_status = "已配置" if config.is_llm_configured() else "未配置"
    embedding_status = "已配置" if config.is_embedding_configured() else "未配置"
    logger.info(
        f"配置已加载: LLM={llm_status} model={config.llm.model}, "
        f"Embedding={embedding_status} model={config.embedding.model}"
    )

    return config
