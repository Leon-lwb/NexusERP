"""Execute the owner's independent strict-tenant entry only in newly owned synthetic schemas."""
from __future__ import annotations
from harness import ident, sql_value

STRICT_ERP="script/sql/update/2026-10-19-material-trace-strict-constraints-erp.sql"
TABLES=("wm_sn","wm_material_stock")

def strict_sql(c,schema,**overrides):
    values={"expected_schema":schema,"expected_server_uuid":c.query(schema,"SELECT @@server_uuid;").scalar,
        "backup_reference":"SYNTHETIC-EMPTY-BASELINE-"+schema,
        "maintenance_reference":"OWNED-TMPFS-NO-BUSINESS-WRITERS",
        "strict_tenant_cleanup_confirmed":"TENANT_CLEANUP_VERIFIED"}
    c.require(set(overrides).issubset(values),"Unknown strict-entry guard override")
    values.update(overrides)
    setup="\n".join("SET @material_trace_"+key+"="+sql_value(value)+";" for key,value in values.items())
    return setup+"\n"+c.snapshot.text(STRICT_ERP)

def tenants(c,schema):
    return c.query(schema,"SELECT TABLE_NAME,IS_NULLABLE,COALESCE(COLUMN_DEFAULT,'__NULL__') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('wm_sn','wm_material_stock') AND COLUMN_NAME='tenant_id' ORDER BY TABLE_NAME;").rows

def create(c,key):
    c.new_schema("strict_"+key,"erp",subset=True)
    schema=c.dbs["strict_"+key]
    c.require(len(tenants(c,schema))==2,"Strict fixture lost an owner table")
    return schema

def unchanged(c,schema,before):
    c.require(tenants(c,schema)==before,"Strict entry performed a partial tenant ALTER before its failing preflight")

def strict_generator(c):
    c.require(c.snapshot.text(STRICT_ERP)==c.snapshot.contract["strict_constraints"](),"Strict SQL differs from owner generator; never patch the DDL in the harness")
    return {"source":STRICT_ERP,"source_sha256":c.snapshot.hashes[STRICT_ERP],"matches_generator":True}

def guard(c,key,override):
    schema=create(c,"guard_"+key);before=tenants(c,schema)
    output=c.box.run_sql(schema,strict_sql(c,schema,**override),error=1644)
    unchanged(c,schema,before)
    return {"schema":schema,"override":override,"mysql_output":output,"tenant_columns_unchanged":True}

def unresolved(c,table,value):
    suffix="null" if value is None else "blank"
    schema=create(c,suffix+"_"+table);before=tenants(c,schema);pk="sn_id" if table=="wm_sn" else "stock_id";row_id=c.uid()
    values={pk:row_id,"tenant_id":value,"material_id":201}
    values.update({"sn_code":"SYNTH-UNRESOLVED-"+str(row_id)} if table=="wm_sn" else {"warehouse_id":301,"stock_quantity":"0.0000","tracking_version":1})
    c.insert(schema,table,values)
    probe="SELECT COUNT(*) FROM "+ident(table)+" WHERE "+ident(pk)+"="+str(row_id)+" AND tenant_id "+("IS NULL" if value is None else "="+sql_value(value))+";"
    output=c.box.run_sql(schema,strict_sql(c,schema),error=1644)
    c.require("仍有NULL或空白租户" in output,"Strict entry failed for another reason instead of preserving unresolved tenant evidence")
    unchanged(c,schema,before);c.require(c.query(schema,probe).scalar=="1","Strict entry changed or deleted an unresolved tenant")
    return {"schema":schema,"table":table,"input_tenant":value,"row_id":str(row_id),"mysql_output":output,"unresolved_row_preserved":True,"tenant_columns_unchanged":True}

def conflicts(c,kind):
    schema=create(c,kind);before=tenants(c,schema);identity=c.uid()
    if kind=="duplicate_code":
        c.query(schema,"ALTER TABLE wm_sn DROP INDEX uk_sn_code;")
        for _ in range(2):c.insert(schema,"wm_sn",{"sn_id":c.uid(),"tenant_id":"901001","material_id":201,"sn_code":"SYNTH-DUPLICATE"})
        count_sql="SELECT COUNT(*) FROM wm_sn;";expected_count="2";message="正式码存在旧唯一性冲突"
    elif kind=="duplicate_active_sn":
        c.query(schema,"ALTER TABLE wm_material_stock DROP INDEX uk_material_stock_active_sn;")
        for _ in range(2):c.insert(schema,"wm_material_stock",{"stock_id":c.uid(),"tenant_id":"901001","warehouse_id":c.uid(),"material_id":201,"sn_id":identity,"tracking_version":2,"stock_quantity":"1.0000"})
        count_sql="SELECT COUNT(*) FROM wm_material_stock;";expected_count="2";message="新版正库存身份缺失或重复"
    else:
        c.require(kind=="missing_active_sn","Unknown strict conflict fixture")
        c.insert(schema,"wm_material_stock",{"stock_id":c.uid(),"tenant_id":"901001","warehouse_id":301,"material_id":201,"sn_id":None,"tracking_version":2,"stock_quantity":"1.0000"})
        count_sql="SELECT COUNT(*) FROM wm_material_stock WHERE sn_id IS NULL;";expected_count="1";message="新版正库存身份缺失或重复"
    output=c.box.run_sql(schema,strict_sql(c,schema),error=1644)
    c.require(message in output,"Strict entry missed the dirty legacy data guard")
    unchanged(c,schema,before);c.require(c.query(schema,count_sql).scalar==expected_count,"Strict entry silently repaired conflicting rows")
    return {"schema":schema,"fixture":kind,"mysql_output":output,"rows_preserved":expected_count,"tenant_columns_unchanged":True,
        "boundary":"Only the dedicated synthetic fixture drops an index to reproduce a dirty legacy baseline; owner DDL is executed unchanged"}

def success(c):
    schema=create(c,"success");identity=c.uid();code="SYNTH-STRICT-"+str(identity)
    c.insert(schema,"wm_sn",{"sn_id":identity,"tenant_id":"901001","material_id":201,"sn_code":code})
    stock={"stock_id":c.uid(),"tenant_id":"901001","material_id":201,"warehouse_id":301,"sn_id":identity,"tracking_version":2,"stock_quantity":"1.0000"}
    c.insert(schema,"wm_material_stock",stock)
    for _ in range(2):c.insert(schema,"wm_material_stock",{**stock,"stock_id":c.uid(),"stock_quantity":"0.0000"})
    output=c.box.run_sql(schema,strict_sql(c,schema));c.column_cache={}
    columns=tenants(c,schema);c.require(columns==[["wm_material_stock","NO","__NULL__"],["wm_sn","NO","__NULL__"]],"Strict entry did not remove NULL tenant and fallback defaults")
    null_errors={}
    null_errors["wm_sn"]=c.insert(schema,"wm_sn",{"sn_id":c.uid(),"tenant_id":None,"material_id":201,"sn_code":code+"-NULL"},error=1048).errors
    null_errors["wm_material_stock"]=c.insert(schema,"wm_material_stock",{**stock,"stock_id":c.uid(),"tenant_id":None},error=1048).errors
    duplicate_code=c.insert(schema,"wm_sn",{"sn_id":c.uid(),"tenant_id":"901001","material_id":201,"sn_code":code},error=1062).errors
    duplicate_stock=c.insert(schema,"wm_material_stock",{**stock,"stock_id":c.uid()},error=1062).errors
    c.insert(schema,"wm_sn",{"sn_id":c.uid(),"tenant_id":"901002","material_id":201,"sn_code":code})
    historical=c.query(schema,"SELECT COUNT(*) FROM wm_material_stock WHERE tenant_id='901001' AND sn_id="+str(identity)+" AND stock_quantity=0 AND active_sn_id IS NULL;").scalar
    c.require(historical=="2","Strict tenant entry removed allowed zero-quantity history")
    c.box.run_sql(schema,strict_sql(c,schema));c.require(tenants(c,schema)==columns,"Replaying strict entry changed tenant metadata")
    return {"schema":schema,"source":STRICT_ERP,"source_sha256":c.snapshot.hashes[STRICT_ERP],"mysql_output":output,"tenant_columns":columns,
        "explicit_null_errors":null_errors,"duplicate_code_error":duplicate_code,"duplicate_active_stock_error":duplicate_stock,"allowed_zero_history_rows":historical,"cross_tenant_code_allowed":True,"replay_metadata_unchanged":True,
        "boundary":"Actual independent strict DDL in an empty owned schema with synthetic evidence markers, not proof of production cleanup, backups or a maintenance window"}

def run_strict_cases(c):
    c.case("strict_entry_matches_owner_generator",lambda:strict_generator(c),["migration_erp"])
    required=["strict_entry_matches_owner_generator"]
    for key,override in [("schema",{"expected_schema":"not_the_synthetic_schema"}),("uuid",{"expected_server_uuid":"00000000-0000-0000-0000-000000000000"}),
        ("backup",{"backup_reference":None}),("maintenance",{"maintenance_reference":""}),("cleanup",{"strict_tenant_cleanup_confirmed":None})]:
        c.case("strict_tenant_guard_"+key,lambda key=key,override=override:guard(c,key,override),required)
    for table in TABLES:
        for value in (None,"   "):
            c.case("strict_tenant_rejects_"+("null_" if value is None else "blank_")+table,lambda table=table,value=value:unresolved(c,table,value),required)
    for kind in ("duplicate_code","duplicate_active_sn","missing_active_sn"):
        c.case("strict_tenant_stops_"+kind,lambda kind=kind:conflicts(c,kind),required)
    c.case("strict_tenant_success_null_rejected_unique_and_zero_history",lambda:success(c),required)
