"""Release policy regressions, independent of the APK implementation."""
from pathlib import Path
import re
import unittest
ROOT=Path(__file__).resolve().parents[2]

def job_blocks(text):
    return dict(re.findall(r'^  ([a-z][a-z-]*):\n(.*?)(?=^  [a-z][a-z-]*:\n|\Z)',text.split('\njobs:\n',1)[1],re.M|re.S))

def dependencies(block):
    match=re.search(r'^    needs: (.+)$',block,re.M)
    return set(match.group(1).strip('[]').replace(' ','').split(',')) if match else set()

class PipelinePolicyTests(unittest.TestCase):
    def setUp(self):
        self.text=(ROOT/'.github/workflows/android.yml').read_text();self.jobs=job_blocks(self.text)
    def test_parallel_gates_all_precede_signing_and_signed_install_precedes_release(self):
        for job in ['tooling','checks','emulator']:self.assertEqual(set(),dependencies(self.jobs[job]))
        self.assertEqual({'tooling','checks','emulator'},dependencies(self.jobs['signed-apk']))
        self.assertEqual({'signed-apk'},dependencies(self.jobs['signed-install']))
        self.assertEqual({'signed-apk','signed-install'},dependencies(self.jobs['publish']))
        self.assertFalse(any('publish' in dependencies(block) for block in self.jobs.values()))
        def visit(name,path):
            self.assertNotIn(name,path)
            for parent in dependencies(self.jobs[name]):visit(parent,path|{name})
        for job in self.jobs:visit(job,set())
    def test_every_main_push_is_queued_and_workflow_identity_is_preserved(self):
        self.assertTrue(self.text.startswith('name: Android\n'))
        header=self.text.split('\njobs:\n',1)[0]
        self.assertIn('branches: [main]',header);self.assertNotIn('paths-ignore',header);self.assertNotIn('paths:',header)
        self.assertIn('cancel-in-progress: false',header);self.assertIn('queue: max',header)
    def test_secrets_only_in_trusted_signing_and_only_publisher_can_write_contents(self):
        for name,block in self.jobs.items():
            if 'secrets.' in block:
                self.assertEqual('signed-apk',name);self.assertIn("github.ref == 'refs/heads/main'",block)
            if 'contents: write' in block:self.assertEqual('publish',name)
        for file in [ROOT/'.github/workflows/android.yml',ROOT/'.github/actions/android-setup/action.yml']:
            for action in re.findall(r'uses: ([^\s]+)',file.read_text()):
                self.assertTrue(action.startswith('./') or re.fullmatch(r'[^@]+@[0-9a-f]{40}',action),action)
