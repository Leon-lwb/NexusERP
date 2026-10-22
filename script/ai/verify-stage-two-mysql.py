"""新建带所有者标记的本机隔离库验证AI治理，不迁移业务库。"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import uuid
import xml.etree.ElementTree as ET
import yaml

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / 'ruoyi-modules/ruoyi-ai/target/ai-stage-two-evidence'

def merge(target, data):
    for key, value in data.items():
        if isinstance(value, dict) and isinstance(target.get(key), dict):
            merge(target[key], value)
        else:
            target[key] = value

def connection_settings():
    config = {}
    resource = ROOT / 'ruoyi-admin/src/main/resources'
    for document in yaml.safe_load_all((resource / 'application-dev.yml').read_text(encoding='utf-8')):
        if document and not document.get('spring', {}).get('config', {}).get('activate'):
            merge(config, document)
    props = yaml.safe_load((resource / 'application-secret-dev.yml').read_text(encoding='utf-8')) or {}
    private = Path(os.environ.get('NEXUS_CONFIG_DIR', str(ROOT / 'config')))
    for name in ['application-secret.yml', 'application-local.yml']:
        file = private / name
        if file.exists():
            for document in yaml.safe_load_all(file.read_text(encoding='utf-8')):
                if document:
                    merge(config, document)
                    props.update({k: v for k, v in document.items() if isinstance(v, (str, int, float))})
    props.update(os.environ)
    def resolve(value):
        text = str(value)
        for _ in range(30):
            match = re.search(r'\$\{([^{}]+)\}', text)
            if not match:
                return text
            name, sep, default = match.group(1).partition(':')
            if name not in props and not sep:
                raise RuntimeError('数据库连接配置不完整')
            text = text[:match.start()] + str(props.get(name, default)) + text[match.end():]
        raise RuntimeError('数据库配置未解析')
    master = config['spring']['datasource']['dynamic']['datasource']['master']
    url = resolve(master['url'])
    if not re.match(r'jdbc:mysql://(localhost|127\.0\.0\.1):3306/ry-vue(?:\?|$)', url):
        raise RuntimeError('只允许已核对的本机localhost:3306/ry-vue服务器作为隔离库创建入口')
    return url, resolve(master['username']), resolve(master['password'])

JAVA = r'''
import java.sql.*;
public class IsolatedAiDatabase {
 public static void main(String[] args) throws Exception {
  String name=System.getenv("AI_TEST_DATABASE"), nonce=System.getenv("AI_GOV_TEST_NONCE");
  if(name==null||!name.matches("nexus_ai_stage2_test_[a-f0-9]{16}")||nonce==null||!nonce.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("invalid isolated database identity");
  try(var c=DriverManager.getConnection(System.getenv("AI_TEST_MASTER_URL"),System.getenv("AI_GOV_TEST_USER"),System.getenv("AI_GOV_TEST_PASSWORD"));var s=c.createStatement()){
   if(!"ry-vue".equals(c.getCatalog()))throw new IllegalStateException("unexpected server entry");
   if("create".equals(args[0])) {
    s.execute("CREATE DATABASE "+name+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    s.execute("CREATE TABLE "+name+"._codex_test_owner(owner_nonce varchar(64) PRIMARY KEY) ENGINE=InnoDB");
    try(var p=c.prepareStatement("INSERT INTO "+name+"._codex_test_owner(owner_nonce) VALUES (?)")){p.setString(1,nonce);p.executeUpdate();}
    try(var r=s.executeQuery("SELECT VERSION()")){r.next();System.out.println(r.getString(1));}
   } else if("drop".equals(args[0])) {
    try(var p=c.prepareStatement("SELECT COUNT(*) FROM "+name+"._codex_test_owner WHERE owner_nonce=?")){p.setString(1,nonce);try(var r=p.executeQuery()){r.next();if(r.getInt(1)!=1)throw new IllegalStateException("owner mismatch");}}
    s.execute("DROP DATABASE "+name);
   } else throw new IllegalArgumentException("invalid action");
  } catch(Exception e){System.err.println("isolated database operation failed: "+e.getClass().getSimpleName());System.exit(1);}
 }
}
'''

def main():
    url, user, password = connection_settings()
    driver = Path(os.environ.get('AI_MYSQL_DRIVER_JAR', str(Path.home() / '.m2/repository/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar')))
    if not driver.is_file():
        raise RuntimeError('缺少测试JDBC驱动，请先解析Maven测试依赖或设置AI_MYSQL_DRIVER_JAR')
    database = 'nexus_ai_stage2_test_' + uuid.uuid4().hex[:16]
    env = os.environ.copy()
    env.update(AI_TEST_DATABASE=database, AI_GOV_TEST_NONCE=uuid.uuid4().hex,
               AI_TEST_MASTER_URL=url, AI_GOV_TEST_USER=user, AI_GOV_TEST_PASSWORD=password,
               AI_GOV_TEST_URL=re.sub(r'/ry-vue(?=\?|$)', '/' + database, url))
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    evidence = {'database': 'localhost:3306/' + database, 'businessDatabasesChanged': False,
                'startedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(),
                'schemaSha256': hashlib.sha256((ROOT / 'script/sql/update/2026-10-22-ai-stage-two.sql').read_bytes()).hexdigest()}
    created = False
    with tempfile.TemporaryDirectory(prefix='nexus-ai-mysql-') as directory:
        temporary = Path(directory).resolve()
        if temporary.parent != Path(tempfile.gettempdir()).resolve() or not temporary.name.startswith('nexus-ai-mysql-'):
            raise RuntimeError('临时目录范围错误')
        helper = temporary / 'IsolatedAiDatabase.java'
        helper.write_text(JAVA, encoding='utf-8')
        command = ['java', '-Dfile.encoding=UTF-8', '--class-path', str(driver), str(helper)]
        try:
            probe = subprocess.run(command + ['create'], env=env, capture_output=True, text=True, encoding='utf-8', timeout=40)
            if probe.returncode:
                raise RuntimeError('隔离库创建失败；未执行测试或业务库迁移')
            created = True
            evidence['mysqlVersion'] = probe.stdout.strip()
            with (EVIDENCE / 'mysql-it.log').open('w', encoding='utf-8') as output:
                result = subprocess.run([shutil.which('mvn') or 'mvn', '-pl', 'ruoyi-modules/ruoyi-ai', '-am', 'test',
                    '-DskipTests=false', '-Dprofiles.active=', '-Dtest=AiMySqlGovernanceIT',
                    '-Dsurefire.failIfNoSpecifiedTests=false'], cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT, timeout=900)
            report = ROOT / 'ruoyi-modules/ruoyi-ai/target/surefire-reports/TEST-com.nexus.ai.service.governance.AiMySqlGovernanceIT.xml'
            if result.returncode or not report.is_file():
                raise RuntimeError('隔离MySQL测试失败，详情见本轮mysql-it.log')
            suite = ET.parse(report).getroot()
            counts = {key: int(suite.attrib[key]) for key in ['tests', 'failures', 'errors', 'skipped']}
            if counts['tests'] <= 0 or any(counts[k] for k in ['failures', 'errors', 'skipped']):
                raise RuntimeError('隔离测试未达到非零用例、零失败/错误/跳过')
            evidence.update(counts)
        finally:
            if created:
                cleanup = subprocess.run(command + ['drop'], env=env, capture_output=True, text=True, encoding='utf-8', timeout=40)
                evidence['isolatedDatabaseRemoved'] = cleanup.returncode == 0
                if cleanup.returncode:
                    print('隔离库保留，所有者校验或清理失败：' + database)
            evidence['finishedAt'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
            (EVIDENCE / 'mysql-it-summary.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(evidence, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(str(error) if isinstance(error, RuntimeError) else '隔离验证异常：' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
