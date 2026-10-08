import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('mpos_ci_version', Path(__file__).resolve().parents[1] / 'scripts/ci/version.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class VersionTests(unittest.TestCase):
    config = {'versionCodeBase': 1000000, 'versionNameBase': '0.1'}

    def test_rerun_and_next_run_increase(self):
        first = module.version(self.config, 155, 1)
        rerun = module.version(self.config, 155, 999)
        next_run = module.version(self.config, 156, 1)
        self.assertLess(first[0], rerun[0])
        self.assertLess(rerun[0], next_run[0])
        self.assertEqual(first[1], '0.1.155-1')

    def test_invalid_counters_and_overflow_rejected(self):
        for run, attempt in [(0, 1), (1, 0), (1, 1000), (2100000, 1)]:
            with self.assertRaises(ValueError):
                module.version(self.config, run, attempt)
