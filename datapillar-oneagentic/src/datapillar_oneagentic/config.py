"""
Datapillar OneAgentic 配置系统

支持多种配置方式（优先级从低到高）：
1. 代码默认值
2. 配置文件（toml/yaml/json）
3. 环境变量
4. 代码直接传入

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
export DATAPILLAR_LLM__API_KEY="sk-xxx"
export DATAPILLAR_EMBEDDING__API_KEY="sk-xxx"
```

代码配置示例：
```python
from datapillar_oneagentic import datapillar_configure

datapillar_configure(
    llm={"api_key": "sk-xxx", "model": "gpt-4o"},
    agent={"max_steps": 50},
)
```
"""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any, ClassVar

from pydantic import Field
from pydantic_settings import BaseSettings, PydanticBaseSettingsSource, SettingsConfigDict

from datapillar_oneagentic.cache.config import CacheConfig
from datapillar_oneagentic.core.config import AgentConfig, ContextConfig
from datapillar_oneagentic.experience.config import LearningConfig
from datapillar_oneagentic.providers.llm.config import EmbeddingConfig, LLMConfig
from datapillar_oneagentic.telemetry.config import TelemetryConfig

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
                logger.debug(f"已加载配置文件: {self._config_file}")
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
        env_nested_delimiter="__",
        extra="allow",
    )

    # 配置文件路径（可选，不指定则自动查找）
    config_file: ClassVar[Path | None] = None

    llm: LLMConfig = Field(default_factory=LLMConfig)
    """LLM 配置"""

    embedding: EmbeddingConfig = Field(default_factory=EmbeddingConfig)
    """Embedding 配置"""

    context: ContextConfig = Field(default_factory=ContextConfig)
    """上下文配置"""

    agent: AgentConfig = Field(default_factory=AgentConfig)
    """Agent 执行配置"""

    learning: LearningConfig = Field(default_factory=LearningConfig)
    """经验学习配置"""

    cache: CacheConfig = Field(default_factory=CacheConfig)
    """缓存配置"""

    telemetry: TelemetryConfig = Field(default_factory=TelemetryConfig)
    """遥测配置"""

    @classmethod
    def settings_customise_sources(
        cls,
        settings_cls: type[BaseSettings],
        init_settings: PydanticBaseSettingsSource,
        env_settings: PydanticBaseSettingsSource,
        dotenv_settings: PydanticBaseSettingsSource,
        file_secret_settings: PydanticBaseSettingsSource,
    ) -> tuple[PydanticBaseSettingsSource, ...]:
        """自定义配置源优先级（后者覆盖前者）"""
        return (
            FileConfigSource(settings_cls, cls.config_file),  # 配置文件
            env_settings,  # 环境变量
            init_settings,  # 代码传入
        )

    def is_llm_configured(self) -> bool:
        """检查 LLM 是否已配置"""
        return self.llm.is_configured()

    def is_embedding_configured(self) -> bool:
        """检查 Embedding 是否已配置"""
        return self.embedding.is_configured()

    def validate_llm(self) -> None:
        """验证 LLM 配置"""
        if not self.is_llm_configured():
            raise ConfigurationError(
                "LLM 未配置！请通过配置文件、环境变量或代码配置 LLM。\n"
                "配置文件示例（config.toml）：\n"
                "  [llm]\n"
                "  model = \"gpt-4o\"\n\n"
                "环境变量示例：\n"
                "  export DATAPILLAR_LLM__API_KEY=\"sk-xxx\"\n"
                "  export DATAPILLAR_LLM__MODEL=\"gpt-4o\""
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
                "  export DATAPILLAR_EMBEDDING__API_KEY=\"sk-xxx\"\n"
                "  export DATAPILLAR_EMBEDDING__MODEL=\"text-embedding-3-small\""
            )


class ConfigurationError(Exception):
    """配置错误异常"""

    pass


# === 全局配置实例 ===

_config: DatapillarConfig | None = None


def datapillar_configure(
    config_file: str | Path | None = None,
    **kwargs,
) -> DatapillarConfig:
    """
    配置框架

    参数：
        config_file: 配置文件路径（可选，支持 toml/yaml/json）
        **kwargs: 配置项（会覆盖配置文件和环境变量）

    示例：
    ```python
    # 方式1：指定配置文件
    datapillar_configure(config_file="config.toml")

    # 方式2：代码直接配置
    datapillar_configure(
        llm={"api_key": "sk-xxx", "model": "gpt-4o"},
        agent={"max_steps": 50},
    )

    # 方式3：混合使用（代码覆盖配置文件）
    datapillar_configure(
        config_file="config.toml",
        llm={"api_key": "sk-xxx"},  # 覆盖配置文件中的 api_key
    )
    ```
    """
    global _config

    # 设置配置文件路径
    if config_file:
        DatapillarConfig.config_file = Path(config_file)

    # 创建配置实例
    _config = DatapillarConfig(**kwargs)

    # 设置日志级别
    if _config.telemetry.verbose:
        logging.getLogger("datapillar_oneagentic").setLevel(logging.DEBUG)
    else:
        logging.getLogger("datapillar_oneagentic").setLevel(
            getattr(logging, _config.telemetry.log_level.upper(), logging.INFO)
        )

    # 日志输出配置状态
    llm_status = "✓" if _config.is_llm_configured() else "✗"
    embedding_status = "✓" if _config.is_embedding_configured() else "✗"
    logger.info(
        f"配置已加载: LLM[{llm_status}] model={_config.llm.model}, "
        f"Embedding[{embedding_status}] model={_config.embedding.model}"
    )

    return _config


def get_config() -> DatapillarConfig:
    """获取配置（自动从配置文件和环境变量加载）"""
    global _config
    if _config is None:
        _config = DatapillarConfig()
    return _config


def reset_config() -> None:
    """重置配置（仅用于测试）"""
    global _config
    _config = None
    DatapillarConfig.config_file = None


class _DatapillarProxy:
    """配置代理，支持属性访问"""

    def __getattr__(self, name: str):
        return getattr(get_config(), name)

    def __repr__(self) -> str:
        return repr(get_config())


datapillar = _DatapillarProxy()
