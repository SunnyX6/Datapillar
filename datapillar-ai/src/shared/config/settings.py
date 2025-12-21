"""
项目环境配置（使用 dynaconf）
支持多环境配置，类似 Spring Profiles
"""

from dynaconf import Dynaconf

# 初始化 dynaconf（企业级标准）
# 配置文件在项目根目录的 config/ 目录下
settings = Dynaconf(
    # 配置文件路径
    settings_files=[
        "config/settings.toml",       # 通用配置
        "config/.secrets.toml",       # 敏感信息
        "config/settings.local.toml", # 本地覆盖（不提交 git）
    ],

    # 环境配置
    environments=True,                # 启用多环境支持
    env_switcher="ENV_FOR_DYNACONF",  # 环境切换变量

    # 环境变量前缀
    envvar_prefix="DATAPILLAR",       # 环境变量前缀（如 DATAPILLAR_MYSQL_HOST）

    # 其他配置
    merge_enabled=True,               # 允许配置合并
    lowercase_read=True,              # 支持小写读取（settings.mysql_host）
)


# ==================== 使用说明 ====================
# 1. 切换环境：
#    export ENV_FOR_DYNACONF=production
#    python -m uvicorn src.app:app
#
# 2. 环境变量覆盖（优先级最高）：
#    export DATAPILLAR_MYSQL_HOST=192.168.1.100
#
# 3. 本地开发覆盖：
#    创建 config/settings.local.toml（不提交 git）
#
# 4. 读取配置：
#    from src.config import settings
#    print(settings.mysql_host)
#    print(settings.MYSQL_HOST)  # 大写也可以
#
# 5. 验证配置：
#    dynaconf list -k mysql_host
#    dynaconf list -e development
