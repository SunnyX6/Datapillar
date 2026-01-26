"""
Datapillar OneAgentic configuration system.

Supported configuration sources (highest to lowest priority):
1. Config file (toml/yaml/json)
2. Environment variables
3. Explicit code input
4. Code defaults

Config file example (config.toml):
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

Environment variable example (sensitive):
```bash
export DATAPILLAR_LLM_API_KEY="sk-xxx"
export DATAPILLAR_EMBEDDING_API_KEY="sk-xxx"
```

Code example:
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
from datapillar_oneagentic.log import setup_logging
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig, LLMConfig
from datapillar_oneagentic.storage.config import VectorStoreConfig

logger = logging.getLogger(__name__)


# === Config file sources ===


def _find_config_file() -> Path | None:
    """Find a config file by priority."""
    search_paths = [
        Path.cwd(),  # Current directory
        Path.cwd() / "config",  # config subdir
        Path.home() / ".config" / "datapillar",  # User config directory
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
    """Load a config file by extension."""
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
            logger.warning("PyYAML not installed; skipping YAML config file")
            return {}

    elif suffix == ".json":
        return json.loads(content)

    else:
        logger.warning("Unsupported config file format: %s", suffix)
        return {}


class FileConfigSource(PydanticBaseSettingsSource):
    """Config file source (toml/yaml/json)."""

    def __init__(self, settings_cls: type[BaseSettings], config_file: Path | None = None):
        super().__init__(settings_cls)
        self._config_file = config_file or _find_config_file()
        self._file_data: dict[str, Any] = {}
        if self._config_file and self._config_file.exists():
            try:
                self._file_data = _load_config_file(self._config_file)
            except Exception as e:
                logger.warning(
                    "Failed to load config file: %s, error=%s",
                    self._config_file,
                    e,
                )

    def get_field_value(self, field: Any, field_name: str) -> tuple[Any, str, bool]:
        value = self._file_data.get(field_name)
        return value, field_name, False

    def __call__(self) -> dict[str, Any]:
        return self._file_data


# === Main config class ===

class DatapillarConfig(BaseSettings):
    """
    Framework global configuration.

    Supported configuration sources:
    1. Config file (toml/yaml/json)
    2. Environment variables (DATAPILLAR_ prefix)
    3. Code input
    """

    model_config = SettingsConfigDict(
        env_prefix="DATAPILLAR_",
        # Use a single underscore as nested delimiter, split only once:
        # Example: DATAPILLAR_LLM_API_KEY -> llm.api_key
        # Without max_split, API_KEY would become api.key and break config.
        env_nested_delimiter="_",
        env_nested_max_split=1,
        extra="allow",
    )

    # Optional config file path; auto-detected if not provided.
    config_file: Path | None = Field(default=None, exclude=True)

    llm: LLMConfig = Field(default_factory=LLMConfig)
    """LLM configuration."""

    embedding: EmbeddingConfig = Field(default_factory=EmbeddingConfig)
    """Embedding configuration."""

    context: ContextConfig = Field(default_factory=ContextConfig)
    """Context configuration."""

    vector_store: VectorStoreConfig = Field(default_factory=VectorStoreConfig)
    """Vector store configuration (shared by knowledge/experience)."""

    agent: AgentConfig = Field(default_factory=AgentConfig)
    """Agent execution configuration."""

    learning: LearningConfig = Field(default_factory=LearningConfig)
    """Experience learning configuration."""

    knowledge: KnowledgeConfig = Field(default_factory=KnowledgeConfig)
    """Knowledge configuration (including base_config)."""

    verbose: bool = Field(default=False, description="Enable verbose logging")
    """Verbose logging flag."""

    log_level: str = Field(default="INFO", description="Log level")
    """Log level."""

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
        Customize source priority (earlier overrides later).

        Priority (high to low):
        1. Config file (FileConfigSource) - highest priority
        2. Environment variables (env_settings)
        3. Code input (init_settings) - lowest priority
        """
        init_data = init_settings()
        config_file = init_data.get("config_file")
        return (
            FileConfigSource(
                settings_cls,
                Path(config_file) if config_file else None,
            ),  # Config file (highest)
            env_settings,  # Environment variables
            init_settings,  # Code input (lowest)
        )

    def is_llm_configured(self) -> bool:
        """Return True if LLM is configured."""
        return self.llm.is_configured()

    def is_embedding_configured(self) -> bool:
        """Return True if embedding is configured."""
        return self.embedding.is_configured()

    @model_validator(mode="after")
    def _inherit_knowledge_embedding(self) -> "DatapillarConfig":
        """
        Knowledge module reuses global embedding config by default.

        Rules:
        - If knowledge.base_config.embedding is not explicitly set
          (api_key/model are empty)
        - And the global embedding is configured
        Then copy global embedding to knowledge.base_config.embedding.
        """
        if (
            self.knowledge.base_config.embedding.api_key is None
            and self.knowledge.base_config.embedding.model is None
            and self.embedding.is_configured()
        ):
            self.knowledge.base_config.embedding = self.embedding.model_copy(deep=True)
        return self

    def validate_llm(self) -> None:
        """Validate LLM configuration."""
        if not self.is_llm_configured():
            raise ConfigurationError(
                "LLM is not configured. Configure via config file, env vars, or code.\n"
                "Config file example (config.toml):\n"
                "  [llm]\n"
                "  model = \"gpt-4o\"\n\n"
                "Environment variable example:\n"
                "  export DATAPILLAR_LLM_API_KEY=\"sk-xxx\"\n"
                "  export DATAPILLAR_LLM_MODEL=\"gpt-4o\""
            )

    def validate_embedding(self) -> None:
        """Validate embedding configuration."""
        if not self.is_embedding_configured():
            raise ConfigurationError(
                "Embedding is not configured. Experience learning requires embedding.\n"
                "Config file example (config.toml):\n"
                "  [embedding]\n"
                "  model = \"text-embedding-3-small\"\n\n"
                "Environment variable example:\n"
                "  export DATAPILLAR_EMBEDDING_API_KEY=\"sk-xxx\"\n"
                "  export DATAPILLAR_EMBEDDING_MODEL=\"text-embedding-3-small\""
            )


class ConfigurationError(Exception):
    """Configuration error."""

    pass


def datapillar_configure(
    config_file: str | Path | None = None,
    **kwargs,
) -> DatapillarConfig:
    """
    Configure the framework.

    Priority (high to low):
    1. Config file (config.toml)
    2. Environment variables (DATAPILLAR_ prefix)
    3. Code input (**kwargs)
    4. Defaults

    Args:
        config_file: Optional config file path (toml/yaml/json)
        **kwargs: Default config values (overridden by file/env)

    Example (explicit for Datapillar):
    ```python
    # Option 1: specify config file (highest priority)
    config = datapillar_configure(config_file="config.toml")

    # Option 2: code config (defaults)
    config = datapillar_configure(
        llm={"api_key": "sk-xxx", "model": "gpt-4o"},
        agent={"max_steps": 50},
    )

    # Option 3: mixed (config file overrides code)
    config = datapillar_configure(
        config_file="config.toml",
        llm={"api_key": "sk-default"},  # Overridden by config file if provided
    )
    ```
    """
    config = DatapillarConfig(
        config_file=Path(config_file) if config_file else None,
        **kwargs,
    )

    # Initialize logging
    if config.verbose:
        setup_logging(logging.DEBUG)
    else:
        setup_logging(getattr(logging, config.log_level.upper(), logging.INFO))

    # Log configuration status.
    llm_status = "configured" if config.is_llm_configured() else "missing"
    embedding_status = "configured" if config.is_embedding_configured() else "missing"
    logger.info(
        "Configuration loaded: LLM=%s model=%s, Embedding=%s model=%s",
        llm_status,
        config.llm.model,
        embedding_status,
        config.embedding.model,
        extra={"event": "config.loaded"},
    )

    return config
