#!/usr/bin/env python3
"""R39 opt-in adoption contract; source-only, no Gradle/SDK or device."""
from __future__ import annotations
import hashlib
from pathlib import Path
import re
import tomllib
import unittest
ROOT = Path(__file__).resolve().parents[2]
APK = "app/build/outputs/apk/debug/CinderboxCompanion-v0.1.1.apk"
JAR = "423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9"
PINS = {"platforms;android-35", "build-tools;35.0.0", "platform-tools"}

def candidate(cfg: dict) -> tuple[dict, dict, list]:
    target = cfg["targets"]["cinderbox"]
    return (target["jobs"]["r39-hosted-lint"],
            target["jobs"]["r39-hosted-apk"],
            target["workflows"]["r39-hosted-debug"])

class TestR39HostedSource(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cfg = tomllib.loads((ROOT/'.devtool.toml').read_text())

    def test_exact_wrapper_and_distribution(self):
        self.assertEqual(hashlib.sha256((ROOT/'gradle/wrapper/gradle-wrapper.jar').read_bytes()).hexdigest(),JAR)
        props=(ROOT/'gradle/wrapper/gradle-wrapper.properties').read_text()
        self.assertIn('distributionUrl=https\\://services.gradle.org/distributions/gradle-8.11.1-bin.zip',props)
        self.assertIn('distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6',props)

    def test_custom_filename_is_exactly_declared(self):
        gradle=(ROOT/'app/build.gradle.kts').read_text()
        name=re.search(r'versionName\s*=\s*"([^"]+)"',gradle)
        self.assertIsNotNone(name)
        self.assertIn('"CinderboxCompanion-v$versionName.apk"',gradle)
        self.assertEqual(APK,f'app/build/outputs/apk/debug/CinderboxCompanion-v{name.group(1)}.apk')

    def test_reviewed_hosted_metadata_and_tasks(self):
        lint,apk,_=candidate(self.cfg)
        for name,job,task in [('lint',lint,':app:lintDebug'),('apk',apk,':app:assembleDebug')]:
            with self.subTest(name=name):
                self.assertEqual(job['runner'],'android')
                self.assertEqual(job['gradle_tasks'],[task])
                meta=job['metadata']
                self.assertEqual(meta['ci_toolchain'],'android-gradle')
                self.assertEqual(meta['ci_android_project_dir'],'')
                self.assertEqual(meta['ci_android_debug_variant'],'debug')
                self.assertEqual(meta['ci_android_java_version'],'17')
                self.assertEqual(set(meta['ci_android_sdk_packages']),PINS)
                self.assertEqual(meta['ci_android_wrapper_jar_sha256'],JAR)
                self.assertNotIn('connectedDebugAndroidTest',repr(job))
                self.assertNotIn('signing',repr(job).lower())
        self.assertNotIn('outputs',lint)
        self.assertEqual(apk['outputs'],[APK])
        self.assertEqual(apk['metadata']['ci_android_expected_output'],APK)
        self.assertIs(apk['metadata']['release_signed'],False)

    def test_gradle_project_root_contract_is_explicit_empty(self):
        lint, apk, _ = candidate(self.cfg)
        for job in (lint, apk):
            root = job['metadata']['ci_android_project_dir']
            self.assertEqual(root, '')
            self.assertEqual((ROOT / root).resolve(), ROOT.resolve())
        # This checks the root-project contract only. Versioned filename
        # admission must be qualified against Devtool's actual provider.
        self.assertIn('v0.1.1.apk', apk['outputs'][0])

    def test_native_graph_is_lint_then_apk(self):
        _,_,steps=candidate(self.cfg)
        self.assertEqual(steps,[
            {'id':'lint','ref':'job:r39-hosted-lint','depends_on':[]},
            {'id':'apk','ref':'job:r39-hosted-apk','depends_on':['lint']}])

    def test_legacy_jobs_are_not_replaced(self):
        target=self.cfg['targets']['cinderbox']
        self.assertIn('host-check',target['workflows'])
        self.assertIn('build',target['workflows'])
        self.assertEqual(target['jobs']['security-contract']['execution_environment'],'host')
        self.assertEqual(target['jobs']['debug-apk']['gradle_tasks'],[':app:assembleDebug'])
        self.assertEqual(target['jobs']['connected-tests']['metadata']['explicit_only'],True)


    def test_rm06_generated_input_ownership_is_explicit(self):
        # Exporter compile_graph: globs are refused once a DAG owns any output.
        # The repository checkout is SHA-bound; this list identifies only
        # exact tracked build anchors, not an incomplete filesystem sandbox.
        lint, apk, _ = candidate(self.cfg)
        wanted = [
            ".devtool.toml", "gradlew", "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties", "gradle/libs.versions.toml",
            "gradle.properties", "settings.gradle.kts", "build.gradle.kts",
            "app/build.gradle.kts", "app/src/main/AndroidManifest.xml",
        ]
        for name, job in (("lint", lint), ("apk", apk)):
            with self.subTest(name=name):
                self.assertEqual(job["inputs"], wanted)
                self.assertEqual(len(job["inputs"]), len(set(job["inputs"])))
                self.assertTrue(all((ROOT / rel).is_file() for rel in job["inputs"]))
                self.assertFalse(any(any(ch in rel for ch in "*?[]") for rel in job["inputs"]))
                self.assertFalse(set(job["inputs"]) & set(apk["outputs"]))

    def test_generated_input_glob_regression_fails_closed(self):
        import copy
        for name, unsafe in (("glob", "app/src/**"),
                             ("generated-output", APK),
                             ("outside", "../other")):
            with self.subTest(name=name):
                cfg = copy.deepcopy(self.cfg)
                lint, apk, _ = candidate(cfg)
                lint["inputs"].append(unsafe)
                ownership_ok = all(
                    rel not in apk["outputs"] and
                    not any(ch in rel for ch in "*?[]") and
                    not Path(rel).is_absolute() and ".." not in Path(rel).parts
                    for rel in lint["inputs"]
                )
                self.assertFalse(ownership_ok)

    def test_negative_drift_examples(self):
        import copy
        for case in ('release','device','unowned','unsigned_jar'):
            c=copy.deepcopy(self.cfg)
            lint,apk,_=candidate(c)
            if case=='release': apk['gradle_tasks']=[':app:assembleRelease']
            if case=='device': apk['gradle_tasks']=[':app:connectedDebugAndroidTest']
            if case=='unowned': apk['outputs']=['../secret.apk']
            if case=='unsigned_jar': apk['metadata']['ci_android_wrapper_jar_sha256']='0'*64
            valid=(apk['gradle_tasks']==[':app:assembleDebug'] and apk['outputs']==[APK]
                   and apk['metadata']['ci_android_wrapper_jar_sha256']==JAR)
            with self.subTest(case=case): self.assertFalse(valid)

if __name__=='__main__': unittest.main()
