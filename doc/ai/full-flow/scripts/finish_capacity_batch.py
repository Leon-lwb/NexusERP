"""FINISH 来源和窗口总容量上界。纯函数，不连接服务，不创建或修改业务数据。

输入容量须由调用方从同租户现行资源/日历/人力及占用核验；本模块不造容量。
只提供保守总量界，不等同于逐日排程可行性，应用前仍须由实际 APS 复核。
"""
from decimal import Decimal, InvalidOperation, ROUND_CEILING, ROUND_FLOOR
import re

CENT = Decimal("0.01")
MAX_ID = 2**63 - 1


def _id(value, *, allow_zero=False):
    if isinstance(value, bool): raise ValueError("invalid identity")
    text = str(value)
    if not re.fullmatch(r"0|[1-9][0-9]*", text): raise ValueError("invalid identity")
    number = int(text)
    if number > MAX_ID or number < (0 if allow_zero else 1): raise ValueError("invalid identity")
    return text


def _decimal(value, *, positive=False):
    if isinstance(value, (bool, float)): raise ValueError("use exact decimal strings")
    try: result = Decimal(str(value))
    except (InvalidOperation, TypeError, ValueError): raise ValueError("invalid decimal") from None
    if not result.is_finite() or result < 0 or positive and result <= 0:
        raise ValueError("invalid decimal")
    return result


def finish_category_ids(categories):
    if not isinstance(categories, list) or not categories: raise ValueError("missing categories")
    by_id = {}
    for category in categories:
        identity = _id(category.get("categoryId"))
        if identity in by_id: raise ValueError("duplicate category")
        by_id[identity] = category
    roots = {identity for identity, category in by_id.items()
             if category.get("categoryCode") == "FINISH" and str(category.get("status")) == "1"}
    if len(roots) != 1: raise ValueError("one enabled FINISH root required")
    result = set(roots)
    while True:
        more = {identity for identity, category in by_id.items()
                if str(category.get("parentId")) in result and str(category.get("status")) == "1"}
        if more <= result: return result
        result |= more


def plan_orders(products, categories, budgets, *, occupied=None, lots_per_product=4,
                utilization="0.85", tenant_id="000000"):
    """用共享资源总量和人力守恒求最大的统一批量；明确的单笔上限另取最小值。

    每个内部工序：ceil_0.01(标准单件工时 × 数量) + ceil_0.01(本单准备工时)。
    同站、同车间人力、同批多个订单都会合计，不逐产品重复使用全量容量。
    """
    if not isinstance(products, list) or not products: raise ValueError("missing products")
    if isinstance(lots_per_product, bool) or not isinstance(lots_per_product, int) or not 1 <= lots_per_product <= 20:
        raise ValueError("invalid lot count")
    if not isinstance(tenant_id, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,20}", tenant_id):
        raise ValueError("invalid tenant")
    ratio = _decimal(utilization, positive=True)
    if ratio > 1: raise ValueError("capacity expansion refused")
    if not isinstance(budgets, dict) or not budgets: raise ValueError("missing capacity")
    capacity = {}
    for key, value in budgets.items():
        if not isinstance(key, str) or not re.fullmatch(r"(?:WORKSTATION|WORKSHOP|LABOR):[1-9][0-9]*", key):
            raise ValueError("invalid resource key")
        _id(key.split(":")[1]); capacity[key] = _decimal(value, positive=True)
    occupancy = {key: Decimal(0) for key in capacity}
    if occupied is not None:
        if not isinstance(occupied, dict): raise ValueError("invalid occupancy")
        for key, value in occupied.items():
            if key not in capacity: raise ValueError("unknown occupied resource")
            occupancy[key] = _decimal(value)
            if occupancy[key] > capacity[key]: raise ValueError("resource already overbooked")
    free = {key: capacity[key] * ratio - occupancy[key] for key in capacity}
    allowed = finish_category_ids(categories)
    normalized = []; identities = set()
    unit = {}; setup = {}
    for product in products:
        identity = _id(product.get("materialId"))
        if identity in identities: raise ValueError("duplicate product")
        identities.add(identity)
        if _id(product.get("categoryId")) not in allowed or str(product.get("status")) != "1":
            raise ValueError("product not in enabled FINISH category")
        if product.get("tenantId", tenant_id) != tenant_id: raise ValueError("foreign tenant product")
        raw_steps = product.get("steps")
        if not isinstance(raw_steps, list) or not raw_steps: raise ValueError("missing verified route")
        weight = product.get("quantityWeight", 1)
        if isinstance(weight, bool) or not isinstance(weight, int) or weight < 1:
            raise ValueError("invalid quantity weight")
        steps = []
        for step in raw_steps:
            resource, labor = step.get("resourceKey"), step.get("workshopKey")
            if resource not in capacity or labor not in capacity or not str(labor).startswith("LABOR:"):
                raise ValueError("unknown route resource or labor pool")
            if not str(resource).startswith(("WORKSTATION:", "WORKSHOP:")):
                raise ValueError("route must consume a physical resource")
            hours = _decimal(step.get("unitHours"), positive=True)
            ready = _decimal(step.get("setupHours", "0")).quantize(CENT, rounding=ROUND_CEILING)
            people = _decimal(step.get("laborCount"), positive=True)
            steps.append((resource, labor, hours, ready, people))
            for key, factor in ((resource, Decimal(1)), (labor, people)):
                unit[key] = unit.get(key, Decimal(0)) + hours * factor * lots_per_product * weight
                setup[key] = setup.get(key, Decimal(0)) + ready * factor * lots_per_product
        limit = product.get("maxLotQuantity")
        if limit is not None:
            if isinstance(limit, bool) or not isinstance(limit, int) or limit < 1: raise ValueError("invalid lot limit")
        normalized.append((identity, steps, limit, weight))
    upper = min(int(((free[key] - setup[key]) / hours).to_integral_value(rounding=ROUND_FLOOR))
                for key, hours in unit.items())
    if upper < 1: raise ValueError("no capacity for positive lot including setup")

    def loads_at(quantity):
        loads = {key: Decimal(0) for key in capacity}
        for _, steps, limit, weight in normalized:
            count = min(quantity * weight, limit) if limit is not None else quantity * weight
            for resource, labor, hours, ready, people in steps:
                work = (hours * count).quantize(CENT, rounding=ROUND_CEILING) + ready
                loads[resource] += work * lots_per_product
                loads[labor] += (work * people).quantize(CENT, rounding=ROUND_CEILING) * lots_per_product
        return loads

    low, high = 0, upper
    while low < high:
        middle = (low + high + 1) // 2
        loads = loads_at(middle)
        if all(loads[key] <= free[key] for key in capacity): low = middle
        else: high = middle - 1
    if low < 1: raise ValueError("centihour rounding exceeds capacity")
    loads = loads_at(low)
    orders = [{"materialId": identity, "lotIndex": index + 1,
               "quantity": min(low * weight, limit) if limit is not None else low * weight}
              for index in range(lots_per_product) for identity, _, limit, weight in normalized]
    return {"tenantId": tenant_id, "kind": "WINDOW_BOUND_REQUIRES_NATIVE_APS", "orders": orders,
            "loads": {key: format(value, ".2f") for key, value in loads.items()},
            "remaining": {key: format(free[key] - loads[key], "f") for key in capacity},
            "utilizationLimit": format(ratio, "f")}


def allocate_day_capacity(demand, physical_limits, operators, labor_hours, *, fixed=None):
    """按工位负载分配人力工时；不改变现有固定日历，不扩张物理上限。

    输出为工位机器小时，sum(机器小时 × 标准操作人数) <= 当日人力小时。
    固定容量先扣人力，剩余容量按负载比例分配；触达物理上限后重新分配。
    调用方还须提前扣减既有预占及批准请假，避免把旧日历再加一遍。
    """
    if not isinstance(demand, dict) or not demand or not isinstance(physical_limits, dict) or not isinstance(operators, dict):
        raise ValueError("missing daily resource facts")
    if set(demand) != set(physical_limits) or set(demand) != set(operators):
        raise ValueError("daily resource identities differ")
    weights = {key: _decimal(value) for key, value in demand.items()}
    limits = {key: _decimal(value) for key, value in physical_limits.items()}
    people = {key: _decimal(value, positive=True) for key, value in operators.items()}
    available = _decimal(labor_hours)
    result = {key: Decimal(0) for key in demand}
    fixed = fixed or {}
    if not isinstance(fixed, dict) or not set(fixed) <= set(demand): raise ValueError("unknown fixed resource")
    for key, value in fixed.items():
        capacity = _decimal(value)
        if capacity > limits[key] or capacity != capacity.quantize(CENT):
            raise ValueError("fixed capacity exceeds physical or centihour boundary")
        result[key] = capacity
        available -= capacity * people[key]
    if available < 0: raise ValueError("fixed calendar overbooks manpower")
    active = {key for key in demand if key not in fixed and weights[key] > 0 and limits[key] > 0}
    while active and available > 0:
        factor = available / sum(weights[key] * people[key] for key in active)
        capped = {key for key in active if weights[key] * factor >= limits[key]}
        if not capped:
            for key in active: result[key] = (weights[key] * factor).quantize(CENT, rounding=ROUND_FLOOR)
            break
        for key in capped:
            result[key] = limits[key].quantize(CENT, rounding=ROUND_FLOOR)
            available -= result[key] * people[key]
        active -= capped
    if sum(result[key] * people[key] for key in demand) > _decimal(labor_hours):
        raise ValueError("daily manpower conservation violated")
    if any(result[key] > limits[key] for key in demand): raise ValueError("daily physical expansion refused")
    return {key: format(value, ".2f") for key, value in result.items()}


def native_unit_ready(material, unit, *, tenant_id="000000"):
    """对应工单出生的基本单位守卫；换算率为 1 的派生单位仍不能冒充基本单位。

    API VO 可省略租户/删除字段，由调用方已验证的租户作用域约束；字段出现时仍核对。
    不修改或认证任何单位，不为缺失精度、版本或核验时间提供默认值。
    """
    try:
        if not isinstance(material, dict) or not isinstance(unit, dict): return False
        base = _id(material.get("baseUomId"))
        if str(material.get("status")) != "1" or str(unit.get("status")) != "1": return False
        if material.get("tenantId", tenant_id) != tenant_id or unit.get("tenantId", tenant_id) != tenant_id: return False
        if material.get("delFlag", "0") != "0" or unit.get("delFlag", "0") != "0": return False
        if base != _id(unit.get("uomId")) or base != _id(unit.get("primaryId")) or str(unit.get("isBase")) != "1": return False
        if _decimal(unit.get("conversionRate"), positive=True) != Decimal(1): return False
        scale = unit.get("quantityScale")
        if type(scale) is not int or not 0 <= scale <= 4: return False
        return all(isinstance(unit.get(key), str) and unit[key].strip()
                   for key in ("uomName", "conversionVersion", "conversionVerifiedAt"))
    except (TypeError, ValueError):
        return False
