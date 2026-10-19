"""Python 3.10+、零第三方依赖的离线审计/修复入口。所有命令均不连接数据库。"""
import argparse
import json
from pathlib import Path
import sys
from audit import audit
from core import digest, load, write_new
from repair import apply, revert
from sql_writer import emit
import stock_converter
import stock_conversion_sql
import policy_adoption


def _paths(output_paths,input_paths):
    outputs=[Path(p).resolve() for p in output_paths]
    inputs={Path(p).resolve() for p in input_paths if p is not None}
    if len(set(outputs))!=len(outputs) or any(p in inputs for p in outputs):
        raise ValueError("输出不能覆盖输入、备份或其他输出")
    if any(p.exists() for p in outputs):raise ValueError("输出已存在；请使用新路径保留原始证据")
    if any(not p.parent.is_dir() for p in outputs):raise ValueError("输出目录必须已存在")


def _policy_adoption_commands(commands):
    a=commands.add_parser("policy-adoption-check",help="凭原始归档策略、审批与实盘核实历史工单，不使用当前主数据兜底")
    a.add_argument("--snapshot",required=True);a.add_argument("--plan",required=True);a.add_argument("--output",required=True)
    a=commands.add_parser("policy-adoption-plan",help="生成独立采纳审批导入日志及候选快照，不执行数据库")
    a.add_argument("--snapshot",required=True);a.add_argument("--plan",required=True);a.add_argument("--output",required=True);a.add_argument("--snapshot-output",required=True)
    a=commands.add_parser("policy-adoption-emit-sql",help="生成仅INSERT已独立批准的采纳申请的单ERP待审SQL，不执行")
    a.add_argument("--snapshot",required=True);a.add_argument("--journal",required=True);a.add_argument("--output",required=True)
    a.add_argument("--target");a.add_argument("--backup-reference");a.add_argument("--allow-db-writes",action="store_true")


def _policy_adoption_cli(args):
    inputs=[args.snapshot,getattr(args,"plan",None),getattr(args,"journal",None)]
    outputs=[args.output]+([args.snapshot_output] if hasattr(args,"snapshot_output") else [])
    _paths(outputs,inputs);snapshot=load(args.snapshot)
    if args.command=="policy-adoption-check":
        try:
            result=policy_adoption.prepare(snapshot,load(args.plan))
            report={"schema_version":1,"ready":True,"status":result["status"],"tasks":[]}
        except ValueError as blocked:
            report={"schema_version":1,"ready":False,"status":"UNKNOWN_REVIEW_REQUIRED","tasks":[{"code":"ORIGINAL_POLICY_REVIEW","detail":str(blocked),"action":"VERIFY_ORIGINAL_POLICY_AND_CENSUS; NO_CURRENT_MASTER_FALLBACK"}]}
        write_new(args.output,report);return 0 if report["ready"] else 3
    if args.command=="policy-adoption-plan":
        journal=policy_adoption.prepare(snapshot,load(args.plan))
        write_new(args.output,journal);write_new(args.snapshot_output,journal["after_snapshot"])
    else:
        sql=policy_adoption.emit(snapshot,load(args.journal),target=args.target,backup_reference=args.backup_reference,allow_db_writes=args.allow_db_writes)
        write_new(args.output,sql,text=True)
    return 0


def _conversion_commands(commands):
    a=commands.add_parser("convert-check",help="当前库存转换只读门禁及差异任务")
    a.add_argument("--snapshot",required=True);a.add_argument("--plan",required=True);a.add_argument("--output")
    a=commands.add_parser("convert-apply",help="凭实盘把当前库存离线转换为已有正式SN，不执行数据库")
    a.add_argument("--snapshot",required=True);a.add_argument("--plan",required=True);a.add_argument("--previous-journal")
    a.add_argument("--output",required=True);a.add_argument("--journal-output",required=True)
    a=commands.add_parser("convert-revert",help="恢复当前库存业务值；新业务只输出补偿任务，保留转换审计")
    a.add_argument("--snapshot",required=True);a.add_argument("--journal",required=True)
    a.add_argument("--output",required=True);a.add_argument("--journal-output",required=True);a.add_argument("--compensation-output",required=True)
    a=commands.add_parser("convert-emit-sql",help="独立converter单库待审apply/revert SQL，永不连接执行")
    a.add_argument("--snapshot",required=True);a.add_argument("--journal",required=True);a.add_argument("--mode",choices=("apply","revert"),default="apply")
    a.add_argument("--target");a.add_argument("--backup-reference");a.add_argument("--allow-db-writes",action="store_true")
    a.add_argument("--output",required=True);a.add_argument("--compensation-output")


def _conversion_cli(args):
    inputs=[args.snapshot]+[getattr(args,name,None) for name in ("plan","journal","previous_journal")]
    outputs=[getattr(args,name,None) for name in ("output","journal_output","compensation_output")]
    _paths([p for p in outputs if p is not None],inputs)
    snapshot=load(args.snapshot)
    try:
        if args.command=="convert-check":
            report=stock_converter.assess(snapshot,load(args.plan))
            if args.output:write_new(args.output,report)
            else:print(json.dumps(report,ensure_ascii=False,sort_keys=True,indent=2))
            return 0 if report["ready"] else 3
        if args.command=="convert-apply":
            after,journal=stock_converter.apply(snapshot,load(args.plan),load(args.previous_journal) if args.previous_journal else None)
            write_new(args.output,after);write_new(args.journal_output,journal)
        elif args.command=="convert-revert":
            restored,journal=stock_converter.revert(snapshot,load(args.journal))
            write_new(args.output,restored);write_new(args.journal_output,journal)
            write_new(args.compensation_output,{"schema_version":1,"status":"REVERTED","run_id":journal["run_id"],"compensation_tasks":[]})
        else:
            sql=stock_conversion_sql.emit(snapshot,load(args.journal),target=args.target,backup_reference=args.backup_reference,allow_db_writes=args.allow_db_writes,mode=args.mode)
            write_new(args.output,sql,text=True)
        return 0
    except stock_converter.CompensationRequired as blocked:
        if args.compensation_output:write_new(args.compensation_output,blocked.report)
        else:print(json.dumps(blocked.report,ensure_ascii=False,sort_keys=True,indent=2))
        return 3


def main(argv=None):
    parser=argparse.ArgumentParser(description="物料追溯离线工具：默认只读JSON，无数据库执行入口")
    commands=parser.add_subparsers(dest="command")
    a=commands.add_parser("audit",help="只读审计JSON并输出数量/成本/映射差异")
    a.add_argument("--snapshot",required=True);a.add_argument("--output")
    h=commands.add_parser("hash",help="计算规范JSON SHA256，便于形成明确批准计划")
    h.add_argument("--file",required=True)
    a=commands.add_parser("apply",help="只向新JSON文件应用证据计划，并输出运行日志")
    a.add_argument("--snapshot",required=True);a.add_argument("--plan",required=True)
    a.add_argument("--previous-journal");a.add_argument("--output",required=True);a.add_argument("--journal-output",required=True)
    a=commands.add_parser("revert",help="仅在整个导出范围无新业务时恢复JSON；否则要求补偿")
    a.add_argument("--snapshot",required=True);a.add_argument("--journal",required=True)
    a.add_argument("--output",required=True);a.add_argument("--journal-output",required=True)
    a=commands.add_parser("emit-sql",help="生成单数据源待审写SQL；永不执行")
    a.add_argument("--mode",choices=("apply","revert"),default="apply")
    a.add_argument("--snapshot",required=True);a.add_argument("--journal",required=True)
    a.add_argument("--target",help="必须显式指定erp:库名或mes:库名，并匹配快照中的server_uuid")
    a.add_argument("--backup-reference",help="操作者另行核实的备份引用，不接受口令或连接串")
    a.add_argument("--allow-db-writes",action="store_true",help="确认输出包含DML；此标记不会建立连接或执行SQL")
    a.add_argument("--output",required=True)
    _conversion_commands(commands)
    _policy_adoption_commands(commands)
    args=parser.parse_args(argv)
    if args.command is None:parser.print_help();return 0
    try:
        if args.command.startswith("policy-adoption-"):return _policy_adoption_cli(args)
        if args.command.startswith("convert-"):return _conversion_cli(args)
        if args.command=="hash":
            print(digest(load(args.file)));return 0
        snapshot=load(args.snapshot)
        if args.command=="audit":
            result=audit(snapshot)
            if args.output:
                _paths([args.output],[args.snapshot]);write_new(args.output,result)
            else:print(json.dumps(result,ensure_ascii=False,indent=2,sort_keys=True))
        elif args.command=="apply":
            _paths([args.output,args.journal_output],[args.snapshot,args.plan,args.previous_journal])
            after,journal=apply(snapshot,load(args.plan),load(args.previous_journal) if args.previous_journal else None)
            write_new(args.output,after);write_new(args.journal_output,journal)
        elif args.command=="revert":
            _paths([args.output,args.journal_output],[args.snapshot,args.journal])
            before,journal=revert(snapshot,load(args.journal))
            write_new(args.output,before);write_new(args.journal_output,journal)
        else:
            _paths([args.output],[args.snapshot,args.journal])
            sql=emit(snapshot,load(args.journal),target=args.target,backup_reference=args.backup_reference,allow_db_writes=args.allow_db_writes,mode=args.mode)
            write_new(args.output,sql,text=True)
        return 0
    except (ValueError,TypeError,KeyError,OSError) as error:
        print("拒绝操作："+str(error),file=sys.stderr)
        return 2

if __name__=="__main__":
    raise SystemExit(main())
