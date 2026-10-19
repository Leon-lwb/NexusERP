"""converter真实MySQL入口边界；默认单测不启动容器。"""
import unittest
from pathlib import Path
from unittest.mock import patch
try:
    import converter_mysql57_runner as runner
except ModuleNotFoundError:
    runner=None

class ConverterMysqlRunnerSafetyTests(unittest.TestCase):
    def setUp(self):self.assertIsNotNone(runner,"需要隔离converter SQL执行入口")
    def test_no_opt_in_never_constructs_a_container(self):
        with patch.object(runner,"DockerSandbox") as factory:
            self.assertEqual(2,runner.main([]));factory.assert_not_called()
    def test_existing_endpoints_or_targets_are_not_arguments(self):
        for option in ("--jdbc-url","--container","--database","--host","--password"):
            with self.subTest(option=option),self.assertRaises(SystemExit):runner.arguments(["--run",option,"existing"])
    def test_result_path_is_private_and_cannot_replace_hume_results(self):
        self.assertEqual(runner.HERE/"results"/"converter-sql"/"sample",runner.result_directory("results/converter-sql/sample"))
        for path in ("../outside","results/hume","harness.py","results/converter-sql/../existing"):
            with self.subTest(path=path),self.assertRaises(runner.ValidationError):runner.result_directory(path)
    def test_review_sql_must_be_from_the_owned_output_and_bound_target(self):
        good="-- target: erp:mtc_owned_1\n"+runner.sql_literal("owned-server")
        runner.check_review_binding(good,"mtc_owned_1","owned-server")
        for sql in (good.replace("mtc_owned_1","existing"),good.replace(runner.sql_literal("owned-server"),"NULL")):
            with self.assertRaises(runner.ValidationError):runner.check_review_binding(sql,"mtc_owned_1","owned-server")

if __name__=="__main__":unittest.main()
