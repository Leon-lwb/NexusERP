"""离线生成采购供货确认资料，不连接网络、数据库，也不办理业务提交。

调用方提供真实只读快照；本模块仅校验输入和文件字节，不能证明原报价已批准、
供应关系有效或单位确为根单位。所有金额及工业指标由原生业务服务负责计算。
当前 SupplierTermsRequest 实际为 15 字段，提交白名单以该原生记录为准。
"""
from datetime import date
from decimal import Decimal
import hashlib
import html
import json
from pathlib import Path
import re
import stat
import unicodedata

_ORIGIN = "LOCAL_SOFTWARE_BUSINESS_VALIDATION"
_STATEMENT = "本资料仅用于本机软件业务验证，非外部供应商签署，非实物收货、验收或履约证明。"
_PRICE_NOTICE = "报价身份与价格依据由调用方提供；生成器未核验原报价审批、根单位或供应关系。历史来源不能继承旧复核、签名或审批，不能替代本批新资料哈希。"
_MAX_SAFE_INTEGER = 9_007_199_254_740_991
_MAX_LONG = 9_223_372_036_854_775_807
_ROW_FIELDS = {
    "orderId", "orderNo", "nativeOrderVersion", "materialId", "materialName",
    "vendorId", "vendorName", "baseUomId", "quantity", "requestKey",
    "unitPriceExcludingTax", "taxRate", "currency", "exchangeRate",
    "promisedArrivalDate", "validFrom", "validUntil", "supplierQuoteReference",
    "supplierContact", "paymentTerms", "deliveryTerms", "priceSource",
}
_SOURCE_FIELDS = {
    "sourceQuoteId", "sourceOrderId", "sourceQuoteHash", "sourceEvidenceSha256",
    "sourceUnitPriceExcludingTax", "sourceTaxRate",
}
_TERMS_INPUT_FIELDS = (
    "requestKey", "unitPriceExcludingTax", "taxRate", "currency", "exchangeRate",
    "promisedArrivalDate", "validFrom", "validUntil", "supplierQuoteReference",
    "supplierContact", "paymentTerms", "deliveryTerms",
)
_BUSINESS_MARKER = re.compile(r"TEST|DEMO|MOCK|测试用例", re.I)
_CREDENTIAL = re.compile(
    r"\b(?:bearer|basic)\s+\S+|\bauthorization\s*:|"
    r"\b(?:password|passwd|pwd|api[_ -]*key|access[_ -]*token|refresh[_ -]*token|"
    r"client[_ -]*secret|private[_ -]*key|authorization|secret|token)\s*[:=]\s*\S+|"
    r"(?:密码|密钥|令牌|私钥)\s*[:=]\s*\S+|\bcookie\s*[:=]|"
    r"\b(?:sk-[A-Za-z0-9_-]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AIza[A-Za-z0-9_-]{30,})\b|"
    r"\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+|"
    r"-----BEGIN[^\r\n]*PRIVATE KEY-----|"
    r"[A-Za-z][A-Za-z0-9+.-]*://[^\s/]+:[^\s/]+@|"
    r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b", re.I,
)
_LABELS = (
    ("orderId", "原订单ID"), ("orderNo", "原订单号"),
    ("materialId", "物料ID"), ("materialName", "物料名称"),
    ("vendorId", "供应商ID"), ("vendorName", "供应商名称"),
    ("baseUomId", "原根单位ID"), ("quantity", "原订单数量"),
    ("requestKey", "固定请求键"), ("unitPriceExcludingTax", "未税单价"),
    ("taxRate", "税率"), ("currency", "币种"), ("exchangeRate", "汇率"),
    ("promisedArrivalDate", "承诺到货日"), ("validFrom", "有效起日"),
    ("validUntil", "有效止日"), ("supplierQuoteReference", "报价资料引用"),
    ("supplierContact", "供货联系部门"), ("paymentTerms", "付款条款"),
    ("deliveryTerms", "交付条款"),
)


def _shape(value, required, label, optional=frozenset()):
    """只接受明确字典字段，禁止附带主体、核验标记或凭据。"""
    if type(value) is not dict or not required <= value.keys() or value.keys() - required - optional:
        raise ValueError(f"{label}字段缺失或包含不允许字段")


def _text(value, maximum, label, business=True):
    """校验原生 UTF-16 长度、普通命名及常见凭据，不在错误中回显值。"""
    if type(value) is not str or not value or value != value.strip():
        raise ValueError(f"{label}必须为非空且无首尾空白的文本")
    if any(unicodedata.category(character).startswith("C") for character in value):
        raise ValueError(f"{label}含控制字符或不可见字符")
    if len(value.encode("utf-16-le")) // 2 > maximum:
        raise ValueError(f"{label}超出原生文本长度")
    normalized = unicodedata.normalize("NFKC", value)
    if _CREDENTIAL.search(normalized):
        raise ValueError(f"{label}含敏感凭据")
    if business and _BUSINESS_MARKER.search(normalized):
        raise ValueError(f"{label}须使用普通业务名称")
    return value


def _identifier(value, label):
    """接受规范正 Long 文本或安全整数，输出字符串避免雪花 ID 精度丢失。"""
    if type(value) is int:
        if not 0 < value <= _MAX_SAFE_INTEGER:
            raise ValueError(f"{label}数值不安全，请使用规范十进制ID文本")
        return str(value)
    if type(value) is not str or not re.fullmatch(r"[1-9][0-9]{0,18}", value):
        raise ValueError(f"{label}不是精确正整数ID")
    if int(value) > _MAX_LONG:
        raise ValueError(f"{label}超出原生Long范围")
    return value


def _business_date(value, label):
    """严格读取 ISO 日期并对齐原生 MySQL DATE 年份范围。"""
    if type(value) is not str or not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", value):
        raise ValueError(f"{label}必须为ISO日期文本")
    try:
        result = date.fromisoformat(value)
    except ValueError:
        raise ValueError(f"{label}不是有效日期") from None
    if not 1000 <= result.year <= 9999:
        raise ValueError(f"{label}超出原生DATE范围")
    return result


def _decimal_text(value, label):
    """仅解析有限定长的定点 Decimal 文本，保留原文，不转换浮点或计算金额。"""
    if (type(value) is not str or len(value) > 256
            or not re.fullmatch(r"(?:0|[1-9][0-9]*)(?:\.[0-9]+)?", value)):
        raise ValueError(f"{label}必须为非负定点十进制文本")
    return Decimal(value)


def _native_scale(value):
    """按去掉尾零后的原生四位小数守卫校验，不使用会受上下文舍入的 normalize。"""
    return len(value.partition(".")[2].rstrip("0")) <= 4


def _positive_decimal(value, label):
    """对齐原生单价/数量正数、去尾零四位小数及十四位整数合同。"""
    number = _decimal_text(value, label)
    if number <= 0 or not _native_scale(value) or len(value.partition(".")[0]) > 14:
        raise ValueError(f"{label}不满足原生正数、四位小数或十四位整数范围")
    return number


def _tax_decimal(value, label):
    """对齐原生税率零到一及去尾零四位小数合同。"""
    number = _decimal_text(value, label)
    if number > 1 or not _native_scale(value):
        raise ValueError(f"{label}必须在0到1且去尾零后不超过四位小数")
    return number

def _validated_order(raw, today):
    """完整校验一单作用域、来源及原生报价条款；NULL 版本不补默认值。"""
    _shape(raw, _ROW_FIELDS, "订单")
    row = dict(raw)
    for field in ("orderId", "materialId", "vendorId", "baseUomId"):
        row[field] = _identifier(row[field], field)
    version = row["nativeOrderVersion"]
    if version is not None and (type(version) is not int or not 0 <= version < 2_147_483_647):
        raise ValueError("原生订单版本必须为NULL或范围内整数，不允许推定")
    for field, maximum in (
        ("orderNo", 128), ("materialName", 255), ("vendorName", 255), ("requestKey", 128),
        ("supplierQuoteReference", 128), ("supplierContact", 128),
        ("paymentTerms", 255), ("deliveryTerms", 255),
    ):
        _text(row[field], maximum, field)
    if (not re.fullmatch(r"[A-Za-z0-9_\-\u3400-\u9fff]+", row["orderNo"])
            or re.fullmatch(r"CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]", row["orderNo"], re.I)):
        raise ValueError("原订单号含不安全路径字符或设备名")
    if not re.fullmatch(r"[A-Za-z0-9:_-]{1,128}", row["requestKey"]):
        raise ValueError("固定请求键不符合原生合同")
    _positive_decimal(row["quantity"], "quantity")
    price = _positive_decimal(row["unitPriceExcludingTax"], "unitPriceExcludingTax")
    _tax_decimal(row["taxRate"], "taxRate")
    exchange = _decimal_text(row["exchangeRate"], "exchangeRate")
    if row["currency"] != "CNY" or exchange != Decimal("1"):
        raise ValueError("原生报价只支持CNY及显式十进制汇率1")
    promise = _business_date(row["promisedArrivalDate"], "promisedArrivalDate")
    start = _business_date(row["validFrom"], "validFrom")
    end = _business_date(row["validUntil"], "validUntil")
    if start > today or end < today or end < start or promise < today or promise < start:
        raise ValueError("报价未生效、已过期或承诺到货日无效")
    source = row["priceSource"]
    _shape(source, _SOURCE_FIELDS, "价格来源", {"origin", "basis"})
    if "origin" in source and source["origin"] != _ORIGIN:
        raise ValueError("价格来源必须明确为本机软件业务验证，不接受外部来源")
    row["priceSource"] = dict(source)
    for field in ("sourceQuoteId", "sourceOrderId"):
        row["priceSource"][field] = _identifier(source[field], f"priceSource.{field}")
    for field in ("sourceQuoteHash", "sourceEvidenceSha256"):
        if type(source[field]) is not str or not re.fullmatch(r"[0-9a-f]{64}", source[field]):
            raise ValueError(f"priceSource.{field}必须为原持久来源的小写SHA256文本")
    source_price = _positive_decimal(source["sourceUnitPriceExcludingTax"], "priceSource.sourceUnitPriceExcludingTax")
    _tax_decimal(source["sourceTaxRate"], "priceSource.sourceTaxRate")
    if price != source_price:
        raise ValueError("新资料未税单价与原已批准本地报价单价不一致，不能代填或舍入")
    if "basis" in source:
        _text(source["basis"], 500, "priceSource.basis")
    return row


def _inspect_path(path, directory):
    """用不跟随链接的属性拒绝符号链接、Windows 重解析点及证据硬链接。"""
    try:
        info = path.lstat()
    except FileNotFoundError:
        return False
    except OSError:
        raise ValueError("输出路径无法安全核验") from None
    if (stat.S_ISLNK(info.st_mode)
            or getattr(info, "st_file_attributes", 0) & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 1024)):
        raise ValueError("输出路径不允许链接或重解析点")
    if directory:
        if not stat.S_ISDIR(info.st_mode):
            raise ValueError("输出目录被非目录对象占用")
    elif not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
        raise ValueError("证据路径被非普通单链接文件占用")
    return True


def _check_directory_tree(directory):
    """逐层核对既有父目录，不先 resolve 到可能越界的链接目标。"""
    for path in reversed((directory, *directory.parents)):
        _inspect_path(path, directory=True)


def _output_path(directory):
    """要求显式本机绝对目录，拒绝父级穿越、根目录及网络/设备路径。"""
    if (not isinstance(directory, Path) or not directory.is_absolute()
            or ".." in directory.parts or str(directory).startswith(("\\\\", "//"))
            or directory == Path(directory.anchor)):
        raise ValueError("输出必须是无穿越的本机绝对目录，不能为根目录或网络路径")
    for component in directory.parts:
        if component == directory.anchor:
            continue
        if (component != component.strip() or component.endswith(".")
                or re.search(r'[<>:"|?*\\]', component)
                or any(unicodedata.category(character).startswith("C") for character in component)
                or re.fullmatch(r"(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\..*)?", component, re.I)):
            raise ValueError("输出路径含Windows别名、设备名、附加流或不可见字符")
    return directory


def _markdown_text(value):
    """转义业务文本，避免 HTML 或 Markdown 标记伪造审计展示。"""
    escaped = html.escape(str(value), quote=True)
    return re.sub(r"([\\`*_{}\[\]()#+!|>~])", r"\\\1", escaped)


def _markdown(row, business_day, dataset):
    """生成可读供货资料，明确软件来源及未知版本的不可提交状态。"""
    lines = ["# 采购供货确认资料", "", _STATEMENT, "", f"审计来源：{_ORIGIN}",
             f"业务日期：{business_day}", f"业务名称：{_markdown_text(dataset['description'])}", "",
             _PRICE_NOTICE, "", "## 原单作用域与供货条款", ""]
    lines.extend(f"- {label}：{_markdown_text(row[field])}" for field, label in _LABELS)
    version = row["nativeOrderVersion"]
    lines += [f"- 原生订单版本：{'未知（NULL）' if version is None else version}", "",
              "## 原已批准本地报价来源（调用方提供）", ""]
    for field, label in (
        ("sourceQuoteId", "原报价ID"), ("sourceOrderId", "原报价所属订单ID"),
        ("sourceQuoteHash", "原报价哈希"), ("sourceEvidenceSha256", "原报价证据哈希"),
        ("sourceUnitPriceExcludingTax", "原报价未税单价"), ("sourceTaxRate", "原报价税率"),
    ):
        lines.append(f"- {label}：{_markdown_text(row['priceSource'][field])}")
    if "basis" in row["priceSource"]:
        lines.append(f"- 价格依据：{_markdown_text(row['priceSource']['basis'])}")
    lines += ["", "## 提交边界", "",
              ("原生订单版本未知；仅生成资料，不可提交报价条款。" if version is None else
               "仅条款结构及原生版本已知；未提交报价，仍须原生权限、租户、来源与版本守卫。")]
    return ("\n".join(lines) + "\n").encode("utf-8")


def _same_bytes(path, expected):
    """对既有文件重新读取实际字节，任何异字节冲突均不覆盖。"""
    if not _inspect_path(path, directory=False):
        return False
    try:
        actual = path.read_bytes()
    except OSError:
        raise ValueError("既有证据字节无法读取") from None
    if actual != expected:
        raise ValueError("同名资料字节冲突，整批拒绝且不覆盖")
    return True


def build_documents(payload: dict, output_directory: Path) -> dict:
    """整批预检后生成每原单 JSON 证据及 Markdown，返回内存清单，不提交业务。

    payload 必须仅含 businessDate（ISO 文本）、dataset（origin 固定为本机来源、
    description 为普通业务名称）及非空 orders 列表。每行必须显式提供模块所列
    全部字段；priceSource 必含 sourceQuoteId、sourceOrderId、sourceQuoteHash、
    sourceEvidenceSha256、sourceUnitPriceExcludingTax、sourceTaxRate，可附 basis
    及相同的本机 origin；旧哈希和税率仅记来源，不继承旧复核。新单价须与来源等值。
    ID 为规范正 Long 文本或 JS 安全正整数，输出统一文本；数量、单价、税率及
    汇率只接受定点 Decimal 文本。nativeOrderVersion 只能显式 NULL 或原生整数。

    返回 businessDate / dataset / orders；每单仅含 orderId、evidencePath、
    evidenceSha256、submissionReady、termsBody。证据为 UTF-8 JSON 原字节，
    termsBody 仅原生 15 字段；NULL 版本的 termsBody 为 None、就绪标志为 False。
    同名同字节复用不改写；输入或任一既有文件冲突在整批写前封闭拒绝。
    就绪不表示系统已核验、批准或受理；文件系统不提供跨文件事务或并发隔离。
    """
    _shape(payload, {"businessDate", "dataset", "orders"}, "输入")
    today = _business_date(payload["businessDate"], "businessDate")
    business_day = today.isoformat()
    dataset = payload["dataset"]
    _shape(dataset, {"origin", "description"}, "业务资料集")
    if dataset["origin"] != _ORIGIN:
        raise ValueError("业务资料集必须保留本机软件来源，不允许外部来源")
    _text(dataset["description"], 255, "业务名称")
    dataset = dict(dataset)
    if type(payload["orders"]) is not list or not payload["orders"]:
        raise ValueError("orders必须为非空显式原单列表")
    rows = [_validated_order(row, today) for row in payload["orders"]]
    for field in ("orderId", "orderNo", "requestKey"):
        values = [row[field].casefold() for row in rows]
        if len(set(values)) != len(values):
            raise ValueError(f"原单列表有重复{field}")
    directory = _output_path(output_directory)
    files = []
    entries = []
    for row in rows:
        stem = f"供货确认-{row['orderId']}"
        evidence = directory / f"{stem}.json"
        reference = _text(str(evidence), 255, "evidenceReference", business=False)
        ready = row["nativeOrderVersion"] is not None
        document = {"documentName": "采购供货确认资料", "businessDate": business_day,
                    "dataset": dataset, "audit": {"origin": _ORIGIN, "statement": _STATEMENT,
                                                  "priceSourceNotice": _PRICE_NOTICE},
                    "order": row, "submissionReady": ready}
        raw = (json.dumps(document, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + "\n").encode("utf-8")
        digest = hashlib.sha256(raw).hexdigest()
        body = None
        if ready:
            body = {field: row[field] for field in _TERMS_INPUT_FIELDS}
            body.update(expectedOrderVersion=row["nativeOrderVersion"],
                        evidenceReference=reference, evidenceSha256=digest)
        entries.append({"orderId": row["orderId"], "evidencePath": reference,
                        "evidenceSha256": digest, "submissionReady": ready, "termsBody": body})
        files.extend(((evidence, raw), (directory / f"{stem}.md", _markdown(row, business_day, dataset))))
    # 所有行、引用长度、目录及全部既有字节先校验，晚到的一单不能造成早单补写。
    _check_directory_tree(directory)
    for path, raw in files:
        _same_bytes(path, raw)
    try:
        directory.mkdir(parents=True, exist_ok=True)
        _check_directory_tree(directory)
        for path, raw in files:
            _check_directory_tree(directory)
            if _same_bytes(path, raw):
                continue
            try:
                with path.open("xb") as stream:
                    stream.write(raw)
            except FileExistsError:
                # 并发出现的文件也只能同字节复用，绝不转为覆盖模式。
                _same_bytes(path, raw)
        for path, raw in files:
            if not _same_bytes(path, raw):
                raise ValueError("写后证据缺失，不能返回可提交清单")
        for entry in entries:
            actual_digest = hashlib.sha256(Path(entry["evidencePath"]).read_bytes()).hexdigest()
            if actual_digest != entry["evidenceSha256"]:
                raise ValueError("实际证据字节哈希发生变化，不能返回可提交清单")
            entry["evidenceSha256"] = actual_digest
    except OSError:
        raise ValueError("资料文件操作失败，不能确认整批结果；未覆盖既有文件") from None
    return {"businessDate": business_day, "dataset": dataset, "orders": entries}
