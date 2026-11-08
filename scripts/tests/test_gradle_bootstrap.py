import importlib.util
from pathlib import Path
import subprocess
import unittest
from unittest.mock import Mock

spec = importlib.util.spec_from_file_location('bootstrap', Path(__file__).resolve().parents[1] / 'ci/gradle-bootstrap.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class GradleBootstrapTests(unittest.TestCase):
    def result(self, code, error=''):
        return subprocess.CompletedProcess([], code, stdout='', stderr=error)

    def test_network_retries_only_provision_and_eventually_succeeds(self):
        run = Mock(side_effect=[self.result(1, 'Server returned HTTP response code: 504'),
                                self.result(1, 'java.net.SocketTimeoutException'), self.result(0)])
        sleep = Mock()
        self.assertEqual(0, module.bootstrap(run, sleep))
        self.assertEqual([10, 20], [c.args[0] for c in sleep.call_args_list])
        for call in run.call_args_list:
            self.assertEqual(['--no-daemon', '--version'], call.args[0][1:])

    def test_network_failure_exhausts_three_attempts_and_fails(self):
        run = Mock(return_value=self.result(1, 'Server returned HTTP response code: 504'))
        self.assertEqual(1, module.bootstrap(run, Mock()))
        self.assertEqual(3, run.call_count)

    def test_validation_and_non_network_failures_are_not_retried(self):
        for error in ['Verification of Gradle distribution failed', 'Unsupported Java version',
                      'Verification of Gradle distribution failed java.net.SocketTimeoutException']:
            run = Mock(return_value=self.result(2, error))
            sleep = Mock()
            self.assertEqual(2, module.bootstrap(run, sleep))
            self.assertEqual(1, run.call_count)
            sleep.assert_not_called()
