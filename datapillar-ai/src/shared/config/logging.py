# -*- coding: utf-8 -*-
"""
日志配置模块

从 logging.yaml 加载配置，支持：
- 控制台输出
- 文件轮转
- 按模块配置日志级别
"""

import logging
import logging.config
import os
from pathlib import Path

import yaml


class ExcludeNoisyFilter(logging.Filter):
    """过滤第三方库的噪音日志"""

    NOISY_LOGGERS = {"httpx", "httpcore", "neo4j", "asyncio"}

    def filter(self, record: logging.LogRecord) -> bool:
        return not any(record.name.startswith(name) for name in self.NOISY_LOGGERS)


def setup_logging(config_path: str = None) -> None:
    """
    初始化日志配置

    Args:
        config_path: 配置文件路径，默认为项目根目录的 logging.yaml
    """
    if config_path is None:
        # 默认查找项目根目录的 logging.yaml
        # logging.py 位于 src/shared/config/ 需要向上4层到达项目根目录
        root_dir = Path(__file__).parent.parent.parent.parent
        config_path = root_dir / "logging.yaml"

    if not os.path.exists(config_path):
        # 配置文件不存在，使用基础配置
        logging.basicConfig(
            level=logging.INFO,
            format="%(asctime)s | %(levelname)-8s | %(name)s - %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S",
        )
        logging.warning(f"日志配置文件不存在: {config_path}，使用默认配置")
        return

    # 加载 YAML 配置
    with open(config_path, "r", encoding="utf-8") as f:
        config = yaml.safe_load(f)

    # 处理环境变量 LOG_HOME
    log_home = os.environ.get("LOG_HOME", "/tmp/datapillar-logs")

    # 确保日志目录存在（从配置中读取）
    for handler in config.get("handlers", {}).values():
        if "filename" in handler:
            # 替换日志路径前缀
            handler["filename"] = handler["filename"].replace(
                "/tmp/datapillar-logs", log_home
            )
            log_dir = Path(handler["filename"]).parent
            log_dir.mkdir(parents=True, exist_ok=True)

    # 应用配置
    logging.config.dictConfig(config)
    logging.info(f"日志配置已加载: {config_path}")


def get_logger(name: str) -> logging.Logger:
    """
    获取 logger 实例

    Args:
        name: logger 名称，通常使用 __name__

    Returns:
        logging.Logger 实例
    """
    return logging.getLogger(name)
