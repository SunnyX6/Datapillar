from __future__ import annotations


def split_schema_object(name: str) -> tuple[str | None, str]:
    """
    按第一个 '.' 分割 qualified name，返回 (schema, object)。

    约定：
    - name 形如 "{schema}.{object}" 时返回 (schema, object)
    - name 不包含 '.' 或格式非法时返回 (None, 原始 name)
    """
    raw = (name or "").strip()
    if not raw:
        return (None, "")

    parts = raw.split(".", 1)
    if len(parts) != 2:
        return (None, raw)

    schema = parts[0].strip()
    obj = parts[1].strip()
    if not schema or not obj:
        return (None, raw)

    return (schema, obj)


def parse_schema_table(name: str) -> tuple[str, str] | None:
    """
    解析 schema.table，返回 (schema, table)。

    - 使用第一个 '.' 作为分隔符（兼容 table 内部包含 '.' 的情况）
    - schema/table 任一为空视为非法，返回 None
    """
    schema, table = split_schema_object(name)
    if not schema:
        return None
    return (schema, table)


def parse_table_column(name: str) -> tuple[str, str, str] | None:
    """
    解析 schema.table.column，返回 (schema, table, column)。

    - 使用前两个 '.' 作为分隔符（兼容 column 内部包含 '.' 的情况）
    - 任一段为空视为非法，返回 None
    """
    raw = (name or "").strip()
    if not raw:
        return None

    parts = raw.split(".", 2)
    if len(parts) != 3:
        return None

    schema = parts[0].strip()
    table = parts[1].strip()
    column = parts[2].strip()
    if not schema or not table or not column:
        return None

    return (schema, table, column)
