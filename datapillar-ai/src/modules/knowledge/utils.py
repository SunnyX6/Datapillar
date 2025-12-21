# -*- coding: utf-8 -*-
"""
知识图谱工具函数
"""

import base64
from typing import Any

import msgpack


def msgpack_encode(data: Any) -> str:
    """将数据编码为 msgpack + base64 字符串"""
    packed = msgpack.packb(data, use_bin_type=True)
    return base64.b64encode(packed).decode("ascii")
