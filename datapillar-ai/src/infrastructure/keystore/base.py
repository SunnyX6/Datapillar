# @author Sunny
# @date 2026-02-07

"""Private key storage interface."""

from __future__ import annotations

from abc import ABC, abstractmethod


class KeyStorage(ABC):
    @abstractmethod
    def load_private_key(self, tenant_code: str) -> bytes:
        raise NotImplementedError
