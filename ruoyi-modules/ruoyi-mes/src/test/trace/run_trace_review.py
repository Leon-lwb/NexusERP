"""Isolated diagnostic reproductions for the P3 integration review. Never writes module target or real DB."""
from pathlib import Path
import os, subprocess, tempfile, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[5]
OUT=Path(tempfile.mkdtemp(prefix="review-p3-",dir=Path(__file__).parent))
reports=list((ROOT/"ruoyi-modules/ruoyi-station/target/surefire-reports").glob("TEST-*.xml"))
if not reports:reports=list((ROOT/"ruoyi-modules/ruoyi-mes/target/surefire-reports").glob("TEST-*.xml"))
properties=ET.parse(reports[0]).getroot().findall("./properties/property")
cp=next(p.get("value") for p in properties if p.get("name")=="java.class.path").split(os.pathsep)
cp=[str(p) for p in ROOT.glob("**/target/classes") if ".agents" not in str(p)]+cp
h2=Path.home()/".m2/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar"
if h2.exists():cp.append(str(h2))
cp=list(dict.fromkeys(cp))
# Prevent target/test-classes logging configurations from obscuring diagnostic observations.
logback=OUT/"review-logback.xml"
logback.write_text('<configuration><root level="ERROR"/></configuration>',encoding="utf-8")
sources=list((ROOT/"ruoyi-common/ruoyi-common-trace/src/main/java").rglob("*.java"))
sources+=[Path(__file__).with_name("TraceReviewProbe.java")]
for name in ["ruoyi-admin/src/main/java/com/nexus/web/service/MaterialIdentityTraceService.java",
             "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/trace/ErpProcessTraceEventHandler.java",
             "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/WmTraceNoGoodOutputService.java",
             "ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/wm/service/WmProductionRecptWriteGuard.java",
             "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/integration/trace/MesIdentityTraceReader.java",
             "ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/trace/MesTrackingUsageService.java",
             "ruoyi-modules/ruoyi-station/src/main/java/com/nexus/station/service/StationPassService.java"]:
 sources.append(ROOT/name)
args=["--release","17","-encoding","UTF-8","-parameters","-cp",os.pathsep.join(cp),"-processor","lombok.launch.AnnotationProcessorHider$AnnotationProcessor","-d",str(OUT)]+[str(p) for p in sources]
compile_file=OUT/"javac.args"
compile_file.write_text("\n".join('"'+a.replace("\\","/")+'"' for a in args),encoding="utf-8")
print("REVIEW_OUTPUT",OUT,flush=True)
subprocess.run(["javac","@"+str(compile_file)],cwd=ROOT,check=True)
run=OUT/"java.args"
run.write_text('"-Dlogback.configurationFile='+str(logback).replace("\\","/")+'"\n"-cp"\n"'+os.pathsep.join([str(OUT)]+cp).replace("\\","/")+'"\ncom.nexus.erp.wm.service.TraceReviewProbe\n',encoding="utf-8")
subprocess.run(["java","@"+str(run)],cwd=ROOT,check=True)
