"""单独编译/执行原业务URL适配回归，复用本机已编译依赖；不启动服务或Maven reactor。"""

from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
target = root / "ruoyi-modules/ruoyi-erp/target"
out = target / "original-sn-adapter-slice"
out.mkdir(exist_ok=True)
repo = Path.home() / ".m2/repository"
launcher = sorted((repo / "org/junit/platform/junit-platform-launcher").glob("*/*.jar"))[-1]
lombok = repo / "org/projectlombok/lombok/1.18.42/lombok-1.18.42.jar"
classpath = ";".join(
    [
        str(out),
        str(target / "p2-classes"),
        str(launcher),
        (target / "p2-classpath.txt").read_text(encoding="utf-8-sig").strip(),
    ]
)
base = root / "ruoyi-modules/ruoyi-erp/src"
sources = sorted((base / "main/java/com/nexus/erp/wm/controller/serial").glob("*.java"))
sources += [
    base / "main/java/com/nexus/erp/wm/controller/WmSerialDetailAdvice.java",
    base / "main/java/com/nexus/erp/wm/domain/vo/pda/PdaActionResultVo.java",
]
sources += [
    base / ("main/java/com/nexus/erp/wm/" + path)
    for path in [
        "service/WmSerialSelectionService.java",
        "service/WmSerialInventoryService.java",
        "service/WmSerialInventoryRepository.java",
        "domain/bo/WmSerialSelectionBo.java",
    ]
]
tests = sorted((base / "test/java/com/nexus/erp/wm/controller/serial").glob("*Test.java"))
sources += sorted((base / "test/java/com/nexus/erp/wm/controller/serial").glob("*.java"))
runner = out / "OriginalSerialTestRunner.java"
runner.write_text(
    """import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.*;
public class OriginalSerialTestRunner {
 public static void main(String[] args) {
  var builder=LauncherDiscoveryRequestBuilder.request();for(String name:args)builder.selectors(selectClass(name));
  var launcher=LauncherFactory.create();var listener=new SummaryGeneratingListener();launcher.registerTestExecutionListeners(listener);
  launcher.execute(builder.build());var summary=listener.getSummary();var output=new java.io.PrintWriter(System.out);
  summary.printTo(output);summary.printFailuresTo(output);
  if(summary.getTestsFoundCount()==0||summary.getTestsSkippedCount()>0||summary.getTotalFailureCount()>0)System.exit(1);
 }
}
""",
    encoding="utf-8",
)
sources += [runner]
extra_tests = []
if "--inventory-regressions" in sys.argv:
    extra_tests = [
        "WmSerialSelectionVersionTest",
        "WmSerialInventoryServiceTest",
        "WmSerialInventoryTransactionTest",
    ]
    sources += [base / ("test/java/com/nexus/erp/wm/" + name + ".java") for name in extra_tests]


def args_file(name, values):
    path = out / name
    path.write_text(
        "\n".join('"' + str(a).replace("\\", "/") + '"' for a in values), encoding="utf-8"
    )
    return "@" + str(path)


compile_args = args_file(
    "compile.args",
    [
        "-encoding",
        "UTF-8",
        "-parameters",
        "-processorpath",
        lombok,
        "-processor",
        "lombok.launch.AnnotationProcessorHider$AnnotationProcessor",
        "-cp",
        classpath,
        "-d",
        out,
        *sources,
    ],
)
result = subprocess.run(["javac", compile_args])
if result.returncode:
    sys.exit(result.returncode)
selected_tests = ["com.nexus.erp.wm.controller.serial." + t.stem for t in tests]
if "--class" in sys.argv:
    selected_name = sys.argv[sys.argv.index("--class") + 1]
    if "." not in selected_name:
        selected_name = "com.nexus.erp.wm.controller.serial." + selected_name
    if selected_name not in selected_tests:
        raise SystemExit("指定的适配测试类不存在: " + selected_name)
    selected_tests = [selected_name]
run_args = args_file(
    "run.args",
    [
        "-Djunit.jupiter.extensions.autodetection.enabled=false",
        "-Dfile.encoding=UTF-8",
        "-cp",
        classpath,
        "OriginalSerialTestRunner",
        *selected_tests,
        *["com.nexus.erp.wm." + name for name in extra_tests],
    ],
)
sys.exit(subprocess.run(["java", run_args], cwd=out).returncode)
