import importlib.util
import os
import shutil
import sys
import tempfile
import types
import unittest
from pathlib import Path


class DagGeneratorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.mkdtemp(prefix="datapillar-dags-")
        self._saved_modules = {}
        self._inject_fake_airflow_modules()
        self._inject_fake_jinja2_module()
        self.module = self._load_module()

    def tearDown(self) -> None:
        for name, previous in self._saved_modules.items():
            if previous is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = previous
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def test_should_cleanup_legacy_flat_file_on_startup(self) -> None:
        legacy_file = Path(self.temp_dir) / "datapillar_project_old.py"
        legacy_file.write_text("# legacy", encoding="utf-8")

        generator = self.module.DagGenerator()

        self.assertFalse(legacy_file.exists())
        self.assertEqual([], generator.list_dags())

    def test_should_build_and_parse_canonical_dag_id(self) -> None:
        generator = self.module.DagGenerator()

        dag_id = generator.build_dag_id("Tenant_A", 42)
        parsed_tenant, parsed_workflow = generator.parse_dag_id(dag_id)

        self.assertEqual("dp_tenant_a_w42", dag_id)
        self.assertEqual("tenant_a", parsed_tenant)
        self.assertEqual(42, parsed_workflow)

    def test_should_generate_tenant_isolated_path(self) -> None:
        generator = self.module.DagGenerator()
        dag_id = generator.build_dag_id("tenant_a", 9)
        workflow = {"workflow_name": "wf_9", "jobs": [], "dependencies": []}

        dag_path = generator.generate("tenant_a", 9, dag_id, workflow)

        self.assertTrue(Path(dag_path).exists())
        self.assertTrue(dag_path.endswith("datapillar/tenant_a/wf_9.py"))
        self.assertEqual(["dp_tenant_a_w9"], generator.list_dags())

    def _inject_fake_airflow_modules(self) -> None:
        airflow_module = types.ModuleType("airflow")
        airflow_module.__version__ = "3.0.0"
        self._save_module("airflow", airflow_module)

        airflow_conf_module = types.ModuleType("airflow.configuration")
        airflow_conf_module.conf = types.SimpleNamespace(
            get=lambda section, key: self.temp_dir
        )
        self._save_module("airflow.configuration", airflow_conf_module)

    def _inject_fake_jinja2_module(self) -> None:
        class _FakeTemplate:
            @staticmethod
            def render(**kwargs):
                return f"# dag_id={kwargs.get('dag_id', '')}\n"

        class _FakeEnvironment:
            def __init__(self, loader=None, trim_blocks=False, lstrip_blocks=False):
                self.loader = loader
                self.trim_blocks = trim_blocks
                self.lstrip_blocks = lstrip_blocks

            @staticmethod
            def get_template(name: str):
                return _FakeTemplate()

        class _FakeFileSystemLoader:
            def __init__(self, searchpath):
                self.searchpath = searchpath

        jinja2_module = types.ModuleType("jinja2")
        jinja2_module.Environment = _FakeEnvironment
        jinja2_module.FileSystemLoader = _FakeFileSystemLoader
        self._save_module("jinja2", jinja2_module)

    def _save_module(self, name: str, module: types.ModuleType) -> None:
        self._saved_modules[name] = sys.modules.get(name)
        sys.modules[name] = module

    def _load_module(self):
        module_path = Path(__file__).resolve().parents[1] / "dag_generator.py"
        spec = importlib.util.spec_from_file_location("datapillar_dag_generator_under_test", module_path)
        if spec is None or spec.loader is None:
            raise RuntimeError("Failed to create dag_generator module spec")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module


if __name__ == "__main__":
    unittest.main()
