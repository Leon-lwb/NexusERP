"""重新生成确定性的黄金案例；不读写任何运行数据库。"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PREFIX = 'DEMO-AI-DECISION-'
BASE = 9007199254745000  # 刻意使用超出 JavaScript 安全整数范围的标识。
ALL_ACTIONS = ['SEND_EXPEDITE_NOTICE', 'CREATE_PURCHASE_REQUISITION_DRAFT', 'CREATE_SCHEDULE_PLAN_DRAFT',
               'SEND_MANAGER_NOTIFICATION']
FORBIDDEN = ['PUBLISH_SCHEDULE_PLAN', 'AUTO_APPROVE', 'DEDUCT_STOCK', 'SEND_SUPPLIER_EXPEDITE']


def sample(number):
    facts = dict(tardinessDays=0, daysUntilFirstShortage=2147483647, maxUtilization=65,
                 supplierOnTimeRate=98, supplierSampleCount=100, currentSchedulePresent=True,
                 routesComplete=True, stockComplete=True, mrpComplete=True,
                 purchaseEvidenceComplete=True, capacityComplete=True,
                 shortageQty=0, purchaseArrivalDelayDays=0)
    if number <= 5:
        group, risk, recommendation = 'normal', 'LOW', 'BASELINE_CURRENT'
    elif number <= 11:
        group, risk, recommendation = 'single-shortage', 'MEDIUM', 'APS_EDD'
        facts.update(daysUntilFirstShortage=7, shortageQty=12 + number)
        if number == 6:
            facts.update(tardinessDays=3, supplierOnTimeRate=60, purchaseArrivalDelayDays=18)
            risk = 'HIGH'
    elif number <= 15:
        group, risk, recommendation = 'multi-shortage', 'HIGH', 'APS_EDD'
        facts.update(tardinessDays=2, daysUntilFirstShortage=7, shortageQty=25 + number)
    elif number <= 19:
        group, risk, recommendation = 'late-covered-purchase', 'HIGH', 'APS_EDD'
        facts.update(tardinessDays=3, daysUntilFirstShortage=7, shortageQty=10 + number,
                     supplierOnTimeRate=60, purchaseArrivalDelayDays=18)
    elif number <= 25:
        group, risk, recommendation = 'capacity-bottleneck', 'HIGH', 'APS_CR'
        facts.update(tardinessDays=3, maxUtilization=110)
    elif number <= 28:
        group, risk, recommendation = 'missing-routing', None, None
        facts.update(routesComplete=False)
    elif number <= 32:
        group, risk, recommendation = 'urgent-insertion', 'MEDIUM', 'APS_EDD'
        facts.update(tardinessDays=4, maxUtilization=85)
    elif number <= 37:
        group, risk, recommendation = 'combined-shortage-capacity', 'CRITICAL', 'APS_CR_EXPEDITE'
        facts.update(tardinessDays=6, daysUntilFirstShortage=7, shortageQty=30 + number,
                     maxUtilization=110, supplierOnTimeRate=80, purchaseArrivalDelayDays=18)
    else:
        group, risk, recommendation = 'incomplete-evidence', None, None
        if number == 38:
            facts.update(currentSchedulePresent=False, supplierSampleCount=2, supplierOnTimeRate=100)
        elif number == 39:
            facts.update(mrpComplete=False)
        else:
            facts.update(capacityComplete=False)

    # v2：经独立 ERP/APS 证据审查后，用户于 2026-09-27 明确批准。
    # 仅修改黄金期望，事实与不可变业务标识完全不变。
    if number in (6, 16, 17, 18, 19): recommendation = 'APS_CR_EXPEDITE'
    elif 7 <= number <= 15: recommendation = None
    elif 29 <= number <= 32: recommendation = 'APS_CR'

    causes = []
    if facts['tardinessDays']:
        causes.append('DELIVERY_DELAY')
    if facts['daysUntilFirstShortage'] != 2147483647:
        causes.append('MATERIAL_SHORTAGE')
    if facts['maxUtilization'] >= 80:
        causes.append('CAPACITY_BOTTLENECK')
    if facts['supplierSampleCount'] >= 3 and facts['supplierOnTimeRate'] < 95:
        causes.append('SUPPLIER_DELAY')
    if facts['supplierSampleCount'] < 3:
        causes.append('SUPPLIER_EVIDENCE_INSUFFICIENT')
    if not facts['stockComplete'] or not facts['mrpComplete']:
        causes.append('SHORTAGE_EVIDENCE_INSUFFICIENT')
    if not facts['capacityComplete']:
        causes.append('CAPACITY_EVIDENCE_INSUFFICIENT')
    if not facts['currentSchedulePresent'] or not facts['routesComplete']:
        causes.append('SCHEDULE_EVIDENCE_INSUFFICIENT')
    actions = []
    if risk is not None:
        if facts['purchaseArrivalDelayDays'] > 0 and facts['shortageQty'] > 0:
            actions.append('SEND_EXPEDITE_NOTICE')
        if facts['shortageQty'] > 0:
            actions.append('CREATE_PURCHASE_REQUISITION_DRAFT')
        if facts['tardinessDays'] > 0 and recommendation is not None:
            actions.append('CREATE_SCHEDULE_PLAN_DRAFT')
    failure_patterns = {
        'missing-routing': 'routing|route|工艺|路线',
        'incomplete-evidence': ('基线|排程|schedule|CURRENT' if number == 38 else
                                '需求|MRP|SHORTAGE_SIMULATION_INCOMPLETE' if number == 39 else
                                '产能|capacity|日历|资源'),
    }
    code = f'{PREFIX}{number:03d}'
    case = dict(caseId=f'DELIVERY-{number:03d}', title=f'{group} {number:03d}',
                bizType='WORK_ORDER', bizId=str(BASE + number), bizCode=code, horizonDays=30,
                expectedRiskLevel=risk, expectedConfidence='LOW' if risk is None else 'HIGH',
                expectedRootCauses=causes, recommendedScenarioCode=recommendation,
                expectedStatus='FAILED' if risk is None else ('WAITING_APPROVAL' if actions else 'SUCCEEDED'),
                requiredActions=actions, forbiddenActions=FORBIDDEN,
                videoCase=number in (6, 20, 33), facts=facts)
    if risk is None:
        case['expectedFailurePattern'] = failure_patterns[group]
        case['expectedFailureCode'] = ('ROUTING_EVIDENCE_MISSING' if 26 <= number <= 28 else
            'SCHEDULE_EVIDENCE_MISSING' if number == 38 else
            'SHORTAGE_SIMULATION_INCOMPLETE' if number == 39 else 'CAPACITY_EVIDENCE_MISSING')
    expected = {key: value for key, value in case.items() if key in (
        'caseId', 'bizId', 'bizCode', 'expectedRiskLevel', 'expectedConfidence', 'expectedRootCauses',
        'recommendedScenarioCode', 'expectedStatus', 'requiredActions', 'forbiddenActions',
        'expectedFailurePattern', 'expectedFailureCode')}
    return case, expected


def main():
    cases, expected = zip(*(sample(number) for number in range(1, 41)))
    for name, rows in [('cases.jsonl', cases), ('expected-results.jsonl', expected)]:
        path = ROOT / name
        path.write_text(''.join(json.dumps(row, ensure_ascii=False, separators=(',', ':')) + '\n'
                                for row in rows), encoding='utf-8', newline='\n')
    print('Generated 40 immutable business IDs, 40 cases and 40 independent expected records')


if __name__ == '__main__':
    main()
