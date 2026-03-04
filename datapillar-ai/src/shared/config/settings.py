# @author Sunny
# @date 2026-01-27

"""
Project environment configuration（Nacos unique configuration source）

constraint：
- Do not load any local settings File
- Do not load any environment variable configuration
- The start-up phase must start with Nacos Pull configuration and inject settings
"""

from dynaconf import Dynaconf

settings = Dynaconf(
    # Prevent local files from participating in runtime configuration
    settings_files=[],
    environments=False,
    load_dotenv=False,
    # Disable any environment variable overrides（Nacos is the only source of runtime configuration）
    loaders=[],
    envvar_prefix="DATAPILLAR",
    merge_enabled=True,
    lowercase_read=True,
)
