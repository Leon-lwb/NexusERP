"""确定性的验收案例预期值；绝不创建业务记录。"""
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path
import json
import re

CASES_PATH = Path(__file__).resolve().parents[2] / 'benchmark' / 'delivery-risk' / 'cases.jsonl'

@dataclass(frozen=True)
class BusinessDates:
    order_date: date
    material_required_date: date
    promise_date: date
    purchase_arrival_date: date
    production_start_date: date
    production_end_date: date

@dataclass(frozen=True)
class FlowCase:
    case_id: str
    group: str
    risk_facts: dict
    expected_status: str
    expected_actions: tuple[str, ...]

def build_business_dates(base_date: date) -> BusinessDates:
    if not isinstance(base_date, date):
        raise TypeError('base_date must be a date')
    return BusinessDates(base_date, base_date + timedelta(days=3),
                         base_date + timedelta(days=12), base_date + timedelta(days=15),
                         base_date + timedelta(days=4), base_date + timedelta(days=11))

def validate_business_name(name: str) -> bool:
    return isinstance(name, str) and bool(name.strip()) and not re.search(
        r'测试|演示|临时|DEMO-|TEST-|MOCK-', name, flags=re.IGNORECASE)

def _historical_case(number: int) -> dict:
    if type(number) is not int or not 1 <= number <= 40:
        raise ValueError('case number must be in 1..40')
    with CASES_PATH.open(encoding='utf-8') as source:
        cases = [json.loads(line) for line in source if line.strip()]
    case = next((item for item in cases if item['caseId'] == f'DELIVERY-{number:03d}'), None)
    if case is None:
        raise ValueError('historical case missing')
    return case

def expected_case_group(number: int) -> str:
    case = _historical_case(number)
    title = case['title'].rsplit(' ', 1)[0]
    if title == 'single-shortage':
        return 'shortage_supplier_delay'
    if title == 'capacity-bottleneck':
        return 'capacity_bottleneck'
    if title == 'incomplete-evidence' and not case['facts']['capacityComplete']:
        return 'capacity_evidence_missing'
    return title.replace('-', '_')

def build_case_facts(number: int) -> FlowCase:
    case = _historical_case(number)
    return FlowCase(case['caseId'], expected_case_group(number), dict(case['facts']),
                    case['expectedStatus'], tuple(case['requiredActions']))
