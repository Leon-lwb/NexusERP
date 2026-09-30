"""独立盒装 M12 安装套件的纯离线契约。

不包含 HTTP/数据库客户端、文件读写、时钟读取、编码生成器或写入开关。
调用或运行本模块不能创建业务记录。请求字典
只描述数据形状，并非执行器。另行明确授权的执行方必须提供真实
读取快照，证明其租户、环境及来源，并重新读回结果。
使用 JSON parse_float=Decimal 或十进制字符串，不能使用二进制浮点数。
"""
from copy import deepcopy
from datetime import date, datetime
from decimal import Decimal, InvalidOperation
import re


class FixtureContractError(ValueError):
    """调用方提供的契约事实缺失、不安全或不一致。"""


FINISHED_NAME = "M12×60六角螺栓安装套件（独立盒装）"
ROUTING_NAME = FINISHED_NAME + "装配包装路线"
BOM_NAME = FINISHED_NAME + "制造BOM"
# 物料类型使用实际 bd_material DDL 枚举，不是自行编造的业务编码。
_MATERIALS = {
    "finished": (FINISHED_NAME, "M12×60；螺栓、螺母、平垫各1件；独立纸盒1件", "finished", 3, "PROCESS"),
    "bolt": ("六角螺栓M12×60（8.8级）", "M12×60；8.8级；已加工采购件", "piece", 1, "PROCUREMENT"),
    "nut": ("六角螺母M12（8级）", "M12；8级；已加工采购件", "piece", 1, "PROCUREMENT"),
    "washer": ("平垫M12", "M12；已加工采购件", "piece", 1, "PROCUREMENT"),
    "box": ("M12安装套件独立纸盒", "每套1件；适配M12×60安装套件", "box", 4, "PROCUREMENT"),
}
_COMPONENTS = ("bolt", "nut", "washer", "box")
# 实际 process/list 使用本地中文分类；编码、名称和分类共同构成一个绑定。
_PROCESS_ROLES = (("assembly", "ASM", "装配", "装配|组装"),
                  ("packaging", "PKG", "通用", "包装|装盒"),
                  ("quality", "QC", "通用", "质检|检验|质量"))
_EVENTS = ("order", "requisition", "receipt", "issue", "completion")
_PATHS = {"material": "/erp/bd/material", "routing": "/erp/bd/routing",
          "bom": "/erp/bd/bom", "detail": "/erp/bd/bom/detail"}
_MATERIAL_FIELDS = frozenset(("materialName", "materialSpec", "materialCode", "categoryId", "materialType",
                             "baseUomId", "purchaseUomId", "inventoryUomId", "saleUomId",
                             "sourceType", "isPhantom", "status"))
_ROUTING_FIELDS = frozenset(("routingName", "routingCode", "materialId", "routingVersion",
                            "isCurrent", "status", "effectiveDate", "steps"))
_BOM_FIELDS = frozenset(("parentMaterialId", "bomVersion", "bomName", "sourceType", "bomType",
                        "routingId", "isCurrent", "status", "effectiveDate"))
_DETAIL_FIELDS = frozenset(("bomId", "childMaterialId", "quantityPerParent", "lossRate", "operationStep",
                           "sortOrder", "sourceType", "status", "effectiveDate"))
_STEP_REQUIRED = frozenset(("sequenceNum", "processId", "stepName", "standardHours",
                           "setupHours", "isOutsource", "qualityRequired"))
_STEP_OPTIONAL = frozenset(("laborCount", "machineCount", "isKeyProcess", "workstationId"))


def _require(condition, message):
    if not condition:
        raise FixtureContractError(message)


def _row(mapping, key):
    _require(isinstance(mapping, dict) and isinstance(mapping.get(key), dict), f"missing read row: {key}")
    return mapping[key]


def _decimal(value, label):
    _require(type(value) in (str, int, Decimal), f"{label} must be an exact decimal, not float/bool/unknown")
    try:
        result = Decimal(value)
    except (InvalidOperation, ValueError):
        raise FixtureContractError(f"{label} is not a decimal") from None
    _require(result.is_finite(), f"{label} must be finite")
    return result


def _integer(value, label, minimum=0):
    number = _decimal(value, label)
    _require(minimum <= number <= 2**63 - 1 and number == number.to_integral_value(),
             f"{label} must be an integer >= {minimum} within signed-long range")
    return int(number)


def _id(value, label):
    _require(type(value) in (str, int) and re.fullmatch(r"[1-9][0-9]{0,18}", str(value)) is not None,
             f"{label} must be a positive server ID; unresolved/guessed placeholders are forbidden")
    _require(int(value) <= 2**63 - 1, f"{label} exceeds signed-long range")
    return str(value)


def _normal_name(value, label):
    _require(isinstance(value, str) and bool(value.strip()) and
             re.search(r"测试|演示|临时|TEST|DEMO|MOCK", value, re.IGNORECASE) is None,
             f"{label} must be a normal business name, without test/demo prefixes")
    return value


def _day(value, label):
    _require(isinstance(value, str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", value) is not None,
             f"{label} requires an explicit YYYY-MM-DD date")
    try:
        return date.fromisoformat(value)
    except ValueError:
        raise FixtureContractError(f"{label} is not a valid calendar date") from None


def _boundary_day(value, label):
    if isinstance(value, str) and len(value) > 10:
        _require(re.match(r"^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}", value) is not None,
                 f"{label} must be an API date or datetime")
        try:
            return datetime.fromisoformat(value).date()
        except ValueError:
            raise FixtureContractError(f"{label} is not a valid API datetime") from None
    return _day(value, label)


def _enabled(row, label):
    _require(_integer(row.get("status"), f"{label}.status") == 1, f"{label} is not enabled")


def _hours(value, label, positive=False):
    number = _decimal(value, label)
    _require(0 <= number <= Decimal("999999.9999") and (not positive or number > 0),
             f"{label} must be a usable observed duration, not guessed capacity")
    _require(number == number.quantize(Decimal("0.0001")), f"{label} exceeds routing-step scale 4")
    return format(number, "f")


def _id_set(values, label):
    _require(isinstance(values, (list, tuple, set, frozenset)), f"{label} needs the complete pre-create ID collection")
    return {_id(value, label) for value in values}


def _new_ref(record, field):
    return f"$new:{record}.{field}"


def _request(key, kind, body):
    return {"key": key, "method": "POST", "path": _PATHS[kind], "body": body}


def _verify_units(units, unit_catalog):
    """将每个选定的离散计量单位绑定到唯一一行 /measure/all 数据及其有效基本单位。

    套件可使用 1:1 派生单位。所有组件必须选择同一个
    已观测基本计量单位 Unit26（件）；不使用无关的损坏目录行。
    """
    _require(isinstance(unit_catalog, (list, tuple)), "unit_catalog must be /measure/all readback rows")
    catalog = {}
    for row in unit_catalog:
        _require(isinstance(row, dict), "unit catalog must contain VO rows")
        unit_id = _id(row.get("uomId"), "unit_catalog.uomId")
        _require(unit_id not in catalog, "ambiguous duplicate unit ID in catalog")
        catalog[unit_id] = row
    unit_ids = {}
    for role, names in (("finished", {"套"}), ("piece", {"件"}), ("box", {"件"})):
        selected = _row(units, role)
        unit_id = _id(selected.get("uomId"), f"unit.{role}.uomId")
        _require(unit_id in catalog and selected == catalog[unit_id],
                 f"unit.{role} must match exactly one live catalog VO row")
        _enabled(selected, f"unit.{role}")
        _require(selected.get("uomName") in names, f"unit.{role} requires its actual discrete unit name")
        category = selected.get("category")
        _normal_name(category, f"unit.{role}.category")
        is_base = _integer(selected.get("isBase"), "isBase")
        _require(is_base in (0, 1), "isBase must be binary")
        if role in ("piece", "box"):
            _require(unit_id == "26" and category == "数量" and is_base == 1,
                     f"unit.{role} requires observed base quantity Unit26 (件)")
        if is_base == 1:
            _require(selected.get("primaryId") is None,
                     f"base unit.{role} cannot itself point to a different primary")
            if selected.get("conversionRate") is not None:
                _require(_decimal(selected["conversionRate"], "conversionRate") == 1,
                         "base unit conversion, if present, must be 1:1")
        else:
            primary_id = _id(selected.get("primaryId"), f"unit.{role}.primaryId")
            _require(primary_id != unit_id and primary_id in catalog,
                     f"unit.{role} primary must exist in the same unit catalog")
            primary = catalog[primary_id]
            _enabled(primary, f"unit.{role}.primary")
            _require(_integer(primary.get("isBase"), "primary.isBase") == 1 and
                     primary.get("primaryId") is None and primary.get("category") == category,
                     f"unit.{role} must belong directly to an active base of the same category")
            _normal_name(primary.get("uomName"), f"unit.{role}.primary.uomName")
            if selected.get("primaryName") is not None:
                _require(selected["primaryName"] == primary["uomName"],
                         f"unit.{role}.primaryName contradicts primary readback")
            _require(_decimal(selected.get("conversionRate"), "conversionRate") == 1,
                     f"unit.{role} requires a verified 1:1 conversion to the primary")
        unit_ids[role] = unit_id
    _require(unit_ids["finished"] not in (unit_ids["piece"], unit_ids["box"]), "kit and component units must differ")
    _require(unit_ids["piece"] == unit_ids["box"] == "26",
             "box and hardware must share the observed base quantity Unit26 (件)")
    return unit_ids


def _verify_workstations(workstations, workshops, processes):
    """绑定独立读回的工位和车间 VO 行，绝不使用工序默认值。"""
    if workstations is None and workshops is None:
        return None
    _require(isinstance(workstations, dict) and isinstance(workshops, dict),
             "station and workshop readbacks must be provided together")
    station_ids = {}
    for role, _, _, label in _PROCESS_ROLES:
        station = _row(workstations, role)
        workshop = _row(workshops, role)
        _enabled(station, f"workstation.{role}")
        _enabled(workshop, f"workshop.{role}")
        station_id = _id(station.get("workstationId"), f"workstation.{role}.workstationId")
        workshop_id = _id(workshop.get("workshopId"), f"workshop.{role}.workshopId")
        _require(_id(station.get("workshopId"), f"workstation.{role}.workshopId") == workshop_id,
                 "station workshop membership must match independent workshop readback")
        station_name = _normal_name(station.get("workstationName"), f"workstation.{role}.name")
        workshop_name = _normal_name(workshop.get("workshopName"), f"workshop.{role}.name")
        _require(re.search(label, station_name) and re.search(label, workshop_name),
                 f"workstation.{role} must match the selected assembly/packaging/quality role")
        if station.get("workshopName") is not None:
            _require(station["workshopName"] == workshop_name, "station workshop name contradicts workshop readback")
        default_id = processes[role].get("workstationId")
        if default_id is not None:
            _require(_id(default_id, f"process.{role}.workstationId") == station_id,
                     "process default contradicts independently read workstation")
        station_ids[role] = station_id
    _require(len(set(station_ids.values())) == len(station_ids), "each route step must bind a distinct verified station")
    return station_ids


def build_fixture(*, units, unit_catalog, categories, processes, numbering_rules, dates, as_of_date,
                  workstations=None, workshops=None):
    """仅构建 5 个物料、1 条工艺路线、1 个 BOM 和 4 条明细的 CREATE 数据形状。

    读取映射使用角色 finished/piece/box 及 assembly/packaging/quality。
    映射值是调用方提供的 API VO 行，不是按名称猜测的数据库 ID。
    读回新组件的最小起订量（MOQ）前，不选择任何成品数量。
    """
    as_of = _day(as_of_date, "as_of_date")
    _require(isinstance(dates, dict) and set(dates) == set(_EVENTS), "all five event dates are required")
    event_days = [_day(dates[event], event) for event in _EVENTS]
    _require(event_days == sorted(event_days), "date chain must be order<=requisition<=receipt<=issue<=completion")
    _require(event_days[-1] <= as_of, "future sample dates cannot be represented as completed receipt/manufacture")
    unit_ids = _verify_units(units, unit_catalog)
    category_ids = {}
    for role in ("finished", "piece", "box"):
        category = _row(categories, role)
        _enabled(category, f"category.{role}")
        _normal_name(category.get("categoryName"), f"category.{role}.categoryName")
        category_ids[role] = _id(category.get("categoryId"), f"category.{role}.categoryId")
    _require(isinstance(numbering_rules, (list, tuple)), "numbering rule API rows are required")
    rule_bindings = {}
    for code in ("MATERIAL_CODE", "ROUTING_CODE"):
        matches = [row for row in numbering_rules if isinstance(row, dict) and row.get("ruleCode") == code]
        _require(len(matches) == 1 and matches[0].get("enableFlag") == "Y", f"one enabled {code} rule is required")
        rule_bindings[code] = {"ruleId": _id(matches[0].get("ruleId"), f"{code}.ruleId"), "generateOnCreate": True}
    steps = []
    for index, (role, code, category, label) in enumerate(_PROCESS_ROLES, start=1):
        process = _row(processes, role)
        _enabled(process, f"process.{role}")
        _require(process.get("processCategory") == category, f"process.{role} has the wrong category")
        _require(process.get("processCode") == code, f"process.{role} code must be {code} from readback")
        process_name = _normal_name(process.get("processName"), f"process.{role}.processName")
        _require(re.search(label, process_name) and not re.search(r"委外|外协", process_name),
                 f"process.{role} name is not the expected in-house operation")
        step = {"sequenceNum": index * 10, "processId": _id(process.get("processId"), f"process.{role}.processId"),
                "stepName": _normal_name(process.get("processName"), f"process.{role}.processName"),
                "standardHours": _hours(process.get("standardHours"), "standardHours", positive=True),
                "setupHours": _hours(process.get("setupHours"), "setupHours"),
                "isOutsource": _integer(process.get("isOutsource"), "isOutsource"),
                "qualityRequired": _integer(process.get("qualityRequired"), "qualityRequired")}
        for field in _STEP_OPTIONAL:
            if process.get(field) is not None:
                step[field] = _integer(process[field], field)
        steps.append(step)
    # 实际工序 VO 可能没有默认工位；工艺路线要具备可执行条件，仍
    # 必须独立核验工位和车间的读回数据。
    station_ids = _verify_workstations(workstations, workshops, processes)
    if station_ids is not None:
        for step, (role, _, _, _) in zip(steps, _PROCESS_ROLES):
            step["workstationId"] = station_ids[role]
    _validate_steps(steps)
    requests = []
    for key, (name, spec, role, material_type, source_type) in _MATERIALS.items():
        body = {"materialName": name, "materialSpec": spec, "materialCode": "", "categoryId": category_ids[role],
                "materialType": material_type, "sourceType": source_type, "isPhantom": 0, "status": 1}
        body.update({field: unit_ids[role] for field in ("baseUomId", "purchaseUomId", "inventoryUomId", "saleUomId")})
        requests.append(_request(f"material.{key}", "material", body))
    requests.append(_request("routing", "routing", {
        "routingName": ROUTING_NAME, "routingCode": "", "materialId": _new_ref("material.finished", "materialId"),
        "routingVersion": "1.0", "isCurrent": 1, "status": 1, "effectiveDate": dates["order"], "steps": steps}))
    requests.append(_request("bom", "bom", {
        "parentMaterialId": _new_ref("material.finished", "materialId"), "bomVersion": "1.0", "bomName": BOM_NAME,
        "sourceType": "PROCESS", "bomType": "MBOM", "routingId": _new_ref("routing", "routingId"),
        "isCurrent": 1, "status": 1, "effectiveDate": dates["order"]}))
    for index, component in enumerate(_COMPONENTS, start=1):
        requests.append(_request(f"bom.detail.{component}", "detail", {
            "bomId": _new_ref("bom", "bomId"), "childMaterialId": _new_ref(f"material.{component}", "materialId"),
            "quantityPerParent": 1, "lossRate": 0, "operationStep": 20 if component == "box" else 10,
            "sortOrder": index, "sourceType": "PROCUREMENT", "status": 1,
            "effectiveDate": dates["order"]}))
    blockers = ["new_component_moq"]
    if station_ids is None:
        blockers.insert(0, "verified_workstations")
    return {"contractVersion": 1, "writeEnabled": False, "requiresSeparateAuthorization": True,
            "masterDataReady": station_ids is not None, "readyForExecution": False,
            "blockers": blockers, "requests": requests, "numberingRules": rule_bindings,
            "workstationBindingStatus": ("SEPARATE_READBACK_BOUND" if station_ids is not None
                                         else "BLOCKED_AWAITING_VERIFIED_WORKSTATIONS"),
            "dates": deepcopy(dates), "asOfDate": as_of_date,
            "finishedQuantity": None, "quantityStatus": "AWAITING_NEW_COMPONENT_MOQ_READBACK",
            "evidence": {"level": "SOFTWARE_SAMPLE_ONLY", "physicalAcceptance": False,
                         "actualReceiptEvidence": False, "actualCapacityEvidence": False},
            "risks": ["API availability does not prove these process rows exist or suit this kit.",
                      "Verify tenant, complete pre-create IDs, created records and unit/hour applicability externally.",
                      "Read supplier MOQ for the NEW components; do not copy old material MOQ or invent prices.",
                      "BOM current-version flags are safe only for the fresh independent parent.",
                      "Observed standard hours are planning inputs, not actual production capacity or现场 evidence."]}


def _validate_steps(steps, *, require_workstations=False):
    _require(isinstance(steps, list) and len(steps) == 3, "assembly/packaging/quality steps are required")
    process_ids = []
    for index, step in enumerate(steps, start=1):
        _require(isinstance(step, dict) and _STEP_REQUIRED <= set(step) <= _STEP_REQUIRED | _STEP_OPTIONAL,
                 "routing step has missing or unexpected fields, including existing IDs/rates")
        _require(_integer(step.get("sequenceNum"), "sequenceNum") == index * 10, "routing sequence must be 10,20,30")
        process_ids.append(_id(step.get("processId"), "processId"))
        if require_workstations:
            _id(step.get("workstationId"), "workstationId")
        elif "workstationId" in step:
            _id(step["workstationId"], "workstationId")
        _normal_name(step.get("stepName"), "stepName")
        _hours(step.get("standardHours"), "standardHours", positive=True)
        _hours(step.get("setupHours"), "setupHours")
        _require(_integer(step.get("isOutsource"), "isOutsource") == 0, "fixture steps must be in-house")
        quality = _integer(step.get("qualityRequired"), "qualityRequired")
        _require(quality in (0, 1) and (index != 3 or quality == 1), "final quality step must require inspection")
        for field in _STEP_OPTIONAL & set(step):
            number = _integer(step[field], field)
            _require(field != "isKeyProcess" or number in (0, 1), "isKeyProcess must be binary")
    _require(len(set(process_ids)) == 3, "three distinct explicitly bound process IDs are required")


def _check_material_readback(key, row, preexisting):
    _require(key in _MATERIALS and isinstance(row, dict), "new material readback is required")
    name, spec, _, material_type, source = _MATERIALS[key]
    material_id = _id(row.get("materialId"), f"material.{key}.materialId")
    _require(material_id not in preexisting, "a pre-existing material cannot be bound as newly created")
    _enabled(row, f"material.{key}")
    _require(row.get("materialName") == name and row.get("materialSpec") == spec and row.get("sourceType") == source,
             "new material identity/spec/source does not match the independent contract")
    _require(_integer(row.get("materialType"), "materialType") == material_type and
             _integer(row.get("isPhantom"), "isPhantom") == 0, "new material classification must match")
    _normal_name(row.get("materialCode"), "readback.materialCode")
    uom_ids = {_id(row.get(field), field) for field in ("baseUomId", "purchaseUomId", "inventoryUomId", "saleUomId")}
    _require(len(uom_ids) == 1, "new material requires identical discrete base/purchase/inventory/sale units")
    if key in _COMPONENTS:
        _require(uom_ids == {"26"}, "new components must read back the verified Unit26 (件)")
    return material_id


def resolve_new_references(request, *, created_records, preexisting_ids):
    """仅使用经外部证明的数据行解析本契约的 $new 引用。

    基线集合必须在同一租户和环境中保持完整。
    此比较是用于一致性校验的守卫，不是认证或创建记录的证明：
    纯离线函数不能核验调用方关于来源的声明。
    返回的 POST 仍只是静态数据，不发生授权或执行。
    """
    _require(isinstance(created_records, dict) and isinstance(preexisting_ids, dict), "readbacks and baselines are required")
    existing = {scope: _id_set(preexisting_ids.get(scope), f"baseline.{scope}") for scope in ("material", "routing", "bom")}

    def record_id(key):
        row = _row(created_records, key)
        if key.startswith("material."):
            return _check_material_readback(key.split(".", 1)[1], row, existing["material"])
        _require(key in ("routing", "bom"), "unknown new-record reference")
        field = key + "Id"
        result = _id(row.get(field), field)
        _require(result not in existing[key], f"pre-existing {key} cannot be bound as newly created")
        _enabled(row, key)
        if key == "routing":
            _require(row.get("routingName") == ROUTING_NAME and
                     _id(row.get("materialId"), "routing.materialId") == record_id("material.finished"),
                     "routing readback must belong to the fresh independent finished material")
            _normal_name(row.get("routingCode"), "readback.routingCode")
        else:
            _require(row.get("bomName") == BOM_NAME and row.get("sourceType") == "PROCESS" and
                     _id(row.get("parentMaterialId"), "bom.parentMaterialId") == record_id("material.finished") and
                     _id(row.get("routingId"), "bom.routingId") == record_id("routing"),
                     "BOM readback must belong to the new parent and new routing")
        return result

    _require(isinstance(request, dict) and isinstance(request.get("body"), dict), "request template is required")
    key, body = request.get("key"), request["body"]
    expected_refs = {}
    if key == "routing":
        expected_refs = {"materialId": _new_ref("material.finished", "materialId")}
    elif key == "bom":
        expected_refs = {"parentMaterialId": _new_ref("material.finished", "materialId"),
                         "routingId": _new_ref("routing", "routingId")}
    elif isinstance(key, str) and key.startswith("bom.detail."):
        component = key.rsplit(".", 1)[1]
        _require(component in _COMPONENTS, "unknown component role")
        expected_refs = {"bomId": _new_ref("bom", "bomId"),
                         "childMaterialId": _new_ref(f"material.{component}", "materialId")}
    for field, expected in expected_refs.items():
        _require(body.get(field) == expected,
                 "new-record positions must use the exact role placeholder, never numeric IDs or swapped roles")
    material_ids = [record_id(f"material.{component}") for component in _MATERIALS
                    if f"material.{component}" in created_records]
    _require(len(material_ids) == len(set(material_ids)), "new material readbacks must have distinct server IDs")

    def resolve(value):
        if isinstance(value, dict):
            return {key: resolve(child) for key, child in value.items()}
        if isinstance(value, list):
            return [resolve(child) for child in value]
        if isinstance(value, str) and value.startswith("$new:"):
            match = re.fullmatch(r"\$new:(material\.(?:finished|bolt|nut|washer|box)|routing|bom)\.(\w+)", value)
            _require(match is not None, "unknown new-record placeholder")
            key, field = match.groups()
            _require(field == ("materialId" if key.startswith("material.") else key + "Id"), "invalid ID reference field")
            return record_id(key)
        return deepcopy(value)

    result = resolve(request)
    assert_request_ready(result)
    return result


def assert_request_ready(request):
    """数据形状或编号校验失败即关闭通路；通过此守卫绝不意味着允许写入。"""
    _require(isinstance(request, dict) and request.get("method") == "POST", "only inert CREATE shapes are supported")
    body, path, key = request.get("body"), request.get("path"), request.get("key")
    _require(isinstance(body, dict), "request body must be an object")
    _enabled(body, "request")
    if "effectiveDate" in body:
        _day(body["effectiveDate"], "effectiveDate")
    if path == _PATHS["material"]:
        _require(set(body) == _MATERIAL_FIELDS and key in {f"material.{name}" for name in _MATERIALS}, "unexpected material CREATE fields/key")
        name, spec, _, material_type, source = _MATERIALS[key.split(".", 1)[1]]
        _require(body.get("materialName") == name and body.get("materialSpec") == spec and body.get("sourceType") == source,
                 "material CREATE must match the independent identity")
        _require(body.get("materialCode") in (None, ""), "business code must be blank for server generation, never a preview")
        _id(body.get("categoryId"), "categoryId")
        _require(_integer(body.get("materialType"), "materialType") == material_type and
                 _integer(body.get("isPhantom"), "isPhantom") == 0, "material type/phantom contract mismatch")
        uoms = {_id(body.get(field), field) for field in ("baseUomId", "purchaseUomId", "inventoryUomId", "saleUomId")}
        _require(len(uoms) == 1, "unit conversions are outside this contract")
        if key.split(".", 1)[1] in _COMPONENTS:
            _require(uoms == {"26"}, "component CREATE must retain the verified Unit26 (件)")
    elif path == _PATHS["routing"]:
        _require(set(body) == _ROUTING_FIELDS and key == "routing", "unexpected routing CREATE fields/key")
        _require(body.get("routingName") == ROUTING_NAME and body.get("routingCode") in (None, ""),
                 "routing identity/code must match server-generation contract")
        _id(body.get("materialId"), "routing.materialId")
        _validate_steps(body.get("steps"), require_workstations=True)
    elif path == _PATHS["bom"]:
        _require(set(body) == _BOM_FIELDS and key == "bom", "unexpected BOM CREATE fields/key")
        _require(body.get("bomName") == BOM_NAME and body.get("sourceType") == "PROCESS" and body.get("bomType") == "MBOM",
                 "BOM parent must be PROCESS/MBOM")
        _id(body.get("parentMaterialId"), "parentMaterialId")
        _id(body.get("routingId"), "routingId")
    elif path == _PATHS["detail"]:
        _require(set(body) == _DETAIL_FIELDS and key in {f"bom.detail.{name}" for name in _COMPONENTS}, "unexpected BOM detail CREATE fields/key")
        component = key.rsplit(".", 1)[1]
        _id(body.get("bomId"), "bomId")
        _id(body.get("childMaterialId"), "childMaterialId")
        _require(_decimal(body.get("quantityPerParent"), "quantityPerParent") == 1 and
                 _decimal(body.get("lossRate"), "lossRate") == 0, "each discrete component is exactly 1 per kit, loss 0")
        _require(body.get("sourceType") == "PROCUREMENT",
                 "components must be purchased, without child BOM; supplyType requires separate business confirmation")
        _require(_integer(body.get("operationStep"), "operationStep") == (20 if component == "box" else 10) and
                 _integer(body.get("sortOrder"), "sortOrder") == _COMPONENTS.index(component) + 1,
                 "component operation/sort must match assembly or packaging")
    else:
        raise FixtureContractError("unsupported path; no generator, update, delete or batch replacement is allowed")
    if "isCurrent" in body:
        _require(_integer(body["isCurrent"], "isCurrent") == 1, "new BOM/routing must be current")
    for field in ("bomVersion", "routingVersion"):
        if field in body:
            _require(body[field] == "1.0", "independent initial version must be 1.0")


def decide_lot_quantity(*, moq_reads, component_readbacks, baseline_material_ids, as_of_date,
                        candidate_lots=(600, 1000), kit_lot_multiple=1):
    """使用当前新物料的最小起订量（MOQ），选择明确的完整套件数量候选值。

    MOQ 是下限，绝不能假定它是供应商的订购倍数。kit_lot_multiple
    是显式的软件批次增量，不是供应商事实。MOQ 缺失或
    不适用时必须阻断；绝不自动放大旧 MRP 缺料数量。
    quantityUomId/unitBasisConfirmed 是执行方的标注，不是供应商 BO
    字段：实际供应商 VO 不包含单位 ID，MOQ 的计量口径仍需审查。
    """
    as_of = _day(as_of_date, "as_of_date")
    existing = _id_set(baseline_material_ids, "baseline_material_ids")
    _require(isinstance(candidate_lots, (list, tuple)) and bool(candidate_lots), "explicit candidate lots are required")
    candidates = sorted({_integer(value, "candidate_lot", minimum=1) for value in candidate_lots})
    multiple = _integer(kit_lot_multiple, "kit_lot_multiple", minimum=1)
    evidence, ids = {}, []
    for component in _COMPONENTS:
        material = _row(component_readbacks, component)
        material_id = _check_material_readback(component, material, existing)
        ids.append(material_id)
        _require(material.get("baseUomName") == "件", "MOQ requires the observed piece-count component unit name")
        read = _row(moq_reads, component)
        _require(_day(read.get("readDate"), f"{component}.readDate") == as_of, "MOQ read must be current, not stale/future")
        _require(read.get("unitBasisConfirmed") is True and
                 _id(read.get("quantityUomId"), "quantityUomId") == _id(material.get("purchaseUomId"), "purchaseUomId"),
                 "MOQ quantity unit basis must be explicitly confirmed for this new component")
        row = _row(read, "row")
        _enabled(row, f"MOQ.{component}")
        _require(_id(row.get("materialId"), "MOQ.materialId") == material_id, "MOQ must refer to the exact new material")
        minimum = _integer(row.get("minOrderQty"), "minOrderQty")
        for field, compare in (("effectiveDate", lambda value: value <= as_of), ("expiryDate", lambda value: value >= as_of)):
            if row.get(field) is not None:
                _require(compare(_boundary_day(row[field], field)), "supplier relation is not effective on the read date")
        evidence[component] = {"relationId": _id(row.get("relationId"), "relationId"), "materialId": material_id,
                               "vendorId": _id(row.get("vendorId"), "vendorId"), "minOrderQty": minimum,
                               "quantityUomId": _id(read.get("quantityUomId"), "quantityUomId"), "readDate": read["readDate"]}
    _require(len(set(ids)) == 4, "each new component must have a distinct server material ID")
    threshold = max(row["minOrderQty"] for row in evidence.values())
    eligible = [count for count in candidates if count >= threshold and count % multiple == 0]
    _require(bool(eligible), "no explicitly allowed integer lot meets current MOQ; review separately, never enlarge old shortage")
    quantity = eligible[0]
    return {"status": "READ_BASED_PLAN_NOT_EXECUTED", "finishedQuantity": quantity, "finishedUnit": "套",
            "componentDemand": {component: quantity for component in _COMPONENTS}, "moqEvidence": evidence,
            "moqInterpretation": "FLOOR_NOT_ORDER_MULTIPLE", "kitLotMultiple": multiple,
            "kitLotMultipleIsSupplierFact": False, "historicalMrpUnchanged": True, "actualReceiptEvidence": False}
