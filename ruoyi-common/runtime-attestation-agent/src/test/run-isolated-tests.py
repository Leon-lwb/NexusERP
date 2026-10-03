"""只在独立临时目录编译及运行；不调用 Maven，不读写共享 target，不启动网络端口。"""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[4]
MODULE = ROOT / 'ruoyi-common/runtime-attestation-agent'
REPOSITORY = Path.home() / '.m2/repository'
DEPENDENCIES = (
    ('org/junit/jupiter/junit-jupiter-api', '5.12.2'),
    ('org/junit/jupiter/junit-jupiter-engine', '5.12.2'),
    ('org/junit/platform/junit-platform-commons', '1.12.2'),
    ('org/junit/platform/junit-platform-engine', '1.12.2'),
    ('org/junit/platform/junit-platform-launcher', '1.12.2'),
    ('org/opentest4j/opentest4j', '1.3.0'),
    ('org/apiguardian/apiguardian-api', '1.1.2'),
)
LAUNCHER = r'''import java.nio.file.Path;
import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
public final class IsolatedTestLauncher {
    public static void main(String[] args) {
        var request = LauncherDiscoveryRequestBuilder.request().selectors(
            DiscoverySelectors.selectPackage("com.nexus.runtime.attestation"),
            DiscoverySelectors.selectPackage("com.nexus.system.runtime")).build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        var out = new PrintWriter(System.out, true);
        listener.getSummary().printTo(out); listener.getSummary().printFailuresTo(out);
        if (listener.getSummary().getTestsFoundCount() == 0 || listener.getSummary().getTotalFailureCount() != 0) System.exit(1);
    }
}'''

def main():
    jars = []
    for artifact, version in DEPENDENCIES:
        name = artifact.rsplit('/', 1)[1]
        jar = REPOSITORY / artifact / version / f'{name}-{version}.jar'
        if not jar.is_file():
            raise SystemExit(f'缺少本地隔离测试依赖：{name} {version}；未下载或调用 Maven')
        jars.append(str(jar))
    environment = os.environ.copy()
    for name in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'):
        environment.pop(name, None)
    with tempfile.TemporaryDirectory(prefix='nexus-runtime-c-') as directory:
        temp = Path(directory)
        output = temp / 'classes'
        output.mkdir()
        launcher = temp / 'IsolatedTestLauncher.java'
        launcher.write_text(LAUNCHER, encoding='utf-8')
        sources = list((MODULE / 'src/main/java').rglob('*.java')) + list((MODULE / 'src/test/java').rglob('*.java'))
        for name in ('LoadedArtifactAssessment.java', 'RuntimeAttestationReport.java'):
            source = ROOT / 'ruoyi-modules/ruoyi-system/src/main/java/com/nexus/system/runtime' / name
            if list((MODULE / 'src/main/java').rglob('*.java')):
                sources.append(source)
        sources += list((ROOT / 'ruoyi-modules/ruoyi-system/src/test/java/com/nexus/system/runtime').glob('LoadedArtifactWitness*Test.java'))
        classpath = os.pathsep.join(jars)
        subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-cp', classpath,
                        '-d', str(output), *map(str, sources), str(launcher)], check=True, env=environment)
        subprocess.run(['java', '-Dnexus.runtime.test.classes=' + str(output),
                        '-Dnexus.runtime.test.work=' + str(temp), '-cp', str(output) + os.pathsep + classpath,
                        'IsolatedTestLauncher'], check=True, env=environment)

if __name__ == '__main__':
    main()
