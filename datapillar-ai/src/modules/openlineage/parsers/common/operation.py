# -*- coding: utf-8 -*-
# @author Sunny
# @date 2026-01-27

from __future__ import annotations

from src.modules.openlineage.schemas.events import RunEvent


def get_operation(event: RunEvent) -> str:
    job_name = event.job.name if event.job else ""
    return job_name.split(".")[-1] if "." in job_name else job_name
