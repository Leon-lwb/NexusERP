"""验证显式启用的编译探针入口安全，不启动容器或数据库。"""

import contextlib
import io
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch
import registry_java_runner as runner


class RegistryJavaRunnerSafetyTests(unittest.TestCase):
    def test_no_opt_in_never_constructs_or_starts_a_sandbox(self):
        with (
            patch.object(runner, "DockerSandbox") as factory,
            contextlib.redirect_stdout(io.StringIO()),
        ):
            self.assertEqual(2, runner.main([]))
            factory.assert_not_called()

    def test_external_endpoints_container_names_and_schema_overrides_are_not_cli_options(self):
        for option in ("--jdbc-url", "--container", "--host", "--schema", "--password"):
            with (
                self.subTest(option=option),
                contextlib.redirect_stderr(io.StringIO()),
                self.assertRaises(SystemExit),
            ):
                runner.arguments(["--run", option, "existing"])

    def test_output_cannot_escape_the_dedicated_results_directory(self):
        with self.assertRaises(runner.ValidationError):
            runner.result_directory("../outside")
        with self.assertRaises(runner.ValidationError):
            runner.result_directory("harness.py")
        result = runner.result_directory("results/registry-java-test")
        self.assertEqual(runner.HERE / "results/registry-java-test", result)

    def test_bridge_refuses_socket_creation_until_exact_container_ownership_passes(self):
        box = Mock()
        box.verify.side_effect = runner.ValidationError("wrong owner")
        with patch.object(runner.socketserver, "ThreadingTCPServer") as server:
            with self.assertRaises(runner.ValidationError):
                runner.OwnedJdbcBridge(box)
            server.assert_not_called()

    def test_failed_skipped_or_zero_java_tests_cannot_be_reported_as_a_pass(self):
        self.assertTrue(
            runner.successful_probe(
                0, {"testsFound": 12, "testsSucceeded": 12, "testsFailed": 0, "testsSkipped": 0}
            )
        )
        for code, evidence in [
            (0, {}),
            (1, {"testsFound": 12, "testsSucceeded": 12}),
            (0, {"testsFound": 12, "testsSucceeded": 11, "testsFailed": 1, "testsSkipped": 0}),
            (0, {"testsFound": 12, "testsSucceeded": 11, "testsFailed": 0, "testsSkipped": 1}),
            (0, {"testsFound": 12, "testsSucceeded": 1, "testsFailed": 0, "testsSkipped": 0}),
        ]:
            with self.subTest(code=code, evidence=evidence):
                self.assertFalse(runner.successful_probe(code, evidence))

    def test_only_local_cached_jars_and_existing_class_directories_are_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            existing = Path(tmp) / "classes"
            existing.mkdir()
            self.assertEqual([str(existing)], runner.classpath_entries(str(existing)))
            with self.assertRaises(runner.ValidationError):
                runner.classpath_entries("https://example.invalid/dependency.jar")
            with self.assertRaises(runner.ValidationError):
                runner.classpath_entries(str(Path(tmp) / "missing.jar"))


if __name__ == "__main__":
    unittest.main()
