"""两表五列原生精度迁移v3：逐表DDL与全计划单调恢复守卫，不认证历史来源。

固定增量输入已于2026-10-08退役；缺失时在连接前拒绝，不能以主安装SQL替代。
v1/v2签名均拒绝。维护、备份、审查计划及执行签名必须显式覆盖流水三列与库存两列，
目标DECIMAL(18,4)；没有apply/verified布尔授权。三个独立Ed25519角色，公钥指纹
由外部可信通道提供。签名对象为canonical规范UTF-8 JSON，公钥/签名为32/64原始字节。

stock-native-maintenance-v3绑定scope、原生target、UTC窗口、NULL策略、维护票据、
特权写者围栏凭据与artifact_hashes。stock-native-backup-v3绑定相同scope/target、
完整sourceSnapshotSha256、实际备份哈希/大小、隔离恢复演练及其凭据。
precheck返回stock-native-reviewed-plan-v3，含完整基线元数据、原存储合同、TX再STOCK
的两个明确DDL和三个确定检查点；五列所有数值及默认值必须无损，禁止CAST/文本伪证明。

原生复制守卫清点全部performance_schema通道、总applier、协调者、worker、后台线程，
交叉核SHOW SLAVE STATUS和group插件/变量/成员事实。禁用、权限不足、未完整观测均拒绝。
普通5.7只有成功查询证明group插件和变量均不存在时才是已知NOT_INSTALLED；不能猜零。
两表所有列必须有native EXTRA和GENERATION_EXPRESSION；任意生成列提前拒绝。依赖可见性
不足或外键/触发器闭包未知也拒绝。原native RowFormat/表空间/压缩事实必须明确；单独
COPY明确ROW_FORMAT保原值，CREATE_OPTIONS由隐式变显式真实建模，不由默认Dynamic猜测。

stock-native-execution-v3绑定scope、stepsSha256、原审查计划/签名、OS token执行人、
期限、新nonce和EXECUTE_ONCE/RESUME_ONCE动作，还必须绑定planHistorySha256、
planHeadExecutionSha256、planHighWatermark。inspect_plan_history在同计划OS文件锁下
遍历全部nonce签名/日志/回执和持久索引。索引与锁文件尾锚维持最新链尾/单调高水位，
不能只选一个parent日志、删除子日志或截断索引回到祖先。日志缺链/孤儿/损坏一律拒绝。
每条DDL前持久日志fsync；DDL后重获两表WRITE锁并精确回读。不假称多表可ROLLBACK。

恢复另需stock-native-recovery-maintenance-v3的新窗口/围栏签名，绑定原计划和原备份；
新execution-v3还绑定parentExecutionSha256、父日志摘要、实际检查点completedPrefix/
observedSnapshotSha256、remainingStepsSha256、新维护签名及完整最新history/head/high-water。
只可引用最新链尾，实际前缀不能小于整个计划已知高水位。A0丢响应->B2成功->还原0后，
引用祖先A或最新B的新nonce均不提供恢复权；必须独立新备份、新计划和新授权。

能力边界：整个日志/索引/尾锚目录必须独立可靠持久保存，不随业务库回滚或用备份覆盖。
本地日志不提供对整个磁盘被恶意回滚的防篡改硬件保证。缺链、索引/尾锚落盘不一致、
非原检查点或工具SQL版本变化均失败关闭，不能凭编辑JSON修复。只支持原固定计划的向前
恢复/确认，不含真实备份还原、反向DDL或认证/单位/链头/版本/旧数量回填。
五列物理达标也仅sourceCertification=NOT_PERFORMED、historicalPrecisionRecovered=False。
实际MySQL仅纯Python Connector/Python prepared二进制读值；缺依赖拒绝，不自动安装。
本任务测试仅内存夹具及本地文件/OS锁，没有真实DB/服务或MySQL运行验收。
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation, localcontext
import hashlib
import json
import math
import os
from pathlib import Path
import re
from types import MappingProxyType

ROOT = Path(__file__).resolve().parents[4]
SQL_PATH = ROOT / 'script/sql/update/2026-10-03-native-stock-precision-ry-erp.sql'
PRIVATE_ROOT = ROOT / '.codex/full-flow-20260928/native-stock-precision'
STOCK = 'wm_material_stock'
TX = 'wm_material_stock_tx'
NATIVE_COLUMNS = {STOCK: ('stock_quantity', 'reserved_quantity'),
                  TX: ('tx_quantity', 'before_quantity', 'after_quantity')}
NULL_POLICIES = ('PRESERVE_NULL_UNKNOWN', 'REJECT_NULL')
MAX_QUANTITY = Decimal('99999999999999.9999')
MAX_DOCUMENT = 1_000_000
STEP_ORDER = (TX, STOCK)
# 只接受已审核目标的5.7原生目录名，不能把逻辑schema、URL或未核Unicode编码猜成目录名。
NATIVE_DIRECTORY_NAMES = MappingProxyType({
    ('ry-erp',STOCK):'ry@002derp/wm_material_stock',
    ('ry-erp',TX):'ry@002derp/wm_material_stock_tx',
})
AUTHORIZATION_SCOPE = [{'table':table, 'columns':list(NATIVE_COLUMNS[table]), 'targetType':'DECIMAL(18,4)'}
                       for table in STEP_ORDER]
MARKER = r'/\* NEXUS_NATIVE_STOCK_PRECISION_DDL_BEGIN (wm_material_stock(?:_tx)?)\n(.*?)\nNEXUS_NATIVE_STOCK_PRECISION_DDL_END \*/'
EXPECTED_TEMPLATES = {table: ('ALTER TABLE `__SCHEMA__`.`' + table + '`\n'
    '  __COLUMN_CHANGES__,\n  ROW_FORMAT=__ROW_FORMAT__,\n  ALGORITHM=COPY, LOCK=EXCLUSIVE;') for table in STEP_ORDER}


class MigrationBlocked(RuntimeError):
    """只输出固定类别，不附带SQL、凭据或远端错误原文。"""
    def __init__(self, code, *, ddl_attempted=False):
        super().__init__('BLOCKED: ' + code)
        self.code = code
        self.ddl_attempted = ddl_attempted


def require(condition, code):
    if not condition:
        raise MigrationBlocked(code)


def require_migration_input():
    """历史一次性输入不在仓库时失败关闭，不授权重建或执行主安装 dump。"""
    require(SQL_PATH.is_file(), 'MIGRATION_RETIRED')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'),
                      allow_nan=False).encode('utf-8')


def is_hash(value):
    return isinstance(value, str) and re.fullmatch('[0-9a-f]{64}', value) is not None


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'DOCUMENT_REJECTED')
        result[key] = value
    return result


def reject_fraction(_):
    raise MigrationBlocked('DOCUMENT_REJECTED')


def document(raw):
    require(type(raw) is bytes and 0 < len(raw) <= MAX_DOCUMENT, 'DOCUMENT_REJECTED')
    try:
        value = json.loads(raw.decode('utf-8'), object_pairs_hook=unique_object,
                           parse_float=reject_fraction, parse_constant=reject_fraction)
        require(isinstance(value, dict) and canonical(value) == raw, 'DOCUMENT_REJECTED')
        return value
    except (ValueError, UnicodeError, TypeError, RecursionError):
        raise MigrationBlocked('DOCUMENT_REJECTED') from None


@dataclass(frozen=True)
class SignedDocument:
    payload: bytes = field(repr=False)
    signature: bytes = field(repr=False)


@dataclass(frozen=True)
class AuthorityKeys:
    material: dict = field(repr=False)

    def __post_init__(self):
        require(set(self.material) == {'backup', 'review', 'execution'}, 'TRUST_KEYS_REJECTED')
        fingerprints = set()
        for public, pin in self.material.values():
            require(type(public) is bytes and len(public) == 32 and is_hash(pin)
                    and sha(public) == pin, 'TRUST_KEYS_REJECTED')
            fingerprints.add(pin)
        require(len(fingerprints) == 3, 'TRUST_KEYS_REJECTED')
        object.__setattr__(self, 'material', MappingProxyType({role: tuple(pair) for role, pair in self.material.items()}))

    def verify(self, role, signed):
        try:
            from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
            require(isinstance(signed, SignedDocument) and len(signed.signature) == 64, 'SIGNATURE_REJECTED')
            public, _ = self.material[role]
            Ed25519PublicKey.from_public_bytes(public).verify(signed.signature, signed.payload)
        except ImportError:
            raise MigrationBlocked('SIGNATURE_DEPENDENCY_MISSING') from None
        except MigrationBlocked:
            raise
        except Exception:
            raise MigrationBlocked('SIGNATURE_REJECTED') from None
        return document(signed.payload)


@dataclass(frozen=True)
class EvidenceFiles:
    backup: Path = field(repr=False)
    restore: Path = field(repr=False)
    fence: Path = field(repr=False)


def file_digest(path):
    try:
        path = Path(path)
        require(path.is_file() and not path.is_symlink(), 'EVIDENCE_MISMATCH')
        before = path.stat()
        digest = hashlib.sha256()
        size = 0
        with path.open('rb') as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b''):
                digest.update(chunk)
                size += len(chunk)
        after = path.stat()
        require(size > 0 and size == before.st_size == after.st_size and
                (before.st_ino, before.st_mtime_ns) == (after.st_ino, after.st_mtime_ns), 'EVIDENCE_MISMATCH')
        return digest.hexdigest(), size
    except OSError:
        raise MigrationBlocked('EVIDENCE_MISMATCH') from None


def artifact_hashes():
    require_migration_input()
    return {'migrationSqlSha256': file_digest(SQL_PATH)[0], 'executorSha256': file_digest(Path(__file__))[0]}


def executable_sql_outside_template(sql):
    matches = re.findall(MARKER, sql, re.S)
    require(len(matches)==2 and {table for table, _ in matches}=={STOCK,TX}, 'SQL_TEMPLATE_REJECTED')
    outside = re.sub(MARKER, '', sql, flags=re.S)
    return re.sub(r'--[^\n]*', '', outside).strip()


def sql_template(table=STOCK):
    require_migration_input()
    require(table in STEP_ORDER, 'SQL_TEMPLATE_REJECTED')
    try:
        sql = SQL_PATH.read_text(encoding='utf-8-sig')
    except FileNotFoundError:
        raise MigrationBlocked('MIGRATION_RETIRED') from None
    except OSError:
        raise MigrationBlocked('SQL_TEMPLATE_REJECTED') from None
    outside = executable_sql_outside_template(sql)
    require(re.fullmatch(r"SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '[^']+';", outside) is not None,
            'SQL_TEMPLATE_REJECTED')
    templates = dict(re.findall(MARKER, sql, re.S))
    require(templates == EXPECTED_TEMPLATES, 'SQL_TEMPLATE_REJECTED')
    return templates[table]


def native_precision_gaps(key, columns):
    """与formal共用物理列合同，绝不从候选列、CAST或迁移结果认证来源。"""
    table = key.rsplit('.', 1)[-1]
    gaps = []
    for name in NATIVE_COLUMNS.get(table, ()):
        spec = columns.get(name, {})
        if not (str(spec.get('type', '')).lower() == 'decimal' and type(spec.get('precision')) is int
                and spec['precision'] == 18 and type(spec.get('scale')) is int and spec['scale'] == 4):
            gaps.append(key + '.' + name + ': native DECIMAL required')
    return gaps


def exact_quantity(value, kind, null_policy, nullable):
    """按原生DOUBLE二进制值判断无损表达，禁止先str浮点或量化舍入。"""
    require(null_policy in NULL_POLICIES and type(nullable) is bool, 'NULL_POLICY_REQUIRED')
    if value is None:
        require(nullable and null_policy == 'PRESERVE_NULL_UNKNOWN', 'NULL_NOT_ALLOWED')
        return None
    if kind == 'double':
        require(type(value) is float, 'NATIVE_BINARY_VALUE_REQUIRED')
        require(math.isfinite(value), 'LOSSY_VALUE')
        result = Decimal.from_float(value)
    elif kind == 'decimal':
        require(isinstance(value, Decimal), 'NATIVE_DECIMAL_VALUE_REQUIRED')
        result = value
    else:
        raise MigrationBlocked('SOURCE_TYPE_UNSUPPORTED')
    require(result.is_finite() and result.copy_abs() <= MAX_QUANTITY, 'LOSSY_VALUE')
    with localcontext() as context:
        # 最小DOUBLE的精确展开也要完整校验，不能让默认28位上下文先舍入。
        context.prec = 1200
        scaled = result * Decimal(10000)
        require(scaled == scaled.to_integral_value(), 'LOSSY_VALUE')
        if kind == 'double':
            # 5.7的double2decimal经过my_gcvt最短表示；超过15有效位的精确二进制数也可能改变十进制值。
            # 不以repr或文本协议冒充无损证据；保守拒绝这类原生转换尚未证明的值。
            significant = len(''.join(str(digit) for digit in result.as_tuple().digits).rstrip('0'))
            require(significant <= 15, 'MYSQL_DOUBLE_CONVERSION_UNPROVEN')
        quantized = result.quantize(Decimal('0.0001'))
        return quantized.copy_abs() if quantized.is_zero() else quantized


def column_default(col):
    value = col.get('default')
    if value is None:
        return None
    try:
        source = float(value) if col['type'] == 'double' else Decimal(value)
        return exact_quantity(source, col['type'], 'REJECT_NULL', col['nullable'])
    except (InvalidOperation, ValueError, TypeError, OverflowError):
        raise MigrationBlocked('DEFAULT_UNPROVEN') from None


def scalar(value):
    """类型标签保全未修改字段；不记录原始行内容到输出。"""
    if value is None:
        return ['null']
    if isinstance(value, Decimal):
        require(value.is_finite(), 'ROW_VALUE_UNSUPPORTED')
        return ['decimal', format(value, 'f')]
    if type(value) is float:
        return ['double', value.hex()]
    if type(value) in (int, str):
        return [type(value).__name__, str(value)]
    if isinstance(value, (bytes, bytearray)):
        return ['bytes', bytes(value).hex()]
    if isinstance(value, (datetime, date)):
        return [type(value).__name__, value.isoformat()]
    if isinstance(value, timedelta):
        return ['timedelta', str(value.days), str(value.seconds), str(value.microseconds)]
    raise MigrationBlocked('ROW_VALUE_UNSUPPORTED')


def native_decimal_shape(col):
    if col.get('type') != 'decimal' or not isinstance(col.get('columnType'), str):
        return False
    shape = re.fullmatch(r'decimal\(([0-9]+),([0-9]+)\)(?: unsigned)?(?: zerofill)?', col['columnType'])
    return (shape is not None and type(col.get('precision')) is int and type(col.get('scale')) is int and
            1 <= col['precision'] <= 65 and 0 <= col['scale'] <= min(30, col['precision']) and
            tuple(map(int, shape.groups())) == (col['precision'], col['scale']))


def metadata_precision_gaps(metadata):
    gaps = []
    for table in NATIVE_COLUMNS:
        columns = {col['name']: col for col in metadata[table]['columns']}
        gaps.extend(native_precision_gaps('ry-erp.' + table, columns))
    return gaps


def validate_table_storage(item):
    options=item.get('tableOptions')
    physical=item.get('physicalStorage')
    definition=item.get('tableDefinitionOptions')
    require(isinstance(options,list) and len(options)==3 and isinstance(options[1],str) and
            isinstance(options[2],str) and isinstance(physical,dict) and isinstance(definition,str),
            'STORAGE_ATTRIBUTES_UNKNOWN')
    row=options[1].upper()
    require(row in ('COMPACT','REDUNDANT','DYNAMIC') and physical.get('rowFormat','').upper()==row and
            physical.get('spaceType')=='Single' and type(physical.get('zipPageSize')) is int and
            physical['zipPageSize']==0 and type(physical.get('pageSize')) is int and
            physical['pageSize'] in (4096,8192,16384,32768,65536),'STORAGE_ATTRIBUTES_UNSUPPORTED')
    require(not re.search(r'(?i)\b(COMPRESSION|ENCRYPTION|TABLESPACE|KEY_BLOCK_SIZE|PARTITION|CONNECTION)\b|(?:DATA|INDEX)\s+DIRECTORY',definition),
            'STORAGE_ATTRIBUTES_UNSUPPORTED')
    original=options[2].strip()
    require(original=='' or re.fullmatch(r'(?i)row_format=(COMPACT|REDUNDANT|DYNAMIC)',original) is not None,
            'STORAGE_ATTRIBUTES_UNSUPPORTED')
    if original:
        require(original.split('=',1)[1].upper()==row,'STORAGE_ATTRIBUTES_UNKNOWN')
    require(item.get('foreignKeys')==[] and item.get('inboundForeignKeys')==[] and item.get('triggers')==[],
            'DEPENDENCY_CLOSURE_UNSUPPORTED')
    return row


def validate_metadata(metadata):
    require(isinstance(metadata, dict) and set(metadata) == {STOCK, TX}, 'METADATA_REQUIRED')
    for table, primary in ((STOCK, 'stock_id'), (TX, 'tx_id')):
        item = metadata[table]
        require(item.get('engine') == 'InnoDB' and item.get('primaryKey') == [primary], 'STORAGE_CONTRACT_REJECTED')
        validate_table_storage(item)
        columns = item.get('columns')
        require(isinstance(columns, list) and columns, 'METADATA_REQUIRED')
        names = set()
        for col in columns:
            require(isinstance(col, dict) and isinstance(col.get('name'), str) and col['name'] not in names,
                    'METADATA_REQUIRED')
            require(re.fullmatch(r'[A-Za-z_][A-Za-z_0-9]*', col['name']) is not None, 'METADATA_REQUIRED')
            require(isinstance(col.get('extra'),str) and isinstance(col.get('generationExpression'),str),
                    'GENERATION_METADATA_UNKNOWN')
            require('GENERATED' not in col['extra'].upper() and col['generationExpression']=='',
                    'GENERATED_COLUMNS_UNSUPPORTED')
            names.add(col['name'])
        require({primary, *NATIVE_COLUMNS[table]} <= names, 'METADATA_REQUIRED')
    quantities = {}
    for table in STEP_ORDER:
        columns = {col['name']:col for col in metadata[table]['columns']}
        for name in NATIVE_COLUMNS[table]:
            col = columns[name]
            require(col.get('extra')=='' and type(col.get('nullable')) is bool and
                    isinstance(col.get('comment'),str) and '\x00' not in col['comment'], 'SOURCE_ATTRIBUTES_UNSUPPORTED')
            if table==TX:
                require(native_decimal_shape(col), 'TX_NATIVE_PRECISION_REQUIRED')
            else:
                require(col.get('type') in ('double','decimal') and isinstance(col.get('columnType'),str) and
                        re.fullmatch(r'(?:double(?:\([0-9]+,[0-9]+\))?|decimal\([0-9]+,[0-9]+\))',col['columnType']) is not None,
                        'SOURCE_ATTRIBUTES_UNSUPPORTED')
                if col['type']=='decimal':
                    require(native_decimal_shape(col), 'METADATA_REQUIRED')
            if col['type']=='decimal':
                require(col['scale']<=4 and col['precision']-col['scale']<=14, 'DECIMAL_CHANGE_IS_NOT_WIDENING')
            column_default(col)
        quantities[table]=columns
    return quantities


def quantity_attributes(col):
    if col['type']=='decimal':
        return col['columnType'][col['columnType'].index(')')+1:]
    return ''


def canonical_metadata(metadata, *, goal_tables=()):
    result = json.loads(json.dumps(metadata))
    for table in STEP_ORDER:
        item=result[table]
        row=validate_table_storage(item)
        # 只有实际DDL才将隐式属性改为显式；检查点必须建模CREATE_OPTIONS的真实改变，不能抹去。
        explicit_goal=table in goal_tables and any(col['name'] in NATIVE_COLUMNS[table] and
            not (col['type']=='decimal' and col['precision']==18 and col['scale']==4) for col in item['columns'])
        options_were_explicit=bool(item['tableOptions'][2].strip())
        item['tableOptions'][2]='row_format='+row if explicit_goal or options_were_explicit else ''
        definition_was_explicit=re.search(r'(?i)\s+ROW_FORMAT\s*=\s*(COMPACT|REDUNDANT|DYNAMIC)\b',item['tableDefinitionOptions']) is not None
        definition=re.sub(r'(?i)\s+ROW_FORMAT\s*=\s*(COMPACT|REDUNDANT|DYNAMIC)\b','',item['tableDefinitionOptions'])
        item['tableDefinitionOptions']=definition+' ROW_FORMAT='+row if explicit_goal or definition_was_explicit else definition
        for col in result[table]['columns']:
            if col['name'] in NATIVE_COLUMNS[table]:
                default = column_default(col)
                col['default'] = None if default is None else format(default,'.4f')
                if table in goal_tables:
                    suffix = quantity_attributes(col)
                    col.update(type='decimal',columnType='decimal(18,4)'+suffix,precision=18,scale=4)
    return result


def snapshot_from_parts(metadata, parts):
    value = {name:parts[name] for name in ('stockRowsSha256','stockTxRowsSha256','stockRows','stockTxRows','nullCounts')}
    value.update(schemaSha256=sha(canonical(canonical_metadata(metadata))),
                 immutableSchemaSha256=sha(canonical(canonical_metadata(metadata,goal_tables=STEP_ORDER))))
    value['snapshotSha256']=sha(canonical(value))
    return {**value,'metadata':metadata}


def capture_snapshot(store, null_policy):
    metadata = store.describe()
    quantities = validate_metadata(metadata)
    table_hashes, counts = {}, {}
    null_counts = {table+'.'+name:0 for table in STEP_ORDER for name in NATIVE_COLUMNS[table]}
    for table, primary in ((STOCK,'stock_id'),(TX,'tx_id')):
        digest=hashlib.sha256()
        names={col['name'] for col in metadata[table]['columns']}
        previous=None
        count=0
        for row in store.iter_rows(table):
            require(isinstance(row,dict) and set(row)==names and type(row[primary]) is int, 'FULL_ROW_SNAPSHOT_REQUIRED')
            require(previous is None or row[primary]>previous, 'ROW_ORDER_OR_DUPLICATE_REJECTED')
            previous=row[primary]
            normalized={}
            for name,value in row.items():
                if name in NATIVE_COLUMNS[table]:
                    col=quantities[table][name]
                    exact=exact_quantity(value,col['type'],null_policy,col['nullable'])
                    require(exact is None or 'unsigned' not in quantity_attributes(col) or exact>=0, 'LOSSY_VALUE')
                    normalized[name]=['quantity',None if exact is None else format(exact,'.4f')]
                    null_counts[table+'.'+name]+=int(exact is None)
                else:
                    normalized[name]=scalar(value)
            raw=canonical(normalized)
            digest.update(str(len(raw)).encode('ascii')+b':'+raw)
            count+=1
        table_hashes[table]=digest.hexdigest()
        counts[table]=count
    return snapshot_from_parts(metadata,{'stockRowsSha256':table_hashes[STOCK],'stockTxRowsSha256':table_hashes[TX],
        'stockRows':counts[STOCK],'stockTxRows':counts[TX],'nullCounts':null_counts})


def render_ddl(metadata, table=STOCK):
    columns=validate_metadata(metadata)[table]
    changes=[]
    for name in NATIVE_COLUMNS[table]:
        col=columns[name]
        if col['type']=='decimal' and col['precision']==18 and col['scale']==4:
            continue
        nullable='NULL' if col['nullable'] else 'NOT NULL'
        default=column_default(col)
        default_sql=' DEFAULT NULL' if default is None and col['nullable'] else ''
        if default is not None:
            default_sql=' DEFAULT '+format(default,'.4f')
        suffix=quantity_attributes(col).upper()
        comment=col['comment'].replace("'","''")
        changes.append(f"MODIFY COLUMN `{name}` DECIMAL(18,4){suffix} {nullable}{default_sql} COMMENT '{comment}'")
    if not changes:
        return None
    return sql_template(table).replace('__SCHEMA__','ry-erp').replace('__COLUMN_CHANGES__',',\n  '.join(changes)).replace(
        '__ROW_FORMAT__',validate_table_storage(metadata[table]))


def timestamp(value, code):
    try:
        require(isinstance(value, str), code)
        result = datetime.fromisoformat(value)
        require(result.tzinfo is not None and result.utcoffset() == timedelta(0), code)
        return result
    except (ValueError, TypeError):
        raise MigrationBlocked(code) from None


def native_directory_name(schema,table):
    require(isinstance(schema,str) and isinstance(table,str),'NATIVE_DIRECTORY_NAME_UNSUPPORTED')
    name=NATIVE_DIRECTORY_NAMES.get((schema,table))
    require(name is not None,'NATIVE_DIRECTORY_NAME_UNSUPPORTED')
    return name


def validate_target(target):
    required = {'schema', 'host', 'port', 'serverUuid', 'serverId', 'serverHostnameSha256', 'version'}
    require(isinstance(target, dict) and set(target) == required, 'EXPLICIT_TARGET_REQUIRED')
    require(target['schema'] == 'ry-erp' and target['host'] == '127.0.0.1' and
            type(target['port']) is int and target['port'] == 3306, 'EXPLICIT_TARGET_REQUIRED')
    require(re.fullmatch('[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', str(target['serverUuid'])) is not None and
            type(target['serverId']) is int and 0 <= target['serverId'] < 2**32 and
            is_hash(target['serverHostnameSha256']) and
            re.fullmatch(r'5\.7\.[0-9]+(?:[-.][A-Za-z0-9_.-]+)?', str(target['version'])) is not None,
            'EXPLICIT_TARGET_REQUIRED')


def check_window(maintenance, now):
    start = timestamp(maintenance.get('windowStart'), 'MAINTENANCE_WINDOW_REJECTED')
    end = timestamp(maintenance.get('windowEnd'), 'MAINTENANCE_WINDOW_REJECTED')
    require(start <= now < end and timedelta(0) < end - start <= timedelta(hours=24), 'MAINTENANCE_WINDOW_REJECTED')
    return start, end


def evidence_matches(maintenance, backup, files):
    require(isinstance(files, EvidenceFiles), 'EVIDENCE_MISMATCH')
    actual_hash, size = file_digest(files.backup)
    require(actual_hash == backup.get('backupSha256') and type(backup.get('backupBytes')) is int and
            backup['backupBytes'] == size, 'EVIDENCE_MISMATCH')
    require(file_digest(files.restore)[0] == backup.get('restoreEvidenceSha256') and
            file_digest(files.fence)[0] == maintenance.get('fenceEvidenceSha256'), 'EVIDENCE_MISMATCH')


def validate_original_contracts(maintenance, backup, keys, now):
    require(isinstance(keys,AuthorityKeys),'TRUST_KEYS_REJECTED')
    maint=keys.verify('review',maintenance)
    saved=keys.verify('backup',backup)
    require(maint.get('kind')=='stock-native-maintenance-v3' and saved.get('kind')=='stock-native-backup-v3',
            'LEGACY_AUTHORIZATION_REJECTED')
    require(maint.get('scope')==AUTHORIZATION_SCOPE and saved.get('scope')==AUTHORIZATION_SCOPE,
            'FIVE_COLUMN_SCOPE_REQUIRED')
    target=maint.get('target')
    validate_target(target)
    require(saved.get('target')==target,'BACKUP_TARGET_MISMATCH')
    require(maint.get('nullPolicy') in NULL_POLICIES and isinstance(maint.get('ticketId'),str) and maint['ticketId'],
            'AUTHORIZATION_CONTRACT_REJECTED')
    start=timestamp(maint.get('windowStart'),'MAINTENANCE_WINDOW_REJECTED')
    end=timestamp(maint.get('windowEnd'),'MAINTENANCE_WINDOW_REJECTED')
    require(timedelta(0)<end-start<=timedelta(hours=24),'MAINTENANCE_WINDOW_REJECTED')
    require(maint.get('fenceStrategy')=='GLOBAL_READ_ONLY_PRIVILEGED_WRITERS_FENCED' and
            is_hash(maint.get('fenceEvidenceSha256')),'WRITER_FENCE_MISSING')
    require(all(maint.get(name)==value for name,value in artifact_hashes().items()),'ARTIFACT_MISMATCH')
    require(isinstance(saved.get('backupId'),str) and saved['backupId'] and is_hash(saved.get('sourceSnapshotSha256')),
            'BACKUP_RECEIPT_REQUIRED')
    captured=timestamp(saved.get('capturedAt'),'BACKUP_RECEIPT_REQUIRED')
    restored=timestamp(saved.get('restoredAt'),'BACKUP_RECEIPT_REQUIRED')
    require(start<=captured<=restored<end and restored<=now and
            re.fullmatch('[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}',str(saved.get('restoredServerUuid'))) is not None and
            saved['restoredServerUuid']!=target['serverUuid'],'BACKUP_RECEIPT_REQUIRED')
    sql_template()
    return maint,saved


def validate_context(maintenance, backup, keys, files, now):
    maint,saved=validate_original_contracts(maintenance,backup,keys,now)
    check_window(maint,now)
    evidence_matches(maint,saved,files)
    return maint,saved


def observe_target(store, target, *, execution=False):
    observed = store.observe_identity()
    require(all(observed.get(key) == value for key, value in target.items()), 'TARGET_MISMATCH')
    require(observed.get('readOnly') == 1 and observed.get('replicationApplierCount') == 0 and
            observed.get('eventScheduler') in ('OFF', 'DISABLED'), 'WRITER_FENCE_MISSING')
    inventory=observed.get('replicationInventory')
    require(isinstance(inventory,dict) and inventory.get('status')=='COMPLETE' and
            inventory.get('activeApplierCount')==0 and is_hash(inventory.get('inventorySha256')),
            'REPLICATION_INVENTORY_UNKNOWN')
    if execution:
        require(observed.get('superReadOnly') == 0, 'WRITER_FENCE_MISSING')


def make_plan(snapshot, maintenance, backup, signed_maintenance, signed_backup):
    steps=[]
    metadata=snapshot['metadata']
    checkpoints=[snapshot['snapshotSha256']]
    original=[]
    for index,table in enumerate(STEP_ORDER):
        columns={col['name']:col for col in metadata[table]['columns']}
        original.extend({'table':table,'column':name,'type':columns[name]['type'],
                         'precision':columns[name]['precision'],'scale':columns[name]['scale']} for name in NATIVE_COLUMNS[table])
        ddl=render_ddl(metadata,table)
        after_metadata=canonical_metadata(metadata,goal_tables=(table,))
        after=snapshot_from_parts(after_metadata,snapshot)
        steps.append({'stepIndex':index,'table':table,'columns':list(NATIVE_COLUMNS[table]),
                      'operation':'ALTER_COLUMN_TYPES' if ddl else 'NOOP_NATIVE', 'ddl':ddl,
                      'ddlSha256':sha(ddl.encode('utf-8')) if ddl else None,
                      'beforeSnapshotSha256':checkpoints[-1],'afterSnapshotSha256':after['snapshotSha256']})
        checkpoints.append(after['snapshotSha256'])
        metadata=after_metadata
    source={key:value for key,value in snapshot.items() if key!='metadata'}
    return {'kind':'stock-native-reviewed-plan-v3', 'scope':AUTHORIZATION_SCOPE,
            'status':'PRECHECK_READY_REVIEW_REQUIRED' if any(step['ddl'] for step in steps) else 'ALREADY_NATIVE_SOURCE_UNCERTIFIED',
            'target':maintenance['target'],'nullPolicy':maintenance['nullPolicy'],
            'maintenanceSha256':sha(signed_maintenance.payload),'backupReceiptSha256':sha(signed_backup.payload),
            'sourceSnapshotSha256':snapshot['snapshotSha256'],'sourceSnapshot':source,
            'stockRows':snapshot['stockRows'],'stockTxRows':snapshot['stockTxRows'],'nullCounts':snapshot['nullCounts'],
            'baselineMetadata':snapshot['metadata'],'originalStorageContracts':original,
            'steps':steps,'checkpoints':checkpoints, **artifact_hashes(),
            'sourceCertification':'NOT_PERFORMED','historicalPrecisionRecovered':False,
            'missingStockDoesNotProveZero':True,'businessDataWrites':0,
            'ddlImplicitCommitRequiresIndependentRecovery':True}


def verify_reviewed_plan(keys, signed, maint, saved, signed_maint, signed_backup):
    plan=keys.verify('review',signed)
    require(plan.get('kind')=='stock-native-reviewed-plan-v3' and plan.get('scope')==AUTHORIZATION_SCOPE,
            'REVIEWED_PLAN_REJECTED')
    try:
        metadata=plan['baselineMetadata']
        validate_metadata(metadata)
        snapshot=snapshot_from_parts(metadata,plan['sourceSnapshot'])
        require(snapshot['snapshotSha256']==saved['sourceSnapshotSha256'],'BACKUP_SNAPSHOT_MISMATCH')
        require(make_plan(snapshot,maint,saved,signed_maint,signed_backup)==plan,'REVIEWED_PLAN_REJECTED')
    except (KeyError,TypeError,ValueError):
        raise MigrationBlocked('REVIEWED_PLAN_REJECTED') from None
    return plan


def match_checkpoint(plan, snapshot):
    # 仅接受原签名计划的精确schema/全行/数量前缀；不能重建一个新的未经审查基线。
    matches=[index for index,value in enumerate(plan['checkpoints']) if value==snapshot['snapshotSha256']]
    require(bool(matches),'RECOVERY_STATE_NOT_A_SIGNED_CHECKPOINT')
    return max(matches)


def precheck(*, store_factory, maintenance, backup, keys, files, now=lambda: datetime.now(timezone.utc)):
    require_migration_input()
    maint, saved = validate_context(maintenance, backup, keys, files, now())
    store = None
    try:
        store = store_factory(maint['target'])
        observe_target(store, maint['target'])
        store.begin_readonly()
        snapshot = capture_snapshot(store, maint['nullPolicy'])
        require(snapshot['snapshotSha256'] == saved['sourceSnapshotSha256'], 'BACKUP_SNAPSHOT_MISMATCH')
        check_window(maint, now())
        evidence_matches(maint, saved, files)
        return make_plan(snapshot, maint, saved, maintenance, backup)
    except MigrationBlocked:
        raise
    except Exception:
        raise MigrationBlocked('PRECHECK_FAILED') from None
    finally:
        if store is not None and cleanup_store(store):
            raise MigrationBlocked('PRECHECK_CLEANUP_FAILED') from None


def cleanup_store(store, *, locked=False):
    failed = False
    callbacks = [store.unlock, store.close] if locked else [store.close]
    for callback in callbacks:
        try:
            callback()
        except Exception:
            failed = True
    return failed


def operator_digest():
    # 用户环境变量可伪造，不能用getpass/USERNAME冒充真正的OS执行人。
    if os.name != 'nt':
        require(hasattr(os, 'geteuid'), 'OPERATOR_IDENTITY_UNAVAILABLE')
        return sha(('posix-euid:' + str(os.geteuid())).encode('ascii'))
    import ctypes
    from ctypes import wintypes
    token = wintypes.HANDLE()
    text_sid = wintypes.LPWSTR()
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    security = ctypes.WinDLL('advapi32', use_last_error=True)
    kernel.GetCurrentProcess.argtypes = []
    kernel.GetCurrentProcess.restype = wintypes.HANDLE
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel.CloseHandle.restype = wintypes.BOOL
    kernel.LocalFree.argtypes = [ctypes.c_void_p]
    kernel.LocalFree.restype = ctypes.c_void_p
    security.OpenProcessToken.argtypes = [wintypes.HANDLE, wintypes.DWORD, ctypes.POINTER(wintypes.HANDLE)]
    security.OpenProcessToken.restype = wintypes.BOOL
    security.GetTokenInformation.argtypes = [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p,
                                             wintypes.DWORD, ctypes.POINTER(wintypes.DWORD)]
    security.GetTokenInformation.restype = wintypes.BOOL
    security.ConvertSidToStringSidW.argtypes = [ctypes.c_void_p, ctypes.POINTER(wintypes.LPWSTR)]
    security.ConvertSidToStringSidW.restype = wintypes.BOOL
    try:
        require(security.OpenProcessToken(kernel.GetCurrentProcess(), 0x0008, ctypes.byref(token)),
                'OPERATOR_IDENTITY_UNAVAILABLE')
        needed = wintypes.DWORD()
        security.GetTokenInformation(token, 1, None, 0, ctypes.byref(needed))
        require(0 < needed.value <= 65536, 'OPERATOR_IDENTITY_UNAVAILABLE')
        buffer = ctypes.create_string_buffer(needed.value)
        require(security.GetTokenInformation(token, 1, buffer, needed.value, ctypes.byref(needed)),
                'OPERATOR_IDENTITY_UNAVAILABLE')
        # TOKEN_USER的首字段是SID_AND_ATTRIBUTES，其首字段为SID指针。
        sid = ctypes.cast(buffer, ctypes.POINTER(ctypes.c_void_p)).contents.value
        require(sid and security.ConvertSidToStringSidW(sid, ctypes.byref(text_sid)),
                'OPERATOR_IDENTITY_UNAVAILABLE')
        require(re.fullmatch(r'S-[0-9]+(?:-[0-9]+)+', text_sid.value) is not None,
                'OPERATOR_IDENTITY_UNAVAILABLE')
        return sha(('windows-token-sid:' + text_sid.value).encode('ascii'))
    except MigrationBlocked:
        raise
    except Exception:
        raise MigrationBlocked('OPERATOR_IDENTITY_UNAVAILABLE') from None
    finally:
        if text_sid:
            kernel.LocalFree(ctypes.cast(text_sid, ctypes.c_void_p))
        if token:
            kernel.CloseHandle(token)


def execution_document(keys, signed, review, plan):
    grant=keys.verify('execution',signed)
    require(grant.get('kind')=='stock-native-execution-v3' and grant.get('target')==plan['target'] and
            grant.get('scope')==AUTHORIZATION_SCOPE and grant.get('stepsSha256')==sha(canonical(plan['steps'])) and
            grant.get('reviewedPlanSha256')==sha(review.payload) and
            grant.get('reviewSignatureSha256')==sha(review.signature) and
            grant.get('action') in ('EXECUTE_ONCE','RESUME_ONCE') and is_hash(grant.get('operatorSha256')) and
            {'planHistorySha256','planHeadExecutionSha256','planHighWatermark'}<=set(grant) and
            is_hash(grant.get('planHistorySha256')) and
            (grant.get('planHeadExecutionSha256') is None or is_hash(grant['planHeadExecutionSha256'])) and
            type(grant.get('planHighWatermark')) is int and 0<=grant['planHighWatermark']<=len(plan['steps']) and
            re.fullmatch('[0-9a-f]{32}',str(grant.get('nonce'))) is not None,'EXECUTION_GRANT_REJECTED')
    issued=timestamp(grant.get('issuedAt'),'EXECUTION_GRANT_REJECTED')
    expires=timestamp(grant.get('expiresAt'),'EXECUTION_GRANT_REJECTED')
    require(timedelta(0)<expires-issued<=timedelta(minutes=30),'EXECUTION_GRANT_REJECTED')
    return grant


def validate_execution(keys, signed, review, plan, target, now):
    grant=execution_document(keys,signed,review,plan)
    require(grant['target']==target and grant['operatorSha256']==operator_digest() and
            timestamp(grant['issuedAt'],'EXECUTION_GRANT_REJECTED')<=now<timestamp(grant['expiresAt'],'EXECUTION_GRANT_REJECTED'),
            'EXECUTION_GRANT_REJECTED')
    return grant


def write_receipt(path, report, *, exclusive=False):
    raw = canonical(report)
    try:
        with path.open('xb' if exclusive else 'wb') as handle:
            handle.write(raw)
            handle.flush()
            os.fsync(handle.fileno())
    except FileExistsError:
        raise MigrationBlocked('EXECUTION_ALREADY_CONSUMED') from None
    except OSError:
        raise MigrationBlocked('RECEIPT_WRITE_FAILED') from None


JOURNAL_EVENTS = {'HEADER','DDL_INTENT','DDL_RESPONSE','STEP_NATIVE_NOOP','STEP_VERIFIED','FAILED','SUCCESS','INTERRUPTED'}


def read_journal(path):
    raw=read_small(path,MAX_DOCUMENT)
    require(raw.endswith(b'\n'),'JOURNAL_INTEGRITY_REJECTED')
    events=[]
    previous='0'*64
    for index,line in enumerate(raw.splitlines()):
        event=document(line)
        checksum=event.pop('eventSha256',None)
        require(event.get('sequence')==index and event.get('previousEventSha256')==previous and
                event.get('event') in JOURNAL_EVENTS and checksum==sha(canonical(event)), 'JOURNAL_INTEGRITY_REJECTED')
        event['eventSha256']=checksum
        events.append(event)
        previous=checksum
    require(events and events[0]['event']=='HEADER','JOURNAL_INTEGRITY_REJECTED')
    return events


class MigrationJournal:
    """每步DDL发送前fsync哈希链；日志不是授权，恢复仍需独立新签名与现场检查点。"""
    def __init__(self,path,header):
        self.path=path
        self.previous='0'*64
        self.sequence=0
        try:
            with path.open('xb'):
                pass
        except FileExistsError:
            raise MigrationBlocked('EXECUTION_ALREADY_CONSUMED') from None
        self.append('HEADER',**header)

    def append(self,event,**fields):
        if self.sequence:
            events=read_journal(self.path)
            require(len(events)==self.sequence and events[-1]['eventSha256']==self.previous,'JOURNAL_INTEGRITY_REJECTED')
        value={'event':event,'sequence':self.sequence,'previousEventSha256':self.previous,**fields}
        value['eventSha256']=sha(canonical(value))
        try:
            with self.path.open('ab') as handle:
                handle.write(canonical(value)+b'\n')
                handle.flush()
                os.fsync(handle.fileno())
        except OSError:
            raise MigrationBlocked('JOURNAL_WRITE_FAILED') from None
        self.previous=value['eventSha256']
        self.sequence+=1


def validate_parent_journal(events, parent, signed_parent, plan, review):
    header=events[0]
    require(header.get('executionSha256')==sha(signed_parent.payload) and
            header.get('reviewedPlanSha256')==sha(review.payload) and header.get('nonce')==parent['nonce'] and
            header.get('scope')==AUTHORIZATION_SCOPE and header.get('stepsSha256')==sha(canonical(plan['steps'])),
            'JOURNAL_INTEGRITY_REJECTED')
    prefix=header.get('startPrefix')
    require(type(prefix) is int and 0<=prefix<=len(plan['steps']) and
            prefix==(parent.get('completedPrefix') if parent['action']=='RESUME_ONCE' else 0) and
            header.get('initialSnapshotSha256')==plan['checkpoints'][prefix] and
            header.get('parentJournalSha256')==(parent.get('parentJournalSha256') if parent['action']=='RESUME_ONCE' else None),
            'JOURNAL_INTEGRITY_REJECTED')
    pending=False
    for event in events[1:]:
        if event['event'] in ('DDL_INTENT','STEP_NATIVE_NOOP'):
            require(not pending and event.get('stepIndex')==prefix and prefix<len(plan['steps']), 'JOURNAL_INTEGRITY_REJECTED')
            step=plan['steps'][prefix]
            require(event.get('ddlSha256')==step['ddlSha256'] and
                    (event['event']=='DDL_INTENT')==(step['ddl'] is not None),'JOURNAL_INTEGRITY_REJECTED')
            pending=True
        elif event['event']=='STEP_VERIFIED':
            require(pending and event.get('stepIndex')==prefix and
                    event.get('snapshotSha256')==plan['checkpoints'][prefix+1],'JOURNAL_INTEGRITY_REJECTED')
            prefix+=1
            pending=False
        elif event['event']=='DDL_RESPONSE':
            require(pending and event.get('stepIndex')==prefix,'JOURNAL_INTEGRITY_REJECTED')
    return prefix


@contextmanager
def plan_lock(receipt_dir,plan_id):
    """同计划原生OS文件锁，不能用可编辑布尔标志代替；锁文件始终保留。"""
    require(is_hash(plan_id),'PLAN_HISTORY_INCOMPLETE')
    directory=Path(receipt_dir)
    directory.mkdir(parents=True,exist_ok=True)
    require(not directory.is_symlink(),'PLAN_HISTORY_INCOMPLETE')
    path=directory/('.plan-'+plan_id+'.lock')
    require(not path.is_symlink(),'PLAN_HISTORY_INCOMPLETE')
    handle=path.open('a+b')
    locked=False
    try:
        if path.stat().st_size==0:
            handle.write(b'L')
            handle.flush()
            os.fsync(handle.fileno())
        handle.seek(0)
        try:
            if os.name=='nt':
                import msvcrt
                msvcrt.locking(handle.fileno(),msvcrt.LK_NBLCK,1)
            else:
                import fcntl
                fcntl.flock(handle.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
            locked=True
        except OSError:
            raise MigrationBlocked('PLAN_LOCK_BUSY') from None
        yield
    finally:
        if locked:
            handle.seek(0)
            if os.name=='nt':
                import msvcrt
                msvcrt.locking(handle.fileno(),msvcrt.LK_UNLCK,1)
            else:
                import fcntl
                fcntl.flock(handle.fileno(),fcntl.LOCK_UN)
        handle.close()


def plan_index_events(raw):
    require(not raw or raw.endswith(b'\n'),'PLAN_HISTORY_INCOMPLETE')
    events=[]
    previous='0'*64
    for index,line in enumerate(raw.splitlines()):
        event=document(line)
        checksum=event.pop('eventSha256',None)
        require(event.get('sequence')==index and event.get('previousEventSha256')==previous and
                event.get('event') in ('PLAN_ATTEMPT','PLAN_HIGH_WATER') and checksum==sha(canonical(event)),
                'PLAN_HISTORY_INCOMPLETE')
        event['eventSha256']=checksum
        events.append(event)
        previous=checksum
    return events


def history_summary(plan_id,index_raw,artifacts,head,high):
    return {'planHistorySha256':sha(canonical({'planId':plan_id,'indexSha256':sha(index_raw),
                'artifacts':artifacts,'headExecutionSha256':head,'highWatermark':high})),
            'planHeadExecutionSha256':head,'planHighWatermark':high}


def read_plan_marker(path):
    # Windows字节锁禁止读取首字节；尾锚从偏移1读取，不跨越已持有的锁区间。
    try:
        with path.open('rb') as handle:
            handle.seek(1)
            value=handle.read(MAX_DOCUMENT+1)
        require(len(value)<=MAX_DOCUMENT,'PLAN_HISTORY_INCOMPLETE')
        return value
    except OSError:
        raise MigrationBlocked('PLAN_HISTORY_INCOMPLETE') from None


class PlanHistory:
    """持久计划索引加全部原生nonce日志，不允许按所选父日志降低高水位或跳过链尾。"""
    def __init__(self,directory,plan,review,keys):
        self.directory=Path(directory)
        self.plan=plan
        self.review=review
        self.keys=keys
        self.plan_id=sha(review.payload)
        self.path=self.directory/('.plan-'+self.plan_id+'.history.jsonl')
        self.marker_path=self.directory/('.plan-'+self.plan_id+'.lock')
        self.marker=read_plan_marker(self.marker_path)
        require(not self.path.is_symlink(),'PLAN_HISTORY_INCOMPLETE')
        self.raw=self.path.read_bytes() if self.path.is_file() else b''
        require(len(self.raw)<=MAX_DOCUMENT,'PLAN_HISTORY_INCOMPLETE')
        self.events=plan_index_events(self.raw)
        self.archives=[]
        self.head=None
        self.high=0
        seen={}
        raw_prefix=b''
        registry_high=0
        for event,line in zip(self.events,self.raw.splitlines(keepends=True)):
            require(event.get('planId')==self.plan_id,'PLAN_HISTORY_INCOMPLETE')
            if event['event']=='PLAN_ATTEMPT':
                try:
                    signed=SignedDocument(bytes.fromhex(event['executionPayloadHex']),bytes.fromhex(event['executionSignatureHex']))
                except (KeyError,ValueError,TypeError):
                    raise MigrationBlocked('PLAN_HISTORY_INCOMPLETE') from None
                grant=execution_document(keys,signed,review,plan)
                nonce=grant['nonce']
                binding=history_summary(self.plan_id,raw_prefix,self.archives,self.head,self.high)
                require(all(grant.get(key)==value for key,value in binding.items()) and nonce not in seen,
                        'PLAN_HISTORY_INCOMPLETE')
                require((self.head is None and grant['action']=='EXECUTE_ONCE') or
                        (self.head is not None and grant['action']=='RESUME_ONCE' and grant.get('parentExecutionSha256')==self.head),
                        'PLAN_HISTORY_INCOMPLETE')
                start=event.get('startPrefix')
                require(type(start) is int and self.high<=start<=len(plan['steps']) and
                        start==(grant.get('completedPrefix') if grant['action']=='RESUME_ONCE' else 0),
                        'PLAN_HISTORY_INCOMPLETE')
                receipt=self.directory/(nonce+'.json')
                journal=self.directory/(nonce+'.events.jsonl')
                require(receipt.is_file() and journal.is_file() and not receipt.is_symlink() and not journal.is_symlink(),
                        'PLAN_HISTORY_INCOMPLETE')
                native=read_journal(journal)
                prefix=validate_parent_journal(native,grant,signed,plan,review)
                report=document(read_small(receipt,MAX_DOCUMENT))
                require(report.get('nonce')==nonce and report.get('reviewedPlanSha256')==self.plan_id and
                        report.get('executionSha256')==sha(signed.payload),'PLAN_HISTORY_INCOMPLETE')
                archive={'nonce':nonce,'executionSha256':sha(signed.payload),
                         'journalSha256':sha(read_small(journal,MAX_DOCUMENT)),
                         'receiptSha256':sha(read_small(receipt,MAX_DOCUMENT))}
                self.archives.append(archive)
                self.head=sha(signed.payload)
                self.high=max(self.high,start,prefix)
                seen[nonce]=native
                registry_high=max(registry_high,start)
            else:
                prefix=event.get('verifiedPrefix')
                nonce=event.get('nonce')
                require(nonce in seen and type(prefix) is int and registry_high<=prefix<=len(plan['steps']) and
                        event.get('snapshotSha256')==plan['checkpoints'][prefix] and
                        any(row['event']=='STEP_VERIFIED' and row.get('stepIndex')==prefix-1 and
                            row.get('eventSha256')==event.get('journalEventSha256') for row in seen[nonce]),
                        'PLAN_HISTORY_INCOMPLETE')
                registry_high=prefix
                self.high=max(self.high,prefix)
            raw_prefix+=line
        # 遍历原生目录，任何同计划孤儿或被索引记录但缺失的日志都不能静默跳过。
        native_nonces=set()
        for path in self.directory.glob('*.events.jsonl'):
            rows=read_journal(path)
            if rows[0].get('reviewedPlanSha256')==self.plan_id:
                nonce=rows[0].get('nonce')
                require(isinstance(nonce,str) and path.name==nonce+'.events.jsonl','PLAN_HISTORY_INCOMPLETE')
                native_nonces.add(nonce)
        for path in self.directory.glob('*.json'):
            report=document(read_small(path,MAX_DOCUMENT))
            if report.get('reviewedPlanSha256')==self.plan_id:
                require(report.get('nonce') in seen and path.name==report['nonce']+'.json','PLAN_HISTORY_INCOMPLETE')
        require(native_nonces==set(seen),'PLAN_HISTORY_INCOMPLETE')
        if self.raw:
            require(bool(self.marker),'PLAN_HISTORY_INCOMPLETE')
            marker=document(self.marker)
            require(marker.get('planId')==self.plan_id and marker.get('indexSha256')==sha(self.raw) and
                    marker.get('headExecutionSha256')==self.head and type(marker.get('highWatermark')) is int and
                    0<=marker['highWatermark']<=self.high,'PLAN_HISTORY_INCOMPLETE')
        else:
            require(not self.marker,'PLAN_HISTORY_INCOMPLETE')
        self.binding=history_summary(self.plan_id,self.raw,self.archives,self.head,self.high)

    def check_archives(self):
        try:
            current=self.path.read_bytes() if self.path.is_file() else b''
            require(current==self.raw,'PLAN_HISTORY_CHANGED')
            require(read_plan_marker(self.marker_path)==self.marker,'PLAN_HISTORY_CHANGED')
            for archive in self.archives:
                nonce=archive['nonce']
                require(sha(read_small(self.directory/(nonce+'.json'),MAX_DOCUMENT))==archive['receiptSha256'] and
                        sha(read_small(self.directory/(nonce+'.events.jsonl'),MAX_DOCUMENT))==archive['journalSha256'],
                        'PLAN_HISTORY_CHANGED')
        except OSError:
            raise MigrationBlocked('PLAN_HISTORY_CHANGED') from None

    def append(self,event,**fields):
        self.check_archives()
        value={'event':event,'sequence':len(self.events),'planId':self.plan_id,
               'previousEventSha256':self.events[-1]['eventSha256'] if self.events else '0'*64,**fields}
        value['eventSha256']=sha(canonical(value))
        raw=canonical(value)+b'\n'
        with self.path.open('ab') as handle:
            handle.write(raw)
            handle.flush()
            os.fsync(handle.fileno())
        self.raw+=raw
        self.events.append(value)
        if event=='PLAN_ATTEMPT':
            self.head=sha(bytes.fromhex(fields['executionPayloadHex']))
            self.high=max(self.high,fields['startPrefix'])
        else:
            self.high=max(self.high,fields['verifiedPrefix'])
        # 锁文件首字节用于OS锁，其余为独立持久高水位/尾锚；删除或截断索引不能回到祖先。
        self.marker=canonical({'planId':self.plan_id,'indexSha256':sha(self.raw),
                               'headExecutionSha256':self.head,'highWatermark':self.high})
        with self.marker_path.open('r+b') as handle:
            handle.seek(1)
            handle.write(self.marker)
            handle.truncate()
            handle.flush()
            os.fsync(handle.fileno())

    def begin(self,signed,start):
        self.append('PLAN_ATTEMPT',startPrefix=start,executionPayloadHex=signed.payload.hex(),
                    executionSignatureHex=signed.signature.hex())

    def checkpoint(self,nonce,prefix,journal):
        self.append('PLAN_HIGH_WATER',nonce=nonce,verifiedPrefix=prefix,
                    snapshotSha256=self.plan['checkpoints'][prefix],journalEventSha256=journal.previous)


def inspect_plan_history(*,plan,reviewed_plan,keys,receipt_dir):
    require(keys.verify('review',reviewed_plan)==plan and plan.get('kind')=='stock-native-reviewed-plan-v3',
            'REVIEWED_PLAN_REJECTED')
    with plan_lock(receipt_dir,sha(reviewed_plan.payload)):
        return PlanHistory(receipt_dir,plan,reviewed_plan,keys).binding


def validate_history_binding(history,grant):
    require(all(grant.get(key)==value for key,value in history.binding.items()),'PLAN_HISTORY_GRANT_REJECTED')


def _run_steps(*,store_factory,plan,review,execution,grant,keys,files,active_maint,saved,receipt_dir,now,
               start_prefix=0,parent_journal_sha=None,parent_journal_path=None,plan_history=None):
    path=Path(receipt_dir)/(grant['nonce']+'.json')
    require(not path.exists(),'EXECUTION_ALREADY_CONSUMED')
    store=None
    locked=False
    attempted=False
    consumed=False
    journal=None
    prefix=start_prefix
    uncertain=None
    known_applied=[]
    def check_parent():
        if parent_journal_path is not None:
            try:
                current_hash=sha(read_small(parent_journal_path,MAX_DOCUMENT))
            except MigrationBlocked:
                raise MigrationBlocked('PARENT_JOURNAL_CHANGED') from None
            require(current_hash==parent_journal_sha,'PARENT_JOURNAL_CHANGED')
        plan_history.check_archives()
    report={'schemaVersion':3,'status':'INTENT','nonce':grant['nonce'],'scope':AUTHORIZATION_SCOPE,
        'executionSha256':sha(execution.payload),'reviewedPlanSha256':sha(review.payload),
        'verifiedPrefix':prefix,'knownAppliedSteps':known_applied,'uncertainStep':None,
        'sourceCertification':'NOT_PERFORMED','historicalPrecisionRecovered':False,
        'automaticRollback':'FORBIDDEN','ddlAttempted':False}
    try:
        store=store_factory(plan['target'])
        observe_target(store,plan['target'],execution=True)
        before=capture_snapshot(store,plan['nullPolicy'])
        require(before['snapshotSha256']==plan['checkpoints'][start_prefix],
                'RECOVERY_STATE_NOT_A_SIGNED_CHECKPOINT' if grant['action']=='RESUME_ONCE' else 'SNAPSHOT_CHANGED')
        check_window(active_maint,now())
        store.configure_execution()
        store.lock()
        locked=True
        locked_snapshot=capture_snapshot(store,plan['nullPolicy'])
        require(locked_snapshot['snapshotSha256']==before['snapshotSha256'],'SNAPSHOT_CHANGED')
        check_parent()
        observe_target(store,plan['target'],execution=True)
        check_window(active_maint,now())
        validate_execution(keys,execution,review,plan,plan['target'],now())
        evidence_matches(active_maint,saved,files)
        path.parent.mkdir(parents=True,exist_ok=True)
        plan_history.begin(execution,start_prefix)
        write_receipt(path,report,exclusive=True)
        consumed=True
        journal=MigrationJournal(path.with_name(grant['nonce']+'.events.jsonl'),
            {'schemaVersion':3,'nonce':grant['nonce'],'executionSha256':sha(execution.payload),
             'reviewedPlanSha256':sha(review.payload),'scope':AUTHORIZATION_SCOPE,
             'stepsSha256':sha(canonical(plan['steps'])),'startPrefix':start_prefix,
             'initialSnapshotSha256':before['snapshotSha256'],'parentJournalSha256':parent_journal_sha})
        for index in range(start_prefix,len(plan['steps'])):
            step=plan['steps'][index]
            current=capture_snapshot(store,plan['nullPolicy'])
            require(current['snapshotSha256']==plan['checkpoints'][index],'SNAPSHOT_CHANGED')
            observe_target(store,plan['target'],execution=True)
            check_window(active_maint,now())
            validate_execution(keys,execution,review,plan,plan['target'],now())
            evidence_matches(active_maint,saved,files)
            require(all(plan.get(key)==value for key,value in artifact_hashes().items()),'ARTIFACT_MISMATCH')
            check_parent()
            journal.append('DDL_INTENT' if step['ddl'] else 'STEP_NATIVE_NOOP',stepIndex=index,
                ddlSha256=step['ddlSha256'],beforeSnapshotSha256=current['snapshotSha256'],
                expectedAfterSnapshotSha256=plan['checkpoints'][index+1])
            if step['ddl']:
                # 日志已持久化才发送；DDL未知结果绝不能用ROLLBACK作成功恢复依据。
                report.update(uncertainStep=index,status='DDL_INTENT_DURABLE')
                write_receipt(path,report)
                check_window(active_maint,now())
                validate_execution(keys,execution,review,plan,plan['target'],now())
                evidence_matches(active_maint,saved,files)
                observe_target(store,plan['target'],execution=True)
                check_parent()
                check_window(active_maint,now())
                validate_execution(keys,execution,review,plan,plan['target'],now())
                attempted=True
                uncertain=index
                report.update(ddlAttempted=True,uncertainStep=index,status='DDL_OUTCOME_UNCERTAIN')
                store.alter(step['ddl'])
                journal.append('DDL_RESPONSE',stepIndex=index)
                # 两表DDL不原子，ALTER可能解除锁；独立维护围栏覆盖重获两表WRITE锁的间隙。
                store.lock()
            try:
                after=capture_snapshot(store,plan['nullPolicy'])
            except MigrationBlocked:
                raise MigrationBlocked('POST_DDL_PRESERVATION_FAILED') from None
            require(after['snapshotSha256']==plan['checkpoints'][index+1],'POST_DDL_PRESERVATION_FAILED')
            observe_target(store,plan['target'],execution=True)
            check_window(active_maint,now())
            validate_execution(keys,execution,review,plan,plan['target'],now())
            evidence_matches(active_maint,saved,files)
            check_window(active_maint,now())
            validate_execution(keys,execution,review,plan,plan['target'],now())
            journal.append('STEP_VERIFIED',stepIndex=index,snapshotSha256=after['snapshotSha256'])
            plan_history.checkpoint(grant['nonce'],index+1,journal)
            prefix=index+1
            if step['ddl']:
                known_applied.append(index)
            uncertain=None
            report.update(verifiedPrefix=prefix,uncertainStep=None,status='PARTIAL_APPLIED' if known_applied else 'NOOP_PREFIX_VERIFIED')
            write_receipt(path,report)
        final=capture_snapshot(store,plan['nullPolicy'])
        require(final['snapshotSha256']==plan['checkpoints'][-1],'POST_DDL_PRESERVATION_FAILED')
        gaps=metadata_precision_gaps(final['metadata'])
        require(not gaps,'POST_DDL_PRESERVATION_FAILED')
        observe_target(store,plan['target'],execution=True)
        evidence_matches(active_maint,saved,files)
        check_parent()
        check_window(active_maint,now())
        validate_execution(keys,execution,review,plan,plan['target'],now())
        report.update(status='MIGRATED_SOURCE_UNCERTIFIED',afterSnapshotSha256=final['snapshotSha256'],
            remainingSourcePrecisionGaps=gaps,sourcePrecisionStatus='NATIVE_METADATA_ONLY',
            missingStockDoesNotProveZero=True,businessDataWrites=0)
        journal.append('SUCCESS',verifiedPrefix=prefix,snapshotSha256=final['snapshotSha256'])
        write_receipt(path,report)
        return report
    except BaseException as error:
        code=error.code if isinstance(error,MigrationBlocked) else 'EXECUTION_INTERRUPTED' if isinstance(error,KeyboardInterrupt) else 'EXECUTION_FAILED'
        if attempted and code!='POST_DDL_PRESERVATION_FAILED':
            code='PARTIAL_APPLIED_DDL_OUTCOME_UNCERTAIN' if known_applied else 'DDL_OUTCOME_UNCERTAIN'
        state='PARTIAL_APPLIED_UNVERIFIED' if known_applied else 'DDL_OUTCOME_UNCERTAIN' if attempted else 'BLOCKED_BEFORE_DDL'
        if consumed:
            report.update(status=code,migrationState=state,verifiedPrefix=prefix,uncertainStep=uncertain,
                          ddlAttempted=attempted,recoveryRequired=True)
            try:
                if journal:
                    journal.append('INTERRUPTED' if isinstance(error,KeyboardInterrupt) else 'FAILED',
                                   code=code,verifiedPrefix=prefix,uncertainStep=uncertain)
                write_receipt(path,report)
            except MigrationBlocked:
                pass
        blocked=MigrationBlocked(code,ddl_attempted=attempted)
        blocked.migration_state=state
        blocked.verified_prefix=prefix
        raise blocked from None
    finally:
        if store is not None and cleanup_store(store,locked=locked):
            code='DDL_OUTCOME_UNCERTAIN' if attempted else 'EXECUTION_CLEANUP_FAILED'
            if consumed:
                report.update(status=code,migrationState='APPLIED_CLEANUP_UNVERIFIED' if prefix==len(plan['steps']) else 'CLEANUP_UNVERIFIED',
                              verifiedPrefix=prefix,uncertainStep=uncertain,recoveryRequired=True)
                try:
                    if journal:
                        journal.append('FAILED',code=code,verifiedPrefix=prefix,uncertainStep=uncertain)
                    write_receipt(path,report)
                except MigrationBlocked:
                    pass
            blocked=MigrationBlocked(code,ddl_attempted=attempted)
            blocked.migration_state=report.get('migrationState','CLEANUP_UNVERIFIED')
            blocked.verified_prefix=prefix
            raise blocked from None


def execute_once(*,store_factory,maintenance,backup,reviewed_plan,execution,keys,files,receipt_dir,
                 now=lambda:datetime.now(timezone.utc)):
    require_migration_input()
    maint,saved=validate_context(maintenance,backup,keys,files,now())
    plan=verify_reviewed_plan(keys,reviewed_plan,maint,saved,maintenance,backup)
    grant=validate_execution(keys,execution,reviewed_plan,plan,maint['target'],now())
    require(grant['action']=='EXECUTE_ONCE','EXECUTION_GRANT_REJECTED')
    require(plan['status']=='PRECHECK_READY_REVIEW_REQUIRED','ALREADY_NATIVE')
    require(not (Path(receipt_dir)/(grant['nonce']+'.json')).exists(),'EXECUTION_ALREADY_CONSUMED')
    with plan_lock(receipt_dir,sha(reviewed_plan.payload)):
        history=PlanHistory(receipt_dir,plan,reviewed_plan,keys)
        validate_history_binding(history,grant)
        require(history.head is None,'PLAN_ALREADY_STARTED')
        return _run_steps(store_factory=store_factory,plan=plan,review=reviewed_plan,execution=execution,grant=grant,keys=keys,
            files=files,active_maint=maint,saved=saved,receipt_dir=receipt_dir,now=now,plan_history=history)


def resume_once(*,store_factory,maintenance,backup,reviewed_plan,parent_execution,recovery_maintenance,execution,
                keys,files,receipt_dir,now=lambda:datetime.now(timezone.utc)):
    require_migration_input()
    # 原计划与备份仍须认证；只有恢复维护签名可开新窗口，过期原授权不提供任何执行权。
    maint,saved=validate_original_contracts(maintenance,backup,keys,now())
    plan=verify_reviewed_plan(keys,reviewed_plan,maint,saved,maintenance,backup)
    parent=execution_document(keys,parent_execution,reviewed_plan,plan)
    journal_path=Path(receipt_dir)/(parent['nonce']+'.events.jsonl')
    recovery=keys.verify('review',recovery_maintenance)
    require(recovery.get('kind')=='stock-native-recovery-maintenance-v3' and recovery.get('scope')==AUTHORIZATION_SCOPE and
            recovery.get('target')==plan['target'] and recovery.get('nullPolicy')==plan['nullPolicy'] and
            recovery.get('originalReviewedPlanSha256')==sha(reviewed_plan.payload) and
            recovery.get('backupReceiptSha256')==plan['backupReceiptSha256'] and
            recovery.get('fenceStrategy')==maint['fenceStrategy'] and all(recovery.get(key)==value for key,value in artifact_hashes().items()),
            'RECOVERY_MAINTENANCE_REJECTED')
    check_window(recovery,now())
    evidence_matches(recovery,saved,files)
    grant=validate_execution(keys,execution,reviewed_plan,plan,maint['target'],now())
    prefix=grant.get('completedPrefix')
    require(grant['action']=='RESUME_ONCE' and grant['nonce']!=parent['nonce'] and type(prefix) is int and
            0<=prefix<=len(plan['steps']) and grant.get('parentExecutionSha256')==sha(parent_execution.payload) and
            is_hash(grant.get('parentJournalSha256')) and grant.get('observedSnapshotSha256')==plan['checkpoints'][prefix] and
            grant.get('remainingStepsSha256')==sha(canonical(plan['steps'][prefix:])) and
            grant.get('recoveryMaintenanceSha256')==sha(recovery_maintenance.payload),'RECOVERY_GRANT_REJECTED')
    require(not (Path(receipt_dir)/(grant['nonce']+'.json')).exists(),'EXECUTION_ALREADY_CONSUMED')
    with plan_lock(receipt_dir,sha(reviewed_plan.payload)):
        history=PlanHistory(receipt_dir,plan,reviewed_plan,keys)
        require(history.head==sha(parent_execution.payload),'PLAN_PARENT_NOT_HEAD')
        # 所选父日志也在同一原生计划锁下读取；不能按锁前或祖先视图继续执行。
        journal_hash=history.archives[-1]['journalSha256']
        require(grant['parentJournalSha256']==journal_hash,'RECOVERY_GRANT_REJECTED')
        validate_history_binding(history,grant)
        require(prefix>=history.high,'PLAN_HIGH_WATERMARK_VIOLATION')
        return _run_steps(store_factory=store_factory,plan=plan,review=reviewed_plan,execution=execution,grant=grant,keys=keys,
            files=files,active_maint=recovery,saved=saved,receipt_dir=receipt_dir,now=now,
            start_prefix=prefix,parent_journal_sha=journal_hash,parent_journal_path=journal_path,plan_history=history)




class MySQLStore:
    """5.7实际执行适配器；仅prepared二进制游标可读取原数量，禁止文本回退。"""
    def __init__(self, target):
        require_migration_input()
        validate_target(target)
        try:
            import mysql.connector
        except ImportError:
            raise MigrationBlocked('MYSQL_BINARY_DRIVER_MISSING') from None
        user = os.environ.get('NEXUS_STOCK_MIGRATION_DB_USER')
        password = os.environ.get('NEXUS_STOCK_MIGRATION_DB_PASSWORD')
        require(isinstance(user, str) and bool(user) and isinstance(password, str) and bool(password),
                'DATABASE_CREDENTIALS_REQUIRED')
        self.target = target
        try:
            self.connection = mysql.connector.connect(host=target['host'], port=target['port'], database=target['schema'],
                user=user, password=password, use_pure=True, autocommit=True, connection_timeout=5,
                allow_local_infile=False, consume_results=False, charset='utf8mb4')
        except Exception:
            raise MigrationBlocked('DATABASE_CONNECTION_FAILED') from None

    def query(self, sql, args=None):
        cursor = self.connection.cursor(buffered=True)
        try:
            cursor.execute(sql, args)
            return cursor.fetchall() if cursor.with_rows else []
        finally:
            cursor.close()

    def observe_identity(self):
        row=self.query('SELECT DATABASE(),@@port,@@server_uuid,@@server_id,@@hostname,VERSION(),'
                       '@@GLOBAL.read_only,@@GLOBAL.super_read_only,@@GLOBAL.event_scheduler')[0]
        count=self.replication_appliers()
        return {'schema':row[0],'host':self.target['host'],'port':row[1],'serverUuid':row[2],
                'serverId':row[3],'serverHostnameSha256':sha(row[4].encode('utf-8')),'version':row[5],
                'readOnly':int(row[6]),'superReadOnly':int(row[7]),'eventScheduler':row[8],
                'replicationApplierCount':count,'replicationInventory':self.replication_inventory}

    def dictionary_query(self,sql,args=None):
        cursor=self.connection.cursor(dictionary=True,buffered=True)
        try:
            cursor.execute(sql,args)
            return cursor.fetchall()
        finally:
            cursor.close()

    def replication_appliers(self):
        """原生清点全部通道/worker/协调者/group；UNKNOWN绝不按零写线程处理。"""
        try:
            enabled=self.query('SELECT @@GLOBAL.performance_schema')
            require(enabled==[(1,)],'REPLICATION_INVENTORY_UNKNOWN')
            plugins=self.dictionary_query("SELECT PLUGIN_NAME,PLUGIN_STATUS FROM information_schema.PLUGINS WHERE PLUGIN_NAME='group_replication'")
            group_variables=self.dictionary_query("SHOW GLOBAL VARIABLES LIKE 'group_replication%'")
            instruments=self.dictionary_query("SELECT NAME,ENABLED FROM performance_schema.setup_instruments WHERE NAME LIKE 'thread/%'")
            relevant={row['NAME']:row['ENABLED'] for row in instruments if
                      any(word in row['NAME'].lower() for word in ('slave','replica','group_rpl','group_replication'))}
            require({'thread/sql/slave_sql','thread/sql/slave_worker','thread/sql/slave_io'}<=set(relevant) and
                    all(value=='YES' for value in relevant.values()),'REPLICATION_INVENTORY_UNKNOWN')
            configuration=self.dictionary_query('SELECT CHANNEL_NAME FROM performance_schema.replication_connection_configuration')
            connections=self.dictionary_query('SELECT CHANNEL_NAME,SERVICE_STATE FROM performance_schema.replication_connection_status')
            appliers=self.dictionary_query('SELECT CHANNEL_NAME,SERVICE_STATE FROM performance_schema.replication_applier_status')
            coordinators=self.dictionary_query('SELECT CHANNEL_NAME,THREAD_ID,SERVICE_STATE FROM performance_schema.replication_applier_status_by_coordinator')
            workers=self.dictionary_query('SELECT CHANNEL_NAME,WORKER_ID,THREAD_ID,SERVICE_STATE FROM performance_schema.replication_applier_status_by_worker')
            threads=self.dictionary_query("SELECT THREAD_ID,NAME,INSTRUMENTED FROM performance_schema.threads WHERE TYPE='BACKGROUND'")
            ordinary=self.dictionary_query('SHOW SLAVE STATUS')
            channels={row['CHANNEL_NAME'] for row in configuration}
            require(len(channels)==len(configuration) and all(isinstance(name,str) for name in channels),
                    'REPLICATION_INVENTORY_UNKNOWN')
            require({row['CHANNEL_NAME'] for row in connections}==channels and
                    {row['CHANNEL_NAME'] for row in appliers}==channels,'REPLICATION_INVENTORY_UNKNOWN')
            for rows in (connections,appliers,coordinators,workers):
                for row in rows:
                    require(row.get('CHANNEL_NAME') in channels and row.get('SERVICE_STATE') in ('ON','OFF','CONNECTING'),
                            'REPLICATION_INVENTORY_UNKNOWN')
                    require(row['SERVICE_STATE']=='OFF' and row.get('THREAD_ID') is None,'WRITER_FENCE_MISSING')
            for row in threads:
                require(isinstance(row.get('NAME'),str),'REPLICATION_INVENTORY_UNKNOWN')
                require(not any(word in row['NAME'].lower() for word in ('slave','replica','group_rpl','group_replication')),
                        'WRITER_FENCE_MISSING')
            require(all(row.get('Slave_SQL_Running')=='No' and row.get('Slave_IO_Running')=='No' and
                        row.get('Channel_Name') in channels for row in ordinary),'WRITER_FENCE_MISSING')
            members=[]
            stats=[]
            if not plugins:
                # 原生插件清单与变量查询均成功且不存在，才允许普通5.7的已知NOT_INSTALLED。
                require(not group_variables and not any('group_replication' in name for name in channels),
                        'GROUP_REPLICATION_STATUS_UNKNOWN')
                group_state='NOT_INSTALLED'
            else:
                require(len(plugins)==1 and plugins[0].get('PLUGIN_NAME')=='group_replication' and
                        plugins[0].get('PLUGIN_STATUS')=='ACTIVE' and bool(group_variables) and
                        any(name.startswith('thread/group_rpl/') for name in relevant),
                        'GROUP_REPLICATION_STATUS_UNKNOWN')
                members=self.dictionary_query('SELECT CHANNEL_NAME,MEMBER_ID,MEMBER_STATE FROM performance_schema.replication_group_members')
                stats=self.dictionary_query('SELECT CHANNEL_NAME,MEMBER_ID FROM performance_schema.replication_group_member_stats')
                server_uuid=self.query('SELECT @@server_uuid')[0][0]
                require(members and any(row.get('MEMBER_ID')==server_uuid for row in members) and
                        all(row.get('MEMBER_STATE')=='OFFLINE' for row in members), 'GROUP_REPLICATION_STATUS_UNKNOWN')
                require(all((row.get('CHANNEL_NAME'),row.get('MEMBER_ID')) in
                            {(member.get('CHANNEL_NAME'),member.get('MEMBER_ID')) for member in members} for row in stats),
                        'GROUP_REPLICATION_STATUS_UNKNOWN')
                group_state='KNOWN_OFFLINE'
            facts={'configuration':configuration,'connections':connections,'appliers':appliers,'coordinators':coordinators,
                   'workers':workers,'replicationInstruments':relevant,'groupPlugins':plugins,'groupMembers':members,
                   'groupStats':stats,'groupState':group_state}
            self.replication_inventory={'status':'COMPLETE','activeApplierCount':0,'groupState':group_state,
                                        'inventorySha256':sha(canonical(facts))}
            return 0
        except MigrationBlocked:
            raise
        except Exception:
            raise MigrationBlocked('REPLICATION_INVENTORY_UNKNOWN') from None

    def describe(self):
        directory_names={table:native_directory_name(self.target['schema'],table) for table in (STOCK,TX)}
        # information_schema按权限过滤；只读空列表不证明没有外部依赖，必须先核全局可见权限。
        try:
            grants=self.query('SHOW GRANTS')
            privileges=set()
            for row in grants:
                require(isinstance(row,(tuple,list)) and len(row)==1 and isinstance(row[0],str),
                        'DEPENDENCY_VISIBILITY_UNKNOWN')
                match=re.match(r'(?i)^GRANT (.+?) ON \*\.\* TO ',row[0])
                if match:
                    privileges.update(value.strip().upper() for value in match.group(1).split(','))
            require('ALL PRIVILEGES' in privileges or {'SELECT','TRIGGER'}<=privileges,
                    'DEPENDENCY_VISIBILITY_UNKNOWN')
        except MigrationBlocked:
            raise
        except Exception:
            raise MigrationBlocked('DEPENDENCY_VISIBILITY_UNKNOWN') from None
        result = {}
        for table in (STOCK, TX):
            tables = self.query('SELECT ENGINE,TABLE_COLLATION,ROW_FORMAT,CREATE_OPTIONS FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s '
                                "AND TABLE_NAME=%s AND TABLE_TYPE='BASE TABLE'", ('ry-erp', table))
            require(len(tables) == 1, 'METADATA_REQUIRED')
            raw_columns = self.query('SELECT COLUMN_NAME,DATA_TYPE,NUMERIC_PRECISION,NUMERIC_SCALE,IS_NULLABLE,'
                                     'COLUMN_DEFAULT,COLUMN_COMMENT,EXTRA,COLUMN_TYPE,GENERATION_EXPRESSION '
                                     'FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s '
                                     'ORDER BY ORDINAL_POSITION', ('ry-erp', table))
            columns = [{'name': r[0], 'type': r[1], 'precision': r[2], 'scale': r[3], 'nullable': r[4] == 'YES',
                        'default':r[5],'comment':r[6],'extra':r[7],'columnType':r[8],'generationExpression':r[9]} for r in raw_columns]
            indexes = self.query('SELECT INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART,INDEX_TYPE '
                                 'FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s '
                                 'ORDER BY INDEX_NAME,SEQ_IN_INDEX', ('ry-erp', table))
            primary = [r[3] for r in indexes if r[0] == 'PRIMARY']
            foreign = self.query('SELECT CONSTRAINT_NAME,COLUMN_NAME,ORDINAL_POSITION,REFERENCED_TABLE_SCHEMA,'
                                 'REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE '
                                 'WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s AND REFERENCED_TABLE_NAME IS NOT NULL '
                                 'ORDER BY CONSTRAINT_NAME,ORDINAL_POSITION', ('ry-erp', table))
            triggers = self.query('SELECT TRIGGER_NAME,EVENT_MANIPULATION,ACTION_TIMING,ACTION_STATEMENT,SQL_MODE,'
                                  'CHARACTER_SET_CLIENT,COLLATION_CONNECTION,DATABASE_COLLATION '
                                  'FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA=%s AND EVENT_OBJECT_TABLE=%s '
                                  'ORDER BY TRIGGER_NAME', ('ry-erp', table))
            incoming=self.query('SELECT TABLE_SCHEMA,TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_COLUMN_NAME '
                'FROM information_schema.KEY_COLUMN_USAGE WHERE REFERENCED_TABLE_SCHEMA=%s AND REFERENCED_TABLE_NAME=%s '
                'ORDER BY TABLE_SCHEMA,TABLE_NAME,CONSTRAINT_NAME,ORDINAL_POSITION',('ry-erp',table))
            definition=self.query(f'SHOW CREATE TABLE `ry-erp`.`{table}`')
            require(len(definition)==1 and len(definition[0])==2 and isinstance(definition[0][1],str), 'STORAGE_ATTRIBUTES_UNKNOWN')
            closing=definition[0][1].rfind(') ENGINE=')
            require(closing>=0,'STORAGE_ATTRIBUTES_UNKNOWN')
            physical=self.query('SELECT t.ROW_FORMAT,t.SPACE_TYPE,t.ZIP_PAGE_SIZE,s.PAGE_SIZE,s.SPACE_TYPE,s.ZIP_PAGE_SIZE '
                'FROM information_schema.INNODB_SYS_TABLES t JOIN information_schema.INNODB_SYS_TABLESPACES s ON t.SPACE=s.SPACE '
                'WHERE BINARY t.NAME=BINARY %s',(directory_names[table],))
            file_per_table=self.query('SELECT @@GLOBAL.innodb_file_per_table')
            require(len(physical)==1 and len(physical[0])==6 and physical[0][1]==physical[0][4] and
                    physical[0][2]==physical[0][5] and file_per_table==[(1,)],'STORAGE_ATTRIBUTES_UNKNOWN')
            result[table]={'engine':tables[0][0],'columns':columns,'primaryKey':primary,
                'tableOptions':list(tables[0][1:]),'foreignKeys':[list(r) for r in foreign],
                'inboundForeignKeys':[list(r) for r in incoming],'triggers':[list(r) for r in triggers],
                'indexes':[list(r) for r in indexes], 'tableDefinitionOptions':definition[0][1][closing+2:],
                'physicalStorage':{'rowFormat':physical[0][0],'spaceType':physical[0][1],
                                   'zipPageSize':physical[0][2],'pageSize':physical[0][3]}}
        return result

    def iter_rows(self, table):
        require(table in (STOCK, TX), 'UNAPPROVED_TABLE')
        primary = 'stock_id' if table == STOCK else 'tx_id'
        cursor = self.connection.cursor(prepared=True)
        try:
            # 不加租户或删除过滤：类型变更覆盖全表，必须核全部原行。
            cursor.execute(f'SELECT * FROM `ry-erp`.`{table}` ORDER BY `{primary}`')
            names = [item[0] for item in cursor.description]
            for row in cursor:
                yield dict(zip(names, row))
        finally:
            cursor.close()

    def begin_readonly(self):
        self.query('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ')
        self.query('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')

    def configure_execution(self):
        modes = self.query('SELECT @@SESSION.sql_mode')[0][0].split(',')
        modes = sorted(set(modes) | {'STRICT_ALL_TABLES', 'NO_BACKSLASH_ESCAPES'})
        self.query('SET SESSION sql_mode=%s', (','.join(modes),))
        self.query('SET SESSION lock_wait_timeout=10')

    def lock(self):
        self.query('LOCK TABLES `ry-erp`.`wm_material_stock` WRITE, `ry-erp`.`wm_material_stock_tx` WRITE')

    def unlock(self):
        self.query('UNLOCK TABLES')

    def alter(self, sql):
        self.query(sql)
        warnings = self.query('SHOW WARNINGS')
        require(not warnings, 'DDL_WARNINGS_REQUIRE_REVIEW')

    def close(self):
        self.connection.close()


def private_output(path):
    path = Path(path).resolve()
    require(PRIVATE_ROOT.resolve() in path.parents, 'PRIVATE_OUTPUT_REQUIRED')
    return path


def read_small(path, maximum):
    try:
        path = Path(path)
        require(path.is_file() and 0 < path.stat().st_size <= maximum, 'INPUT_FILE_REJECTED')
        return path.read_bytes()
    except OSError:
        raise MigrationBlocked('INPUT_FILE_REJECTED') from None


def main(argv=None):
    class SafeParser(argparse.ArgumentParser):
        def error(self, message):
            raise MigrationBlocked('CLI_ARGUMENTS_REJECTED')
    parser = SafeParser(description='独立签名授权的库存原生精度一次性迁移守卫', exit_on_error=False)
    parser.add_argument('operation', choices=('precheck', 'execute-once', 'resume-once'))
    for role in ('backup', 'review', 'execution'):
        parser.add_argument('--' + role + '-public-key', required=True)
        parser.add_argument('--' + role + '-key-sha256', required=True)
    for name in ('maintenance', 'backup-receipt'):
        parser.add_argument('--' + name, required=True)
        parser.add_argument('--' + name + '-signature', required=True)
    for name in ('backup-artifact', 'restore-evidence', 'fence-evidence', 'output'):
        parser.add_argument('--' + name, required=True)
    for name in ('reviewed-plan', 'execution-grant', 'recovery-maintenance', 'parent-execution-grant'):
        parser.add_argument('--' + name)
        parser.add_argument('--' + name + '-signature')
    try:
        require_migration_input()
        args = parser.parse_args(argv)
        if args.operation=='resume-once':
            require(all((args.reviewed_plan,args.reviewed_plan_signature,args.execution_grant,args.execution_grant_signature,
                         args.recovery_maintenance,args.recovery_maintenance_signature,
                         args.parent_execution_grant,args.parent_execution_grant_signature)),'RECOVERY_AUTHORITY_REQUIRED')
        keys = AuthorityKeys({role: (read_small(getattr(args, role + '_public_key'), 32),
                                     getattr(args, role + '_key_sha256')) for role in ('backup', 'review', 'execution')})
        def signed(name):
            stem = name.replace('-', '_')
            return SignedDocument(read_small(getattr(args, stem), MAX_DOCUMENT),
                                  read_small(getattr(args, stem + '_signature'), 64))
        common = {'store_factory': MySQLStore, 'maintenance': signed('maintenance'),
                  'backup': signed('backup-receipt'), 'keys': keys,
                  'files': EvidenceFiles(Path(args.backup_artifact), Path(args.restore_evidence), Path(args.fence_evidence))}
        output = private_output(args.output)
        require(not output.exists(), 'OUTPUT_ALREADY_EXISTS')
        if args.operation=='precheck':
            report=precheck(**common)
        elif args.operation=='execute-once':
            require(all((args.reviewed_plan,args.reviewed_plan_signature,args.execution_grant,args.execution_grant_signature)),
                    'INDEPENDENT_EXECUTION_GRANT_REQUIRED')
            report=execute_once(**common,reviewed_plan=signed('reviewed-plan'),execution=signed('execution-grant'),
                                receipt_dir=private_output(PRIVATE_ROOT/'spent'/'anchor').parent)
        else:
            report=resume_once(**common,reviewed_plan=signed('reviewed-plan'),execution=signed('execution-grant'),
                parent_execution=signed('parent-execution-grant'),recovery_maintenance=signed('recovery-maintenance'),
                receipt_dir=private_output(PRIVATE_ROOT/'spent'/'anchor').parent)
        output.parent.mkdir(parents=True, exist_ok=True)
        write_receipt(output, report, exclusive=True)
        print(json.dumps({'status': report['status'], 'sourceCertification': 'NOT_PERFORMED',
                          'outputPath': str(output)}, ensure_ascii=True))
        return 0
    except (MigrationBlocked, argparse.ArgumentError, OSError, ValueError, TypeError) as error:
        print(json.dumps({'status': 'BLOCKED', 'reason': error.code if isinstance(error, MigrationBlocked) else 'INPUT_REJECTED',
                          'ddlAttempted': bool(getattr(error, 'ddl_attempted', False)), 'sourceCertification': 'NOT_PERFORMED',
                          'migrationState':getattr(error,'migration_state','NOT_STARTED'),
                          'verifiedPrefix':getattr(error,'verified_prefix',None)}))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
