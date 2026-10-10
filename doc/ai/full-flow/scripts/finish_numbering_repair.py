"""Plan tenant-bound, number-only maintenance; no connections, writes or code generation.

Numbers must already have been reserved through the native rule service. The caller
supplies patterns derived from the actual rule parts, then verifies current locked
rows against each complete ``before`` snapshot before applying these operations.
"""
from collections import Counter
from copy import deepcopy
import re

_CONTEXTS = {
    ('so_sales_order_header','so_no'): ('so_id',0),
    ('prd_production_plan','plan_no'): ('plan_id',1),
    ('prd_production_plan','reference_no'): ('plan_id',1),
    ('prd_material_requirement','plan_no'): ('requirement_id',2),
}
_KINDS = {
    'sales_order': ('soId','soNo','SO_ORDER_CODE'),
    'production_plan': ('planId','planNo','PRD_PLAN_CODE'),
}

def _require(condition, message):
    if not condition: raise ValueError(message)

def _id(value):
    _require(not isinstance(value,(bool,float)), 'invalid identifier type')
    text=str(value)
    _require(bool(re.fullmatch(r'[1-9][0-9]{0,18}',text)), 'invalid identifier')
    _require(int(text)<=9223372036854775807, 'identifier exceeds bigint')
    return text


def build_number_repairs(cases, allocations, references, *, tenant_id='000000',
                         allowed_extra_plan_ids=(), code_patterns=None):
    """Return stable row-level operations, allowing only exact in-batch code fields.

    Expected inputs are API case identities and a privately saved ERP row inventory.
    An extra auto-created plan must be explicitly allowlisted, share the actual sale
    line, and be DRAFT/CLOSED. No business status/quantity/source-ID edit is returned.
    """
    _require(isinstance(tenant_id,str) and bool(re.fullmatch(r'[A-Za-z0-9_-]{1,20}',tenant_id)), 'invalid tenant')
    _require(isinstance(cases,list) and bool(cases), 'empty batch')
    _require(isinstance(code_patterns,dict) and all(k in code_patterns for k in ('SO_ORDER_CODE','PRD_PLAN_CODE')), 'actual code rule patterns required')
    by_sale={};by_plan={};by_line={};old_sale={};old_plan={}
    for case in cases:
        so=_id(case.get('soId'));plan=_id(case.get('planId'));line=_id(case.get('lineId'))
        _require(so not in by_sale and plan not in by_plan and line not in by_line, 'duplicate batch identity')
        _require(isinstance(case.get('soNo'),str) and bool(case['soNo']) and isinstance(case.get('planNo'),str) and bool(case['planNo']), 'missing old codes')
        _require(case['soNo'] not in old_sale and case['planNo'] not in old_plan, 'duplicate old codes')
        _require(isinstance(case.get('requirementCount'),int) and not isinstance(case['requirementCount'],bool) and case['requirementCount']>0, 'missing requirement count')
        by_sale[so]=case;by_plan[plan]=case;by_line[line]=case;old_sale[case['soNo']]=case;old_plan[case['planNo']]=case
    _require(not (set(old_sale)&set(old_plan)), 'ambiguous old code')
    extras={_id(v) for v in allowed_extra_plan_ids};_require(not (extras&set(by_plan)), 'extra plan overlaps batch root')
    native={};seen_new=set()
    for allocation in allocations:
        kind=allocation.get('kind');_require(kind in _KINDS,'unknown allocation kind')
        idfield,codefield,rule=_KINDS[kind];identity=_id(allocation.get('id'))
        lookup=by_sale if kind=='sales_order' else by_plan;_require(identity in lookup,'allocation outside batch')
        case=lookup[identity];key=(kind,identity)
        _require(key not in native,'duplicate allocation')
        _require(allocation.get('rule')==rule and allocation.get('oldCode')==case[codefield],'allocation source differs from batch')
        code=allocation.get('newCode')
        _require(isinstance(code,str) and bool(re.fullmatch(code_patterns[rule],code)),'number does not match actual native rule')
        _require(code not in seen_new and code not in old_sale and code not in old_plan,'duplicate or reused number')
        native[key]=code;seen_new.add(code)
    _require(set(native)=={(kind,identity) for kind,lookup in [('sales_order',by_sale),('production_plan',by_plan)] for identity in lookup},'incomplete allocations')
    new_sale={code:native[('sales_order',_id(case['soId']))] for code,case in old_sale.items()}
    new_plan={code:native[('production_plan',_id(case['planId']))] for code,case in old_plan.items()}
    operations={};observed_sales=set();observed_plans=set();observed_refs=set();mrp_counts=Counter()
    for reference in references:
        table=reference.get('table');field=reference.get('field');context=(table,field)
        _require(context in _CONTEXTS,'unexpected repair table or field')
        pk,order=_CONTEXTS[context]
        _require(reference.get('primaryKey')==[pk] and reference.get('engine')=='InnoDB','unverified transactional primary key')
        _require(isinstance(reference.get('rows'),list),'missing reference rows')
        for row in reference['rows']:
            _require(row.get('tenant_id')==tenant_id,'foreign tenant row')
            identity=_id(row.get(pk));old=row.get(field)
            if table=='so_sales_order_header':
                _require(identity in by_sale and old==by_sale[identity]['soNo'],'sales row outside batch')
                new=new_sale[old];observed_sales.add(identity)
            elif table=='prd_production_plan' and field=='plan_no':
                _require(identity in by_plan and old==by_plan[identity]['planNo'],'plan row outside batch')
                _require(_id(row.get('order_line_id'))==_id(by_plan[identity]['lineId']),'plan sale line mismatch')
                new=new_plan[old];observed_plans.add(identity)
            elif table=='prd_production_plan':
                _require(old in old_sale,'unmatched sales reference')
                case=old_sale[old];_require(_id(row.get('order_line_id'))==_id(case['lineId']),'reference sale line mismatch')
                _require(identity==_id(case['planId']) or identity in extras,'unapproved extra plan')
                if identity in extras:_require(row.get('status') in ('DRAFT','CLOSED'),'extra plan is live')
                new=new_sale[old];observed_refs.add(identity)
            else:
                parent=_id(row.get('plan_id'))
                _require(parent in by_plan and old==by_plan[parent]['planNo'],'requirement parent mismatch')
                new=new_plan[old];mrp_counts[parent]+=1
            key=(table,identity)
            if key not in operations:
                operations[key]={'table':table,'primaryKey':pk,'id':identity,'tenantId':tenant_id,'before':deepcopy(row),'changes':{},'_order':order}
            operation=operations[key]
            _require(operation['before']==row,'conflicting before snapshots')
            _require(field not in operation['changes'],'duplicate reference row')
            operation['changes'][field]=new
    _require(observed_sales==set(by_sale) and observed_plans==set(by_plan),'missing main rows')
    _require(observed_refs==set(by_plan)|extras,'incomplete plan references')
    _require(mrp_counts==Counter({identity:case['requirementCount'] for identity,case in by_plan.items()}),'incomplete requirement references')
    ordered=sorted(operations.values(),key=lambda x:(x['_order'],int(x['id'])))
    for operation in ordered:operation.pop('_order')
    return ordered
