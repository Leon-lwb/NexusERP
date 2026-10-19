"""只读取原业务类的查询字面量，不运行该类；动态拼接范围必须有显式限制。"""

from __future__ import annotations
import json
import re
import textwrap
from harness import ValidationError, sql_value

JAVA_TOKENS = re.compile(
    r'//[^\n]*|/\*[\s\S]*?\*/|(?P<block>"""[\s\S]*?""")|(?P<string>"(?:\\.|[^"\\])*")'
)
SQL_TOKENS = re.compile(r"`(?:``|[^`])*`|'(?:\\.|''|[^'])*'|\"(?:\\.|\"\"|[^\"])*\"|\?")
DYNAMIC_IN = re.compile(
    r'^\s*\+\s*String\.join\(\s*","\s*,\s*Collections\.nCopies\(\s*identities\.size\(\)\s*,\s*"\?"\s*\)\s*\)\s*\+\s*("(?:\\.|[^"\\])*")'
)


def fold_java_literals(source):
    """合并编译期相邻字符串，保留完整内容和原源码行号。

    只处理直接由加号连接的字符串常量；变量、调用和条件表达式仍由原受限规则核对。
    """
    tokens = [token for token in JAVA_TOKENS.finditer(source) if token.group("string")]
    pieces = []
    position = 0
    index = 0
    while index < len(tokens):
        first = tokens[index]
        end = first.end()
        cluster = [first.group()]
        index += 1
        while index < len(tokens):
            following = tokens[index]
            separator = JAVA_TOKENS.sub(
                lambda token: (
                    token.group() if token.group("block") or token.group("string") else ""
                ),
                source[end : following.start()],
            )
            if separator.strip() != "+":
                break
            cluster.append(following.group())
            end = following.end()
            index += 1
        if len(cluster) > 1:
            value = "".join(json.loads(literal) for literal in cluster)
            pieces.extend(
                (
                    source[position : first.start()],
                    json.dumps(value, ensure_ascii=False),
                    "\n" * source[first.start() : end].count("\n"),
                )
            )
            position = end
    pieces.append(source[position:])
    return "".join(pieces)


def _boolean_fragment(source, token, sql, line):
    """只展开同方法内未重赋值的布尔双字面量，不猜测任意Java表达式。"""
    tail = source[token.end() :]
    literal = r'("(?:\\.|[^"\\])*")'
    inline = re.match(
        r"^\s*\+\s*\(\s*(\w+)\s*\?\s*" + literal + r"\s*:\s*" + literal + r"\s*\)", tail
    )
    if inline:
        prefix = re.split(r"(?m)^    (?:public|private|protected)\b", source[: token.start()])[-1]
        prefix = JAVA_TOKENS.sub(
            lambda m: (
                m.group() if m.group("block") or m.group("string") else "\n" * m.group().count("\n")
            ),
            prefix,
        )
        condition, left, right = inline.groups()
        name = re.escape(condition)
        if (
            re.search(r"\bboolean\s+" + name + r"\b", prefix)
            and not re.search(r"\b" + name + r"\s*(?:=(?!=)|\+=)", prefix)
            and not tail[inline.end() :].lstrip().startswith("+")
        ):
            return [
                (condition + "_true", sql + json.loads(left)),
                (condition + "_false", sql + json.loads(right)),
            ]
    fragment = re.match(r"^\s*\+\s*(\w+)\s*\+\s*" + literal, tail)
    if fragment:
        prefix = re.split(r"(?m)^    (?:public|private|protected)\b", source[: token.start()])[-1]
        prefix = JAVA_TOKENS.sub(
            lambda m: (
                m.group() if m.group("block") or m.group("string") else "\n" * m.group().count("\n")
            ),
            prefix,
        )
        name = re.escape(fragment[1])
        declaration = re.findall(
            r"\bString\s+"
            + name
            + r"\s*=\s*(\w+)\s*\?\s*"
            + literal
            + r"\s*:\s*"
            + literal
            + r"\s*;",
            prefix,
        )
        assignments = re.findall(r"\b" + name + r"\s*(?:=|\+=)", prefix)
        if (
            len(declaration) == 1
            and len(assignments) == 1
            and not tail[fragment.end() :].lstrip().startswith("+")
        ):
            condition, left, right = declaration[0]
            if re.search(r"\bboolean\s+" + re.escape(condition) + r"\b", prefix):
                suffix = json.loads(fragment[2])
                return [
                    (condition + "_true", sql + json.loads(left) + suffix),
                    (condition + "_false", sql + json.loads(right) + suffix),
                ]
    raise ValidationError("Unsupported Java SQL concatenation at line " + str(line))


def extract_selects(source, widths=(1, 2, 500)):
    source = fold_java_literals(source)
    queries = []
    for token in JAVA_TOKENS.finditer(source):
        if token.group("block"):
            sql = textwrap.dedent(token.group()[3:-3]).strip()
        elif token.group("string"):
            sql = json.loads(token.group())
        else:
            continue
        if not re.match(r"^SELECT\b", sql.lstrip(), re.I):
            continue
        line = source.count("\n", 0, token.start()) + 1
        tail = source[token.end() :]
        variants = [("static", sql)]
        if tail.lstrip().startswith("+"):
            dynamic = DYNAMIC_IN.match(tail)
            if dynamic is None:
                variants = _boolean_fragment(source, token, sql, line)
            else:
                suffix = json.loads(dynamic[1])
                variants = [
                    ("in_" + str(n), sql + ",".join("?" for _ in range(n)) + suffix) for n in widths
                ]
        else:
            declaration = re.search(
                r"\bString\s+(\w+)\s*=\s*$", source[max(0, token.start() - 80) : token.start()]
            )
            if declaration:
                method_tail = re.split(
                    r"(?m)^    (?:public|private|protected)\b", tail, maxsplit=1
                )[0]
                additions = re.findall(
                    r"\b" + re.escape(declaration[1]) + r'\s*\+=\s*("(?:\\.|[^"\\])*")\s*;',
                    method_tail,
                )
                if len(additions) > 1:
                    raise ValidationError(
                        "Inspect multiple optional SQL fragments at line " + str(line)
                    )
                if additions:
                    variants.append(("sn_filter", sql + json.loads(additions[0])))
        queries.extend(
            {"line": line, "sql": statement, "variant": variant} for variant, statement in variants
        )
    return queries


def parameter_positions(sql):
    return [token.start() for token in SQL_TOKENS.finditer(sql) if token.group() == "?"]


def bind_sql(sql, values):
    positions = parameter_positions(sql)
    if len(positions) != len(values):
        raise ValidationError(
            "SQL parameter arity drift: "
            + str(len(positions))
            + " placeholders / "
            + str(len(values))
            + " values"
        )
    pieces = []
    previous = 0
    for position, value in zip(positions, values):
        pieces.extend((sql[previous:position], sql_value(value)))
        previous = position + 1
    pieces.append(sql[previous:])
    return "".join(pieces).rstrip().rstrip(";") + ";"
