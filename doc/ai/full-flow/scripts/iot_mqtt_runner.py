"""限定范围的本地 MQTT 验收；数据库只允许 SELECT，密钥仅保留在内存中。

验收证明的是观测到的黑盒最新值变化，而非数据行元数据
的来源。实时写入要求后端已重新编译并重启，且不存在旧的活动告警。
"""
import argparse
import base64
from datetime import datetime, timezone
from decimal import Decimal, ROUND_CEILING, ROUND_FLOOR
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import string
import socket
import subprocess
import sys
import time
import zipfile
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[4]
EVIDENCE = ROOT / '.codex/full-flow-20260928/iot-mqtt-evidence.json'
DEVICE_ID, DEVICE_CODE, TENANT_ID, PRODUCT_ID = 2104512194450214913, 'DEV0025', '000000', 1

class ScopeFailure(RuntimeError): pass
class UnresolvedAlertFailure(ScopeFailure): pass
class RuntimeAttestationFailure(ScopeFailure): pass
class LatestTransitionFailure(ScopeFailure): pass
class TemperatureSelectionFailure(ScopeFailure): pass
class MqttFailure(RuntimeError): pass
class ApiFailure(RuntimeError): pass

# 两项 IoT 修复都必须在源码修改后编译；绑定到 localhost:8080 的 Nexus 进程
# 必须在两个类文件都构建完成后启动。
FIX_ARTIFACTS = (
    ('ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/pipeline/IotAlertChecker.java',
     'ruoyi-modules/ruoyi-iot/target/classes/com/nexus/iot/pipeline/IotAlertChecker.class'),
    ('ruoyi-modules/ruoyi-iot/src/main/java/com/nexus/iot/device/controller/IotDeviceController.java',
     'ruoyi-modules/ruoyi-iot/target/classes/com/nexus/iot/device/controller/IotDeviceController.class'),
)


def read_backend_listener():
    """只返回 PID、启动时间和应用身份，不返回原始命令行。"""
    ps = (
        '$listeners=@(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction Stop);'
        '$owners=@($listeners | Select-Object -ExpandProperty OwningProcess -Unique);'
        'if ($owners.Count -ne 1) { exit 2 };'
        '$proc=Get-CimInstance Win32_Process -Filter ("ProcessId=" + $owners[0]) -ErrorAction Stop;'
        'if ($null -eq $proc) { exit 2 };'
        '[pscustomobject]@{pid=[int]$proc.ProcessId;'
        'startedUtc=$proc.CreationDate.ToUniversalTime().ToString("o");'
        'isNexus=($proc.CommandLine -like "*com.nexus.NexusApplication*");'
        'isJava=($proc.Name -in @("java.exe","javaw.exe"))} | ConvertTo-Json -Compress'
    )
    try:
        completed = subprocess.run(
            ['powershell', '-NoProfile', '-NonInteractive', '-Command', ps],
            capture_output=True, text=True, timeout=12, check=False,
        )
        if completed.returncode != 0:
            raise RuntimeAttestationFailure('cannot verify local backend listener')
        data = json.loads(completed.stdout)
        if not isinstance(data, dict):
            raise RuntimeAttestationFailure('invalid backend listener metadata')
        return data
    except RuntimeAttestationFailure:
        raise
    except (OSError, ValueError, subprocess.TimeoutExpired):
        raise RuntimeAttestationFailure('cannot verify local backend listener') from None


def read_runtime_classpath(pid):
    """在内存中读取所绑定 JVM 自身的类路径；绝不输出虚拟机属性。"""
    tool = shutil.which('jcmd')
    if not tool:
        raise RuntimeAttestationFailure('JVM diagnostic tool unavailable')
    try:
        result = subprocess.run([tool, str(pid), 'VM.system_properties'], capture_output=True,
                                text=True, timeout=12, check=False)
        if result.returncode != 0:
            raise RuntimeAttestationFailure('runtime classpath cannot be read')
        values = [line.partition('=')[2] for line in result.stdout.splitlines()
                  if line.startswith('java.class.path=')]
        if len(values) != 1 or not values[0]:
            raise RuntimeAttestationFailure('runtime classpath missing')
        return values[0]
    except RuntimeAttestationFailure:
        raise
    except (OSError, ValueError, subprocess.TimeoutExpired):
        raise RuntimeAttestationFailure('runtime classpath cannot be read') from None


def verify_iot_classpath(classpath):
    """在实际 JVM 类路径中解析每项修复对应的类；拒绝会遮蔽这些类的提供方。"""
    entries = re.sub(r'\\([\\:=])', r'\1', classpath).split(';')
    expected = (ROOT / 'ruoyi-modules/ruoyi-iot/target/classes').resolve()
    class_names = ('com/nexus/iot/pipeline/IotAlertChecker.class',
                   'com/nexus/iot/device/controller/IotDeviceController.class')
    found = {name: [] for name in class_names}
    try:
        for entry in entries:
            if not entry or '*' in entry:
                raise RuntimeAttestationFailure('unknown/wildcard classpath provider')
            provider = Path(entry).resolve()
            if provider.is_dir():
                for name in class_names:
                    if (provider / name).is_file():
                        found[name].append((provider / name).resolve())
            elif provider.is_file() and provider.suffix.lower() == '.jar':
                with zipfile.ZipFile(provider) as jar:
                    for name in class_names:
                        if name in jar.namelist() or any(n.endswith('/' + name) for n in jar.namelist()):
                            raise RuntimeAttestationFailure('IoT class has an alternate jar provider')
            else:
                raise RuntimeAttestationFailure('uninspectable runtime classpath entry')
        for name, paths in found.items():
            if paths != [(expected / name).resolve()]:
                raise RuntimeAttestationFailure('IoT class not bound uniquely to fixed output')
    except RuntimeAttestationFailure:
        raise
    except (OSError, ValueError, zipfile.BadZipFile):
        raise RuntimeAttestationFailure('runtime bytecode provider inspection failed') from None
    return True


def read_idea_parent_roots(pid):
    """将 IDEA 插桩绑定到真实 JVM 父进程链；绝不返回参数。"""
    if type(pid) is not int or pid <= 0:
        raise RuntimeAttestationFailure('invalid JVM PID')
    script = (
        '$proc=Get-CimInstance Win32_Process -Filter "ProcessId=' + str(pid) + '" -ErrorAction Stop;'
        '$roots=@();'
        'for($i=0; $i -lt 8 -and $null -ne $proc; $i++) {'
        'if($proc.Name -eq "idea64.exe" -and $proc.ExecutablePath) {'
        '$roots+=(Split-Path -Parent (Split-Path -Parent $proc.ExecutablePath)) };'
        'if($proc.ParentProcessId -le 0){break};'
        '$proc=Get-CimInstance Win32_Process -Filter ("ProcessId="+$proc.ParentProcessId) -ErrorAction Stop };'
        'ConvertTo-Json -InputObject @($roots) -Compress'
    )
    try:
        result = subprocess.run(['powershell', '-NoProfile', '-NonInteractive', '-Command', script],
                                capture_output=True, text=True, timeout=12, check=False)
        roots = json.loads(result.stdout) if result.returncode == 0 else None
        if not isinstance(roots, list) or not all(isinstance(item, str) for item in roots):
            raise RuntimeAttestationFailure('IDEA parent installation cannot be verified')
        return [Path(item).resolve() for item in roots]
    except RuntimeAttestationFailure:
        raise
    except (OSError, ValueError, subprocess.TimeoutExpired):
        raise RuntimeAttestationFailure('IDEA parent installation cannot be verified') from None


def verify_idea_launcher_agent(agent_path, installed_roots):
    """只允许来自实际 IDEA 安装目录的已知 AppMain 启动器 jar。"""
    try:
        agent = Path(agent_path.strip('"')).resolve(strict=True)
        root = agent.parent.parent
        if (agent.name != 'idea_rt.jar' or agent.parent.name != 'lib' or
            root not in [Path(path).resolve() for path in installed_roots] or
            not (root / 'bin/idea64.exe').is_file()):
            raise RuntimeAttestationFailure('unknown runtime agent installation')
        product = json.loads((root / 'product-info.json').read_text(encoding='utf-8'))
        if product.get('name') != 'IntelliJ IDEA' or product.get('productCode') not in ('IU', 'IC'):
            raise RuntimeAttestationFailure('unknown runtime agent product')
        with zipfile.ZipFile(agent) as jar:
            manifest = jar.read('META-INF/MANIFEST.MF').decode('utf-8')
        attrs = dict(re.findall(r'^([^:\r\n]+):\s*([^\r\n]+)', manifest, re.MULTILINE))
        if (attrs.get('Premain-Class') != 'com.intellij.rt.execution.application.AppMainV2$Agent' or
            attrs.get('Can-Redefine-Classes', 'false').lower() != 'false' or
            attrs.get('Can-Retransform-Classes', 'false').lower() != 'false' or
            'Boot-Class-Path' in attrs or 'Agent-Class' in attrs):
            raise RuntimeAttestationFailure('runtime agent is not plain IDEA launch instrumentation')
    except RuntimeAttestationFailure:
        raise
    except (OSError, ValueError, KeyError, zipfile.BadZipFile):
        raise RuntimeAttestationFailure('runtime agent identity cannot be verified') from None
    return True


def require_standard_class_loading(pid):
    """仅允许已核验的 IDEA 启动插桩，绝不允许任意代理或加载器。"""
    tool = shutil.which('jcmd')
    if not tool:
        raise RuntimeAttestationFailure('JVM diagnostic tool unavailable')
    try:
        result = subprocess.run([tool, str(pid), 'VM.command_line'], capture_output=True,
                                text=True, timeout=12, check=False)
        if result.returncode != 0 or 'jvm_args:' not in result.stdout:
            raise RuntimeAttestationFailure('runtime loader options unavailable')
        unsafe = ('-agentlib:', '-agentpath:', '-Xbootclasspath',
                  '-Djava.system.class.loader=', '-Dloader.path=', '--patch-module', '-XX:SharedArchiveFile')
        if any(option in result.stdout for option in unsafe):
            raise RuntimeAttestationFailure('runtime agents or custom loading prevent bytecode proof')
        agent_matches = list(re.finditer(r'-javaagent:(?:"([^"]+\.jar)"|([^"\r\n]+?\.jar))(?==|["\s]|$)', result.stdout))
        if len(agent_matches) != result.stdout.count('-javaagent:') or len(agent_matches) > 1:
            raise RuntimeAttestationFailure('unknown or multiple runtime agents')
        if agent_matches:
            agent = agent_matches[0].group(1) or agent_matches[0].group(2)
            verify_idea_launcher_agent(agent, read_idea_parent_roots(pid))
    except RuntimeAttestationFailure:
        raise
    except (OSError, ValueError, subprocess.TimeoutExpired):
        raise RuntimeAttestationFailure('runtime loader options unavailable') from None
    return True


def require_live_classpath(pid):
    require_standard_class_loading(pid)
    return verify_iot_classpath(read_runtime_classpath(pid))


def prove_fixed_backend_loaded():
    """除非 IDEA 中实时 Nexus 进程的启动晚于两项已编译修复，否则校验失败并关闭通路。"""
    latest_build = 0.0
    artifact_hashes = {}
    for source_name, class_name in FIX_ARTIFACTS:
        source = ROOT / source_name
        compiled = ROOT / class_name
        try:
            source_modified = source.stat().st_mtime
            class_modified = compiled.stat().st_mtime
            artifact_hashes[source] = hashlib.sha256(source.read_bytes()).digest()
            artifact_hashes[compiled] = hashlib.sha256(compiled.read_bytes()).digest()
        except OSError:
            raise RuntimeAttestationFailure('IoT fix source/class artifact unavailable') from None
        if class_modified < source_modified:
            raise RuntimeAttestationFailure('IoT fix not recompiled after source edit')
        latest_build = max(latest_build, source_modified, class_modified)
    listener = read_backend_listener()
    if (type(listener.get('pid')) is not int or listener['pid'] <= 0 or
        listener.get('isNexus') is not True or listener.get('isJava') is not True):
        raise RuntimeAttestationFailure('unexpected local backend process')
    try:
        # Windows CIM 输出七位小数秒，而 Python 3.10 最多接受六位。
        start_text = re.sub(r'(\.\d{6})\d+(?=Z|[+-]\d{2}:\d{2}$)', r'\1', listener['startedUtc'])
        started = datetime.fromisoformat(start_text.replace('Z', '+00:00'))
        if started.tzinfo is None or started.timestamp() <= latest_build:
            raise RuntimeAttestationFailure('backend predates compiled IoT fixes')
    except (KeyError, AttributeError, TypeError, ValueError, OverflowError):
        raise RuntimeAttestationFailure('invalid backend start time') from None
    require_live_classpath(listener['pid'])
    # 整个运行时检查过程中，保持 PID、启动时间及源码和类文件的字节身份不变。
    if read_backend_listener() != listener:
        raise RuntimeAttestationFailure('backend process changed during bytecode proof')
    try:
        if any(hashlib.sha256(path.read_bytes()).digest() != digest
               for path, digest in artifact_hashes.items()):
            raise RuntimeAttestationFailure('IoT source/class bytes changed during proof')
    except OSError:
        raise RuntimeAttestationFailure('IoT artifacts changed during proof') from None
    return True

def require_loopback(url, port):
    p = urlsplit(url)
    if p.scheme != 'http' or p.hostname not in ('localhost','127.0.0.1') or p.port != port or p.path not in ('','/') or p.query or p.username or p.password:
        raise ScopeFailure('local target scope mismatch')

def require_scope(device, product, device_id):
    if (device_id != DEVICE_ID or not device or not product or
        str(device.get('device_id')) != str(device_id) or device.get('device_code') != DEVICE_CODE or
        device.get('tenant_id') != TENANT_ID or str(device.get('product_id')) != str(PRODUCT_ID) or
        str(device.get('mes_equipment_id')) != '1' or device.get('status') != 'ACTIVE' or
        str(product.get('product_id')) != str(PRODUCT_ID) or product.get('tenant_id') != TENANT_ID or
        product.get('protocol_type') != 'MQTT' or product.get('status') != 'ACTIVE'):
        raise ScopeFailure('device/product scope mismatch')

def select_temperature_rule(rules, product_id, device_id):
    # 匹配 IotAlertRuleServiceImpl.specificity，包括设备范围优先于
    # 规则的产品范围；只有完成范围匹配后，才校验运算符和阈值。
    def specificity(rule):
        if rule.get('property_code') != 'temperature':
            return 0
        scoped_device = int(rule.get('device_id') or 0)
        if scoped_device:
            return 3 if scoped_device == device_id else 0
        scoped_product = int(rule.get('product_id') or 0)
        if scoped_product:
            return 2 if scoped_product == product_id else 0
        return 1

    ranked = [(specificity(rule), rule) for rule in rules]
    maximum = max((rank for rank, _ in ranked), default=0)
    candidates = [rule for rank, rule in ranked if rank == maximum and rank > 0]
    if len(candidates) != 1:
        raise ScopeFailure('temperature rule missing or precedence ambiguous')
    return candidates[0]

def require_no_unresolved_alert(alerts):
    if any(a.get('status') in ('ACTIVE', 'ACKNOWLEDGED') or a.get('active_flag') in (1, '1') for a in alerts):
        raise UnresolvedAlertFailure('existing unresolved temperature alert requires audited recovery')

def temperatures_for_rule(rule):
    if (rule['property_code']!='temperature' or rule['compare_operator']!='GT' or
        Decimal(str(rule['threshold']))!=Decimal('220') or not 0<=int(rule['duration_sec'])<=60):
        raise ScopeFailure('unexpected temperature rule')
    return (187.2,224.8,187.3)

def parse_latest_temperature(values):
    value=values.get('temperature') if isinstance(values,dict) else None
    if value is None: return None
    if isinstance(value, bool) or not isinstance(value, (int, float, Decimal, str)) or not re.fullmatch(r'\d{1,3}(?:\.\d{1,4})?', str(value)):
        raise ScopeFailure('latest sensor value invalid')
    try:
        number = Decimal(str(value))
        if number.is_finite() and 0 <= number <= 250:
            return float(number)
    except (ValueError, ArithmeticError):
        pass
    raise ScopeFailure('latest sensor value invalid')

def recent_temperature_values(rows, prior_latest=None):
    values = []
    for row in rows:
        if (row.get('tenant_id') != TENANT_ID or row.get('device_code') != DEVICE_CODE or
            row.get('property_code') != 'temperature' or row.get('quality') != 'GOOD' or
            row.get('numeric_value') is None):
            continue
        value = parse_latest_temperature({'temperature': row['numeric_value']})
        values.append(Decimal(str(value)))
    if prior_latest is not None:
        values.append(Decimal(str(parse_latest_temperature({'temperature': prior_latest}))))
    return set(values)


def choose_temperature_sequence(rule, prior_rows, prior_latest):
    """在已测量边界内，确定性地选择两个尚未使用的百分之一度精度值。"""
    _, high, _ = temperatures_for_rule(rule)
    observed = recent_temperature_values(prior_rows)
    threshold = Decimal(str(rule['threshold']))
    normal_values = sorted(value for value in observed if value < threshold)
    if len(normal_values) < 2:
        raise TemperatureSelectionFailure('no measured normal temperature range')
    lower, upper = normal_values[0], normal_values[-1]
    midpoint = (lower + upper) / 2
    occupied = recent_temperature_values(prior_rows, prior_latest)
    first = int((lower * 100).to_integral_value(rounding=ROUND_CEILING))
    last = int((upper * 100).to_integral_value(rounding=ROUND_FLOOR))
    candidates = [Decimal(index) / 100 for index in range(first, last + 1)
                  if Decimal(index) / 100 not in occupied]
    candidates.sort(key=lambda value: (abs(value - midpoint), value))
    if len(candidates) < 2:
        raise TemperatureSelectionFailure('no two fresh temperatures inside measured normal range')
    return float(candidates[0]), high, float(candidates[1])


def prove_latest_transition(prior, current, db_rows, api_rows, existing_ids, expected, prior_values):
    """核验观测到的黑盒值变化及新持久化的遥测数据。

    历史记录标识用于佐证新增 MQTT 行，并不是 /latest 的元数据。
    """
    if (current != expected or current == prior or
        Decimal(str(expected)) in {Decimal(str(value)) for value in prior_values}):
        raise LatestTransitionFailure('no fresh latest-value transition observed')
    fresh = fresh_telemetry(db_rows, existing_ids)
    if not fresh or not db_rows or fresh[0] != db_rows[0]:
        raise ScopeFailure('latest telemetry not the newest persisted row')
    row = fresh[0]
    if (row.get('quality') != 'GOOD' or parse_latest_temperature({'temperature': row.get('numeric_value')}) != current):
        raise ScopeFailure('latest DB row value/quality mismatch')
    api_latest = next((item for item in api_rows if item.get('propertyCode') == 'temperature'
                       and str(item.get('dataId')) == str(row['data_id'])), None)
    if (not api_latest or str(api_latest.get('dataId')) != str(row['data_id']) or
        str(api_latest.get('deviceId')) != str(DEVICE_ID) or
        api_latest.get('tenantId') != TENANT_ID or api_latest.get('deviceCode') != DEVICE_CODE or
        api_latest.get('source') != 'MQTT' or api_latest.get('quality') != 'GOOD' or
        not api_latest.get('collectTime') or not api_latest.get('receiveTime') or
        parse_latest_temperature({'temperature': api_latest.get('numericValue')}) != current):
        raise ScopeFailure('latest API history does not confirm the fresh DB row')
    return {'latest_transition_verified': True, 'metadataProvenanceVerified': False}


def safe_evidence(data):
    allowed = ('device_id','device_code','tenant_id','product_id','rule_id','rule_threshold','phase','result','category','history_rows','history_ids','alert_id','alert_status','baseline','high','recovery','latest','sse','started_at','ended_at','api_preflight','blocking_alert_id','latest_before_run','latest_after_baseline','latest_before_recovery','latest_transition_verified','baseline_transition_verified','recovery_transition_verified','metadataProvenanceVerified')
    temperatures = ('latest', 'latest_before_run', 'latest_after_baseline', 'latest_before_recovery')
    safe = {k:data[k] for k in allowed if k in data and k not in temperatures}
    for field in temperatures:
        if field in data:
            try:
                value = parse_latest_temperature({'temperature': data[field]})
                if value is not None:
                    safe[field] = value
            except ScopeFailure:
                pass  # 绝不持久化任意 API 字符串，即使在失败路径中也是如此。
    safe['metadataProvenanceVerified'] = False
    return safe

def remaining_length(n):
    if not 0<=n<=268435455: raise ValueError('invalid MQTT remaining length')
    out=bytearray()
    while True:
        digit=n%128; n//=128; out.append(digit|(128 if n else 0))
        if not n: return bytes(out)

def mqtt_string(text):
    b=text.encode('utf-8')
    if len(b)>65535: raise ValueError('MQTT field too long')
    return len(b).to_bytes(2,'big')+b

def connect_packet(code, secret):
    if not re.fullmatch(r'DEV\d{4}',code) or not secret: raise ValueError('invalid device code or secret')
    body=mqtt_string('MQTT')+b'\x04\xc2\x00\x3c'+mqtt_string(code)+mqtt_string(code)+mqtt_string(secret)
    return b'\x10'+remaining_length(len(body))+body

def property_payload(prop, value):
    if prop!='temperature' or isinstance(value,bool) or not 0<=value<=250: raise ScopeFailure('sensor value out of scope')
    return dict(property=prop,value=value,dataType='NUMERIC',unit='°C',timestamp=int(time.time()*1000),quality='GOOD')

def publish_packet(tenant,code,prop,value,packet_id):
    if tenant!=TENANT_ID or code!=DEVICE_CODE or not 1<=packet_id<=65535: raise ValueError('MQTT publish scope mismatch')
    body=mqtt_string(f'iot/{tenant}/{code}/property/post')+packet_id.to_bytes(2,'big')+json.dumps(property_payload(prop,value),separators=(',',':'),ensure_ascii=False).encode()
    return b'\x32'+remaining_length(len(body))+body

def recv_exact(sock,n):
    out=bytearray()
    while len(out)<n:
        part=sock.recv(n-len(out))
        if not part: raise MqttFailure('MQTT connection closed')
        out.extend(part)
    return bytes(out)

def read_packet(sock):
    kind=recv_exact(sock,1)[0]; size=0; mult=1
    for _ in range(4):
        digit=recv_exact(sock,1)[0];size+=(digit&127)*mult
        if not digit&128: return kind,recv_exact(sock,size)
        mult*=128
    raise MqttFailure('invalid MQTT packet length')

def require_connack(kind,body):
    if kind!=0x20 or len(body)!=2 or body[0]&0xfe or body[1]!=0: raise MqttFailure('MQTT authentication failed')

def require_puback(kind,body,packet_id):
    if kind!=0x40 or body!=packet_id.to_bytes(2,'big'): raise MqttFailure('MQTT PUBACK mismatch')

class MqttDevice:
    def __init__(self, secret): self.secret=secret;self.sock=None;self.sequence=0
    def __enter__(self):
        try:
            self.sock=socket.create_connection(('127.0.0.1',1883),timeout=6)
            self.sock.settimeout(8)
            self.sock.sendall(connect_packet(DEVICE_CODE,self.secret))
            require_connack(*read_packet(self.sock))
            return self
        except (OSError,MqttFailure):
            self.__exit__(None,None,None)
            raise MqttFailure('MQTT connect/auth failure') from None
    def publish(self,value):
        self.sequence+=1
        try:
            self.sock.sendall(publish_packet(TENANT_ID,DEVICE_CODE,'temperature',value,self.sequence))
            require_puback(*read_packet(self.sock),self.sequence)
        except (OSError,MqttFailure): raise MqttFailure('MQTT publish/ack failure') from None
    def __exit__(self,*_):
        if self.sock:
            try: self.sock.sendall(b'\xe0\x00')
            except OSError: pass
            self.sock.close();self.sock=None
        self.secret=None

def read_only_query(db_password,query,params=()):
    import pymysql
    conn=pymysql.connect(host='127.0.0.1',port=3306,user='root',password=db_password,database='ry-iot',charset='utf8mb4',cursorclass=pymysql.cursors.DictCursor,connect_timeout=5,read_timeout=5,autocommit=False)
    try:
        with conn.cursor() as cur:
            cur.execute('SET SESSION TRANSACTION READ ONLY')
            cur.execute('START TRANSACTION READ ONLY')
            cur.execute(query,params)
            return cur.fetchall()
    finally:
        conn.rollback();conn.close()

def inspect_db(password):
    d=read_only_query(password,"SELECT device_id,device_code,tenant_id,product_id,mes_equipment_id,status FROM iot_device WHERE device_id=%s AND del_flag='0'",(DEVICE_ID,))
    p=read_only_query(password,"SELECT product_id,tenant_id,product_code,protocol_type,status FROM iot_product WHERE product_id=%s AND del_flag='0'",(PRODUCT_ID,))
    if len(d)!=1 or len(p)!=1: raise ScopeFailure('device/product not found')
    require_scope(d[0],p[0],DEVICE_ID)
    if p[0]['product_code']!='TEMP-SENSOR': raise ScopeFailure('unexpected product definition')
    rules=read_only_query(password,"SELECT rule_id,product_id,device_id,property_code,compare_operator,threshold,duration_sec FROM iot_alert_rule WHERE tenant_id=%s AND enabled='1' AND property_code='temperature' AND (device_id=%s OR (device_id=0 AND product_id IN (0,%s)))",(TENANT_ID,DEVICE_ID,PRODUCT_ID))
    rule=select_temperature_rule(rules,PRODUCT_ID,DEVICE_ID)
    temperatures_for_rule(rule)
    return rule

def vite_value(key):
    text=(ROOT/'plus-ui/.env.development').read_text(encoding='utf-8')
    found=re.search(r'^'+re.escape(key)+r"\s*=\s*['\"]([^'\"]+)['\"]",text,re.MULTILINE)
    if not found: raise ScopeFailure('local frontend crypto configuration missing')
    return found.group(1)

def local_default_login():
    text=(ROOT/'plus-ui/src/views/login.vue').read_text(encoding='utf-8')
    u=re.search(r"^\s*username: '([^']+)'",text,re.MULTILINE)
    p=re.search(r"^\s*password: '([^']+)'",text,re.MULTILINE)
    if not u or not p or u.group(1)!='admin': raise ScopeFailure('local default login missing')
    return u.group(1),p.group(1)

def encrypt_login(document):
    from cryptography.hazmat.primitives import serialization, padding as sympad
    from cryptography.hazmat.primitives.asymmetric import padding as asympad
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    key=''.join(secrets.choice(string.ascii_letters+string.digits) for _ in range(32)).encode('ascii')
    public=serialization.load_der_public_key(base64.b64decode(vite_value('VITE_APP_RSA_PUBLIC_KEY')))
    header=base64.b64encode(public.encrypt(base64.b64encode(key),asympad.PKCS1v15())).decode()
    pad=sympad.PKCS7(128).padder()
    data=json.dumps(document,separators=(',',':')).encode()
    plain=pad.update(data)+pad.finalize()
    enc=Cipher(algorithms.AES(key),modes.ECB()).encryptor()
    return base64.b64encode(enc.update(plain)+enc.finalize()),header

def decrypt_login_response(response):
    from cryptography.hazmat.primitives import serialization, padding as sympad
    from cryptography.hazmat.primitives.asymmetric import padding as asympad
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    header=response.headers.get('encrypt-key')
    if not header: return response.json()  # @ApiEncrypt 的默认值为 response=false。
    private=serialization.load_der_private_key(base64.b64decode(vite_value('VITE_APP_RSA_PRIVATE_KEY')),password=None)
    key=base64.b64decode(private.decrypt(base64.b64decode(header),asympad.PKCS1v15()))
    dec=Cipher(algorithms.AES(key),modes.ECB()).decryptor()
    pad=dec.update(base64.b64decode(response.content))+dec.finalize()
    unpad=sympad.PKCS7(128).unpadder()
    return json.loads(unpad.update(pad)+unpad.finalize())

class LocalApi:
    def __init__(self):
        import requests
        self.base='http://127.0.0.1:8080'
        require_loopback(self.base,8080)
        self.session=requests.Session(); self.session.trust_env=False
        self.session.headers['clientid']=vite_value('VITE_APP_CLIENT_ID')
        self.token=None
    def request(self,method,path,*,stream=False):
        if not path.startswith('/iot/') and path!='/auth/code': raise ScopeFailure('API path scope mismatch')
        try:
            response=self.session.request(method,self.base+path,stream=stream,timeout=(5,8),allow_redirects=False)
        except Exception: raise ApiFailure('local API transport failure') from None
        if response.status_code!=200:
            response.close();raise ApiFailure('local API HTTP status failure')
        if stream: return response
        try: result=response.json()
        except ValueError: raise ApiFailure('local API invalid response') from None
        if result.get('code')!=200: raise ApiFailure('local API business failure')
        return result.get('data',result.get('rows')),result
    def login(self,user,password):
        code,_=self.request('GET','/auth/code')
        if code.get('captchaEnabled') is not False: raise ScopeFailure('captcha enabled; do not bypass')
        try:
            body,header=encrypt_login(dict(username=user,password=password,tenantId=TENANT_ID,clientId=self.session.headers['clientid'],grantType='password'))
            res=self.session.post(self.base+'/auth/login',data=body,headers={'encrypt-key':header,'Content-Type':'application/json'},timeout=(5,8),allow_redirects=False)
            if res.status_code!=200: raise ApiFailure('login HTTP failure')
            doc=decrypt_login_response(res)
            if doc.get('code')!=200 or not isinstance(doc.get('data'),dict): raise ApiFailure('login business failure')
            self.token=doc['data']['access_token']
            self.session.headers['Authorization']='Bearer '+self.token
        except ApiFailure: raise
        except Exception: raise ApiFailure('login crypto/transport failure') from None
    def preflight(self):
        d,_=self.request('GET',f'/iot/device/{DEVICE_ID}')
        p,_=self.request('GET',f'/iot/product/{PRODUCT_ID}')
        if not d or not p: raise ScopeFailure('API device/product not found')
        require_scope(dict(device_id=d.get('deviceId'),device_code=d.get('deviceCode'),tenant_id=d.get('tenantId',TENANT_ID),product_id=d.get('productId'),mes_equipment_id=d.get('mesEquipmentId'),status=d.get('status')),
                      dict(product_id=p.get('productId'),tenant_id=p.get('tenantId',TENANT_ID),protocol_type=p.get('protocolType'),status=p.get('status')),DEVICE_ID)
        self.request('GET',f'/iot/device/{DEVICE_ID}/latest')
        self.request('GET',f'/iot/device/{DEVICE_ID}/data')
        return True
    def reset_secret(self):
        data,_=self.request('PUT',f'/iot/device/{DEVICE_ID}/resetSecret')
        if not data or str(data.get('deviceId'))!=str(DEVICE_ID) or data.get('deviceCode')!=DEVICE_CODE or not data.get('deviceSecret'):
            raise ApiFailure('secret rotation response scope mismatch')
        return data['deviceSecret']
    def sse(self,value):
        try:
            with self.request('GET',f'/iot/device/{DEVICE_ID}/realtime',stream=True) as response:
                frames = 0
                for line in response.iter_lines(chunk_size=1,decode_unicode=True):
                    if line.startswith('data:'):
                        frames += 1
                        frame=json.loads(line[5:].strip())
                        if str(frame.get('deviceId'))==str(DEVICE_ID) and float(frame.get('properties',{}).get('temperature','nan'))==value:
                            return 'frame_matched'
                        if frames >= 5:
                            return 'no_frame'
            return 'no_frame'
        except Exception: return 'unavailable'

def fresh_telemetry(rows, existing_ids, value=None):
    high_watermark = max((int(identifier) for identifier in existing_ids), default=0)
    return [row for row in rows if int(row['data_id']) > high_watermark and str(row['data_id']) not in existing_ids
            and row['tenant_id'] == TENANT_ID and row['device_code'] == DEVICE_CODE
            and row['property_code'] == 'temperature' and row['source'] == 'MQTT'
            and row['numeric_value'] is not None
            and (value is None or Decimal(str(row['numeric_value'])) == Decimal(str(value)))]

def db_history(p):
    return read_only_query(p,"SELECT data_id,tenant_id,device_code,property_code,numeric_value,source,quality FROM iot_device_data WHERE device_id=%s AND tenant_id=%s AND property_code='temperature' ORDER BY receive_time DESC,data_id DESC LIMIT 20",(DEVICE_ID,TENANT_ID))

def db_unresolved_alerts(password):
    """读取此确切租户、设备和属性的全部未解决告警，不限定为最近的数据行。"""
    return read_only_query(password, "SELECT alert_id, rule_id, status, active_flag "
        "FROM iot_alert_record WHERE tenant_id=%s AND device_id=%s "
        "AND property_code='temperature' "
        "AND (active_flag=1 OR status IN ('ACTIVE','ACKNOWLEDGED')) "
        "ORDER BY trigger_time DESC", (TENANT_ID, DEVICE_ID))


def require_clear_alerts(password, evidence):
    alerts = db_unresolved_alerts(password)
    if alerts:
        evidence['blocking_alert_id'] = str(alerts[0]['alert_id'])
    require_no_unresolved_alert(alerts)


def db_alerts(p):
    return read_only_query(p,"SELECT alert_id,tenant_id,device_code,property_code,rule_id,status,active_flag,threshold,trigger_value,resolve_value FROM iot_alert_record WHERE device_id=%s AND tenant_id=%s AND property_code='temperature' ORDER BY trigger_time DESC LIMIT 5",(DEVICE_ID,TENANT_ID))

def poll(fn,timeout=18):
    until=time.monotonic()+timeout
    while time.monotonic()<=until:
        found=fn()
        if found: return found
        time.sleep(.6)
    return None

def save_evidence(evidence):
    if EVIDENCE.parent.resolve()!=(ROOT/'.codex/full-flow-20260928').resolve(): raise ScopeFailure('evidence path mismatch')
    EVIDENCE.parent.mkdir(parents=True,exist_ok=True)
    EVIDENCE.write_text(json.dumps(safe_evidence(evidence),ensure_ascii=False,indent=2),encoding='utf-8')

def observe_latest_transition(api, db_password, prior, expected, existing_ids, prior_values):
    """轮询新读数的持久化结果，随后观察其在 /latest API 中的值变化。"""
    def probe():
        rows = db_history(db_password)
        if not fresh_telemetry(rows, existing_ids, expected):
            return None
        latest_document, _ = api.request('GET', f'/iot/device/{DEVICE_ID}/latest')
        current = parse_latest_temperature(latest_document)
        if current != expected or current == prior:
            return None
        history, _ = api.request('GET', f'/iot/device/{DEVICE_ID}/data')
        try:
            return prove_latest_transition(prior, current, rows, history or [],
                                           existing_ids, expected, prior_values)
        except ScopeFailure:
            return None
    proof = poll(probe)
    if not proof:
        raise LatestTransitionFailure('persisted reading/latest API transition not observed')
    return proof


def run(db_password,user,password,*,apply=False):
    evidence = dict(device_id=str(DEVICE_ID), device_code=DEVICE_CODE, tenant_id=TENANT_ID,
                    product_id=PRODUCT_ID, phase='preflight', result='incomplete',
                    started_at=datetime.now(timezone.utc).isoformat(),
                    latest_transition_verified=False, metadataProvenanceVerified=False,
                    baseline_transition_verified=False, recovery_transition_verified=False)
    try:
        rule = inspect_db(db_password)
        evidence.update(rule_id=str(rule['rule_id']), rule_threshold=float(rule['threshold']))
        api = LocalApi()
        api.login(user, password)
        evidence['api_preflight'] = api.preflight()
        if not apply:
            evidence['result'] = 'read_only_preflight'
            return evidence
        require_clear_alerts(db_password, evidence)
        # 脚本标志绝不能替代实时进程和类路径证明。
        prove_fixed_backend_loaded()
        prior_rows = db_history(db_password)
        existing_ids = {str(row['data_id']) for row in prior_rows}
        require_clear_alerts(db_password, evidence)
        prior_document, _ = api.request('GET', f'/iot/device/{DEVICE_ID}/latest')
        prior_latest = parse_latest_temperature(prior_document)
        normal, high, recover = choose_temperature_sequence(rule, prior_rows, prior_latest)
        occupied_values = recent_temperature_values(prior_rows, prior_latest)
        evidence.update(baseline=normal, high=high, recovery=recover)
        if prior_latest is not None:
            evidence['latest_before_run'] = prior_latest
        # 快照和 API 读取可能耗时；轮换前不得混入旧告警。
        require_clear_alerts(db_password, evidence)
        evidence['phase'] = 'rotating_secret'
        secret = api.reset_secret()
        with MqttDevice(secret) as device:
            try:
                evidence['phase'] = 'baseline_transition'
                device.publish(normal)
                baseline_proof = observe_latest_transition(api, db_password, prior_latest, normal,
                                                          existing_ids, occupied_values)
                evidence['baseline_transition_verified'] = baseline_proof['latest_transition_verified']
                evidence['latest_after_baseline'] = normal
                # 即使后端已获证明，也不得产生正常温度的误报。
                require_clear_alerts(db_password, evidence)
                evidence['sse'] = api.sse(normal)
                evidence['phase'] = 'high_threshold'
                device.publish(high)
                if not poll(lambda: fresh_telemetry(db_history(db_password), existing_ids, high)):
                    raise ScopeFailure('high telemetry not persisted')
                def high_seen():
                    value, _ = api.request('GET', f'/iot/device/{DEVICE_ID}/latest')
                    return parse_latest_temperature(value) == high
                if not poll(high_seen):
                    raise LatestTransitionFailure('high-temperature API state not observed')
                evidence['latest_before_recovery'] = high
                time.sleep(int(rule['duration_sec']) + 1)
                device.publish(high)
                active = poll(lambda: next((alarm for alarm in db_alerts(db_password)
                    if alarm['tenant_id'] == TENANT_ID and alarm['device_code'] == DEVICE_CODE
                    and alarm['property_code'] == 'temperature' and str(alarm['rule_id']) == str(rule['rule_id'])
                    and alarm['status'] in ('ACTIVE', 'ACKNOWLEDGED') and alarm['active_flag'] == 1), None))
                if not active:
                    raise ScopeFailure('product threshold alert not persisted')
                evidence['alert_id'] = str(active['alert_id'])
                evidence['alert_status'] = active['status']
            finally:
                # 仅执行传感器恢复；此处绝不通过 API 确认或恢复已有告警。
                device.publish(recover)
        evidence['phase'] = 'recovery_transition'
        recovery_proof = observe_latest_transition(api, db_password, high, recover, existing_ids,
                                                  occupied_values | {Decimal(str(normal))})
        evidence['recovery_transition_verified'] = recovery_proof['latest_transition_verified']
        evidence['latest'] = recover  # 只有核验观测到的值变化后，才允许执行。
        evidence['latest_transition_verified'] = (evidence['baseline_transition_verified'] and
                                                   evidence['recovery_transition_verified'])
        recovered = poll(lambda: next((alarm for alarm in db_alerts(db_password)
            if str(alarm['alert_id']) == evidence.get('alert_id') and alarm['status'] == 'RESOLVED'
            and alarm['active_flag'] is None and Decimal(str(alarm['resolve_value'])) == Decimal(str(recover))), None))
        history = fresh_telemetry(db_history(db_password), existing_ids)
        evidence['history_ids'] = [str(row['data_id']) for row in history[:4]]
        evidence['history_rows'] = len(history)
        evidence['alert_status'] = 'RESOLVED' if recovered else evidence.get('alert_status', 'unconfirmed')
        evidence['result'] = 'verified' if (recovered and len(history) >= 4 and
            evidence['latest_transition_verified'] and evidence['sse'] == 'frame_matched') else 'partial'
        evidence['phase'] = 'verified'
        return evidence
    except (ScopeFailure, MqttFailure, ApiFailure) as ex:
        evidence.update(category=type(ex).__name__, result='failed')
        return evidence
    except Exception:
        evidence.update(category='unexpected_failure', result='failed')
        return evidence
    finally:
        evidence['ended_at'] = datetime.now(timezone.utc).isoformat()
        save_evidence(evidence)


def main():
    p=argparse.ArgumentParser(description='Scoped local real IoT MQTT acceptance')
    p.add_argument('--apply',action='store_true',help='Rotate secret and publish business sensor data')
    p.add_argument('--use-local-default-login',action='store_true')
    args=p.parse_args()
    try:
        db=getpass.getpass('MySQL password (not saved): ')
        user,password=local_default_login() if args.use_local_default_login else ('admin',getpass.getpass('Web login password (not saved): '))
        result=run(db,user,password,apply=args.apply)
        print(json.dumps(safe_evidence(result),ensure_ascii=False))
        return 0 if result['result'] in ('verified','read_only_preflight') else 1
    except Exception:
        print('IoT preflight failure (no secrets logged)',file=sys.stderr);return 1
if __name__=='__main__': sys.exit(main())
