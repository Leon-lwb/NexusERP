import json
import shutil
import subprocess
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[4]
SCRIPT = ROOT / 'doc/ai/full-flow/scripts/run-local-full-flow.ps1'
POWERSHELL = shutil.which('pwsh') or shutil.which('powershell')


@unittest.skipUnless(POWERSHELL, 'PowerShell executable is unavailable')
class LocalFullFlowScriptTest(unittest.TestCase):
    def run_script(self, *args, shell=None):
        self.assertIn(args[0], ('-ValidateOnly', '-ValidateIdentityOnly', '-ValidateMetadataOnly', '-ValidateStopIdentityOnly'))
        self.assertNotIn('-Stop', args)
        return subprocess.run(
            [shell or POWERSHELL, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(SCRIPT), *args],
            cwd=ROOT,
            capture_output=True,
            text=True,
            encoding='utf-8',
            errors='replace',
            check=False,
        )

    def test_validate_only_quotes_space_containing_jar_and_captcha_argument(self):
        jar = r'C:\Workspaces\Nexus ERP\ruoyi-admin\target\ruoyi-admin.jar'
        result = self.run_script('-ValidateOnly', '-DisableCaptcha', '-JarPath', jar, '-Port', '8080')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        payload = json.loads(result.stdout)
        self.assertEqual(payload['arguments'][0], '-jar')
        self.assertEqual(payload['arguments'][1], f'"{jar}"')
        self.assertIn('--spring.profiles.active=dev', payload['arguments'])
        self.assertIn('--server.port=8080', payload['arguments'])
        self.assertIn('--captcha.enable=false', payload['arguments'])

    def test_identity_validation_rejects_devx_and_jar_prefix_tokens(self):
        jar = r'C:\Workspaces\Nexus ERP\ruoyi-admin\target\ruoyi-admin.jar'
        valid = f'java -jar "{jar}" --spring.profiles.active=dev --server.port=8080'
        devx = f'java -jar "{jar}" --spring.profiles.active=devx --server.port=8080'
        prefixed = f'java -jar "{jar}.bak" --spring.profiles.active=dev --server.port=8080'
        for command_line, expected in ((valid, True), (devx, False), (prefixed, False)):
            result = self.run_script(
                '-ValidateIdentityOnly', '-JarPath', jar, '-Port', '8080',
                '-IdentityProcessName', 'java', '-IdentityCommandLine', command_line,
                '-IdentityCreationTime', '2026-09-28T01:02:03.0000000Z',
                '-ExpectedCreationTime', '2026-09-28T01:02:03.0000000Z',
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertEqual(json.loads(result.stdout)['matches'], expected)
    def test_identity_rejects_conflicting_profile_and_port_tokens(self):
        jar = r'C:\Workspaces\Nexus ERP\ruoyi-admin\target\ruoyi-admin.jar'
        base = f'java -jar "{jar}" --spring.profiles.active=dev --server.port=8080'
        for suffix in (' --spring.profiles.active=prod', ' --server.port=9090'):
            with self.subTest(suffix=suffix):
                result = self.run_script(
                    '-ValidateIdentityOnly', '-JarPath', jar, '-Port', '8080',
                    '-IdentityCreationTime', '2026-09-28T01:02:03.0000000Z',
                    '-ExpectedCreationTime', '2026-09-28T01:02:03.0000000Z',
                    '-IdentityCommandLine', base + suffix,
                )
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertFalse(json.loads(result.stdout)['matches'])

    def test_identity_requires_exact_creation_time(self):
        jar = r'C:\Workspaces\Nexus ERP\ruoyi-admin\target\ruoyi-admin.jar'
        command = f'java -jar "{jar}" --spring.profiles.active=dev --server.port=8080'
        for actual, expected, match in (
            ('2026-09-28T01:02:03.0000000Z', '2026-09-28T01:02:03.0000000Z', True),
            ('2026-09-28T01:02:04.0000000Z', '2026-09-28T01:02:03.0000000Z', False),
            ('', '2026-09-28T01:02:03.0000000Z', False),
        ):
            with self.subTest(actual=actual):
                result = self.run_script(
                    '-ValidateIdentityOnly', '-JarPath', jar, '-Port', '8080',
                    '-IdentityCreationTime', actual, '-ExpectedCreationTime', expected,
                    '-IdentityCommandLine', command,
                )
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(json.loads(result.stdout)['matches'], match)

    def test_stop_identity_validation_round_trips_json_creation_time_and_fails_closed(self):
        # 仅模拟 CIM 身份；此模式不得检查或停止真实进程。
        jar = r'C:\Workspaces\Nexus ERP\ruoyi-admin\target\ruoyi-admin.jar'
        creation_time = '2026-09-28T05:38:50.8169570+00:00'
        command = f'java -jar "{jar}" --spring.profiles.active=dev --server.port=8080'
        metadata = {'pid': 8988, 'port': 8080, 'profile': 'dev',
                    'jar': jar, 'creationTime': creation_time}

        def check(case, changes=None, *, pid_text='8988', identity_pid='8988',
                  identity_time=creation_time, identity_command=command):
            with self.subTest(case=case):
                stored = {**metadata, **(changes or {})}
                result = self.run_script(
                    '-ValidateStopIdentityOnly', '-JarPath', jar, '-PidText', pid_text,
                    '-MetadataJson', json.dumps(stored), '-IdentityPid', identity_pid,
                    '-IdentityCreationTime', identity_time, '-IdentityCommandLine', identity_command,
                )
                if case == 'matching':
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    self.assertTrue(json.loads(result.stdout)['matches'])
                elif case.startswith('metadata ') and case != 'metadata milliseconds' and case != 'metadata port':
                    self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
                else:
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    self.assertFalse(json.loads(result.stdout)['matches'])

        check('matching')
        check('milliseconds', identity_time='2026-09-28T05:38:50.8169571+00:00')
        check('metadata milliseconds', {'creationTime': '2026-09-28T05:38:50.8169571+00:00'})
        check('metadata pid', {'pid': 8989})
        check('process pid', identity_pid='8989')
        check('jar', identity_command=command.replace(jar, jar + '.bak'))
        check('metadata jar', {'jar': jar + '.bak'})
        check('profile', identity_command=command.replace('active=dev', 'active=prod'))
        check('port', identity_command=command.replace('port=8080', 'port=9090'))
        check('metadata profile', {'profile': 'prod'})
        check('metadata port', {'port': 9090})

    def test_cim_datetime_identity_round_trip_in_validation_only_mode(self):
        jar = r'C:\Workspaces\Nexus ERP\ruoyi-admin\target\ruoyi-admin.jar'
        creation_time = '2026-09-28T05:38:50.8169570+00:00'
        metadata = json.dumps({'pid': 8988, 'port': 8080, 'profile': 'dev',
                               'jar': jar, 'creationTime': creation_time})
        command = f'java -jar "{jar}" --spring.profiles.active=dev --server.port=8080'
        for shell in filter(None, (shutil.which('powershell'), shutil.which('pwsh'))):
            for actual, expected in ((creation_time, True),
                                     ('2026-09-28T05:38:50.8169571+00:00', False)):
                with self.subTest(shell=shell, actual=actual):
                    result = self.run_script(
                        '-ValidateStopIdentityOnly', '-SimulateCimDateTime', '-JarPath', jar,
                        '-PidText', '8988', '-MetadataJson', metadata, '-IdentityPid', '8988',
                        '-IdentityCreationTime', actual, '-IdentityCommandLine', command,
                        shell=shell,
                    )
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    self.assertEqual(json.loads(result.stdout)['matches'], expected)

    def test_cim_datetime_switch_requires_identity_validation(self):
        # 实时调用必须在任何运行时文件操作前拒绝此开关。
        for shell in filter(None, (shutil.which('powershell'), shutil.which('pwsh'))):
            with self.subTest(shell=shell):
                result = subprocess.run(
                    [shell, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', str(SCRIPT),
                     '-SimulateCimDateTime'], cwd=ROOT, capture_output=True, text=True,
                    encoding='utf-8', errors='replace', check=False,
                )
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('only permitted in identity validation modes', result.stderr)

    def test_metadata_validation_rejects_mismatched_pid(self):
        jar = str(ROOT / 'ruoyi-admin/target/ruoyi-admin.jar')
        metadata = json.dumps({'pid': 1235, 'port': 8080, 'profile': 'dev',
                               'jar': jar, 'creationTime': '2026-09-28T01:02:03.0000000Z'})
        result = self.run_script('-ValidateMetadataOnly', '-PidText', '1234',
                                 '-MetadataJson', metadata)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('pid', result.stderr.lower())

    def test_metadata_validation_requires_creation_time(self):
        jar = str(ROOT / 'ruoyi-admin/target/ruoyi-admin.jar')
        metadata = json.dumps({'pid': 1234, 'port': 8080, 'profile': 'dev', 'jar': jar})
        result = self.run_script('-ValidateMetadataOnly', '-PidText', '1234', '-MetadataJson', metadata)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('creation time', result.stderr.lower())

    def test_live_overrides_are_guarded_before_any_runtime_file_action(self):
        source = SCRIPT.read_text(encoding='utf-8-sig')
        guard = source.index("$PSBoundParameters.ContainsKey('JarPath')")
        self.assertIn("$PSBoundParameters.ContainsKey('RunDirectory')", source[:source.index('New-Item')])
        self.assertLess(guard, source.index('New-Item'))
        self.assertIn('throw', source[guard:source.index('New-Item')])
        self.assertNotIn('Start-Process', source[:source.index('New-Item')])

    def test_spawn_cleanup_wraps_metadata_steps_and_only_targets_spawned_object(self):
        source = SCRIPT.read_text(encoding='utf-8-sig')
        launch = source[source.rindex('$arguments = @(Build-JavaArguments'):]
        self.assertRegex(launch, r'(?s)\btry\s*\{\s*\$started\s*=\s*Start-Process')
        self.assertRegex(launch, r'(?s)Start-Process.*Get-CimInstance.*Set-Content.*ConvertTo-Json.*\}\s*catch\s*\{')
        catch = launch[launch.index('} catch {'):]
        self.assertIn('Stop-Process -InputObject $started', catch)
        self.assertNotIn('Stop-Process -Id', catch)
        self.assertIn('Remove-Item -LiteralPath $pidFile', catch)
        self.assertIn('Remove-Item -LiteralPath $backendJson', catch)
        self.assertIn('throw', catch)


if __name__ == '__main__':
    unittest.main()
