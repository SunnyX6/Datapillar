"""
预配置的 MCP 服务器

提供常用 MCP 服务器的快捷配置。
"""

from __future__ import annotations

from src.modules.oneagentic.mcp.config import MCPServerStdio


def filesystem_server(
    allowed_directories: list[str],
    *,
    timeout: int = 30,
) -> MCPServerStdio:
    """
    Filesystem MCP 服务器

    提供文件读写能力。

    参数：
    - allowed_directories: 允许访问的目录列表
    - timeout: 超时时间

    返回：
    - MCPServerStdio 配置

    使用示例：
    ```python
    config = filesystem_server(["/tmp", "/home/user/projects"])

    async with MCPClient(config) as client:
        result = await client.call_tool("read_file", {"path": "/tmp/test.txt"})
    ```

    可用工具：
    - read_file: 读取文件
    - read_multiple_files: 读取多个文件
    - write_file: 写入文件
    - create_directory: 创建目录
    - list_directory: 列出目录
    - move_file: 移动文件
    - search_files: 搜索文件
    - get_file_info: 获取文件信息
    """
    return MCPServerStdio(
        command="npx",
        args=[
            "-y",
            "@modelcontextprotocol/server-filesystem",
            *allowed_directories,
        ],
        timeout=timeout,
    )


def git_server(
    repository_path: str | None = None,
    *,
    timeout: int = 30,
) -> MCPServerStdio:
    """
    Git MCP 服务器

    提供 Git 操作能力。

    参数：
    - repository_path: 仓库路径（默认当前目录）
    - timeout: 超时时间

    返回：
    - MCPServerStdio 配置

    使用示例：
    ```python
    config = git_server("/path/to/repo")

    async with MCPClient(config) as client:
        result = await client.call_tool("git_status", {})
    ```

    可用工具：
    - git_status: 获取状态
    - git_diff: 查看差异
    - git_commit: 提交
    - git_log: 查看日志
    - git_show: 查看提交详情
    """
    args = ["-y", "@modelcontextprotocol/server-git"]
    if repository_path:
        args.append(repository_path)

    return MCPServerStdio(
        command="npx",
        args=args,
        timeout=timeout,
    )


def memory_server(
    *,
    timeout: int = 30,
) -> MCPServerStdio:
    """
    Memory MCP 服务器

    提供持久记忆能力（知识图谱）。

    参数：
    - timeout: 超时时间

    返回：
    - MCPServerStdio 配置

    可用工具：
    - create_entities: 创建实体
    - create_relations: 创建关系
    - add_observations: 添加观察
    - delete_entities: 删除实体
    - delete_observations: 删除观察
    - delete_relations: 删除关系
    - read_graph: 读取图
    - search_nodes: 搜索节点
    - open_nodes: 打开节点
    """
    return MCPServerStdio(
        command="npx",
        args=["-y", "@modelcontextprotocol/server-memory"],
        timeout=timeout,
    )


def postgres_server(
    connection_string: str,
    *,
    timeout: int = 30,
) -> MCPServerStdio:
    """
    PostgreSQL MCP 服务器

    提供 PostgreSQL 数据库操作能力。

    参数：
    - connection_string: 数据库连接字符串
    - timeout: 超时时间

    返回：
    - MCPServerStdio 配置

    使用示例：
    ```python
    config = postgres_server("postgresql://user:pass@localhost/db")

    async with MCPClient(config) as client:
        result = await client.call_tool("query", {"sql": "SELECT * FROM users"})
    ```

    可用工具：
    - query: 执行查询
    - list_tables: 列出表
    - describe_table: 描述表结构
    """
    return MCPServerStdio(
        command="npx",
        args=["-y", "@modelcontextprotocol/server-postgres", connection_string],
        timeout=timeout,
    )


def sqlite_server(
    database_path: str,
    *,
    timeout: int = 30,
) -> MCPServerStdio:
    """
    SQLite MCP 服务器

    提供 SQLite 数据库操作能力。

    参数：
    - database_path: 数据库文件路径
    - timeout: 超时时间

    返回：
    - MCPServerStdio 配置

    可用工具：
    - read_query: 执行查询
    - write_query: 执行写入
    - create_table: 创建表
    - list_tables: 列出表
    - describe_table: 描述表结构
    """
    return MCPServerStdio(
        command="npx",
        args=["-y", "@modelcontextprotocol/server-sqlite", database_path],
        timeout=timeout,
    )


def fetch_server(
    *,
    timeout: int = 30,
) -> MCPServerStdio:
    """
    Fetch MCP 服务器

    提供网页抓取能力。

    参数：
    - timeout: 超时时间

    返回：
    - MCPServerStdio 配置

    可用工具：
    - fetch: 抓取网页并转换为 Markdown
    """
    return MCPServerStdio(
        command="npx",
        args=["-y", "@modelcontextprotocol/server-fetch"],
        timeout=timeout,
    )


def sequential_thinking_server(
    *,
    timeout: int = 60,
) -> MCPServerStdio:
    """
    Sequential Thinking MCP 服务器

    提供分步推理能力。

    参数：
    - timeout: 超时时间

    返回：
    - MCPServerStdio 配置

    可用工具：
    - sequentialthinking: 分步推理
    """
    return MCPServerStdio(
        command="npx",
        args=["-y", "@modelcontextprotocol/server-sequential-thinking"],
        timeout=timeout,
    )
