import sys
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from preflight_local3306 import build_backend_args, validate_runtime_args


class RuntimeGuardTest(unittest.TestCase):
    def test_only_loopback_3306_is_accepted(self):
        self.assertEqual(validate_runtime_args('127.0.0.1', 3306, 'dev'), [])
        self.assertEqual(validate_runtime_args('localhost', 3306, 'dev'), [])
        self.assertTrue(validate_runtime_args('10.0.0.2', 3306, 'dev'))
        self.assertTrue(validate_runtime_args('127.0.0.1', 3307, 'dev'))
        self.assertTrue(validate_runtime_args('127.0.0.1', 3306, 'prod'))
        self.assertTrue(validate_runtime_args('127.0.0.1', 3306, 'decision-demo'))

    def test_captcha_switch_is_process_argument_only(self):
        args = build_backend_args(8080, disable_captcha=True)
        self.assertIn('--captcha.enable=false', args)
        self.assertIn('--spring.profiles.active=dev', args)


if __name__ == '__main__':
    unittest.main()
