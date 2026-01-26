"""Time utilities."""

from __future__ import annotations

import time


def now_ms() -> int:
    """Return current time in milliseconds."""
    return int(time.time() * 1000)
