# @author Sunny
# @date 2026-01-27

"""
Log configuration module

from logging.yaml Load configuration，support：
- console output
- File rotation
- Configure log levels by module
"""

import logging
import logging.config
import os
import tempfile
from pathlib import Path

import yaml
from datapillar_oneagentic.log.context import ContextFilter


class ExcludeNoisyFilter(logging.Filter):
    """Filter noise logs from third-party libraries"""

    NOISY_LOGGERS = {"httpx", "httpcore", "neo4j", "asyncio"}

    def filter(self, record: logging.LogRecord) -> bool:
        return not any(record.name.startswith(name) for name in self.NOISY_LOGGERS)


def setup_logging(config_path: str | Path | None = None) -> None:
    """
    Initialize log configuration

    Args:
        config_path: Configuration file path，Defaults to the project root directory logging.yaml
    """
    if config_path is None:
        # By default, the project root directory is searched. logging.yaml
        # logging.py located in src/shared/config/ need up4layer reaches the project root directory
        root_dir = Path(__file__).parent.parent.parent.parent
        config_path = root_dir / "logging.yaml"

    config_path_str = str(config_path)
    if not os.path.exists(config_path_str):
        # Configuration file does not exist，Use basic configuration
        logging.basicConfig(
            level=logging.INFO,
            format="%(asctime)s.%(msecs)03d %(levelname)-5s %(name)s - %(message)s%(context)s",
            datefmt="%Y-%m-%d %H:%M:%S",
        )
        logging.getLogger().addFilter(ContextFilter())
        logging.warning(
            f"Log configuration file does not exist: {config_path_str}，Use default configuration"
        )
        return

    # Load YAML Configuration
    with open(config_path_str, encoding="utf-8") as f:
        config = yaml.safe_load(f)

    # Handle environment variables LOG_HOME
    default_log_home = str(Path(tempfile.gettempdir()) / "datapillar-logs")
    log_home = os.environ.get("LOG_HOME", default_log_home)

    # Make sure the log directory exists（Read from configuration）
    yaml_default_prefix = default_log_home
    for handler in config.get("handlers", {}).values():
        if "filename" in handler:
            # Replace log path prefix
            handler["filename"] = handler["filename"].replace(yaml_default_prefix, log_home)
            log_dir = Path(handler["filename"]).parent
            log_dir.mkdir(parents=True, exist_ok=True)

    # Application configuration
    logging.config.dictConfig(config)
    logging.info(f"Log configuration loaded: {config_path_str}")


def get_logger(name: str) -> logging.Logger:
    """
    Get logger Example

    Args:
        name: logger Name，Commonly used __name__

    Returns:
        logging.Logger Example
    """
    return logging.getLogger(name)
