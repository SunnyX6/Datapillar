from __future__ import annotations

import pytest

import datapillar_oneagentic.security.validator as validator_module
from datapillar_oneagentic.security import (
    URLNotAllowedError,
    configure_security,
    get_security_config,
    reset_security_config,
    validate_url,
)


@pytest.fixture(autouse=True)
def _reset_security_config() -> None:
    reset_security_config()
    yield
    reset_security_config()


def test_is_private_ip_detects_localhost() -> None:
    assert validator_module.is_private_ip("localhost") is True
    assert validator_module.is_private_ip("127.0.0.1") is True


def test_is_private_ip_uses_dns_result(monkeypatch) -> None:
    def fake_getaddrinfo(_hostname, *_args, **_kwargs):
        return [(None, None, None, None, ("8.8.8.8", 0))]

    monkeypatch.setattr(validator_module.socket, "getaddrinfo", fake_getaddrinfo)
    assert validator_module.is_private_ip("public.example") is False


def test_configure_security_updates_config() -> None:
    configure_security(require_confirmation=False, allow_private_urls=True)
    config = get_security_config()
    assert config.require_confirmation is False
    assert config.allow_private_urls is True


def test_validate_url_rejects_unsupported_scheme() -> None:
    with pytest.raises(URLNotAllowedError):
        validate_url("ftp://example.com")


def test_validate_url_requires_https_when_configured(monkeypatch) -> None:
    configure_security(require_https=True)
    monkeypatch.setattr(validator_module, "is_private_ip", lambda _host: False)

    with pytest.raises(URLNotAllowedError):
        validate_url("http://example.com")

    validate_url("https://example.com")


def test_validate_url_rejects_private_when_not_allowed() -> None:
    configure_security(allow_private_urls=False)
    with pytest.raises(URLNotAllowedError):
        validate_url("http://127.0.0.1")


def test_validate_url_allows_whitelisted_domains(monkeypatch) -> None:
    configure_security(allowed_domains=["example.com"])
    monkeypatch.setattr(validator_module, "is_private_ip", lambda _host: False)

    validate_url("https://api.example.com/path")
    with pytest.raises(URLNotAllowedError):
        validate_url("https://bad.com")
