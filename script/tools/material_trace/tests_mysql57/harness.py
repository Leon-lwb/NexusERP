"""Isolated MySQL 5.7 transport. No external DB endpoint or existing container option."""
from __future__ import annotations
import hashlib
import json
import os
import queue
import re
import runpy
import subprocess
import sys
import threading
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
OWNER_LABEL = "com.nexus.material-trace.mysql57.run-id"
WORKSPACE_LABEL = "com.nexus.material-trace.mysql57.workspace"
IMAGE = "4bc6bc963e6d"

class ValidationError(RuntimeError):
    pass

class SqlFailure(ValidationError):
    def __init__(self, schema, sql, output):
        self.schema, self.sql, self.output = schema, sql, output
        super().__init__(f"{schema}: {output[:1400]}")
    def evidence(self):
        return {"schema": self.schema, "sql": self.sql, "mysql_output": self.output}

def blocking_transaction(status, connection_id):
    for block in re.split(r"(?m)^---TRANSACTION ", status)[1:]:
        if "LOCK WAIT" in block and re.search(r"MySQL thread id " + str(connection_id) + r"\b", block):
            return block
    return None

def local_docker_endpoint(endpoint):
    parsed = urlsplit(endpoint)
    if parsed.scheme == "npipe": return endpoint.lower().startswith("npipe:////./pipe/")
    if parsed.scheme == "unix": return not parsed.netloc and parsed.path.startswith("/")
    return parsed.scheme == "tcp" and parsed.hostname in ("127.0.0.1", "localhost", "::1")

def final_mysql_ready(returncode, output):
    return returncode == 0 and output.strip() == "0"

def ident(value):
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", value):
        raise ValidationError("Unsafe SQL identifier")
    return "`" + value + "`"

def sql_value(value):
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    encoded = str(value).encode("utf-8").hex()
    return f"_utf8mb4 0x{encoded}" if encoded else "''"

def scoped_output(root, relative):
    target = (root / relative).resolve()
    if not target.is_relative_to(root.resolve()):
        raise ValidationError("Output must remain inside tests_mysql57")
    return target

def statements(text):
    """Split install SQL, respecting strings/comments; never use this on DELIMITER procedures."""
    result, buf, quote, i = [], [], None, 0
    while i < len(text):
        ch = text[i]
        if quote:
            buf.append(ch)
            if ch == "\\" and quote != "`" and i + 1 < len(text):
                i += 1; buf.append(text[i])
            elif ch == quote:
                if i + 1 < len(text) and text[i + 1] == quote:
                    i += 1; buf.append(text[i])
                else:
                    quote = None
        elif ch in ("'", '"', "`"):
            quote = ch; buf.append(ch)
        elif ch == "#" or (text[i:i+2] == "--" and (i+2 == len(text) or text[i+2].isspace())):
            end = text.find("\n", i)
            i = len(text) if end < 0 else end
            buf.append("\n")
        elif text[i:i+2] == "/*":
            end = text.find("*/", i+2)
            if end < 0: raise ValidationError("Unterminated SQL comment")
            i = end + 1; buf.append(" ")
        elif ch == ";":
            if "".join(buf).strip(): result.append("".join(buf).strip() + ";")
            buf = []
        else:
            buf.append(ch)
        i += 1
    if quote: raise ValidationError("Unterminated SQL literal")
    if "".join(buf).strip(): result.append("".join(buf).strip())
    return result

def extract_tables(text):
    tables = {}
    for statement in statements(text.lstrip("\ufeff")):
        if not re.match(r"CREATE\s+TABLE\b", statement, re.I): continue
        match = re.match(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([A-Za-z_][A-Za-z0-9_]*)`?\s*\(", statement, re.I)
        if not match: raise ValidationError("Only unqualified CREATE TABLE DDL is permitted")
        name = match[1]
        if name in tables: raise ValidationError(f"Duplicate CREATE TABLE: {name}")
        tables[name] = statement if statement.endswith(";") else statement + ";"
    if not tables: raise ValidationError("Install SQL has zero CREATE TABLE statements")
    return tables

def verify_ownership(profile, cid, name, token, workspace, image):
    labels = profile.get("Config", {}).get("Labels") or {}
    host = profile.get("HostConfig", {})
    if (profile.get("Id") != cid or profile.get("Name") != "/" + name or
            profile.get("Image") != image or labels.get(OWNER_LABEL) != token or
            labels.get(WORKSPACE_LABEL) != workspace or not name.startswith("nexus-mtrace-mysql57-")):
        raise ValidationError("Container ownership mismatch: refusing execution/removal")
    if (host.get("NetworkMode") != "none" or host.get("PortBindings") or host.get("Binds") or
            "/var/lib/mysql" not in (host.get("Tmpfs") or {}) or
            any(m.get("Type") != "tmpfs" for m in profile.get("Mounts", []))):
        raise ValidationError("Container isolation mismatch: refusing execution/removal")
    return True

@dataclass
class QueryResult:
    rows: list[list[str]]
    errors: list[str]
    @property
    def first(self):
        if not self.rows: raise ValidationError("Expected a non-empty SQL result")
        return self.rows[0]
    @property
    def scalar(self):
        return self.first[0]

class MysqlSession:
    """One persistent mysql client = one actual server connection, framed over stdin/stdout."""
    def __init__(self, sandbox, schema):
        sandbox.verify()
        self.sandbox, self.schema = sandbox, schema
        self.process = subprocess.Popen(sandbox.mysql_command(schema, force=True), stdin=subprocess.PIPE,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace", bufsize=1)
        self.lines = queue.Queue()
        self.reader = threading.Thread(target=self._read, daemon=True)
        self.reader.start()
        self.connection_id = int(self.execute("SELECT CONNECTION_ID();").scalar)
        sandbox.sessions.append(self)
    def _read(self):
        try:
            for line in self.process.stdout: self.lines.put(line.rstrip("\r\n"))
        finally:
            self.lines.put(None)
    def execute(self, sql, *, error=None, timeout=25):
        marker = "__mtrace_" + uuid.uuid4().hex
        self.process.stdin.write(sql.rstrip().rstrip(";") + ";\nSELECT '" + marker + "';\n")
        self.process.stdin.flush()
        output, deadline = [], time.monotonic() + timeout
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0: raise SqlFailure(self.schema, sql, "SQL frame timed out")
            try: line = self.lines.get(timeout=remaining)
            except queue.Empty: raise SqlFailure(self.schema, sql, "SQL frame timed out") from None
            if line is None: raise SqlFailure(self.schema, sql, "mysql client exited: " + "\n".join(output))
            if line == marker: break
            if line: output.append(line)
        errors = [line for line in output if re.match(r"ERROR\s+\d+", line)]
        if error is None and errors: raise SqlFailure(self.schema, sql, "\n".join(output))
        if error is not None and not any(re.match(r"ERROR\s+" + str(error) + r"\b", line) for line in errors):
            raise SqlFailure(self.schema, sql, f"Expected MySQL error {error}, received: " + "\n".join(output))
        return QueryResult([line.split("\t") for line in output if line not in errors], errors)
    def close(self):
        if self.process.poll() is None:
            try:
                self.process.stdin.write("ROLLBACK;\nquit\n"); self.process.stdin.flush(); self.process.stdin.close()
                self.process.wait(timeout=3)
            except (OSError, subprocess.TimeoutExpired):
                self.process.terminate()
                try: self.process.wait(timeout=3)
                except subprocess.TimeoutExpired: self.process.kill()

class DockerSandbox:
    def __init__(self):
        self.token = uuid.uuid4().hex
        self.name = "nexus-mtrace-mysql57-" + datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + "-" + self.token[:12]
        self.workspace = hashlib.sha256(str(ROOT).encode()).hexdigest()[:20]
        self.cid, self.image, self.sessions, self.removed = None, None, [], False
        self.readiness_observations = []
        self.endpoint_checked = False
        self.endpoint_kind = None
    @staticmethod
    def command(args, **kwargs):
        return subprocess.run(["docker", *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=kwargs.pop("timeout", 60), **kwargs)
    def inspect(self):
        result = self.command(["inspect", self.cid or self.name])
        if result.returncode: raise ValidationError(result.stderr.strip())
        return json.loads(result.stdout)[0]
    def verify(self):
        profile = self.inspect()
        verify_ownership(profile, self.cid, self.name, self.token, self.workspace, self.image)
        return profile
    def start(self):
        endpoint = os.environ.get("DOCKER_HOST") if not os.environ.get("DOCKER_CONTEXT") else None
        if not endpoint:
            context = self.command(["context", "show"])
            if context.returncode: raise ValidationError("Cannot verify local Docker context")
            info = self.command(["context", "inspect", context.stdout.strip()])
            if info.returncode: raise ValidationError("Cannot inspect local Docker context")
            endpoint = json.loads(info.stdout)[0]["Endpoints"]["docker"]["Host"]
        if not local_docker_endpoint(endpoint): raise ValidationError("Remote Docker context rejected; no daemon/container operation performed")
        self.endpoint_checked = True
        self.endpoint_kind = urlsplit(endpoint).scheme
        image = self.command(["image", "inspect", IMAGE])
        if image.returncode: raise ValidationError("Required cached mysql:5.7 image is unavailable; no pull attempted")
        self.image = json.loads(image.stdout)[0]["Id"]
        if not self.image.startswith("sha256:" + IMAGE): raise ValidationError("Unexpected cached image ID")
        args = ["run", "--detach", "--pull=never", "--name", self.name,
                "--label", OWNER_LABEL + "=" + self.token, "--label", WORKSPACE_LABEL + "=" + self.workspace,
                "--network", "none", "--memory", "1g", "--pids-limit", "128",
                "--tmpfs", "/var/lib/mysql:rw,nosuid,nodev,size=768m", "--tmpfs", "/tmp:rw,nosuid,nodev,size=64m",
                "--env", "MYSQL_ALLOW_EMPTY_PASSWORD=yes", self.image,
                "--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci",
                "--innodb-buffer-pool-size=64M", "--innodb-log-file-size=16M", "--innodb-lock-wait-timeout=8"]
        created = self.command(args)
        if created.returncode:
            raise ValidationError("Dedicated container failed to start: " + created.stderr.strip())
        self.cid = created.stdout.strip()
        self.verify()
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            ping = self.command(["exec", self.cid, "mysql", "--protocol=socket", "-uroot", "--batch", "--skip-column-names", "-e", "SELECT @@skip_networking;"], timeout=8)
            self.readiness_observations.append({"returncode": ping.returncode, "skip_networking": ping.stdout.strip()[:30]})
            # The image first starts a temporary socket server with skip_networking=1, then restarts mysqld.
            if final_mysql_ready(ping.returncode, ping.stdout): return self
            time.sleep(0.5)
        raise ValidationError("Dedicated MySQL did not become ready within 60 seconds")
    def mysql_command(self, schema=None, force=False):
        args = ["docker", "exec", "-i", self.cid, "mysql", "--protocol=socket", "-uroot",
                "--default-character-set=utf8mb4", "--batch", "--raw", "--skip-column-names", "--unbuffered"]
        if force: args.append("--force")
        if schema: args += ["--database=" + schema]
        return args
    def run_sql(self, schema, sql, *, error=None):
        self.verify()
        result = subprocess.run(self.mysql_command(schema), input=sql, capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=90)
        output = result.stdout + result.stderr
        if error is None and result.returncode:
            raise SqlFailure(schema, sql, output)
        if error is not None and not re.search(r"ERROR\s+" + str(error) + r"\b", output):
            raise SqlFailure(schema, sql, f"Expected MySQL error {error}, received: " + output)
        return output
    def session(self, schema):
        return MysqlSession(self, schema)
    def cleanup(self):
        if not self.endpoint_checked and not self.cid:
            self.removed = True
            return
        for session in reversed(self.sessions): session.close()
        if not self.cid:
            # A failed `docker run` may have created only this exact unique name.
            result = self.command(["inspect", self.name])
            if result.returncode:
                self.removed = True; return
            profile = json.loads(result.stdout)[0]
            self.cid = profile["Id"]
        self.verify()  # Exact ID, name, unique owner, workspace, image, network and storage checked again.
        removed = self.command(["rm", "--force", self.cid])
        if removed.returncode: raise ValidationError("Owned-container cleanup failed: " + removed.stderr)
        if self.command(["inspect", self.cid]).returncode == 0: raise ValidationError("Owned container still exists")
        self.removed = True

class SchemaSnapshot:
    def __init__(self):
        names = ["script/tools/material_trace/schema_contract.py",
                 "script/tools/material_trace/core.py", "script/tools/material_trace/metadata.py"]
        names += ["script/tools/material_trace/tests_mysql57/" + name + ".py"
                  for name in ("harness", "java_sql", "document_sql", "reader_cases", "business_reader_cases", "policy_cases", "strict_cases", "extended_cases", "run")]
        names += ["ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/" + name + ".java"
                  for name in ("ErpCompletionCancellationService", "ErpCompletionCancellationHandler")]
        names += ["ruoyi-common/ruoyi-common-trace/src/main/java/com/nexus/common/trace/" + name + ".java"
                  for name in ("CompletionCancellationRequest", "CompletionCancellationResult", "CompletionCancellationProof",
                               "port/CompletionCancellationPort", "jdbc/JdbcTraceJournal")]
        names += ["ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpSerialIdentityService.java",
                  "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/integration/trace/MesIdentityTraceReader.java"]
        names += ["ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpIdentityTraceReader.java",
                  "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/integration/trace/MesWorkOrderReferenceReader.java"]
        names += ["ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/" + name + ".java"
                  for name in ("MesErpSourceReader", "MesMaterialTraceWriter", "MesCompletionManifestService", "MesTrackingUsageService", "MesProductLotOutputWriter", "MesCompletionCancellationService", "MesTraceStore")]
        names += ["ruoyi-common/ruoyi-common-trace/src/main/java/com/nexus/common/trace/"+name+".java"
                  for name in ("RecallSubjectSnapshot","TraceRecallSnapshots","IdentityTracePart","BatchTracePart",
                               "SerialIdentity","WorkOrderTraceImpact","TraceReadValues","TraceCursor",
                               "port/IdentityTraceReadPort","port/SerialIdentityPort")]
        names += ["ruoyi-admin/src/main/java/com/nexus/web/service/MaterialIdentityTraceService.java",
                  "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpTraceContext.java",
                  "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesTraceContext.java"]
        names += ["ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/PrdWorkOrderTrackingPolicyService.java",
                  "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/prd/service/PrdWorkOrderPolicyAdoptionService.java",
                  "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/integration/trace/MesWorkOrderTrackingPolicyReader.java",
                  "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesPreparedWorkOrderPolicies.java"]
        names += ["ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/"+name+".java"
                  for name in ("MaterialTrackingPolicyGuard","ErpMaterialPolicyService","StructuredLabelResolver",
                               "ScopedMaterialCodeResolver","ErpDocumentCodeResolver","ErpCodeResolverService","WmBarcodeIdentityGuard")]
        names += ["ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/"+name+".java"
                  for name in ("service/impl/WmBarcodeServiceImpl","domain/WmBarcode","domain/bo/WmBarcodeBo","domain/vo/WmBarcodeVo","domain/vo/WmSnVo",
                               "service/impl/WmBatchServiceImpl")]
        names += ["ruoyi-common/ruoyi-common-trace/src/main/java/com/nexus/common/trace/"+name+".java"
                  for name in ("WorkOrderTrackingPolicySnapshot","port/WorkOrderTrackingPolicyPort","Gs1Identity",
                               "CodeResolution","CodeObjectType","CodeResolveRequest","CodeCandidate","port/CodeResolverPort")]
        names += ["script/sql/update/2026-10-19-material-trace-strict-constraints-erp.sql"]
        names += [f"script/sql/ry-{ds}.sql" for ds in ("erp", "mes")]
        names += [f"script/sql/update/2026-10-18-material-trace-{part}{ds}.sql" for ds in ("erp", "mes") for part in ("", "preflight-")]
        names += ["script/tools/material_trace/examples/schema-handoff.json",
                  "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/WmBatchWriteGuard.java",
                  "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/WmBatch.java",
                  "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/domain/bo/WmBatchBo.java",
                  "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesProductionDetailFactsService.java"]
        names += ["ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/"+name+".java" for name in
                  ("domain/vo/ProFlowCardVo","domain/vo/ProFlowCardStepVo","domain/vo/ProWorkOrderVo",
                   "service/impl/ProFlowCardServiceImpl","service/impl/ProWorkOrderServiceImpl")]
        self.raw = {name: (ROOT / name).read_bytes() for name in names}
        self.hashes = {name: hashlib.sha256(data).hexdigest() for name, data in self.raw.items()}
        self.contract = runpy.run_path(str(ROOT / names[0]))  # __main__ rendering is deliberately never invoked.
        head = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True, check=True)
        self.baseline_ref = head.stdout.strip()
        self.current, self.baseline = {}, {}
        for ds in ("erp", "mes"):
            self.current[ds] = extract_tables(self.text(f"script/sql/ry-{ds}.sql"))
            old = subprocess.run(["git", "show", f"{self.baseline_ref}:script/sql/ry-{ds}.sql"], cwd=ROOT,
                                 capture_output=True, check=True).stdout.decode("utf-8-sig")
            self.baseline[ds] = extract_tables(old)
    def text(self, name):
        return self.raw[name].decode("utf-8-sig").replace("\r\n", "\n")
    def migration(self, ds, schema, *, backup=True, expected=True):
        setup = "SET @material_trace_expected_schema=" + (sql_value(schema) if expected else "NULL") + ";\n"
        setup += "SET @material_trace_backup_reference=" + (sql_value("isolated-synthetic-baseline:" + self.baseline_ref) if backup else "NULL") + ";\n"
        return setup + self.text(f"script/sql/update/2026-10-18-material-trace-{ds}.sql")
    def unchanged(self):
        changed = [name for name, digest in self.hashes.items() if hashlib.sha256((ROOT/name).read_bytes()).hexdigest() != digest]
        if changed: raise ValidationError("Schema owner changed inputs during validation; rerun final DDL: " + ", ".join(changed))
        return True


def cleanup_owned(box, output, report):
    try:
        if box.cid:
            box.verify()
            logs = box.command(["logs", "--tail", "100", box.cid])
            (output / "owned-container-startup.log").write_text(logs.stdout + logs.stderr, encoding="utf-8")
    except Exception as exc:
        report["diagnostic_error"] = str(exc)
    try:
        box.cleanup()  # Independently verifies exact ownership immediately before removal.
        report["container"]["removed_after_ownership_verification"] = box.removed
    except Exception as exc:
        report["cleanup_error"] = str(exc)
