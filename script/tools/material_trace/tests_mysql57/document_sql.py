"""只展开明确的所属模块单据表达式，以及服务端固定列出的来源配置。"""

import re
from harness import ValidationError
from java_sql import JAVA_TOKENS, extract_selects, fold_java_literals

DOC = "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpDocumentCodeResolver.java"
BINDING = '"SELECT "+spec.id()+" FROM "+spec.table()+" WHERE tenant_id=? AND "+spec.id()+"=?"+(spec.logicalDelete()?" AND del_flag=\'0\'":"")+" FOR UPDATE"'
RESOLVER = '"SELECT "+spec.id()+" object_id,"+spec.code()+" document_code FROM "+spec.table()+" WHERE tenant_id=? AND "+spec.code()+"=?"+(spec.logicalDelete()?" AND del_flag=\'0\'":"")'
CARRIER = '"SELECT * FROM "+spec.table()+" WHERE tenant_id=? AND "+spec.id()+"=?"+(spec.logicalDelete()?" AND del_flag=\'0\'":"")'
OPTIONAL = (
    'if(request.sourceDocId()!=null){sql+=" AND "+spec.id()+"=?";args.add(request.sourceDocId());}'
)


def compact_java(source):
    source = fold_java_literals(source)
    result = []
    position = 0
    for token in JAVA_TOKENS.finditer(source):
        result.append(re.sub(r"\s+", "", source[position : token.start()]))
        if token.group("string") or token.group("block"):
            result.append(token.group())
        position = token.end()
    result.append(re.sub(r"\s+", "", source[position:]))
    return "".join(result)


def closed_specs(source):
    block = re.search(
        r"private\s+static\s+final\s+List<Spec>\s+SPECS\s*=\s*List\.of\(([\s\S]*?)\);", source
    )
    if not block:
        raise ValidationError("Document server-side Spec list changed")
    value = compact_java(block[1])
    pattern = 'newSpec\\("([A-Z_]+)","([a-z_]+)","([a-z_]+)","([a-z_]+)","[^"\\n]+",Set\\.of\\("[A-Z_]+"(?:,"[A-Z_]+")*\\),(true|false)\\)'
    entries = list(re.finditer(pattern, value))
    remaining = re.sub(pattern, "", value).replace(",", "")
    if not entries or remaining:
        raise ValidationError("Unknown document Spec expression; do not guess dynamic SQL")
    if len({m[1] for m in entries}) != len(entries):
        raise ValidationError("Duplicate document types in closed Spec list")
    return [(m[1], m[2], m[3], m[4], m[5] == "true") for m in entries]


def document_statements(source, document_source, scope):
    source = fold_java_literals(source)
    if scope == "document_binding":
        match = re.search(
            r'one\s*\(\s*(?P<expression>"SELECT "\s*\+[^;]*?),\s*tenant\s*,\s*value\.getBizId\(\)\s*\)\s*;',
            source,
        )
        expected = BINDING
    elif scope == "document_resolver":
        match = re.search(r'String\s+sql\s*=\s*(?P<expression>"SELECT "\s*\+[^;]*);', source)
        expected = RESOLVER
        normalized = compact_java(source)
        if (
            compact_java(OPTIONAL) not in normalized
            or 'jdbc.queryForList(sql+" LIMIT 101",args.toArray())' not in normalized
        ):
            raise ValidationError("Document resolver optional ID or final SQL suffix changed")
    else:
        raise ValidationError("Unknown document expansion scope")
    if not match or compact_java(match["expression"]) != compact_java(expected):
        raise ValidationError("Unknown document SQL expression; full owner expression must match")
    matches = [match]
    carrier = None
    if scope == "document_resolver":
        carrier = re.search(
            r'jdbc\.queryForList\s*\(\s*(?P<expression>"SELECT \* FROM "\s*\+[^;]*?),\s*tenant\s*,\s*source\s*\)\s*;',
            source,
        )
        if not carrier or compact_java(carrier["expression"]) != compact_java(CARRIER):
            raise ValidationError("Document carrier target SQL changed")
        matches.append(carrier)
    line = source[: match.start("expression")].count("\n") + 1
    masked = source
    for item in sorted(matches, key=lambda m: m.start("expression"), reverse=True):
        start, end = item.span("expression")
        masked = masked[:start] + re.sub(r"[^\n]", " ", masked[start:end]) + masked[end:]
    queries = extract_selects(masked)
    for kind, table, key, code, deleted in closed_specs(document_source):
        if scope == "document_binding":
            variants = [
                (
                    "document_" + kind,
                    "SELECT "
                    + key
                    + " FROM "
                    + table
                    + " WHERE tenant_id=? AND "
                    + key
                    + "=?"
                    + (" AND del_flag='0'" if deleted else "")
                    + " FOR UPDATE",
                )
            ]
        else:
            base = (
                "SELECT "
                + key
                + " object_id,"
                + code
                + " document_code FROM "
                + table
                + " WHERE tenant_id=? AND "
                + code
                + "=?"
                + (" AND del_flag='0'" if deleted else "")
            )
            variants = [
                ("document_" + kind + "_id_false", base + " LIMIT 101"),
                ("document_" + kind + "_id_true", base + " AND " + key + "=? LIMIT 101"),
            ]
        queries.extend({"line": line, "variant": variant, "sql": sql} for variant, sql in variants)
        if carrier:
            queries.append(
                {
                    "line": source[: carrier.start("expression")].count("\n") + 1,
                    "variant": "document_" + kind + "_carrier",
                    "sql": "SELECT * FROM "
                    + table
                    + " WHERE tenant_id=? AND "
                    + key
                    + "=?"
                    + (" AND del_flag='0'" if deleted else ""),
                }
            )
    return queries
