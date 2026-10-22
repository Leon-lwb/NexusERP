import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { delimiter, dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { stripVTControlCharacters } from 'node:util';

const uiRoot = fileURLToPath(new URL('../', import.meta.url));
const repoRoot = resolve(uiRoot, '..');

/** 直接使用 Maven 的 Java 入口，避免 Windows 批处理与 shell 再解析参数中的空格、& 或引号。 */
export function buildMavenCommand({ repoRoot: repository = repoRoot, output, env = process.env, platform = process.platform }) {
  const homes = [env.MAVEN_HOME, env.M2_HOME];
  const pathValue = env.PATH ?? env.Path ?? '';
  for (const directory of pathValue.split(delimiter).filter(Boolean)) {
    if (['mvn.cmd', 'mvn'].some((file) => existsSync(join(directory, file)))) homes.push(resolve(directory, '..'));
  }
  const mavenHome = homes.find((home) => home && existsSync(join(home, 'boot')) && existsSync(join(home, 'bin/m2.conf')));
  assert.ok(mavenHome, '未找到 Maven 安装目录，请设置 MAVEN_HOME 或将 Maven bin 加入 PATH');
  const launchers = readdirSync(join(mavenHome, 'boot')).filter((file) => /^plexus-classworlds-[\w.-]+\.jar$/.test(file));
  assert.equal(launchers.length, 1, 'Maven boot 必须包含唯一的 classworlds 启动 JAR');
  return {
    name: 'Controller → async facade → serialized SSE',
    file: env.JAVA_HOME ? join(env.JAVA_HOME, 'bin', platform === 'win32' ? 'java.exe' : 'java') : platform === 'win32' ? 'java.exe' : 'java',
    args: [
      `-Dmaven.multiModuleProjectDirectory=${repository}`,
      `-Dmaven.home=${mavenHome}`,
      `-Dclassworlds.conf=${join(mavenHome, 'bin/m2.conf')}`,
      '-Dfile.encoding=UTF-8',
      '-cp',
      join(mavenHome, 'boot', launchers[0]),
      'org.codehaus.plexus.classworlds.launcher.Launcher',
      '-B',
      '-ntp',
      '-pl',
      'ruoyi-modules/ruoyi-ai',
      '-am',
      'test',
      '-DskipTests=false',
      '-Dprofiles.active=',
      '-Dai.groups=',
      '-Dai.excludedGroups=',
      '-Dtest=AiChatRoundTripFixtureTest',
      '-Dsurefire.failIfNoSpecifiedTests=false',
      '-Dsurefire.timeout=90',
      `-Dnexus.ai.roundtrip.output=${output}`
    ],
    cwd: repository,
    timeoutMs: 240_000
  };
}

/** 超时终止整棵测试进程树，防止 Maven 的 Surefire 子 JVM 遗留在后台。 */
function terminate(child) {
  if (!child.pid) return;
  if (process.platform === 'win32') {
    spawnSync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], { windowsHide: true, shell: false, stdio: 'ignore', timeout: 5000 });
  } else {
    try {
      process.kill(-child.pid, 'SIGKILL');
    } catch (error) {
      if (error.code !== 'ESRCH') throw error;
    }
  }
}

/** 各阶段使用独立 argv 和硬时限，启动失败、非零退出与超时均拒绝 Promise。 */
export function runCommand(step, { env = process.env, stdio = 'inherit' } = {}) {
  assert.ok(Number.isSafeInteger(step.timeoutMs) && step.timeoutMs > 0, '测试进程必须配置正整数超时');
  return new Promise((accept, reject) => {
    let stdout = '';
    let stderr = '';
    const child = spawn(step.file, step.args, {
      cwd: step.cwd,
      env,
      stdio: ['ignore', 'pipe', 'pipe'],
      shell: false,
      windowsHide: true,
      detached: process.platform !== 'win32'
    });
    // 只保留每个流末尾 1 MiB 的证据，长构建输出不会无限占用内存。
    child.stdout.setEncoding('utf8').on('data', (text) => {
      stdout = (stdout + text).slice(-1_048_576);
      if (stdio === 'inherit') process.stdout.write(text);
    });
    child.stderr.setEncoding('utf8').on('data', (text) => {
      stderr = (stderr + text).slice(-1_048_576);
      if (stdio === 'inherit') process.stderr.write(text);
    });
    const timer = setTimeout(() => {
      try {
        terminate(child);
      } catch (error) {
        reject(error);
        return;
      }
      reject(new Error(`${step.name} 超时（${step.timeoutMs}ms），已终止测试进程树`));
    }, step.timeoutMs);
    child.once('error', (error) => {
      clearTimeout(timer);
      reject(error);
    });
    child.once('close', (code, signal) => {
      clearTimeout(timer);
      if (code === 0) accept({ stdout, stderr });
      else reject(Object.assign(new Error(`${step.name} 失败：${signal ?? `exit ${code}`}`), { exitCode: code ?? 1 }));
    });
  });
}

/** 核对目标夹具的真实 Surefire 摘要，不能把聚合构建成功或其他类的计数当作已执行。 */
function requireBackendTests(result) {
  const output = stripVTControlCharacters(result?.stdout ?? '');
  const summaries = [
    ...output.matchAll(
      /Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)[^\r\n]*\bin\s+com\.nexus\.ai\.controller\.AiChatRoundTripFixtureTest(?=\s|$)/g
    )
  ];
  assert.equal(summaries.length, 1, '后端必须输出唯一的 AiChatRoundTripFixtureTest 用例计数');
  const [tests, failures, errors, skipped] = summaries[0].slice(1).map(Number);
  assert.ok(tests > 0 && failures === 0 && errors === 0 && skipped === 0, '后端必须实际执行非零用例，失败、错误和跳过均须为零');
}

/** Node 的最外层 TAP 统计必须全部执行通过，跳过、取消和待办不能充当验证。 */
function requireFrontendTests(result) {
  const output = stripVTControlCharacters(result?.stdout ?? '').replaceAll('\r', '');
  const count = (name) => {
    const matches = [...output.matchAll(new RegExp(`^# ${name} (\\d+)$`, 'gm'))];
    assert.equal(matches.length, 1, `前端必须输出唯一的 ${name} 用例计数`);
    return Number(matches[0][1]);
  };
  const tests = count('tests');
  assert.ok(tests > 0 && count('pass') === tests, '前端必须实际执行非零用例并全部通过');
  for (const name of ['fail', 'cancelled', 'skipped', 'todo']) assert.equal(count(name), 0, `前端 ${name} 用例计数必须为零`);
}

/** 集成入口始终实际执行后端，再把本轮完整 JSON 交给真实 Vue 处理函数测试，结束时清理临时产物。 */
export async function runRoundTrip({ env = process.env, run = runCommand, tempBase = tmpdir(), log = (text) => process.stdout.write(text) } = {}) {
  const directory = mkdtempSync(join(tempBase, 'nexus ai chat roundtrip '));
  const output = join(directory, 'controller-sse.json');
  const childEnv = { ...env, NEXUS_AI_ROUNDTRIP_ARTIFACT: output };
  delete childEnv.NODE_TEST_CONTEXT;
  try {
    const backend = await run(buildMavenCommand({ output, env: childEnv }), { env: childEnv });
    requireBackendTests(backend);
    const raw = readFileSync(output, 'utf8');
    const artifact = JSON.parse(raw);
    assert.ok(
      artifact.formatVersion === 1 &&
        artifact.synthetic === true &&
        Array.isArray(artifact.scenarios) &&
        artifact.scenarios.length > 0 &&
        Array.isArray(artifact.timeline) &&
        artifact.timeline.length > 0,
      '后端产物缺少本轮有效场景，禁止跳过后报成功'
    );
    log(`\n[AI roundtrip: complete backend scenario JSON]\n${raw}\n`);
    const frontend = await run(
      {
        name: 'Serialized SSE → real Vue page/drawer handlers',
        file: process.execPath,
        args: [
          '--test',
          '--test-concurrency=1',
          '--test-reporter=tap',
          join(uiRoot, 'tests/ai-chat-roundtrip/orchestrator.test.mjs'),
          join(uiRoot, 'tests/ai-chat-roundtrip/roundtrip.test.mjs')
        ],
        cwd: uiRoot,
        timeoutMs: 30_000
      },
      { env: childEnv }
    );
    requireFrontendTests(frontend);
  } finally {
    assert.equal(dirname(resolve(directory)), resolve(tempBase), '清理目标必须是本轮创建的临时子目录');
    rmSync(directory, { recursive: true, force: true });
  }
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  try {
    assert.equal(process.argv.length, 2, '集成入口不接受跳过后端或复用旧产物的参数');
    await runRoundTrip();
  } catch (error) {
    console.error(error);
    process.exitCode = Number.isInteger(error.exitCode) && error.exitCode > 0 && error.exitCode < 256 ? error.exitCode : 1;
  }
}
