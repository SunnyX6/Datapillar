from src.shared.config import settings


def test_settings_from_package_is_dynaconf_object():
    assert hasattr(settings, "get")
    assert callable(settings.get)

