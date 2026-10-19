"""复用已缓存依赖进行隔离编译与测试，不写入模块构建目录或连接真实业务库。"""

from pathlib import Path
import os, subprocess, tempfile, xml.etree.ElementTree as ET, sys

ROOT = Path(__file__).resolve().parents[5]
OUT = Path(tempfile.mkdtemp(prefix="mes-p3-", dir=Path(__file__).parent))
reports = list((ROOT / "ruoyi-modules/ruoyi-station/target/surefire-reports").glob("TEST-*.xml"))
if not reports:
    reports = list((ROOT / "ruoyi-modules/ruoyi-mes/target/surefire-reports").glob("TEST-*.xml"))
props = ET.parse(reports[0]).getroot().findall("./properties/property")
cp = next(p.get("value") for p in props if p.get("name") == "java.class.path").split(os.pathsep)
# 当前工作区编译类及已批准公共契约优先于缓存中的构建产物。
targets = [str(p) for p in ROOT.glob("**/target/classes") if ".agents" not in str(p)]
cp = targets + cp
repo = Path.home() / ".m2/repository"
for artifact in [
    "com/h2database/h2/2.3.232/h2-2.3.232.jar",
    "org/junit/platform/junit-platform-launcher/1.12.2/junit-platform-launcher-1.12.2.jar",
]:
    p = repo / artifact
    if p.exists():
        cp.append(str(p))
launcher = list((repo / "org/junit/platform/junit-platform-launcher").glob("*/*.jar"))
if not any("junit-platform-launcher" in p for p in cp):
    cp.append(str(launcher[-1]))
cp = list(dict.fromkeys(cp))
(OUT / "logback-test.xml").write_text(
    '<configuration><root level="ERROR"/></configuration>', encoding="utf-8"
)
java = OUT / "TraceTestRunner.java"
java.write_text(
    """import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.*;
public class TraceTestRunner {
 public static void main(String[] args) {
  var builder=LauncherDiscoveryRequestBuilder.request();
  for(String name:args) builder.selectors(selectClass(name));
  var listener=new SummaryGeneratingListener();
  var launcher=LauncherFactory.create(); launcher.registerTestExecutionListeners(listener); launcher.execute(builder.build());
  var summary=listener.getSummary(); summary.printTo(new java.io.PrintWriter(System.out)); summary.printFailuresTo(new java.io.PrintWriter(System.out));
  if(summary.getTestsFoundCount()==0 || summary.getTestsFailedCount()>0) System.exit(1);
 }
}""",
    encoding="utf-8",
)
mode = sys.argv[1] if len(sys.argv) > 1 else "all"
sources = [java]
selected = []
station = ROOT / "ruoyi-modules/ruoyi-station/src"
sources += [
    station / "main/java/com/nexus/station/service" / name
    for name in [
        "StationPassService.java",
        "StationSnService.java",
        "StationPassChecks.java",
        "StationSinglePassFingerprint.java",
    ]
]
# 编译全部工位夹具，包括选定业务套件之外的构造器引用。
sources += list((station / "test/java").rglob("*.java"))
selected += [
    "com.nexus.station.service." + name
    for name in [
        "StationPassServiceTest",
        "StationSnServiceTest",
        "StationSinglePassWriteTest",
        "StationProgressCurrentReadTest",
        "StationPassTransactionTest",
    ]
]
if mode == "station":
    mes = ROOT / "ruoyi-modules/ruoyi-mes/src"
    sources += [
        mes / "main/java/com/nexus/mes/trace" / name
        for name in [
            "MesSerialReferenceService.java",
            "MesTraceContext.java",
            "MesTraceSql.java",
            "MesPreparedWorkOrderPolicies.java",
        ]
    ]
    sources += [
        mes / "main/java/com/nexus/mes/integration/trace/MesWorkOrderTrackingPolicyReader.java"
    ]
sources += [
    ROOT / "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/pro/mapper/ProSnMapper.java"
]
if mode != "station":
    mes = ROOT / "ruoyi-modules/ruoyi-mes/src"
    sources += list((ROOT / "ruoyi-common/ruoyi-common-trace/src/main/java").rglob("*.java"))
    sources += list((mes / "main/java/com/nexus/mes/trace").glob("*.java"))
    sources += [
        mes / "main/java/com/nexus/mes/integration/trace/MesWorkOrderTrackingPolicyReader.java"
    ]
    sources += list((mes / "test/java/com/nexus/mes/trace").glob("*.java"))
    sources += [mes / "test/java/com/nexus/mes/testsupport/SpringStaticBootstrap.java"]
    selected += [
        "com.nexus.mes.trace.MesTraceBusinessTest",
        "com.nexus.mes.trace.MesErpSourceReaderTest",
        "com.nexus.mes.trace.MesSerialVisibilityServiceTest",
        "com.nexus.mes.trace.MesBatchOnlyOutputTest",
        "com.nexus.mes.trace.MesCompletionCancellationTest",
        "com.nexus.mes.trace.MesCompletionCancellationBoundaryTest",
        "com.nexus.mes.trace.MesSerialReferenceServiceTest",
        "com.nexus.mes.trace.MesCompletionCorrectionTest",
        "com.nexus.mes.trace.MesCompletionDeliveryModeTest",
        "com.nexus.mes.trace.MesMaterialPolicyGuardTest",
        "com.nexus.mes.trace.MesPreparedWorkOrderPoliciesTest",
        "com.nexus.mes.trace.MesFrozenComponentControllerTest",
        "com.nexus.mes.trace.MesFrozenDispatchEntryTest",
        "com.nexus.mes.trace.MesProductionDetailFactsTest",
    ]
    sources += [
        mes / "main/java/com/nexus/mes/common/mq/consumer/SubcontractSnGenerateConsumer.java",
        mes / "test/java/com/nexus/mes/common/mq/consumer/SubcontractSerialCommandTest.java",
    ]
    selected += ["com.nexus.mes.common.mq.consumer.SubcontractSerialCommandTest"]
    sources += [
        mes / "main/java/com/nexus/mes/pda/service/impl/PdaSnServiceImpl.java",
        mes / "test/java/com/nexus/mes/pda/service/impl/PdaSnServiceImplTest.java",
    ]
    selected += ["com.nexus.mes.pda.service.impl.PdaSnServiceImplTest"]
    sources += [mes / "test/java/com/nexus/mes/lt/service/impl/LtLotServiceImplTest.java"]
    selected += ["com.nexus.mes.lt.service.impl.LtLotServiceImplTest"]
    for name in [
        "lt/service/impl/LtWoConsumptionServiceImplTest.java",
        "pro/service/impl/ProWorkOrderServiceImplTest.java",
        "pro/service/impl/ProFlowCardServiceImplTest.java",
    ]:
        sources.append(mes / "test/java/com/nexus/mes" / name)
    selected += [
        "com.nexus.mes.lt.service.impl.LtWoConsumptionServiceImplTest",
        "com.nexus.mes.pro.service.impl.ProWorkOrderServiceImplTest",
        "com.nexus.mes.pro.service.impl.ProFlowCardServiceImplTest",
    ]
    for name in [
        "pro/domain/vo/ProFlowCardStepVo.java",
        "pro/domain/vo/ProWorkOrderVo.java",
        "pro/domain/ProSn.java",
        "pro/domain/vo/ProSnVo.java",
        "pro/service/IProSnService.java",
        "pro/service/impl/ProSnServiceImpl.java",
        "pro/controller/ProSnController.java",
        "pro/service/impl/ProWorkOrderServiceImpl.java",
        "pro/domain/bo/ProExitBo.java",
        "pro/service/impl/ProFlowCardServiceImpl.java",
        "pro/service/impl/ProDispatchServiceImpl.java",
        "pro/service/ProWorkOrderStepProgressService.java",
        "lt/domain/LtLot.java",
        "lt/domain/LtWoConsumption.java",
        "lt/domain/vo/LtWoConsumptionVo.java",
        "lt/domain/bo/LtWoConsumptionBo.java",
        "lt/domain/bo/LtWoPickBo.java",
        "lt/service/impl/LtWoConsumptionServiceImpl.java",
        "lt/service/impl/LtLotServiceImpl.java",
        "lt/service/impl/LtSnBindingServiceImpl.java",
        "lt/listener/LtSnBindingEventListener.java",
    ]:
        sources.append(mes / "main/java/com/nexus/mes" / name)
if mode == "cancellation":
    selected = [
        "com.nexus.mes.trace.MesCompletionCancellationTest",
        "com.nexus.mes.trace.MesCompletionCancellationBoundaryTest",
    ]
if mode == "detail":
    selected = ["com.nexus.mes.trace.MesProductionDetailFactsTest"]
args = [
    "--release",
    "17",
    "-encoding",
    "UTF-8",
    "-parameters",
    "-cp",
    os.pathsep.join([str(OUT)] + cp),
    "-processor",
    "lombok.launch.AnnotationProcessorHider$AnnotationProcessor",
    "-d",
    str(OUT),
] + [str(p) for p in sources]
argfile = OUT / "javac.args"
argfile.write_text("\n".join('"' + a.replace("\\", "/") + '"' for a in args), encoding="utf-8")
print("ISOLATED_OUTPUT", OUT, flush=True)
subprocess.run(["javac", "@" + str(argfile)], cwd=ROOT, check=True)
# 通过参数文件传入类路径，避免超过操作系统进程命令长度限制。
runargs = OUT / "java.args"
runargs.write_text(
    '"-cp"\n"'
    + os.pathsep.join([str(OUT)] + cp).replace("\\", "/")
    + '"\nTraceTestRunner\n'
    + "\n".join(selected),
    encoding="utf-8",
)
subprocess.run(["java", "@" + str(runargs)], cwd=ROOT, check=True)
