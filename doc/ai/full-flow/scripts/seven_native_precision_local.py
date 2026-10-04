"""已明确授权的本地四表七列迁移；不替代生产V3签名或签发业务来源。

原生数量与默认沿用严格二进制无损校验。十二个临时拒写触发器覆盖ALTER释放表锁的
DML间隙；外键级联、既有触发器、生成列、复制、未知物理属性一律拒绝。不是防恶意
管理员DROP/TRUNCATE的权限边界。只支持全原基线的一次执行；不确定失败保留已建立的围栏；解除阶段失败须单独清点剩余围栏，
不得自动重试、逆向DDL或编辑journal伪造恢复。备份/业务行仅进入调用方私有目录。
"""
from __future__ import annotations

import copy
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import re
from types import MappingProxyType
import uuid

from stock_native_precision_migration import (MigrationBlocked, MySQLStore, canonical, sha, scalar,
    exact_quantity, column_default, native_decimal_shape, quantity_attributes, validate_table_storage)

SCHEMA='ry-erp'
STEP_ORDER=('wm_material_stock_tx','wm_material_stock','wm_material_receive_detail','wm_material_receive_line')
NATIVE_COLUMNS=MappingProxyType({
    'wm_material_stock_tx':('tx_quantity','before_quantity','after_quantity'),
    'wm_material_stock':('stock_quantity','reserved_quantity'),
    'wm_material_receive_detail':('quantity',),
    'wm_material_receive_line':('quantity_received',),
})
PRIMARY_KEYS=MappingProxyType(dict(zip(STEP_ORDER,('tx_id','stock_id','detail_id','line_id'))))
FENCE_MESSAGE='NEXUS_LOCAL_PRECISION_MIGRATION_FENCE'
FENCE_STATEMENT=f"SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '{FENCE_MESSAGE}'"


class LocalMigrationBlocked(MigrationBlocked):
    """仅固定类别可向调用者传播，原始SQL/数量/数据库错误不回显。"""


def require(condition,code):
    if not condition:raise LocalMigrationBlocked(code)


def checked(function,*args,**kwargs):
    try:return function(*args,**kwargs)
    except MigrationBlocked as error:raise LocalMigrationBlocked(error.code) from None


def validate_metadata(metadata):
    require(isinstance(metadata,dict) and set(metadata)==set(STEP_ORDER),'METADATA_REQUIRED')
    for table in STEP_ORDER:
        item=metadata[table]
        require(item.get('engine')=='InnoDB' and item.get('primaryKey')==[PRIMARY_KEYS[table]],'STORAGE_CONTRACT_REJECTED')
        checked(validate_table_storage,item)
        for dependency in ('foreignKeys','inboundForeignKeys','triggers'):
            require(item.get(dependency)==[],'DEPENDENCY_OR_TRIGGER_REJECTED')
        columns=item.get('columns');require(isinstance(columns,list) and columns,'METADATA_REQUIRED')
        names=[]
        for col in columns:
            require(isinstance(col,dict) and isinstance(col.get('name'),str) and
                re.fullmatch('[A-Za-z_][A-Za-z_0-9]*',col['name']) is not None,'METADATA_REQUIRED')
            names.append(col['name'])
            require(isinstance(col.get('extra'),str) and col.get('generationExpression')=='' and
                'GENERATED' not in col['extra'].upper(),'GENERATED_COLUMNS_UNSUPPORTED')
        require(len(names)==len(set(names)) and {PRIMARY_KEYS[table],*NATIVE_COLUMNS[table]}<=set(names),'METADATA_REQUIRED')
        for col in columns:
            if col['name'] not in NATIVE_COLUMNS[table]:continue
            require(col.get('extra')=='' and type(col.get('nullable')) is bool and isinstance(col.get('comment'),str)
                and not any(x in col['comment'] for x in ('\x00','\r','\n')),'SOURCE_ATTRIBUTES_UNSUPPORTED')
            kind=col.get('type')
            require(kind in ('double','decimal'),'SOURCE_TYPE_UNSUPPORTED')
            if kind=='double':
                require(table!='wm_material_stock_tx' and col.get('columnType') in ('double','double(12,2)') and
                    type(col.get('precision')) is int,'SOURCE_ATTRIBUTES_UNSUPPORTED')
            else:
                require(native_decimal_shape(col) and col['scale']<=4 and col['precision']-col['scale']<=14,
                    'DECIMAL_CHANGE_IS_NOT_WIDENING')
            checked(column_default,col)
    return metadata


def option_tokens(value):
    """只在引号外拆表选项，COMMENT内的ROW_FORMAT字样必须原样保留。"""
    require(isinstance(value,str),'STORAGE_ATTRIBUTES_UNKNOWN')
    tokens=[];start=0;index=0;quote=None
    while index<len(value):
        character=value[index]
        if quote:
            if character=='\\' and index+1<len(value):index+=2;continue
            if character==quote:
                if index+1<len(value) and value[index+1]==quote:index+=2;continue
                quote=None
        elif character in ("'",'"','`'):quote=character
        elif character.isspace():
            if start<index:tokens.append(value[start:index])
            start=index+1
        index+=1
    require(quote is None,'STORAGE_ATTRIBUTES_UNKNOWN')
    if start<len(value):tokens.append(value[start:])
    return tokens


def explicit_row_format(value,row):
    tokens=option_tokens(value)
    positions=[index for index,token in enumerate(tokens) if re.fullmatch(r'(?i)ROW_FORMAT=(COMPACT|REDUNDANT|DYNAMIC)',token)]
    require(len(positions)<=1,'STORAGE_ATTRIBUTES_UNKNOWN')
    if positions:tokens[positions[0]]='ROW_FORMAT='+row
    else:
        position=next((index for index,token in enumerate(tokens) if token.startswith('COMMENT=')),len(tokens))
        tokens.insert(position,'ROW_FORMAT='+row)
    return ' '.join(tokens)


def goal_metadata(metadata,tables):
    result=copy.deepcopy(validate_metadata(metadata))
    require(set(tables)<=set(STEP_ORDER),'UNAPPROVED_TABLE')
    for table in tables:
        item=result[table]
        changed=any(col['name'] in NATIVE_COLUMNS[table] and
            (col['type'],col['precision'],col['scale'])!=('decimal',18,4) for col in item['columns'])
        if not changed:continue
        row=checked(validate_table_storage,item)
        item['tableOptions'][2]='row_format='+row
        item['tableDefinitionOptions']=explicit_row_format(item['tableDefinitionOptions'],row)
        for col in item['columns']:
            if col['name'] not in NATIVE_COLUMNS[table]:continue
            default=checked(column_default,col)
            suffix=quantity_attributes(col)
            col.update(type='decimal',precision=18,scale=4,columnType='decimal(18,4)'+suffix,
                       default=None if default is None else format(default,'.4f'))
    return result


def canonical_metadata(metadata):
    result=copy.deepcopy(validate_metadata(metadata))
    # INFORMATION_SCHEMA将十进制默认格式化为新scale，只规范数量默认的数值，不抹去其它属性。
    for table in STEP_ORDER:
        for col in result[table]['columns']:
            if col['name'] in NATIVE_COLUMNS[table]:
                value=checked(column_default,col);col['default']=None if value is None else format(value,'.4f')
    return result


def render_ddl(metadata,table):
    require(table in STEP_ORDER,'UNAPPROVED_TABLE');validate_metadata(metadata)
    changes=[]
    for col in metadata[table]['columns']:
        if col['name'] not in NATIVE_COLUMNS[table] or (col['type'],col['precision'],col['scale'])==('decimal',18,4):continue
        nullable='NULL' if col['nullable'] else 'NOT NULL';default=checked(column_default,col)
        default_sql=' DEFAULT NULL' if default is None and col['nullable'] else ''
        if default is not None:default_sql=' DEFAULT '+format(default,'.4f')
        comment=col['comment'].replace("'","''")
        changes.append(f"MODIFY COLUMN `{col['name']}` DECIMAL(18,4){quantity_attributes(col).upper()} {nullable}{default_sql} COMMENT '{comment}'")
    if not changes:return None
    row=checked(validate_table_storage,metadata[table])
    return f"ALTER TABLE `{SCHEMA}`.`{table}`\n  "+',\n  '.join(changes)+f',\n  ROW_FORMAT={row},\n  ALGORITHM=COPY, LOCK=EXCLUSIVE'


def capture_snapshot(store,*,include_rows=False):
    metadata=validate_metadata(store.describe());tables={};backup_rows={}
    for table in STEP_ORDER:
        names={col['name'] for col in metadata[table]['columns']};columns={col['name']:col for col in metadata[table]['columns']}
        digest=hashlib.sha256();raw_digest=hashlib.sha256();count=0;previous=None;rows=[]
        nulls={name:0 for name in NATIVE_COLUMNS[table]}
        for row in store.iter_rows(table):
            require(isinstance(row,dict) and set(row)==names and type(row[PRIMARY_KEYS[table]])is int,'FULL_ROW_SNAPSHOT_REQUIRED')
            key=row[PRIMARY_KEYS[table]];require(previous is None or key>previous,'ROW_ORDER_OR_DUPLICATE_REJECTED');previous=key
            raw={};normalized={}
            for name,value in row.items():
                raw[name]=checked(scalar,value)
                if name in NATIVE_COLUMNS[table]:
                    col=columns[name];exact=checked(exact_quantity,value,col['type'],'PRESERVE_NULL_UNKNOWN',col['nullable'])
                    require(exact is None or 'unsigned' not in quantity_attributes(col) or exact>=0,'LOSSY_VALUE')
                    normalized[name]=['quantity',None if exact is None else format(exact,'.4f')];nulls[name]+=int(exact is None)
                else:normalized[name]=raw[name]
            for target,data in ((digest,normalized),(raw_digest,raw)):
                encoded=canonical(data);target.update(str(len(encoded)).encode()+b':'+encoded)
            if include_rows:rows.append(raw)
            count+=1
        tables[table]={'rows':count,'rowsSha256':digest.hexdigest(),'rawRowsSha256':raw_digest.hexdigest(),'nullCounts':nulls}
        if include_rows:backup_rows[table]=rows
    facts={table:{key:value for key,value in info.items() if key!='rawRowsSha256'} for table,info in tables.items()}
    result={'tables':tables,'rowsSha256':sha(canonical(facts)),'schemaSha256':sha(canonical(canonical_metadata(metadata))),
        'metadata':metadata,'sourceCertification':'NOT_PERFORMED','historicalPrecisionRecovered':False}
    if include_rows:result['backupRows']=backup_rows
    return result


def decode_scalar(value):
    require(isinstance(value,list) and value and isinstance(value[0],str),'BACKUP_VALUE_REJECTED')
    kind=value[0]
    try:
        if kind=='null' and len(value)==1:return None
        if kind=='int' and len(value)==2:return int(value[1])
        if kind=='str' and len(value)==2:return value[1]
        if kind=='double' and len(value)==2:return float.fromhex(value[1])
        if kind=='decimal' and len(value)==2:return Decimal(value[1])
        if kind=='bytes' and len(value)==2:return bytes.fromhex(value[1])
        if kind=='date' and len(value)==2:return date.fromisoformat(value[1])
        if kind=='datetime' and len(value)==2:return datetime.fromisoformat(value[1])
        if kind=='timedelta' and len(value)==4:return timedelta(days=int(value[1]),seconds=int(value[2]),microseconds=int(value[3]))
    except (ValueError,TypeError,OverflowError):pass
    raise LocalMigrationBlocked('BACKUP_VALUE_REJECTED')


def write_document(path,value):
    try:
        raw=canonical(value)
        with Path(path).open('xb') as handle:handle.write(raw);handle.flush();os.fsync(handle.fileno())
        require(Path(path).read_bytes()==raw,'PERSISTENCE_MISMATCH')
        return sha(raw)
    except LocalMigrationBlocked:raise
    except Exception:raise LocalMigrationBlocked('PERSISTENCE_FAILED') from None


def read_backup(path,expected):
    try:
        raw=Path(path).read_bytes();require(sha(raw)==expected,'BACKUP_HASH_MISMATCH');data=json.loads(raw)
        require(data.get('format')=='nexus-local-seven-native-backup-v1' and set(data['rows'])==set(STEP_ORDER),'BACKUP_FORMAT_REJECTED')
        return data
    except LocalMigrationBlocked:raise
    except Exception:raise LocalMigrationBlocked('BACKUP_FORMAT_REJECTED') from None


class Journal:
    """每个持久事件发送前fsync；单会话日志，不提供磁盘恶意回滚保证或自动恢复授权。"""
    def __init__(self,path):
        self.path=Path(path);self.previous='0'*64;self.sequence=0
        try:self.handle=self.path.open('xb')
        except Exception:raise LocalMigrationBlocked('EXECUTION_ALREADY_EXISTS_OR_UNWRITABLE') from None
    def append(self,event,**facts):
        data={'sequence':self.sequence,'previousSha256':self.previous,'event':event,
            'observedAt':datetime.now(timezone.utc).isoformat(),**facts}
        encoded=canonical(data);self.handle.write(encoded+b'\n');self.handle.flush();os.fsync(self.handle.fileno())
        self.previous=sha(encoded);self.sequence+=1
        anchor={'entries':self.sequence,'lastSha256':self.previous}
        pending=self.path.with_name(self.path.name+'.tail.next')
        write_document(pending,anchor)
        os.replace(pending,self.path.with_name(self.path.name+'.tail'))
    def close(self):self.handle.close()


def read_journal(path):
    try:
        previous='0'*64;events=[];raw=Path(path).read_bytes()
        require(raw.endswith(b'\n'),'JOURNAL_INCOMPLETE')
        for index,line in enumerate(raw.splitlines()):
            data=json.loads(line);require(data['sequence']==index and data['previousSha256']==previous,'JOURNAL_CHAIN_MISMATCH')
            previous=sha(line);events.append(data)
        anchor=json.loads(Path(str(path)+'.tail').read_bytes())
        require(anchor=={'entries':len(events),'lastSha256':previous},'JOURNAL_TAIL_MISMATCH')
        return events
    except LocalMigrationBlocked:raise
    except Exception:raise LocalMigrationBlocked('JOURNAL_CHAIN_MISMATCH') from None


def fence_specs(nonce):
    require(isinstance(nonce,str) and re.fullmatch('[a-f0-9]{16}',nonce)is not None,'FENCE_NAME_REJECTED')
    return tuple({'table':table,'event':event,'name':f'nexus_np7_{nonce}_{index}_{event[0].lower()}',
        'statement':FENCE_STATEMENT} for index,table in enumerate(STEP_ORDER) for event in ('INSERT','UPDATE','DELETE'))


def execute_once(store,directory):
    directory=Path(directory);require(directory.is_dir() and not directory.is_symlink(),'PRIVATE_DIRECTORY_REQUIRED')
    journal=Journal(directory/'journal.jsonl');fence_attempted=False;release_attempted=False;completed=0;attempted=0;baseline=None
    record={'status':'STARTED','ddlCompleted':0,'ddlAttempts':0,'sourceCertification':'NOT_PERFORMED','historicalPrecisionRecovered':False}
    try:
        store.validate_session();identity=store.identity();journal.append('TARGET_VERIFIED',target=identity)
        initial=capture_snapshot(store)
        operations=[table for table in STEP_ORDER if render_ddl(initial['metadata'],table)]
        if not operations:
            record.update(status='NOOP',tables=initial['tables']);journal.append('NOOP');return record
        # 必须全原基线；已迁移前缀需要独立审查，不让新目录成为盲重放工具。
        require(len(operations)==4,'PARTIAL_MIGRATION_REQUIRES_REVIEW')
        fence_attempted=True;journal.append('FENCE_INSTALL_ATTEMPT');store.install_fence(journal);store.verify_fence()
        baseline=capture_snapshot(store,include_rows=True)
        require(baseline['schemaSha256']==initial['schemaSha256'],'SCHEMA_CHANGED_DURING_FENCE_INSTALL')
        journal.append('FENCED_BASELINE',rowsSha256=baseline['rowsSha256'],schemaSha256=baseline['schemaSha256'])
        backup={'format':'nexus-local-seven-native-backup-v1','target':identity,'metadata':baseline['metadata'],
            'rows':baseline['backupRows'],'tables':baseline['tables']}
        digest=write_document(directory/'backup.json',backup);reread=read_backup(directory/'backup.json',digest)
        restore=store.verify_restore(reread)
        require(restore.get('status')=='VERIFIED' and restore.get('restoredRows')==
            {table:baseline['tables'][table]['rows'] for table in STEP_ORDER},'BACKUP_RESTORE_MISMATCH')
        journal.append('BACKUP_RESTORE_VERIFIED',backupSha256=digest,restore=restore)
        store.lock();expected=baseline['metadata'];store.verify_fence()
        locked=capture_snapshot(store)
        require(locked['rowsSha256']==baseline['rowsSha256'] and locked['schemaSha256']==baseline['schemaSha256'],'LOCKED_BASELINE_CHANGED')
        for table in operations:
            store.verify_fence();sql=render_ddl(expected,table)
            journal.append('DDL_ATTEMPT',table=table,sqlSha256=sha(sql.encode()))
            attempted+=1
            store.alter(table,sql);store.lock();store.verify_fence()
            expected=goal_metadata(expected,(table,));snapshot=capture_snapshot(store)
            require(snapshot['rowsSha256']==baseline['rowsSha256'],'POST_DDL_ROWS_CHANGED')
            require(snapshot['schemaSha256']==sha(canonical(canonical_metadata(expected))),'POST_DDL_SCHEMA_CHANGED')
            completed+=1;journal.append('DDL_VERIFIED',table=table,completed=completed,
                rowsSha256=snapshot['rowsSha256'],schemaSha256=snapshot['schemaSha256'])
        release_attempted=True;journal.append('FENCE_REMOVE_ATTEMPT');store.remove_fence(journal)
        snapshot=capture_snapshot(store)
        require(snapshot['rowsSha256']==baseline['rowsSha256'] and
            snapshot['schemaSha256']==sha(canonical(canonical_metadata(expected))),'FINAL_PRESERVATION_MISMATCH')
        store.unlock();fence_attempted=False
        record.update(status='COMPLETED',ddlCompleted=completed,ddlAttempts=attempted,backupSha256=digest,tables=snapshot['tables'],
            rowsSha256=snapshot['rowsSha256'],schemaSha256=snapshot['schemaSha256'],restore=restore)
        journal.append('COMPLETED',ddlCompleted=completed,rowsSha256=snapshot['rowsSha256'],schemaSha256=snapshot['schemaSha256'])
        return record
    except BaseException as error:
        code=error.code if isinstance(error,MigrationBlocked) else 'MIGRATION_FAILED_DETAILS_SUPPRESSED'
        event='STOPPED_FENCE_RELEASE_UNPROVEN' if release_attempted else ('STOPPED_FENCE_RETAINED' if fence_attempted else 'STOPPED')
        journal.append(event,category=code,ddlCompleted=completed,ddlAttempts=attempted)
        raise LocalMigrationBlocked(code,ddl_attempted=attempted>0) from None
    finally:
        # 不拆除失败后围栏；释放会话表锁不会解除持久DML拒写触发器。
        try:store.unlock()
        except Exception:pass
        journal.close()


class LocalMySQLStore(MySQLStore):
    """固定loopback测试库的prepared执行适配器；凭证只能由调用方内存提供。"""
    def __init__(self,connection,expected_uuid_sha256,*,nonce=None):
        self.connection=connection;self.expected_uuid_sha256=expected_uuid_sha256
        self.nonce=nonce or uuid.uuid4().hex[:16];self.specs=fence_specs(self.nonce);self.fence_active=False
        self.target={'host':'127.0.0.1','port':3306,'schema':SCHEMA};self.session_mode=None
    def validate_session(self):
        require(self.connection.__class__.__module__.startswith('mysql.connector.') and self.connection.__class__.__name__=='MySQLConnection',
            'MYSQL_BINARY_DRIVER_REQUIRED')
        require(self.connection.autocommit is True and self.connection.in_transaction is False,'FRESH_AUTOCOMMIT_SESSION_REQUIRED')
        self.query('SET SESSION lock_wait_timeout=10');self.query('SET SESSION innodb_lock_wait_timeout=3')
        modes={value for value in self.query('SELECT @@session.sql_mode')[0][0].split(',') if value}
        self.query('SET SESSION sql_mode=%s',(','.join(sorted(modes|{'STRICT_ALL_TABLES','NO_BACKSLASH_ESCAPES'})),))
        self.session_mode=self.query('SELECT @@session.sql_mode')[0][0]
        require(self.query("SELECT GET_LOCK('nexus.local.seven.native.precision',0)")==[(1,)],'MIGRATION_SESSION_ALREADY_ACTIVE')
    def replication_appliers(self):
        """5.7线程类不在setup_instruments；用完整原生清点与lost计数证明观测无遗漏。"""
        try:
            grants=self.query('SHOW GRANTS')
            privileges=set()
            for row in grants:
                require(len(row)==1 and isinstance(row[0],str),'REPLICATION_INVENTORY_UNKNOWN')
                match=re.match(r'(?i)^GRANT (.+?) ON \*\.\* TO ',row[0])
                if match:privileges.update(value.strip().upper() for value in match.group(1).split(','))
            require('ALL PRIVILEGES' in privileges or {'SELECT','PROCESS','REPLICATION CLIENT'}<=privileges,
                'REPLICATION_INVENTORY_UNKNOWN')
            require(self.query('SELECT @@GLOBAL.performance_schema')==[(1,)],'REPLICATION_INVENTORY_UNKNOWN')
            consumers=self.dictionary_query("SELECT NAME,ENABLED FROM performance_schema.setup_consumers WHERE NAME IN ('global_instrumentation','thread_instrumentation')")
            require(len(consumers)==2 and {r['NAME']:r['ENABLED'] for r in consumers}==
                {'global_instrumentation':'YES','thread_instrumentation':'YES'},'REPLICATION_INVENTORY_UNKNOWN')
            lost=self.query("SHOW GLOBAL STATUS WHERE Variable_name IN ('Performance_schema_thread_classes_lost','Performance_schema_thread_instances_lost')")
            require(len(lost)==2 and {r[0]:r[1] for r in lost}=={'Performance_schema_thread_classes_lost':'0',
                'Performance_schema_thread_instances_lost':'0'},'REPLICATION_INVENTORY_UNKNOWN')
            queries={
                'configuration':'SELECT CHANNEL_NAME FROM performance_schema.replication_connection_configuration',
                'connections':'SELECT CHANNEL_NAME,SERVICE_STATE FROM performance_schema.replication_connection_status',
                'appliers':'SELECT CHANNEL_NAME,SERVICE_STATE FROM performance_schema.replication_applier_status',
                'coordinators':'SELECT CHANNEL_NAME,THREAD_ID,SERVICE_STATE FROM performance_schema.replication_applier_status_by_coordinator',
                'workers':'SELECT CHANNEL_NAME,WORKER_ID,THREAD_ID,SERVICE_STATE FROM performance_schema.replication_applier_status_by_worker',
                'ordinary':'SHOW SLAVE STATUS',
                'plugins':"SELECT PLUGIN_NAME,PLUGIN_STATUS FROM information_schema.PLUGINS WHERE PLUGIN_NAME='group_replication'",
                'groupVariables':"SHOW GLOBAL VARIABLES LIKE 'group_replication%'",
                'systemUsers':"SELECT ID,USER,COMMAND FROM information_schema.PROCESSLIST WHERE USER='system user'",
            }
            facts={name:self.dictionary_query(sql) for name,sql in queries.items()}
            # 本地模式比生产合同更窄：连已配置但停止的通道也不接受，不尝试启动/停止复制。
            require(all(value==[] for value in facts.values()),'REPLICATION_WRITERS_OR_UNKNOWN')
            threads=self.dictionary_query("SELECT THREAD_ID,NAME,INSTRUMENTED FROM performance_schema.threads WHERE TYPE='BACKGROUND'")
            require(threads and len({row['THREAD_ID'] for row in threads})==len(threads),'REPLICATION_INVENTORY_UNKNOWN')
            for row in threads:
                require(type(row['THREAD_ID'])is int and isinstance(row['NAME'],str) and row['INSTRUMENTED']=='YES',
                    'REPLICATION_INVENTORY_UNKNOWN')
                require(not any(name in row['NAME'].lower() for name in ('slave','replica','group_rpl','group_replication')),
                    'REPLICATION_WRITERS_OR_UNKNOWN')
            facts.update(consumers=consumers,lost=lost,threads=threads)
            self.replication_inventory={'status':'COMPLETE','activeApplierCount':0,'groupState':'KNOWN_NOT_INSTALLED',
                'inventorySha256':sha(canonical(facts))}
            return 0
        except MigrationBlocked:raise
        except Exception:raise LocalMigrationBlocked('REPLICATION_INVENTORY_UNKNOWN') from None
    def identity(self):
        raw=self.observe_identity()
        require(raw['schema']==SCHEMA and raw['port']==3306 and str(raw['version']).startswith('5.7.') and
            sha(raw['serverUuid'].encode())==self.expected_uuid_sha256,'TARGET_IDENTITY_REJECTED')
        require(raw['eventScheduler']=='OFF' and raw['replicationApplierCount']==0 and
            raw['replicationInventory']['status']=='COMPLETE','BACKGROUND_WRITERS_UNPROVEN')
        require(self.query("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('ry-vue','ry-erp','ry-mes','ry-iot') ORDER BY SCHEMA_NAME")==
            [('ry-erp',),('ry-iot',),('ry-mes',),('ry-vue',)],'FOUR_TEST_SCHEMAS_REQUIRED')
        return {'serverUuidSha256':sha(raw['serverUuid'].encode()),'port':3306,'schema':SCHEMA,
            'replicationInventorySha256':raw['replicationInventory']['inventorySha256'],'mysqlVersion':raw['version']}
    def storage_and_dependency_inventory(self):
        """一次完整查四表依赖闭包和物理目录，减少全域扫描但不缓存或猜测缺项。"""
        grants=self.query('SHOW GRANTS');privileges=set()
        for entry in grants:
            require(len(entry)==1 and isinstance(entry[0],str),'DEPENDENCY_VISIBILITY_UNKNOWN')
            match=re.match(r'(?i)^GRANT (.+?) ON \*\.\* TO ',entry[0])
            if match:privileges.update(value.strip().upper() for value in match.group(1).split(','))
        require('ALL PRIVILEGES' in privileges or {'SELECT','TRIGGER','PROCESS'}<=privileges,'DEPENDENCY_VISIBILITY_UNKNOWN')
        placeholders=','.join('%s' for _ in STEP_ORDER)
        foreign=self.query('SELECT TABLE_SCHEMA,TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_SCHEMA,REFERENCED_TABLE_NAME '
            'FROM information_schema.KEY_COLUMN_USAGE WHERE '
            f'(TABLE_SCHEMA=%s AND TABLE_NAME IN ({placeholders}) AND REFERENCED_TABLE_NAME IS NOT NULL) OR '
            f'(REFERENCED_TABLE_SCHEMA=%s AND REFERENCED_TABLE_NAME IN ({placeholders}))',
            (SCHEMA,*STEP_ORDER,SCHEMA,*STEP_ORDER))
        require(foreign==[],'DEPENDENCY_OR_TRIGGER_REJECTED')
        directory={table:'ry@002derp/'+table for table in STEP_ORDER}
        names_sql=','.join('BINARY %s' for _ in STEP_ORDER)
        rows=self.query('SELECT t.NAME,t.ROW_FORMAT,t.SPACE_TYPE,t.ZIP_PAGE_SIZE,s.PAGE_SIZE,s.SPACE_TYPE,s.ZIP_PAGE_SIZE '
            'FROM information_schema.INNODB_SYS_TABLES t JOIN information_schema.INNODB_SYS_TABLESPACES s ON t.SPACE=s.SPACE '
            f'WHERE BINARY t.NAME IN ({names_sql})',tuple(directory.values()))
        require(len(rows)==4 and all(len(row)==7 for row in rows) and {row[0] for row in rows}==set(directory.values()) and
            len({row[0] for row in rows})==4 and self.query('SELECT @@GLOBAL.innodb_file_per_table')==[(1,)],'STORAGE_ATTRIBUTES_UNKNOWN')
        for row in rows:require(row[2]==row[5] and row[3]==row[6],'STORAGE_ATTRIBUTES_UNKNOWN')
        reverse={value:key for key,value in directory.items()}
        return {reverse[row[0]]:list(row[1:]) for row in rows}
    def describe(self):
        storage=self.storage_and_dependency_inventory()
        result={}
        for table in STEP_ORDER:
            options=self.query('SELECT ENGINE,TABLE_COLLATION,ROW_FORMAT,CREATE_OPTIONS FROM information_schema.TABLES '
                "WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s AND TABLE_TYPE='BASE TABLE'",(SCHEMA,table))
            require(len(options)==1,'METADATA_REQUIRED')
            raw=self.query('SELECT COLUMN_NAME,DATA_TYPE,NUMERIC_PRECISION,NUMERIC_SCALE,IS_NULLABLE,COLUMN_DEFAULT,COLUMN_COMMENT,EXTRA,'
                'COLUMN_TYPE,GENERATION_EXPRESSION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s ORDER BY ORDINAL_POSITION',(SCHEMA,table))
            columns=[dict(zip(('name','type','precision','scale','nullable','default','comment','extra','columnType','generationExpression'),
                (r[0],r[1],r[2],r[3],r[4]=='YES',*r[5:]))) for r in raw]
            indexes=self.query('SELECT INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART,INDEX_TYPE FROM information_schema.STATISTICS '
                'WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s ORDER BY INDEX_NAME,SEQ_IN_INDEX',(SCHEMA,table))
            foreign=[];incoming=[]
            triggers=self.read_triggers(table)
            if self.fence_active:
                self.verify_table_fence(table,triggers);triggers=[]
            definition=self.query(f'SHOW CREATE TABLE `{SCHEMA}`.`{table}`')
            require(len(definition)==1 and len(definition[0])==2 and isinstance(definition[0][1],str),'STORAGE_ATTRIBUTES_UNKNOWN')
            closing=definition[0][1].rfind(') ENGINE=');require(closing>=0,'STORAGE_ATTRIBUTES_UNKNOWN')
            partition=self.query('SELECT PARTITION_NAME FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s',(SCHEMA,table))
            require(partition==[(None,)],'PARTITION_UNSUPPORTED')
            physical=[storage[table]]
            result[table]={'engine':options[0][0],'columns':columns,'primaryKey':[r[3] for r in indexes if r[0]=='PRIMARY'],
                'tableOptions':list(options[0][1:]),'foreignKeys':[list(r) for r in foreign],'inboundForeignKeys':[list(r) for r in incoming],
                'triggers':[list(r) for r in triggers],'indexes':[list(r) for r in indexes],'tableDefinitionOptions':definition[0][1][closing+2:],
                'physicalStorage':dict(zip(('rowFormat','spaceType','zipPageSize','pageSize'),physical[0][:4]))}
        return result
    def read_triggers(self,table):
        return self.query('SELECT TRIGGER_NAME,EVENT_MANIPULATION,ACTION_TIMING,ACTION_STATEMENT,SQL_MODE,CHARACTER_SET_CLIENT,COLLATION_CONNECTION,DATABASE_COLLATION '
            'FROM information_schema.TRIGGERS WHERE EVENT_OBJECT_SCHEMA=%s AND EVENT_OBJECT_TABLE=%s ORDER BY TRIGGER_NAME',(SCHEMA,table))
    def verify_table_fence(self,table,triggers):
        expected={spec['name']:spec for spec in self.specs if spec['table']==table}
        require(len(triggers)==3 and {row[0] for row in triggers}==set(expected),'WRITER_FENCE_UNPROVEN')
        for row in triggers:
            require(row[1]==expected[row[0]]['event'] and row[2]=='BEFORE' and row[3]==FENCE_STATEMENT and
                row[4]==self.session_mode and row[5]=='utf8mb4','WRITER_FENCE_UNPROVEN')
    def verify_fence(self):
        require(self.fence_active,'WRITER_FENCE_UNPROVEN')
        for table in STEP_ORDER:self.verify_table_fence(table,self.read_triggers(table))
    def install_fence(self,journal):
        for spec in self.specs:
            journal.append('FENCE_TRIGGER_CREATE_ATTEMPT',trigger=spec['name'],table=spec['table'])
            self.query(f"CREATE TRIGGER `{SCHEMA}`.`{spec['name']}` BEFORE {spec['event']} ON `{SCHEMA}`.`{spec['table']}` FOR EACH ROW {FENCE_STATEMENT}")
            require(not self.query('SHOW WARNINGS'),'FENCE_DDL_WARNINGS')
            journal.append('FENCE_TRIGGER_CREATED',trigger=spec['name'])
        self.fence_active=True;self.verify_fence()
    def iter_rows(self,table):
        require(table in STEP_ORDER,'UNAPPROVED_TABLE')
        return self.iter_named_rows(table,PRIMARY_KEYS[table])
    def iter_named_rows(self,table,primary,*,columns=None):
        require(re.fullmatch('[A-Za-z_][A-Za-z_0-9]*',table)is not None and
            re.fullmatch('[A-Za-z_][A-Za-z_0-9]*',primary)is not None,'UNAPPROVED_TABLE')
        # 独立服务端NULL标记与原生值同语句读取，防止驱动把非NULL零日期折叠成None。
        if columns is None:
            observed=self.query('SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s ORDER BY ORDINAL_POSITION',
                (SCHEMA,table))
            columns=[row[0] for row in observed]
        else:
            require(isinstance(columns,list),'METADATA_REQUIRED')
            columns=list(columns)
        require(columns and len(columns)==len(set(columns)) and primary in columns and
            all(isinstance(name,str) and re.fullmatch('[A-Za-z_][A-Za-z_0-9]*',name) and
                not name.startswith('__nexus_np7_null_') for name in columns),'METADATA_REQUIRED')
        null_checks=','.join(f'(`{name}` IS NULL) AS `__nexus_np7_null_{index}`' for index,name in enumerate(columns))
        cursor=self.connection.cursor(prepared=True)
        try:
            cursor.execute(f'SELECT *,{null_checks} FROM `{SCHEMA}`.`{table}` ORDER BY `{primary}`')
            names=[part[0] for part in cursor.description]
            require(names==columns+[f'__nexus_np7_null_{index}' for index in range(len(columns))],'FULL_ROW_SNAPSHOT_REQUIRED')
            for row in cursor:
                require(len(row)==2*len(columns),'FULL_ROW_SNAPSHOT_REQUIRED')
                values=row[:len(columns)];flags=row[len(columns):]
                require(all(type(flag)is int and flag in (0,1) and (value is None)==bool(flag)
                    for value,flag in zip(values,flags)),'NATIVE_NULL_IDENTITY_LOST')
                yield dict(zip(columns,values))
        finally:cursor.close()
    def verify_restore(self,backup):
        restored={};created=[]
        try:
            for index,table in enumerate(STEP_ORDER):
                temporary=f'nexus_np7_restore_{self.nonce}_{index}'
                self.query(f'CREATE TEMPORARY TABLE `{SCHEMA}`.`{temporary}` LIKE `{SCHEMA}`.`{table}`');created.append(temporary)
                columns=[col['name'] for col in backup['metadata'][table]['columns']]
                cursor=self.connection.cursor(prepared=True)
                try:
                    sql=f'INSERT INTO `{SCHEMA}`.`{temporary}` ('+','.join('`'+name+'`' for name in columns)+') VALUES ('+','.join('%s' for _ in columns)+')'
                    for row in backup['rows'][table]:cursor.execute(sql,tuple(decode_scalar(row[name]) for name in columns))
                finally:cursor.close()
                require(not self.query('SHOW WARNINGS'),'RESTORE_WARNINGS')
                digest=hashlib.sha256();count=0
                for row in self.iter_named_rows(temporary,PRIMARY_KEYS[table],columns=columns):
                    encoded=canonical({name:checked(scalar,value) for name,value in row.items()})
                    digest.update(str(len(encoded)).encode()+b':'+encoded);count+=1
                require(digest.hexdigest()==backup['tables'][table]['rawRowsSha256'] and count==backup['tables'][table]['rows'],'BACKUP_RESTORE_MISMATCH')
                restored[table]=count
            return {'status':'VERIFIED','mode':'OWNED_TEMPORARY_TABLE_FULL_RAW_ROWS','restoredRows':restored}
        finally:
            for name in created:self.query(f'DROP TEMPORARY TABLE `{SCHEMA}`.`{name}`')
    def lock(self):
        self.query('LOCK TABLES '+', '.join(f'`{SCHEMA}`.`{table}` WRITE' for table in STEP_ORDER))
    def alter(self,table,sql):
        require(table in STEP_ORDER and sql==render_ddl(self.describe(),table),'DDL_NOT_REVIEWED')
        self.query(sql);require(not self.query('SHOW WARNINGS'),'DDL_WARNINGS_REQUIRE_REVIEW')
    def remove_fence(self,journal):
        self.verify_fence()
        for spec in self.specs:
            journal.append('FENCE_TRIGGER_DROP_ATTEMPT',trigger=spec['name'])
            self.query(f"DROP TRIGGER `{SCHEMA}`.`{spec['name']}`")
            require(not self.query('SHOW WARNINGS'),'FENCE_DROP_WARNINGS')
            journal.append('FENCE_TRIGGER_DROPPED',trigger=spec['name'])
        self.fence_active=False
        require(all(self.read_triggers(table)==[] for table in STEP_ORDER),'FENCE_RELEASE_UNPROVEN')
