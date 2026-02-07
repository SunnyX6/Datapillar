# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-02-07

"""私钥存储接口。"""

from __future__ import annotations

from abc import ABC, abstractmethod


class KeyStorage(ABC):
    @abstractmethod
    def load_private_key(self, tenant_id: int) -> bytes:
        raise NotImplementedError
