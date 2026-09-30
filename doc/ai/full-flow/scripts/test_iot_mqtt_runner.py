"""本地 IoT MQTT 3.1.1 验收执行器的失败先行测试。"""
import importlib.util
import json
import pathlib
import os
import tempfile
import unittest
from unittest.mock import patch

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('iot_mqtt_runner', HERE / 'iot_mqtt_runner.py')
iot = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(iot)


class IoTMqttRunnerTests(unittest.TestCase):
    def test_remaining_length_boundary(self):
        self.assertEqual(iot.remaining_length(127), b'\x7f')
        self.assertEqual(iot.remaining_length(128), b'\x80\x01')
        with self.assertRaises(ValueError):
            iot.remaining_length(-1)

    def test_connect_has_v311_clean_session_credentials_and_never_repr_secret(self):
        packet = iot.connect_packet('DEV0025', 'topsecret')
        self.assertEqual(packet[0], 0x10)
        self.assertIn(b'\x00\x04MQTT\x04\xc2', packet)
        self.assertEqual(packet.count(b'\x00\x07DEV0025'), 2)
        self.assertIn(b'\x00\x09topsecret', packet)
        with self.assertRaisesRegex(ValueError, 'device code'):
            iot.connect_packet('other/device', 'topsecret')

    def test_publish_is_qos1_tenant_scoped_not_retained(self):
        packet = iot.publish_packet('000000', 'DEV0025', 'temperature', 187.2, 513)
        self.assertEqual(packet[0], 0x32)
        self.assertIn(b'iot/000000/DEV0025/property/post\x02\x01', packet)
        self.assertIn(b'"quality":"GOOD"', packet)
        with self.assertRaises(ValueError):
            iot.publish_packet('other', 'DEV0025', 'temperature', 187.2, 1)

    def test_latest_evidence_normalizes_only_plausible_finite_temperature(self):
        for malformed in ('my-secret-token', 'NaN', 'Infinity', '1e309', 250.01, True, {'token': 'xyz'}):
            with self.subTest(value_type=type(malformed).__name__,value=str(malformed)[:10]):
                projected = iot.safe_evidence({'latest': malformed, 'result': 'failed'})
                self.assertNotIn('latest', projected)
                with self.assertRaises(iot.ScopeFailure):
                    iot.parse_latest_temperature({'temperature': malformed})
        self.assertEqual(iot.safe_evidence({'latest': '187.3000'})['latest'], 187.3)
        self.assertEqual(iot.parse_latest_temperature({'temperature': '187.3000'}), 187.3)

    def test_packet_reads_full_mqtt_packet_from_fragmented_socket(self):
        class Fragmented:
            def __init__(self):
                self.parts = [b'\x40', b'\x02', b'\x02', b'\x01']
            def recv(self, amount):
                return self.parts.pop(0) if self.parts else b''
        self.assertEqual(iot.read_packet(Fragmented()), (0x40, b'\x02\x01'))

    def test_connack_and_puback_fail_closed(self):
        with self.assertRaisesRegex(iot.MqttFailure, 'authentication'):
            iot.require_connack(0x20, b'\x00\x04')
        with self.assertRaisesRegex(iot.MqttFailure, 'PUBACK'):
            iot.require_puback(0x40, b'\x02\x02', 513)
        iot.require_puback(0x40, b'\x02\x01', 513)

    def test_scope_must_match_existing_enabled_device_and_product(self):
        device = dict(device_id=2104512194450214913, device_code='DEV0025', tenant_id='000000', product_id=1, mes_equipment_id=1, status='ACTIVE')
        product = dict(product_id=1, tenant_id='000000', protocol_type='MQTT', status='ACTIVE')
        iot.require_scope(device, product, 2104512194450214913)
        with self.assertRaisesRegex(iot.ScopeFailure, 'scope'):
            iot.require_scope({**device, 'tenant_id': '100001'}, product, 2104512194450214913)
        with self.assertRaisesRegex(iot.ScopeFailure, 'scope'):
            iot.require_scope(device, {**product, 'status': 'INACTIVE'}, 2104512194450214913)

    def test_rule_selection_uses_product_priority_and_valid_range(self):
        rules = [dict(rule_id=7001, product_id=0, device_id=0, property_code='temperature', compare_operator='GT', threshold=80, duration_sec=0), dict(rule_id=8701, product_id=1, device_id=0, property_code='temperature', compare_operator='GT', threshold=220, duration_sec=30)]
        rule = iot.select_temperature_rule(rules, 1, 2104512194450214913)
        self.assertEqual(rule['rule_id'], 8701)
        self.assertEqual(iot.temperatures_for_rule(rule), (187.2, 224.8, 187.3))
        with self.assertRaises(iot.ScopeFailure):
            iot.temperatures_for_rule({**rule, 'threshold': 80})

    def test_device_level_lt_rule_preempts_product_gt_and_fails_acceptance(self):
        product = dict(rule_id=8701, product_id=1, device_id=0, property_code='temperature',
                       compare_operator='GT', threshold=220, duration_sec=30)
        device = dict(rule_id=9901, product_id=0, device_id=iot.DEVICE_ID, property_code='temperature',
                      compare_operator='LT', threshold=150, duration_sec=0)
        chosen = iot.select_temperature_rule([product, device], iot.PRODUCT_ID, iot.DEVICE_ID)
        self.assertEqual(chosen['rule_id'], 9901)
        with self.assertRaises(iot.ScopeFailure):
            iot.temperatures_for_rule(chosen)

    def test_device_level_gt_other_threshold_preempts_product_and_fails_acceptance(self):
        product = dict(rule_id=8701, product_id=1, device_id=0, property_code='temperature',
                       compare_operator='GT', threshold=220, duration_sec=30)
        device = dict(rule_id=9902, product_id=0, device_id=iot.DEVICE_ID, property_code='temperature',
                      compare_operator='GT', threshold=80, duration_sec=0)
        chosen = iot.select_temperature_rule([product, device], iot.PRODUCT_ID, iot.DEVICE_ID)
        self.assertEqual(chosen['rule_id'], 9902)
        with self.assertRaises(iot.ScopeFailure):
            iot.temperatures_for_rule(chosen)

    def test_device_scope_matches_backend_even_when_product_scope_does_not(self):
        product = dict(rule_id=8701, product_id=1, device_id=0, property_code='temperature',
                       compare_operator='GT', threshold=220, duration_sec=30)
        device = dict(rule_id=9903, product_id=77, device_id=iot.DEVICE_ID, property_code='temperature',
                      compare_operator='LT', threshold=150, duration_sec=0)
        chosen = iot.select_temperature_rule([product, device], iot.PRODUCT_ID, iot.DEVICE_ID)
        self.assertEqual(chosen['rule_id'], 9903)

    def test_equal_precedence_rules_fail_closed_instead_of_guessing_db_order(self):
        first = dict(rule_id=9901, product_id=0, device_id=iot.DEVICE_ID, property_code='temperature',
                     compare_operator='GT', threshold=220, duration_sec=0)
        second = {**first, 'product_id': 1, 'rule_id': 9902, 'compare_operator': 'LT'}
        with self.assertRaises(iot.ScopeFailure):
            iot.select_temperature_rule([first, second], iot.PRODUCT_ID, iot.DEVICE_ID)

    def test_evidence_projection_strips_tokens_secrets_password_and_headers(self):
        text = json.dumps(iot.safe_evidence({'device_id': '2104512194450214913', 'token': 'tokenSensitive', 'device_secret': 'secretSensitive', 'password': 'passwordSensitive', 'history_rows': 3}), ensure_ascii=False)
        self.assertNotIn('Sensitive', text)
        self.assertIn('history_rows', text)
        projected = iot.safe_evidence({'blocking_alert_id': '2104774461947330562', 'device_secret': 'not-real'})
        self.assertEqual(projected['blocking_alert_id'], '2104774461947330562')
        self.assertNotIn('device_secret', projected)

    def test_login_checks_captcha_data_not_envelope(self):
        from unittest.mock import Mock
        api = object.__new__(iot.LocalApi)
        api.base = 'http://127.0.0.1:8080'
        api.session = Mock()
        api.session.headers = {'clientid': 'local-id'}
        api.request = Mock(return_value=({'captchaEnabled': False}, {'code': 200, 'data': {'captchaEnabled': False}}))
        api.session.post.return_value.status_code = 200
        with patch.object(iot, 'encrypt_login', return_value=(b'cipher', 'header')), \
             patch.object(iot, 'decrypt_login_response', return_value={'code': 200, 'data': {'access_token': 'token'}}):
            api.login('admin', 'unprinted')
        self.assertEqual(api.token, 'token')

    def test_login_key_is_ascii_32_chars_for_backend_base64_utf8_decode(self):
        import base64
        import re
        from cryptography.hazmat.primitives.asymmetric import rsa, padding
        from cryptography.hazmat.primitives import serialization
        private = rsa.generate_private_key(public_exponent=65537, key_size=1024)
        public = base64.b64encode(private.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)).decode()
        with patch.object(iot, 'vite_value', return_value=public):
            _, header = iot.encrypt_login({'user': 'example'})
        key = base64.b64decode(private.decrypt(base64.b64decode(header), padding.PKCS1v15()))
        self.assertRegex(key.decode('utf-8'), r'^[A-Za-z0-9]{32}$')

    def test_login_response_without_encrypt_header_is_plain_json(self):
        from unittest.mock import Mock
        response = Mock()
        response.headers = {}
        response.json.return_value = {'code': 200, 'data': {'access_token': 'secret'}}
        self.assertEqual(iot.decrypt_login_response(response)['code'], 200)

    def test_read_only_inspection_selects_product_220_despite_global_80_and_active_alarm(self):
        scoped = dict(rule_id=8701, product_id=1, device_id=0, property_code='temperature', compare_operator='GT', threshold=220, duration_sec=30)
        global_rule = dict(rule_id=7001, product_id=0, device_id=0, property_code='temperature', compare_operator='GT', threshold=80, duration_sec=0)
        device = dict(device_id=iot.DEVICE_ID, device_code=iot.DEVICE_CODE, tenant_id=iot.TENANT_ID,
                      product_id=iot.PRODUCT_ID, mes_equipment_id=1, status='ACTIVE')
        product = dict(product_id=iot.PRODUCT_ID, tenant_id=iot.TENANT_ID,
                       product_code='TEMP-SENSOR', protocol_type='MQTT', status='ACTIVE')
        with patch.object(iot, 'read_only_query', side_effect=[(device,), (product,), (global_rule, scoped)]) as query, \
             patch.object(iot, 'db_alerts', return_value=[{'status': 'ACTIVE', 'active_flag': 1}]):
            self.assertEqual(iot.inspect_db('placeholder')['rule_id'], 8701)
        self.assertEqual(query.call_count, 3)
        self.assertTrue(all('SELECT ' in call.args[1] for call in query.call_args_list))

    def test_runtime_proof_fails_if_backend_predates_fix_or_artifact_is_stale(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(iot, 'ROOT', pathlib.Path(tmp)), \
             patch.object(iot, 'require_live_classpath', return_value=True):
            sources = ('ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/pipeline/IotAlertChecker.java',
                       'ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/device/controller/IotDeviceController.java')
            classes = ('ruoyi-modules/ruoyi-iot/target/classes/com/nexus/iot/pipeline/IotAlertChecker.class',
                       'ruoyi-modules/ruoyi-iot/target/classes/com/nexus/iot/device/controller/IotDeviceController.class')
            for name, seconds in [*( (source, 200) for source in sources), *( (clazz, 210) for clazz in classes)]:
                f = pathlib.Path(tmp) / name
                f.parent.mkdir(parents=True, exist_ok=True)
                f.write_bytes(b'compiled-or-source')
                os.utime(f, (seconds, seconds))
            new_process = {'pid': 42, 'startedUtc': '1970-01-01T00:03:40+00:00',
                           'isNexus': True, 'isJava': True}
            with patch.object(iot, 'read_backend_listener', return_value=new_process):
                self.assertTrue(iot.prove_fixed_backend_loaded())
            wmi_timestamp = {**new_process, 'startedUtc': '1970-01-01T00:03:40.0000000Z'}
            with patch.object(iot, 'read_backend_listener', return_value=wmi_timestamp):
                self.assertTrue(iot.prove_fixed_backend_loaded())
            with patch.object(iot, 'read_backend_listener', return_value={**new_process, 'startedUtc': '1970-01-01T00:03:15+00:00'}):
                with self.assertRaises(iot.RuntimeAttestationFailure):
                    iot.prove_fixed_backend_loaded()
            with patch.object(iot, 'read_backend_listener', return_value={**new_process, 'isNexus': False}):
                with self.assertRaises(iot.RuntimeAttestationFailure):
                    iot.prove_fixed_backend_loaded()
            os.utime(pathlib.Path(tmp) / classes[0], (190, 190))
            with patch.object(iot, 'read_backend_listener', return_value=new_process):
                with self.assertRaises(iot.RuntimeAttestationFailure):
                    iot.prove_fixed_backend_loaded()

    def test_runtime_proof_fails_closed_when_artifact_is_missing(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(iot, 'ROOT', pathlib.Path(tmp)), \
             patch.object(iot, 'read_backend_listener') as listener:
            with self.assertRaises(iot.RuntimeAttestationFailure):
                iot.prove_fixed_backend_loaded()
            listener.assert_not_called()

    def test_classpath_attestation_requires_real_iot_output_directory(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(iot, 'ROOT', pathlib.Path(tmp)):
            compiled = pathlib.Path(tmp) / 'ruoyi-modules/ruoyi-iot/target/classes'
            compiled.mkdir(parents=True)
            for relative in ('com/nexus/iot/pipeline/IotAlertChecker.class', 'com/nexus/iot/device/controller/IotDeviceController.class'):
                target = compiled / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(b'compiled')
            actual = str(compiled.resolve())
            escaped = actual.replace(chr(92), chr(92) * 2).replace(':', chr(92) + ':')
            self.assertTrue(iot.verify_iot_classpath(escaped))
            with self.assertRaises(iot.RuntimeAttestationFailure):
                iot.verify_iot_classpath(r'C:\\unrelated.jar')
            with self.assertRaises(iot.RuntimeAttestationFailure):
                iot.verify_iot_classpath(str(compiled.parent / 'different-classes'))

    def test_runtime_classpath_shadow_jar_cannot_prove_same_bytecode(self):
        import zipfile
        with tempfile.TemporaryDirectory() as tmp, patch.object(iot, 'ROOT', pathlib.Path(tmp)):
            compiled = pathlib.Path(tmp) / 'ruoyi-modules/ruoyi-iot/target/classes'
            compiled.mkdir(parents=True)
            for name in ('com/nexus/iot/pipeline/IotAlertChecker.class', 'com/nexus/iot/device/controller/IotDeviceController.class'):
                file = compiled / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_bytes(b'current-bytecode')
            shadow = pathlib.Path(tmp) / 'old-iot.jar'
            with zipfile.ZipFile(shadow, 'w') as jar:
                jar.writestr('com/nexus/iot/pipeline/IotAlertChecker.class', b'old-bytecode')
            with self.assertRaises(iot.RuntimeAttestationFailure):
                iot.verify_iot_classpath(str(shadow) + ';' + str(compiled))

    def test_attestation_rejects_runtime_agents_or_custom_classloaders(self):
        from unittest.mock import Mock
        for flags in ('jvm_args: -javaagent:agent.jar', 'jvm_args: -Djava.system.class.loader=Custom',
                      'jvm_args: -Xbootclasspath/a:shadow.jar', 'jvm_args: -agentpath:rewrite.dll'):
            with self.subTest(flags=flags), \
                 patch.object(iot.subprocess, 'run', return_value=Mock(returncode=0, stdout=flags, stderr='')), \
                 patch.object(iot.shutil, 'which', return_value='jcmd.exe'), \
                 self.assertRaises(iot.RuntimeAttestationFailure):
                iot.require_standard_class_loading(42)

    def test_live_classpath_probe_ignores_sensitive_jcmd_failures(self):
        from unittest.mock import Mock
        fake = Mock(returncode=1, stdout='secret-token', stderr='password')
        with patch.object(iot.subprocess, 'run', return_value=fake), \
             patch.object(iot.shutil, 'which', return_value='jcmd.exe'):
            with self.assertRaises(iot.RuntimeAttestationFailure) as error:
                iot.read_runtime_classpath(42)
        self.assertNotIn('secret-token', str(error.exception))
        self.assertNotIn('password', str(error.exception))

    def test_known_idea_launcher_agent_is_allowed_from_the_actual_parent_install(self):
        from unittest.mock import Mock
        import zipfile
        with tempfile.TemporaryDirectory(prefix='IDEA with spaces ') as tmp:
            install = pathlib.Path(tmp)
            (install / 'lib').mkdir()
            (install / 'bin').mkdir()
            (install / 'bin/idea64.exe').write_bytes(b'fixture-launcher')
            (install / 'product-info.json').write_text(json.dumps({'name': 'IntelliJ IDEA', 'productCode': 'IU'}), encoding='utf-8')
            agent = install / 'lib/idea_rt.jar'
            with zipfile.ZipFile(agent, 'w') as jar:
                jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nPremain-Class: com.intellij.rt.execution.application.AppMainV2$Agent\n')
            command = f'jvm_args: -Xmx1g -javaagent:{agent}=63342:{install / "bin"} -Dfile.encoding=UTF-8'
            response = Mock(returncode=0, stdout=command, stderr='')
            with patch.object(iot.subprocess, 'run', return_value=response), \
                 patch.object(iot.shutil, 'which', return_value='jcmd.exe'), \
                 patch.object(iot, 'read_idea_parent_roots', return_value=[install]):
                self.assertTrue(iot.require_standard_class_loading(42))
            with patch.object(iot.subprocess, 'run', return_value=response), \
                 patch.object(iot.shutil, 'which', return_value='jcmd.exe'), \
                 patch.object(iot, 'read_idea_parent_roots', return_value=[]), \
                 self.assertRaises(iot.RuntimeAttestationFailure):
                iot.require_standard_class_loading(42)

    def test_idea_jar_manifest_cannot_enable_class_redefinition(self):
        import zipfile
        with tempfile.TemporaryDirectory() as tmp:
            install = pathlib.Path(tmp)
            (install / 'lib').mkdir()
            (install / 'bin').mkdir()
            (install / 'bin/idea64.exe').write_bytes(b'fixture-launcher')
            (install / 'product-info.json').write_text(json.dumps({'name': 'IntelliJ IDEA', 'productCode': 'IC'}), encoding='utf-8')
            agent = install / 'lib/idea_rt.jar'
            with zipfile.ZipFile(agent, 'w') as jar:
                jar.writestr('META-INF/MANIFEST.MF', 'Premain-Class: com.intellij.rt.execution.application.AppMainV2$Agent\nCan-Retransform-Classes: true\n')
            with self.assertRaises(iot.RuntimeAttestationFailure):
                iot.verify_idea_launcher_agent(str(agent), [install])

    def test_attestation_checks_actual_process_classpath(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(iot, 'ROOT', pathlib.Path(tmp)):
            pairs = (('ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/pipeline/IotAlertChecker.java',
                      'ruoyi-modules/ruoyi-iot/target/classes/com/nexus/iot/pipeline/IotAlertChecker.class'),
                     ('ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/device/controller/IotDeviceController.java',
                      'ruoyi-modules/ruoyi-iot/target/classes/com/nexus/iot/device/controller/IotDeviceController.class'))
            for source, clazz in pairs:
                for name, seconds in ((source, 200), (clazz, 210)):
                    file = pathlib.Path(tmp) / name
                    file.parent.mkdir(parents=True, exist_ok=True)
                    file.write_bytes(b'compiled')
                    os.utime(file, (seconds, seconds))
            listener = {'pid': 42, 'startedUtc': '1970-01-01T00:03:40+00:00',
                        'isNexus': True, 'isJava': True}
            with patch.object(iot, 'read_backend_listener', return_value=listener), \
                 patch.object(iot, 'require_live_classpath', side_effect=iot.RuntimeAttestationFailure('not loaded')) as proof:
                with self.assertRaises(iot.RuntimeAttestationFailure):
                    iot.prove_fixed_backend_loaded()
            proof.assert_called_once_with(42)

    def test_attestation_detects_class_bytes_changed_during_process_probe(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(iot, 'ROOT', pathlib.Path(tmp)):
            files = []
            for source, clazz in iot.FIX_ARTIFACTS:
                for name, seconds in ((source, 200), (clazz, 210)):
                    file = pathlib.Path(tmp) / name
                    file.parent.mkdir(parents=True, exist_ok=True)
                    file.write_bytes(b'compiled')
                    os.utime(file, (seconds, seconds))
                    files.append(file)
            def change_bytes(pid):
                files[1].write_bytes(b'other-bytecode')
                os.utime(files[1], (210, 210))
                return True
            listener = {'pid': 42, 'startedUtc': '1970-01-01T00:03:40+00:00',
                        'isNexus': True, 'isJava': True}
            with patch.object(iot, 'read_backend_listener', return_value=listener), \
                 patch.object(iot, 'require_live_classpath', side_effect=change_bytes), \
                 self.assertRaises(iot.RuntimeAttestationFailure):
                iot.prove_fixed_backend_loaded()

    def test_listener_probe_rejects_failed_powershell_without_exposing_output(self):
        from unittest.mock import Mock
        with patch.object(iot.subprocess, 'run', return_value=Mock(returncode=1, stdout='secret', stderr='secret')):
            with self.assertRaises(iot.RuntimeAttestationFailure) as failure:
                iot.read_backend_listener()
        self.assertNotIn('secret', str(failure.exception))

    def test_existing_unresolved_alerts_block_even_if_status_flag_disagree(self):
        for status, flag in (('ACTIVE', 1), ('ACKNOWLEDGED', 1), ('ACTIVE', None), ('RESOLVED', 1)):
            with self.subTest(status=status, flag=flag), self.assertRaises(iot.UnresolvedAlertFailure):
                iot.require_no_unresolved_alert([{'alert_id': 123, 'status': status, 'active_flag': flag}])
        iot.require_no_unresolved_alert([{'alert_id': 123, 'status': 'RESOLVED', 'active_flag': None}])

    def test_read_only_run_allows_old_process_without_secret_rotation(self):
        from unittest.mock import Mock
        scoped = dict(rule_id=8701, product_id=1, device_id=0, property_code='temperature', compare_operator='GT', threshold=220, duration_sec=30)
        api = Mock()
        api.preflight.return_value = True
        with patch.object(iot, 'inspect_db', return_value=scoped), \
             patch.object(iot, 'LocalApi', return_value=api), \
             patch.object(iot, 'prove_fixed_backend_loaded') as gate, \
             patch.object(iot, 'db_unresolved_alerts') as alarms, \
             patch.object(iot, 'MqttDevice') as mqtt, \
             patch.object(iot, 'save_evidence'):
            result = iot.run('placeholder', 'user', 'placeholder', apply=False)
        self.assertEqual(result['result'], 'read_only_preflight')
        self.assertEqual(result['rule_id'], '8701')
        gate.assert_not_called()
        alarms.assert_not_called()
        api.reset_secret.assert_not_called()
        mqtt.assert_not_called()

    def test_apply_on_old_runtime_refuses_before_secret_rotation_or_mqtt(self):
        from unittest.mock import Mock
        scoped = dict(rule_id=8701, product_id=1, device_id=0, property_code='temperature', compare_operator='GT', threshold=220, duration_sec=30)
        api = Mock()
        api.preflight.return_value = True
        api.reset_secret.side_effect = AssertionError('rotation must not happen')
        with patch.object(iot, 'inspect_db', return_value=scoped), \
             patch.object(iot, 'LocalApi', return_value=api), \
             patch.object(iot, 'prove_fixed_backend_loaded', side_effect=iot.RuntimeAttestationFailure('backend predates fixes')) as gate, \
             patch.object(iot, 'db_unresolved_alerts', return_value=[]) as alarms, \
             patch.object(iot, 'db_history', return_value=[]) as history, \
             patch.object(iot, 'MqttDevice') as mqtt, \
             patch.object(iot, 'save_evidence'):
            result = iot.run('placeholder', 'user', 'placeholder', apply=True)
        self.assertEqual(result['category'], 'RuntimeAttestationFailure')
        self.assertEqual(result['result'], 'failed')
        gate.assert_called_once_with()
        history.assert_not_called()
        api.reset_secret.assert_not_called()
        mqtt.assert_not_called()

    def test_apply_on_new_runtime_still_blocks_existing_active_alert(self):
        from unittest.mock import Mock
        scoped = dict(rule_id=8701, product_id=1, device_id=0, property_code='temperature', compare_operator='GT', threshold=220, duration_sec=30)
        api = Mock()
        api.preflight.return_value = True
        api.reset_secret.side_effect = AssertionError('rotation must not happen')
        prior = [dict(alert_id=2104774461947330562, status='ACTIVE', active_flag=1)]
        with patch.object(iot, 'inspect_db', return_value=scoped), \
             patch.object(iot, 'LocalApi', return_value=api), \
             patch.object(iot, 'prove_fixed_backend_loaded', return_value=True), \
             patch.object(iot, 'db_unresolved_alerts', return_value=prior) as alarms, \
             patch.object(iot, 'db_history', return_value=[]) as history, \
             patch.object(iot, 'MqttDevice') as mqtt, \
             patch.object(iot, 'save_evidence'):
            result = iot.run('placeholder', 'user', 'placeholder', apply=True)
        self.assertEqual(result['category'], 'UnresolvedAlertFailure')
        self.assertEqual(result['blocking_alert_id'], '2104774461947330562')
        alarms.assert_called_once_with('placeholder')
        history.assert_not_called()
        api.reset_secret.assert_not_called()
        mqtt.assert_not_called()

    def test_unresolved_alert_query_scopes_tenant_device_property_without_recent_limit(self):
        with patch.object(iot, 'read_only_query', return_value=[]) as query:
            iot.db_unresolved_alerts('placeholder')
        sql, params = query.call_args.args[1:3]
        self.assertIn('tenant_id=%s', sql)
        self.assertIn('device_id=%s', sql)
        self.assertIn("property_code='temperature'", sql)
        self.assertIn('active_flag', sql)
        self.assertIn('ACKNOWLEDGED', sql)
        self.assertEqual(params, (iot.TENANT_ID, iot.DEVICE_ID))
        self.assertNotIn('LIMIT 5', sql)

    def test_new_alert_appearing_during_attestation_blocks_at_last_check(self):
        from unittest.mock import Mock
        scoped = dict(rule_id=8701, product_id=1, device_id=0, property_code='temperature', compare_operator='GT', threshold=220, duration_sec=30)
        api = Mock()
        api.preflight.return_value = True
        api.reset_secret.side_effect = AssertionError('rotation must not happen')
        prior = [dict(alert_id=2104774461947330562, status='ACTIVE', active_flag=1)]
        with patch.object(iot, 'inspect_db', return_value=scoped), \
             patch.object(iot, 'LocalApi', return_value=api), \
             patch.object(iot, 'db_unresolved_alerts', side_effect=[[], prior]) as alarms, \
             patch.object(iot, 'prove_fixed_backend_loaded', return_value=True) as proof, \
             patch.object(iot, 'db_history', return_value=[]), \
             patch.object(iot, 'MqttDevice') as mqtt, \
             patch.object(iot, 'save_evidence'):
            result = iot.run('placeholder', 'user', 'placeholder', apply=True)
        self.assertEqual(result['category'], 'UnresolvedAlertFailure')
        self.assertEqual(result['blocking_alert_id'], '2104774461947330562')
        self.assertEqual(alarms.call_count, 2)
        proof.assert_called_once_with()
        api.reset_secret.assert_not_called()
        mqtt.assert_not_called()

    def test_live_table_endpoint_is_a_table_envelope_not_nested_data(self):
        from unittest.mock import Mock
        api = object.__new__(iot.LocalApi)
        api.base = 'http://127.0.0.1:8080'
        api.session = Mock()
        api.session.request.return_value.status_code = 200
        api.session.request.return_value.json.return_value = {'code': 200, 'rows': [{'dataId': '1'}], 'total': 1}
        data, envelope = api.request('GET', f'/iot/device/{iot.DEVICE_ID}/data')
        self.assertEqual(data, [{'dataId': '1'}])
        self.assertEqual(envelope['total'], 1)

    def test_live_api_latest_must_validate_identifier_without_coercion(self):
        self.assertEqual(iot.parse_latest_temperature({'temperature': '187.3000'}), 187.3)
        self.assertIsNone(iot.parse_latest_temperature({}))
        with self.assertRaises(iot.ScopeFailure):
            iot.parse_latest_temperature({'temperature': 'secret'})

    def test_sequence_uses_unused_plausible_values_in_observed_normal_range(self):
        rule = dict(rule_id=8701, property_code='temperature', compare_operator='GT', threshold=220, duration_sec=30)
        template = dict(tenant_id=iot.TENANT_ID, device_code=iot.DEVICE_CODE,
                        property_code='temperature', source='MQTT', quality='GOOD')
        history = [{**template, 'numeric_value': value} for value in (187.2, 187.3, 224.8)]
        normal, high, recovery = iot.choose_temperature_sequence(rule, history, 187.25)
        self.assertEqual((normal, high, recovery), (187.24, 224.8, 187.26))
        self.assertGreaterEqual(normal, 187.2)
        self.assertLessEqual(recovery, 187.3)
        self.assertNotEqual(normal, recovery)
        self.assertNotIn(normal, (187.2, 187.3, 187.25, 224.8))
        self.assertNotIn(recovery, (187.2, 187.3, 187.25, 224.8))
        self.assertEqual(iot.choose_temperature_sequence(rule, history, 187.25), (normal, high, recovery))

    def test_sequence_refuses_empty_flat_or_exhausted_measured_range(self):
        rule = dict(rule_id=8701, property_code='temperature', compare_operator='GT', threshold=220, duration_sec=30)
        template = dict(tenant_id=iot.TENANT_ID, device_code=iot.DEVICE_CODE,
                        property_code='temperature', source='MQTT', quality='GOOD')
        for values in ([], [187.2], [187.2, 187.2], [187.2, 187.21, 187.22]):
            with self.subTest(values=values), self.assertRaises(iot.TemperatureSelectionFailure):
                iot.choose_temperature_sequence(rule, [{**template, 'numeric_value': v} for v in values], 187.2)

    def test_blackbox_latest_transition_accepts_new_reading_without_metadata_provenance(self):
        previous = {'data_id': 101, 'tenant_id': iot.TENANT_ID, 'device_code': iot.DEVICE_CODE,
                    'property_code': 'temperature', 'source': 'MQTT', 'quality': 'GOOD', 'numeric_value': 187.3}
        fresh = {**previous, 'data_id': 102, 'numeric_value': 187.4}
        api_rows = [{'dataId': '102', 'deviceId': str(iot.DEVICE_ID), 'tenantId': iot.TENANT_ID,
                     'deviceCode': iot.DEVICE_CODE, 'propertyCode': 'temperature',
                     'numericValue': 187.4, 'source': 'MQTT', 'quality': 'GOOD',
                     'collectTime': '2026-09-29 11:30:01', 'receiveTime': '2026-09-29 11:30:01'}]
        proof = iot.prove_latest_transition(187.3, 187.4, [fresh, previous], api_rows, {'101'}, 187.4, {187.3})
        self.assertTrue(proof['latest_transition_verified'])
        self.assertFalse(proof['metadataProvenanceVerified'])
        for label, before, after, db_rows, rows, old_ids in (
            ('stale same value', 187.3, 187.3, [fresh, previous], api_rows, {'101'}),
            ('api missing', 187.3, 187.4, [fresh, previous], [], {'101'}),
            ('api old id', 187.3, 187.4, [fresh, previous], [{**api_rows[0], 'dataId': '101'}], {'101'}),
            ('db not fresh', 187.3, 187.4, [fresh, previous], api_rows, {'101', '102'}),
            ('latest wrong', 187.3, 187.2, [fresh, previous], api_rows, {'101'}),
            ('missing api timestamp', 187.3, 187.4, [fresh, previous], [{**api_rows[0], 'collectTime': None}], {'101'}),
        ):
            with self.subTest(label=label), self.assertRaises(iot.ScopeFailure):
                iot.prove_latest_transition(before, after, db_rows, rows, old_ids, 187.4, {187.3})

    def test_transition_refuses_a_reading_already_present_in_recent_history_or_cache(self):
        with self.assertRaises(iot.LatestTransitionFailure):
            iot.prove_latest_transition(187.2, 187.3, [], [], set(), 187.3, {187.3})
        with self.assertRaises(iot.LatestTransitionFailure):
            iot.prove_latest_transition(187.3, 187.3, [], [], set(), 187.3, {187.2})

    def test_missing_measured_temperature_range_blocks_before_secret_rotation(self):
        from unittest.mock import Mock
        scoped = dict(rule_id=8701, product_id=1, device_id=0, property_code='temperature',
                      compare_operator='GT', threshold=220, duration_sec=30)
        api = Mock()
        api.preflight.return_value = True
        api.request.return_value = ({'temperature': '187.3000'}, {'code': 200})
        api.reset_secret.side_effect = AssertionError('secret rotation must not happen')
        with patch.object(iot, 'inspect_db', return_value=scoped), \
             patch.object(iot, 'LocalApi', return_value=api), \
             patch.object(iot, 'db_unresolved_alerts', return_value=[]), \
             patch.object(iot, 'prove_fixed_backend_loaded', return_value=True), \
             patch.object(iot, 'db_history', return_value=[]), \
             patch.object(iot, 'MqttDevice') as mqtt, \
             patch.object(iot, 'save_evidence'):
            result = iot.run('placeholder', 'user', 'placeholder', apply=True)
        self.assertEqual(result['category'], 'TemperatureSelectionFailure')
        api.reset_secret.assert_not_called()
        mqtt.assert_not_called()

    def test_corrupt_latest_rejected_before_rotation_without_evidence_leakage(self):
        from unittest.mock import Mock
        scoped = dict(rule_id=8701, product_id=1, device_id=0, property_code='temperature',
                      compare_operator='GT', threshold=220, duration_sec=30)
        api = Mock()
        api.preflight.return_value = True
        api.request.return_value = ({'temperature': 'sensitive-garbage'}, {'code': 200})
        api.reset_secret.side_effect = AssertionError('secret rotation must not happen')
        with patch.object(iot, 'inspect_db', return_value=scoped), \
             patch.object(iot, 'LocalApi', return_value=api), \
             patch.object(iot, 'db_unresolved_alerts', return_value=[]), \
             patch.object(iot, 'prove_fixed_backend_loaded', return_value=True), \
             patch.object(iot, 'db_history', return_value=[]), \
             patch.object(iot, 'MqttDevice') as mqtt, \
             patch.object(iot, 'save_evidence') as save:
            result = iot.run('placeholder', 'user', 'placeholder', apply=True)
        self.assertNotIn('sensitive-garbage', json.dumps(result))
        self.assertNotIn('sensitive-garbage', json.dumps(save.call_args.args[0]))
        self.assertNotIn('latest', result)
        self.assertEqual(result['category'], 'ScopeFailure')
        api.reset_secret.assert_not_called()
        mqtt.assert_not_called()

    def test_alarm_appearing_during_latest_snapshot_blocks_rotation(self):
        from unittest.mock import Mock
        rule = dict(rule_id=8701, property_code='temperature', compare_operator='GT', threshold=220, duration_sec=30)
        template = dict(tenant_id=iot.TENANT_ID, device_code=iot.DEVICE_CODE,
                        property_code='temperature', source='MQTT', quality='GOOD')
        rows = [{**template, 'data_id': 100, 'numeric_value': 187.2},
                {**template, 'data_id': 101, 'numeric_value': 187.3}]
        alarm = dict(alert_id=2104774461947330562, status='ACTIVE', active_flag=1)
        api = Mock()
        api.preflight.return_value = True
        api.request.return_value = ({'temperature': '187.3'}, {'code': 200})
        api.reset_secret.side_effect = AssertionError('rotation must not happen')
        with patch.object(iot, 'inspect_db', return_value=rule), \
             patch.object(iot, 'LocalApi', return_value=api), \
             patch.object(iot, 'prove_fixed_backend_loaded', return_value=True), \
             patch.object(iot, 'db_history', return_value=rows), \
             patch.object(iot, 'db_unresolved_alerts', side_effect=[[], [], [alarm]]), \
             patch.object(iot, 'MqttDevice') as mqtt, \
             patch.object(iot, 'save_evidence'):
            result = iot.run('placeholder', 'user', 'placeholder', apply=True)
        self.assertEqual(result['category'], 'UnresolvedAlertFailure')
        self.assertEqual(result['blocking_alert_id'], '2104774461947330562')
        api.reset_secret.assert_not_called()
        mqtt.assert_not_called()

    def _simulated_acceptance_run(self, frozen_cache=False):
        from unittest.mock import Mock
        scoped = dict(rule_id=8701, property_code='temperature', compare_operator='GT', threshold=220, duration_sec=30)
        template = dict(tenant_id=iot.TENANT_ID, device_code=iot.DEVICE_CODE,
                        property_code='temperature', source='MQTT', quality='GOOD')
        rows = [{**template, 'data_id': 101, 'numeric_value': 187.3},
                {**template, 'data_id': 100, 'numeric_value': 187.2}]
        events, publications = [], []
        cache = {'value': 187.3}
        class FakeDevice:
            def __enter__(self): return self
            def __exit__(self, *args): return None
            def publish(self, value):
                events.append(('publish', value))
                publications.append(value)
                rows.insert(0, {**template, 'data_id': 102 + len(publications) - 1, 'numeric_value': value})
                if not frozen_cache:
                    cache['value'] = value
        api = Mock()
        api.preflight.return_value = True
        api.reset_secret.return_value = 'not-a-real-device-secret'
        api.sse.return_value = 'frame_matched'
        def request(method, path):
            self.assertEqual(method, 'GET')
            if path.endswith('/latest'):
                events.append(('latest', cache['value']))
                return {'temperature': str(cache['value'])}, {'code': 200}
            if path.endswith('/data'):
                mapped = [{'dataId': str(row['data_id']), 'deviceId': str(iot.DEVICE_ID),
                           'tenantId': iot.TENANT_ID, 'deviceCode': iot.DEVICE_CODE,
                           'propertyCode': 'temperature', 'numericValue': row['numeric_value'],
                           'source': 'MQTT', 'quality': 'GOOD', 'collectTime': '2026-09-29 11:30:01',
                           'receiveTime': '2026-09-29 11:30:01'} for row in rows]
                return mapped, {'code': 200, 'rows': mapped}
            raise AssertionError('Unexpected API endpoint')
        api.request.side_effect = request
        def alerts(password):
            if publications.count(224.8) < 2:
                return []
            recovery = publications[-1] < 220
            return [dict(alert_id=999, tenant_id=iot.TENANT_ID, device_code=iot.DEVICE_CODE,
                         property_code='temperature', rule_id=8701, status='RESOLVED' if recovery else 'ACTIVE',
                         active_flag=None if recovery else 1, resolve_value=publications[-1] if recovery else None)]
        def bounded_poll(fn, timeout=18):
            for _ in range(4):
                result = fn()
                if result:
                    return result
            return None
        with patch.object(iot, 'inspect_db', return_value=scoped), \
             patch.object(iot, 'LocalApi', return_value=api), \
             patch.object(iot, 'prove_fixed_backend_loaded', return_value=True), \
             patch.object(iot, 'db_unresolved_alerts', return_value=[]), \
             patch.object(iot, 'db_history', side_effect=lambda password: list(rows)), \
             patch.object(iot, 'db_alerts', side_effect=alerts), \
             patch.object(iot, 'MqttDevice', return_value=FakeDevice()) as mqtt, \
             patch.object(iot, 'poll', side_effect=bounded_poll), \
             patch.object(iot.time, 'sleep') as sleep, \
             patch.object(iot, 'save_evidence') as save:
            result = iot.run('placeholder', 'user', 'placeholder', apply=True)
        return result, events, publications, sleep, save

    def test_full_simulated_sequence_passes_on_observed_transitions_not_metadata(self):
        result, events, publications, sleep, save = self._simulated_acceptance_run()
        self.assertEqual(result['result'], 'verified')
        self.assertEqual(publications, [187.25, 224.8, 224.8, 187.24])
        self.assertTrue(result['latest_transition_verified'])
        self.assertFalse(result['metadataProvenanceVerified'])
        self.assertTrue(result['baseline_transition_verified'])
        self.assertTrue(result['recovery_transition_verified'])
        self.assertEqual(result['latest_before_run'], 187.3)
        self.assertEqual(result['latest'], 187.24)
        self.assertLess(events.index(('latest', 187.25)), events.index(('publish', 224.8)))
        sleep.assert_called_once_with(31)
        self.assertNotIn('not-a-real-device-secret', json.dumps(result))
        self.assertFalse(save.call_args.args[0]['metadataProvenanceVerified'])

    def test_persisted_new_rows_with_frozen_latest_cache_cannot_pass(self):
        result, events, publications, sleep, save = self._simulated_acceptance_run(frozen_cache=True)
        self.assertEqual(result['result'], 'failed')
        self.assertEqual(result['category'], 'LatestTransitionFailure')
        self.assertFalse(result['latest_transition_verified'])
        self.assertFalse(result['metadataProvenanceVerified'])
        self.assertNotIn(224.8, publications)

    def test_evidence_never_claims_metadata_provenance(self):
        proof = iot.safe_evidence({'latest_transition_verified': True, 'metadataProvenanceVerified': True})
        self.assertTrue(proof['latest_transition_verified'])
        self.assertIs(proof['metadataProvenanceVerified'], False)

    def test_sse_stops_after_five_unmatched_frames(self):
        from unittest.mock import Mock
        api = object.__new__(iot.LocalApi)
        api.request = Mock()
        response = Mock()
        response.__enter__ = Mock(return_value=response)
        response.__exit__ = Mock(return_value=None)
        def frames(**kwargs):
            for _ in range(5):
                yield 'data: {"deviceId":"0","properties":{}}'
            raise AssertionError('SSE must not wait forever for unmatched frames')
        response.iter_lines.side_effect = frames
        api.request.return_value = response
        self.assertEqual(api.sse(187.2), 'no_frame')

    def test_alert_query_is_tenant_and_property_scoped(self):
        with patch.object(iot, 'read_only_query', return_value=[]) as query:
            iot.db_alerts('local-db-password')
        sql, parameters = query.call_args.args[1:3]
        self.assertIn('tenant_id=%s', sql)
        self.assertIn("property_code='temperature'", sql)
        self.assertEqual(parameters, (iot.DEVICE_ID, iot.TENANT_ID))

    def test_telemetry_verification_rejects_preexisting_sensor_rows(self):
        previous = {'data_id': 100, 'tenant_id': '000000', 'device_code': 'DEV0025',
                    'property_code': 'temperature', 'source': 'MQTT', 'numeric_value': 187.2}
        current = {**previous, 'data_id': 101}
        self.assertEqual(iot.fresh_telemetry([previous], {'100'}, 187.2), [])
        self.assertEqual(iot.fresh_telemetry([current, previous], {'100'}, 187.2), [current])
        self.assertEqual(iot.fresh_telemetry([{**current, 'tenant_id': '100001'}], {'100'}, 187.2), [])

    def test_history_snapshot_is_scoped_and_ordered_for_last_persisted_reading(self):
        with patch.object(iot, 'read_only_query', return_value=[]) as query:
            iot.db_history('placeholder')
        sql, params = query.call_args.args[1:3]
        self.assertIn('tenant_id=%s', sql)
        self.assertIn("property_code='temperature'", sql)
        self.assertIn('ORDER BY receive_time DESC,data_id DESC', sql)
        self.assertEqual(params, (iot.DEVICE_ID, iot.TENANT_ID))

    def test_old_row_not_in_snapshot_still_cannot_count_as_new(self):
        row = dict(data_id=99, tenant_id=iot.TENANT_ID, device_code=iot.DEVICE_CODE,
                   property_code='temperature', source='MQTT', numeric_value=187.25)
        self.assertEqual(iot.fresh_telemetry([row], {'100'}, 187.25), [])

    def test_transition_does_not_invent_api_tie_breaker_for_equal_second_timestamps(self):
        old = dict(data_id=101, tenant_id=iot.TENANT_ID, device_code=iot.DEVICE_CODE,
                   property_code='temperature', source='MQTT', quality='GOOD', numeric_value=187.3)
        fresh = {**old, 'data_id': 102, 'numeric_value': 187.4}
        template = dict(deviceId=str(iot.DEVICE_ID), tenantId=iot.TENANT_ID, deviceCode=iot.DEVICE_CODE,
                        propertyCode='temperature', source='MQTT', quality='GOOD',
                        collectTime='2026-09-29 11:30:01', receiveTime='2026-09-29 11:30:01')
        # 真实历史接口只按 collectTime 排序；同一秒的记录可能以任意顺序返回。
        rows = [{**template, 'dataId': '101', 'numericValue': 187.3},
                {**template, 'dataId': '102', 'numericValue': 187.4}]
        self.assertTrue(iot.prove_latest_transition(187.3, 187.4, [fresh, old], rows, {'101'}, 187.4, {187.3})['latest_transition_verified'])

    def test_transition_evidence_temperature_fields_never_store_arbitrary_strings(self):
        fields = ('latest_before_run', 'latest_after_baseline', 'latest_before_recovery', 'latest')
        for field in fields:
            with self.subTest(field=field):
                self.assertNotIn(field, iot.safe_evidence({field: 'secret-value'}))
                self.assertEqual(iot.safe_evidence({field: '187.2500'})[field], 187.25)

    def test_local_api_and_db_targets_reject_remote(self):
        iot.require_loopback('http://127.0.0.1:8080', 8080)
        with self.assertRaises(iot.ScopeFailure):
            iot.require_loopback('http://10.152.240.20:8080', 8080)
        with self.assertRaises(iot.ScopeFailure):
            iot.require_loopback('http://127.0.0.1:8181', 8080)


if __name__ == '__main__':
    unittest.main()
