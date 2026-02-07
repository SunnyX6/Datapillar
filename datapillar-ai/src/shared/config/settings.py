# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

"""
项目环境配置（Nacos 唯一配置源）

约束：
- 不加载任何本地 settings 文件
- 不加载任何环境变量配置
- 启动阶段必须先从 Nacos 拉取配置并注入 settings
"""

from dynaconf import Dynaconf

settings = Dynaconf(
    # 禁止本地文件参与运行时配置
    settings_files=[],
    environments=False,
    load_dotenv=False,
    # 禁止任何环境变量覆盖（Nacos 是唯一运行时配置源）
    loaders=[],
    envvar_prefix="DATAPILLAR",
    merge_enabled=True,
    lowercase_read=True,
)
