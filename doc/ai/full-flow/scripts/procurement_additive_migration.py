"""已获用户授权的本地测试库追加结构执行器；不授予任何正式业务写或来源认证资格。

固定七个已审查增量输入，只能执行十二条CREATE/追加可空列/唯一键及新空证据表说明DDL。
拒绝部分基线的自动重放、历史DML、数量类型改写；多步跨库DDL没有回滚保证。
连接、备份声明和用户授权由显式调用方核实，不自动连接/登录/启停服务或提供通用apply入口。
"""
from dataclasses import dataclass
from datetime import date,datetime
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import re
from procurement_schema_contract import schema_evidence
from procurement_runtime_preflight import ROOT,TABLES,SCHEMAS,_read_schema_metadata

INPUTS=(
 ('2026-10-02-certified-purchase-command-ry-erp.sql','23eb532ba1baf344b513103ee9aadc3d755921071f395733857e16026c2b9fb4'),
 ('2026-10-02-certified-allocation-event-sequence-ry-erp.sql','dae8d83e1e47c37c77f0d5e729ff2bbeec7397a34bfcf37f55b4e7705d8b598c'),
 ('2026-10-03-certified-purchase-order-terms-ry-erp.sql','f0020e3d871176edc1250f221b8d7ac6df194d37ab4aab320639b556885d7dda'),
 ('2026-10-03-certified-iqc-evidence-ry-erp.sql','4090f7368ce9fc53357080ac018100cfc2d5cf417377158c989d8b808bf2036a'),
 ('2026-10-03-certified-receipt-posting-ry-erp.sql','11fea389f302cc81ce05c7ceb847d8adc916afec3b562856e6971b2f484db737'),
 ('2026-10-03-native-iqc-birth-ry-erp.sql','d8385a167000844ea606c4be409185410cf95aa27a65250c665a9a05ed35aa0f'),
 ('2026-10-03-native-iqc-birth-ry-mes.sql','ee8b5e9048c96471b3a7f376fe723756055ed818c18ad00284f5dc4ad59fbc60'))
NEW_TABLES=frozenset(('ry-erp.pu_certified_purchase_command','ry-erp.pu_certified_purchase_order_terms',
 'ry-erp.pu_certified_purchase_order_terms_confirmation','ry-erp.pu_certified_purchase_order_operation',
 'ry-erp.wm_certified_iqc_evidence','ry-erp.wm_certified_receipt_posting','ry-erp.wm_receipt_iqc_birth','ry-mes.qc_receipt_iqc_birth'))
OLD_TABLES=('ry-erp.pu_purchase_order','ry-erp.pu_requirement_allocation_event')
LOCK_NAME='nexus_local_procurement_additive_v1'
PRECISION_KEYS=frozenset(('ry-erp.wm_material_stock.stock_quantity','ry-erp.wm_material_stock.reserved_quantity',
 'ry-erp.wm_material_stock_tx.tx_quantity','ry-erp.wm_material_stock_tx.before_quantity','ry-erp.wm_material_stock_tx.after_quantity',
 'ry-erp.wm_material_receive_line.quantity_received','ry-erp.wm_material_receive_detail.quantity'))
EXPECTED_GAPS=frozenset([key+': table unavailable' for key in NEW_TABLES]+[
 'ry-erp.pu_purchase_order.certified_version: column unavailable','ry-erp.pu_purchase_order.certified_terms_id: column unavailable',
 'ry-erp.pu_requirement_allocation_event.event_sequence: column unavailable',
 'ry-erp.pu_requirement_allocation_event.uk_prae_tenant_allocation_sequence: full unique BTREE key unavailable'])

class AdditiveMigrationBlocked(RuntimeError):
    """仅固定分类，不复制底层SQL、凭证、真实业务行或异常正文。"""
    def __init__(self,category):super().__init__(category);self.category=category

def require(condition,category):
    if not condition:raise AdditiveMigrationBlocked(category)

def sha(raw):return hashlib.sha256(raw).hexdigest()

def read_source(name):
    require(name in dict(INPUTS),'MIGRATION_INPUT_REJECTED')
    return (ROOT/'script/sql/update'/name).read_bytes()

def split_sql(text):
    """仅为固定DDL解析引号和分号；可执行块注释或不闭合内容一律拒绝。"""
    require(isinstance(text,str) and len(text)<200000,'MIGRATION_SQL_REJECTED')
    out=[];buf=[];quote=None;at=0
    while at<len(text):
        char=text[at]
        if quote:
            buf.append(char)
            if char=='\\' and quote!='`':
                at+=1;require(at<len(text),'MIGRATION_SQL_REJECTED');buf.append(text[at])
            elif char==quote:
                if at+1<len(text) and text[at+1]==quote:at+=1;buf.append(text[at])
                else:quote=None
        elif text.startswith('/*',at):raise AdditiveMigrationBlocked('MIGRATION_SQL_REJECTED')
        elif text.startswith('--',at) and (at+2==len(text) or text[at+2].isspace()):
            end=text.find('\n',at);at=len(text) if end<0 else end;buf.append('\n');continue
        elif char in ("'",'"','`'):quote=char;buf.append(char)
        elif char==';':
            if ''.join(buf).strip():out.append(''.join(buf).strip())
            buf=[]
        else:buf.append(char)
        at+=1
    require(quote is None,'MIGRATION_SQL_REJECTED')
    if ''.join(buf).strip():out.append(''.join(buf).strip())
    return out

@dataclass(frozen=True)
class Operation:
    ordinal:int
    schema:str
    table:str
    kind:str
    source:str
    source_sha256:str
    sql:str


def load_operations():
    """源码必须与审查冻结字节一致；禁止读取主安装脚本或任意上传SQL。"""
    operations=[]
    for name,expected in INPUTS:
        raw=read_source(name);require(sha(raw)==expected,'MIGRATION_SOURCE_CHANGED')
        try:text=raw.decode('utf-8-sig')
        except UnicodeError:raise AdditiveMigrationBlocked('MIGRATION_SQL_REJECTED') from None
        schema='ry-mes' if name.endswith('ry-mes.sql') else 'ry-erp'
        for sql in split_sql(text):
            found=re.match(r'^(CREATE|ALTER) TABLE (?:IF NOT EXISTS )?`?([a-z_]+)`?(?=\s|\()',sql)
            require(found is not None,'MIGRATION_SQL_REJECTED')
            kind,table=found.groups();key=schema+'.'+table
            require(key in NEW_TABLES|frozenset(OLD_TABLES),'MIGRATION_SCOPE_REJECTED')
            operations.append(Operation(len(operations)+1,schema,table,kind,name,expected,sql))
    require(len(operations)==12 and sum(op.kind=='CREATE' for op in operations)==8,'MIGRATION_SCOPE_REJECTED')
    return operations


def non_precision_gaps(metadata):
    gaps=schema_evidence(metadata)['gaps']
    return [gap for gap in gaps if not any(gap.startswith(key+': ') for key in PRECISION_KEYS)]


def plan_operations(metadata):
    """只有完整原适用基线才计划十二步；已完成为NOOP，任何半迁移状态须单独复核。"""
    operations=load_operations();gaps=non_precision_gaps(metadata)
    if not gaps:return []
    require(set(gaps)==EXPECTED_GAPS,'ADDITIVE_BASELINE_UNVERIFIED')
    require(not any(key in metadata for key in NEW_TABLES),'ADDITIVE_BASELINE_UNVERIFIED')
    return operations


def execute_operations(store,operations,journal):
    """逐步先持久记attempt再执行/回读；失败结果不确定，不自动回滚、删除或重试。"""
    require(operations==load_operations(),'MIGRATION_PLAN_CHANGED')
    completed=0;current=None
    try:
        require(plan_operations(store.initial())==operations,'ADDITIVE_BASELINE_UNVERIFIED')
        journal({'event':'STARTED','ddlPlanned':len(operations),'businessWrites':0,'sourceCertification':'NOT_PERFORMED'})
        for op in operations:
            current=op
            journal({'event':'DDL_ATTEMPT','ordinal':op.ordinal,'schema':op.schema,'table':op.table,
                     'sourceSha256':op.source_sha256,'sqlSha256':sha(op.sql.encode('utf-8'))})
            store.execute(op)
            store.verify_step(op)
            completed+=1
            journal({'event':'DDL_VERIFIED','ordinal':op.ordinal,'schema':op.schema,'table':op.table})
        result=store.verify_final()
        require(result.get('schemaMatched') is True and result.get('sourceCertification')=='NOT_PERFORMED',
                'ADDITIVE_POSTCONDITION_UNVERIFIED')
        journal({'event':'COMPLETED','ddlExecuted':completed,'businessWrites':0})
        return {**result,'ddlExecuted':completed,'businessWrites':0,'businessWriteAuthorized':False}
    except Exception as error:
        try:journal({'event':'FAILED_UNCERTAIN','ordinal':current.ordinal if current else None,
                     'ddlVerified':completed,'category':error.category if isinstance(error,AdditiveMigrationBlocked) else 'DDL_OR_JOURNAL_UNVERIFIED',
                     'rollbackClaimed':False,'automaticRetry':False})
        except Exception:pass
        raise AdditiveMigrationBlocked('ADDITIVE_EXECUTION_UNCERTAIN') from None


def scalar(value):
    """仅在内存产生原行保全摘要，不写出数量、字符串、业务ID或个人信息。"""
    if value is None:return ['null']
    if type(value) is bool:return ['bool',value]
    if type(value) is int:return ['int',str(value)]
    if type(value) is float:return ['binary_float',value.hex()]
    if isinstance(value,Decimal):return ['decimal',str(value)]
    if isinstance(value,(date,datetime)):return ['date',value.isoformat()]
    if isinstance(value,bytes):return ['bytes',value.hex()]
    require(type(value) is str,'ROW_HASH_TYPE_UNSUPPORTED');return ['text',value]


class MysqlAdditiveStore:
    """仅使用显式批准的本地连接；构造无I/O，目标/定义/原行摘要需在任何DDL前重核。

    迁移命名锁只防同类迁移并发，不冒充应用写入围栏。原两表每步哈希/NULL核验有变化即停止，
    不把已提交DDL描述为已回滚；新增表必须仍为空，不能借迁移为旧业务补认证。
    """
    def __init__(self,connection,expected_server_uuid_sha256):
        require(isinstance(expected_server_uuid_sha256,str) and re.fullmatch('[0-9a-f]{64}',expected_server_uuid_sha256),
                'LOCAL_TARGET_UNVERIFIED')
        self.connection=connection;self.expected=expected_server_uuid_sha256
        self.original_columns={};self.original_rows={};self.original_metadata={};self.created=set();self.step=0
    def metadata(self):
        with self.connection.cursor() as cursor:
            metadata,indexes=_read_schema_metadata(cursor,TABLES)
        for key,entry in metadata.items():entry['indexes']=indexes.get(key,[])
        return metadata
    def target(self):
        # autocommit不排除显式BEGIN，因此还须核驱动原生IN_TRANS位；拒绝借用已有事务隐式提交。
        require(getattr(self.connection,'host',None) in ('localhost','127.0.0.1')
                and type(getattr(self.connection,'port',None)) is int and self.connection.port==3306
                and self.connection.get_autocommit() is True
                and type(getattr(self.connection,'server_status',None)) is int
                and not (self.connection.server_status & 1),'FRESH_LOCAL_DDL_SESSION_REQUIRED')
        with self.connection.cursor() as cursor:
            cursor.execute('SELECT IS_USED_LOCK(%s),CONNECTION_ID()', (LOCK_NAME,))
            owner,connection_id=cursor.fetchone()
            require(type(owner) is int and type(connection_id) is int and owner==connection_id,
                    'MIGRATION_LOCK_NOT_OWNED')
            cursor.execute('SELECT VERSION(),@@port,@@server_uuid,@@read_only,DATABASE(),@@session.tx_read_only')
            version,port,uuid,readonly,database,transaction_readonly=cursor.fetchone()
            require(str(version).startswith('5.7.') and type(port) is int and port==3306 and sha(str(uuid).encode())==self.expected
                    and readonly==0 and transaction_readonly==0 and database in SCHEMAS.values(),'LOCAL_TARGET_UNVERIFIED')
            cursor.execute('SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN (%s,%s,%s,%s)',tuple(SCHEMAS.values()))
            require({row[0] for row in cursor.fetchall()}==set(SCHEMAS.values()),'LOCAL_TARGET_UNVERIFIED')
    def row_hash(self,key,columns,metadata):
        schema,table=key.split('.');require(key in OLD_TABLES,'ROW_HASH_SCOPE_REJECTED')
        require(all(re.fullmatch('[a-z][a-z0-9_]*',column) for column in columns),'ROW_HASH_SCOPE_REJECTED')
        primary=sorted((part[2],part[3]) for part in metadata[key]['indexes'] if part[0]=='PRIMARY')
        require(primary,'ROW_HASH_SCOPE_REJECTED')
        query='SELECT '+','.join('`'+column+'`' for column in columns)+' FROM `'+schema+'`.`'+table+'` ORDER BY '+','.join('`'+column+'`' for _,column in primary)+' LIMIT 200001'
        count=0;digest=hashlib.sha256()
        with self.connection.cursor() as cursor:
            cursor.execute(query)
            while True:
                rows=cursor.fetchmany(500)
                if not rows:break
                for row in rows:
                    count+=1;require(count<=200000 and len(row)==len(columns),'ROW_HASH_BOUND_EXCEEDED')
                    raw=json.dumps([scalar(value) for value in row],ensure_ascii=False,separators=(',',':')).encode('utf-8')
                    digest.update(str(len(raw)).encode()+b':'+raw)
        return {'rows':count,'sha256':digest.hexdigest()}
    def initial(self):
        self.target();metadata=self.metadata();require(plan_operations(metadata)==load_operations(),'ADDITIVE_BASELINE_UNVERIFIED')
        self.original_metadata=metadata
        for key in OLD_TABLES:
            self.original_columns[key]=tuple(sorted(metadata[key]['columns']))
            self.original_rows[key]=self.row_hash(key,self.original_columns[key],metadata)
        return metadata
    def execute(self,op):
        self.target();require(type(op.ordinal) is int and op.ordinal==self.step+1
            and op==load_operations()[op.ordinal-1],'MIGRATION_STEP_UNVERIFIED')
        metadata=self.metadata();key=op.schema+'.'+op.table
        require((op.kind=='CREATE' and key not in metadata) or (op.kind=='ALTER' and key in metadata),'MIGRATION_STEP_UNVERIFIED')
        self.verify_original(metadata)
        with self.connection.cursor() as cursor:
            cursor.execute('USE `'+op.schema+'`')
            cursor.execute(op.sql)
        if op.kind=='CREATE':self.created.add(key)
        self.step=op.ordinal
    def verify_original(self,metadata):
        for key in OLD_TABLES:
            require(self.row_hash(key,self.original_columns[key],metadata)==self.original_rows[key],'ORIGINAL_ROWS_CHANGED')
        with self.connection.cursor() as cursor:
            # 新增列不属于冻结原列摘要，必须自出现起逐步核NULL，不能只在最终发现旧行被补认。
            for key,planned in ((OLD_TABLES[0],('certified_version','certified_terms_id')),
                                (OLD_TABLES[1],('event_sequence',))):
                present=[column for column in planned if column in metadata[key]['columns']]
                if not present:continue
                schema,table=key.split('.')
                cursor.execute('SELECT COUNT(*) FROM `'+schema+'`.`'+table+'` WHERE '+
                               ' OR '.join('`'+column+'` IS NOT NULL' for column in present))
                require(cursor.fetchone()[0]==0,'OLD_ROWS_NOT_NULL_UNVERIFIED')
            for key in self.created:
                schema,table=key.split('.');cursor.execute('SELECT COUNT(*) FROM `'+schema+'`.`'+table+'`')
                require(cursor.fetchone()[0]==0,'NEW_CERTIFIED_TABLE_NOT_EMPTY')
    def verify_step(self,op):
        metadata=self.metadata();key=op.schema+'.'+op.table
        require(key in metadata and str(metadata[key].get('engine','')).upper()=='INNODB','MIGRATION_STEP_UNVERIFIED')
        # 新证据表在出生追加之前仍为原V1定义，不能把中间状态冒称完整V2结构。
        relevant=[gap for gap in non_precision_gaps(metadata) if gap.startswith(key+'.') or gap.startswith(key+':')]
        deferred=('birth_id','birth_sha256') if key=='ry-erp.wm_certified_iqc_evidence' and op.ordinal<10 else ()
        require(not [gap for gap in relevant if not any('.'+name+': column unavailable' in gap for name in deferred)],'MIGRATION_STEP_UNVERIFIED')
        self.verify_original(metadata)
    def verify_final(self):
        metadata=self.metadata();require(not non_precision_gaps(metadata),'ADDITIVE_POSTCONDITION_UNVERIFIED')
        self.verify_original(metadata)
        with self.connection.cursor() as cursor:
            for key,columns in ((OLD_TABLES[0],('certified_version','certified_terms_id')),(OLD_TABLES[1],('event_sequence',))):
                schema,table=key.split('.');cursor.execute('SELECT COUNT(*) FROM `'+schema+'`.`'+table+'` WHERE '+
                    ' OR '.join('`'+column+'` IS NOT NULL' for column in columns))
                require(cursor.fetchone()[0]==0,'OLD_ROWS_NOT_NULL_UNVERIFIED')
        return {'schemaMatched':True,'schemaScope':'PROCUREMENT_ADDITIVE_ONLY',
                'sourceCertification':'NOT_PERFORMED','createdTables':sorted(self.created),
                'originalRows':self.original_rows,'nativeQuantityMigrationPerformed':False,'loadedBuildProven':False}
