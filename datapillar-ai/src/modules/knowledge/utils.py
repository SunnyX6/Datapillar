"""
知识图谱工具函数
"""

import base64
from typing import Any

import msgpack


def msgpack_encode(data: Any) -> str:
    """将数据编码为 msgpack + base64 字符串"""
    packed = msgpack.packb(data, use_bin_type=True)
    if not isinstance(packed, (bytes, bytearray)):
        raise TypeError("msgpack.packb 期望返回 bytes/bytearray")
    return base64.b64encode(bytes(packed)).decode("ascii")
