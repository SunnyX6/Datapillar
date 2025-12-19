# -*- coding: utf-8 -*-
"""
Datapillar Airflow Plugin - Configuration

Reads plugin configuration from airflow.cfg [datapillar] section.
"""

from airflow.configuration import conf
from typing import Dict, Any
import logging

logger = logging.getLogger(__name__)

# Default configuration
DEFAULT_CONFIG = {
    "api_token": None,  # No authentication by default
    "dag_prefix": "datapillar_",
}


def get_config() -> Dict[str, Any]:
    """
    Get plugin configuration from airflow.cfg

    Example airflow.cfg:
        [datapillar]
        api_token = your-secret-token

    Returns:
        Configuration dictionary
    """
    config = DEFAULT_CONFIG.copy()

    try:
        # Try to read from [datapillar] section
        if conf.has_section("datapillar"):
            if conf.has_option("datapillar", "api_token"):
                config["api_token"] = conf.get("datapillar", "api_token")

            if conf.has_option("datapillar", "dag_prefix"):
                config["dag_prefix"] = conf.get("datapillar", "dag_prefix")

    except Exception as e:
        logger.warning(f"Failed to read datapillar config: {e}")

    return config
