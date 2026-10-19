"""显式启用的真实事务注册探针，只运行于本次拥有且禁止外部网络的隔离数据库容器。"""

from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import socketserver
import subprocess
import sys
import threading
import uuid

sys.dont_write_bytecode = True
from harness import (
    DockerSandbox,
    ValidationError,
    HERE,
    ROOT,
    cleanup_owned,
    extract_tables,
    ident,
    scoped_output,
    sql_value,
)

JAVA_RELATIVE = (
    "ruoyi-modules/ruoyi-erp/src/test/java/com/nexus/erp/wm/trace/ErpSerialIdentityMysql57IT.java"
)
SOURCES = [
    JAVA_RELATIVE,
    "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpSerialIdentityService.java",
    "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpTraceContext.java",
    "ruoyi-common/ruoyi-common-trace/src/main/java/com/nexus/common/trace/SerialReservationCommand.java",
    "ruoyi-common/ruoyi-common-trace/src/main/java/com/nexus/common/trace/SerialReservation.java",
    "ruoyi-common/ruoyi-common-trace/src/main/java/com/nexus/common/trace/SerialIdentity.java",
    "ruoyi-common/ruoyi-common-trace/src/main/java/com/nexus/common/trace/TraceHashes.java",
    "ruoyi-common/ruoyi-common-trace/src/main/java/com/nexus/common/trace/port/SerialIdentityPort.java",
]
TABLES = [
    "bd_material",
    "bd_serial_number_rule",
    "wm_batch",
    "wm_sn",
    "wm_serial_reservation",
    "wm_serial_sequence",
    "wm_serial_lifecycle_command",
]


def arguments(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--run", action="store_true", help="explicitly permit a new owned temporary container"
    )
    parser.add_argument(
        "--classpath-file",
        type=Path,
        default=ROOT / "ruoyi-modules/ruoyi-erp/target/wm-sn-entry-slice/classpath.txt",
    )
    parser.add_argument(
        "--output", help="new results/registry-java-* directory, never an existing directory"
    )
    return parser.parse_args(argv)


def result_directory(relative):
    target = scoped_output(HERE, relative)
    if target.parent != HERE / "results" or not target.name.startswith("registry-java-"):
        raise ValidationError("Java probe output must be a new results/registry-java-* directory")
    return target


def classpath_entries(text):
    entries = [part.strip() for part in text.strip().split(os.pathsep) if part.strip()]
    if not entries:
        raise ValidationError("Isolated classpath is empty; no Maven is started")
    for item in entries:
        if "://" in item or not Path(item).exists():
            raise ValidationError("Classpath must contain existing local files/directories only")
    return list(dict.fromkeys(entries))


class OwnedJdbcBridge:
    """临时传输仅绑定环回地址，并核验目标为本次拥有且禁止外部网络的容器。"""

    def __init__(self, box):
        box.verify()  # 必须在创建套接字或调用容器执行命令之前完成校验。
        self.box = box
        self.lock = threading.Lock()
        self.processes = set()
        bridge = self

        class Handler(socketserver.BaseRequestHandler):
            def handle(self):
                bridge.box.verify()
                # 只执行固定命令，不接受调用方提供的命令、路径、服务地址或插值内容。
                code = 'exec 3<>/dev/tcp/127.0.0.1/3306; cat <&3 & reader=$!; cat >&3; kill "$reader" 2>/dev/null; exec 3>&-'
                process = subprocess.Popen(
                    ["docker", "exec", "-i", bridge.box.cid, "bash", "-c", code],
                    stdin=subprocess.PIPE,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.DEVNULL,
                    bufsize=0,
                )
                with bridge.lock:
                    bridge.processes.add(process)

                def upstream():
                    try:
                        while chunk := self.request.recv(65536):
                            process.stdin.write(chunk)
                            process.stdin.flush()
                    except (OSError, ValueError):
                        pass
                    finally:
                        try:
                            process.stdin.close()
                        except (OSError, ValueError):
                            pass

                sender = threading.Thread(target=upstream, daemon=True)
                sender.start()
                try:
                    while chunk := process.stdout.read(65536):
                        self.request.sendall(chunk)
                except (OSError, ValueError):
                    pass
                finally:
                    try:
                        self.request.shutdown(socket.SHUT_RDWR)
                    except OSError:
                        pass
                    if process.poll() is None:
                        process.terminate()
                    try:
                        process.wait(timeout=3)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=3)
                    sender.join(timeout=3)
                    with bridge.lock:
                        bridge.processes.discard(process)

        self.server = socketserver.ThreadingTCPServer(("127.0.0.1", 0), Handler)
        self.server.daemon_threads = True
        self.port = self.server.server_address[1]
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def close(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=3)
        with self.lock:
            processes = list(self.processes)
        for process in processes:
            if process.poll() is None:
                process.terminate()
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=3)


def quoted(value):
    return '"' + str(value).replace("\\", "/").replace('"', '\\"') + '"'


def compile_probe(output, classpath_file):
    if not classpath_file.is_file():
        raise ValidationError("Missing local isolated classpath file; no Maven is started")
    original = [
        part
        for part in classpath_file.read_text(encoding="utf-8").strip().split(os.pathsep)
        if part and Path(part).exists()
    ]
    # 单独冻结探针类，并复用项目构建中已管理的数据库驱动版本。
    original = [part for part in original if "mysql-connector" not in part]
    connector = (
        Path.home() / ".m2/repository/com/mysql/mysql-connector-j/9.6.0/mysql-connector-j-9.6.0.jar"
    )
    original.append(str(connector))
    cp = classpath_entries(os.pathsep.join(original))
    classes = output / "classes"
    classes.mkdir()
    compile_args = [
        "-encoding",
        "UTF-8",
        "-parameters",
        "-cp",
        quoted(os.pathsep.join(cp)),
        "-processor",
        "lombok.launch.AnnotationProcessorHider$AnnotationProcessor",
        "-d",
        quoted(classes),
    ]
    compile_args.extend(quoted(ROOT / source) for source in SOURCES)
    args_file = output / "javac.args"
    args_file.write_text("\n".join(compile_args) + "\n", encoding="utf-8")
    result = subprocess.run(
        ["javac", "-J-Dfile.encoding=UTF-8", "@" + str(args_file)],
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        timeout=90,
    )
    (output / "javac.log").write_text(result.stdout + result.stderr, encoding="utf-8")
    if result.returncode:
        raise ValidationError(
            "Isolated javac failed; inspect javac.log (no Maven or container was started)"
        )
    run_file = output / "java.args"
    run_file.write_text(
        "\n".join(
            [
                "-Dfile.encoding=UTF-8",
                "-cp",
                quoted(os.pathsep.join([str(classes), *cp])),
                "com.nexus.erp.wm.trace.ErpSerialIdentityMysql57IT",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return run_file


def execute(args, output, report):
    inputs = [
        *SOURCES,
        "script/sql/ry-erp.sql",
        "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/MaterialTrackingPolicyGuard.java",
        "script/tools/material_trace/tests_mysql57/harness.py",
        "script/tools/material_trace/tests_mysql57/registry_java_runner.py",
    ]
    report["input_sha256"] = {
        name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in inputs
    }
    tables = extract_tables((ROOT / "script/sql/ry-erp.sql").read_text(encoding="utf-8-sig"))
    if missing := set(TABLES) - set(tables):
        raise ValidationError("Current install DDL lacks probe tables: " + str(sorted(missing)))
    run_file = compile_probe(output, args.classpath_file)
    box = DockerSandbox()
    bridge = None
    report["container"] = {"network": "none", "storage": "tmpfs", "published_ports": False}
    try:
        box.start()
        report["container"].update(
            {"id": box.cid, "name": box.name, "image": box.image, "run_token": box.token}
        )
        erp = "mt57_registry_" + box.token[:16] + "_erp"
        master = "mt57_registry_" + box.token[:16] + "_master"
        password = uuid.uuid4().hex
        box.run_sql(
            None,
            "CREATE DATABASE "
            + ident(erp)
            + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE DATABASE "
            + ident(master)
            + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;",
        )
        box.run_sql(erp, "\n".join(tables[name] for name in TABLES))
        box.run_sql(
            erp,
            "CREATE TABLE probe_deadlock_gate(id INT PRIMARY KEY,v INT NOT NULL) ENGINE=InnoDB; INSERT INTO probe_deadlock_gate VALUES "
            + ",".join("(" + str(i) + ",0)" for i in range(1, 103))
            + ";",
        )
        box.run_sql(
            None,
            "CREATE USER 'registry_probe'@'127.0.0.1' IDENTIFIED BY "
            + "'"
            + password
            + "'; GRANT ALL ON "
            + ident(erp)
            + ".* TO 'registry_probe'@'127.0.0.1'; GRANT ALL ON "
            + ident(master)
            + ".* TO 'registry_probe'@'127.0.0.1';",
        )
        (output / "owned-runtime.json").write_text(
            json.dumps(report["container"], indent=2), encoding="utf-8"
        )
        bridge = OwnedJdbcBridge(box)
        report["transport"] = {
            "kind": "loopback-to-owned-docker-exec",
            "bind": "127.0.0.1",
            "port": bridge.port,
            "erp_schema": erp,
            "master_schema": master,
        }
        env = os.environ.copy()
        env.update(
            {
                "NEXUS_REGISTRY_MYSQL57_TOKEN": box.token,
                "NEXUS_REGISTRY_MYSQL57_PORT": str(bridge.port),
                "NEXUS_REGISTRY_MYSQL57_ERP": erp,
                "NEXUS_REGISTRY_MYSQL57_MASTER": master,
                "NEXUS_REGISTRY_MYSQL57_PASSWORD": password,
                "NEXUS_REGISTRY_MYSQL57_OUTPUT": str(output),
            }
        )
        result = subprocess.run(
            ["java", "@" + str(run_file)],
            cwd=ROOT,
            env=env,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=240,
        )
        (output / "java.log").write_text(result.stdout + result.stderr, encoding="utf-8")
        report["java_exit_code"] = result.returncode
        evidence = output / "java-evidence.json"
        if evidence.exists():
            report["java"] = json.loads(evidence.read_text(encoding="utf-8"))
        report["input_unchanged"] = all(
            hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest
            for name, digest in report["input_sha256"].items()
        )
        (output / "innodb-status.txt").write_text(
            box.run_sql(erp, "SHOW ENGINE INNODB STATUS;"), encoding="utf-8"
        )
        if not report["input_unchanged"]:
            raise ValidationError(
                "Input sources/DDL changed during probe; rerun against the final snapshot"
            )
        if not successful_probe(result.returncode, report.get("java", {})):
            raise ValidationError("Java/Spring probe failed or ran zero tests; inspect java.log")
    finally:
        if bridge:
            bridge.close()
        cleanup_owned(box, output, report)
        if not box.removed:
            raise ValidationError("Owned-container cleanup was not verified; inspect cleanup_error")


def successful_probe(exit_code, evidence):
    return (
        exit_code == 0
        and evidence.get("testsSucceeded", 0) > 0
        and evidence.get("testsSucceeded") == evidence.get("testsFound")
        and evidence.get("testsFailed") == 0
        and evidence.get("testsSkipped") == 0
    )


def main(argv=None):
    args = arguments(argv)
    if not args.run:
        print(
            "Opt-in required: --run creates only one owned network-none MySQL 5.7 container; no existing database endpoint is accepted."
        )
        return 2
    suffix = uuid.uuid4().hex[:12]
    output = result_directory(args.output or "results/registry-java-" + suffix)
    if output.exists():
        raise ValidationError("Refusing to reuse an existing result directory")
    output.mkdir(parents=True)
    report = {
        "kind": "real-java-spring-registry-mysql57",
        "synthetic_data_only": True,
        "maven_started": False,
    }
    status = 0
    try:
        execute(args, output, report)
        report["status"] = "PASS"
    except Exception as failure:
        report["status"] = "FAIL"
        report["error"] = str(failure)
        status = 1
    (output / "report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(
        json.dumps(
            {
                "status": report["status"],
                "report": str(output / "report.json"),
                "error": report.get("error"),
                "tests": report.get("java", {}).get("testsSucceeded"),
                "owned_container_removed": report.get("container", {}).get(
                    "removed_after_ownership_verification"
                ),
            },
            ensure_ascii=False,
        )
    )
    return status


if __name__ == "__main__":
    raise SystemExit(main())
