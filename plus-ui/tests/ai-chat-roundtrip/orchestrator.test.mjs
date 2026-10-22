import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { test } from 'node:test';
import { buildMavenCommand, runCommand, runRoundTrip } from '../../scripts/test-ai-chat-roundtrip.mjs';

/** 构造仅供参数与编排断言的临时 Maven 目录，不执行 Maven 或访问外部服务。 */
function sandbox(t) {
  const root = mkdtempSync(join(tmpdir(), 'nexus roundtrip runner & spaces '));
  t.after(() => {
    assert.equal(dirname(resolve(root)), resolve(tmpdir()), '清理目标必须是本轮创建的临时子目录');
    rmSync(root, { recursive: true, force: true });
  });
  const mavenHome = join(root, 'maven home & spaces');
  mkdirSync(join(mavenHome, 'boot'), { recursive: true });
  mkdirSync(join(mavenHome, 'bin'));
  writeFileSync(join(mavenHome, 'boot/plexus-classworlds-2.9.0.jar'), 'synthetic launcher placeholder');
  writeFileSync(join(mavenHome, 'bin/m2.conf'), 'synthetic classworlds configuration');
  return { root, env: { ...process.env, MAVEN_HOME: mavenHome, JAVA_HOME: join(root, 'java home & spaces') } };
}

/** 这里只验证编排控制流；真实场景及 Vue 断言由 roundtrip.test.mjs 校验实际 Java 产物。 */
const controllerArtifact = {
  formatVersion: 1,
  synthetic: true,
  scenarios: [{ name: 'orchestration-port-only' }],
  timeline: ['orchestration-port-only']
};

function artifactOutput(step) {
  const output = step.args.find((argument) => argument.startsWith('-Dnexus.ai.roundtrip.output='));
  assert.ok(output, '必须向实际 JVM 传递本轮输出路径');
  return output.slice('-Dnexus.ai.roundtrip.output='.length);
}

test('Windows Maven 使用 Java 入口和独立 argv，保留含空格与 & 的路径且明确启用单个后端测试', (t) => {
  const { root, env } = sandbox(t);
  const repoRoot = join(root, 'repo with & spaces');
  const output = join(root, 'artifact & spaces/roundtrip.json');
  const step = buildMavenCommand({ repoRoot, output, env, platform: 'win32' });
  assert.equal(step.file, join(env.JAVA_HOME, 'bin/java.exe'));
  assert.equal(step.cwd, repoRoot);
  assert.equal(step.timeoutMs, 240_000);
  assert.ok(step.args.includes('org.codehaus.plexus.classworlds.launcher.Launcher'));
  assert.ok(step.args.includes(`-Dmaven.multiModuleProjectDirectory=${repoRoot}`));
  assert.ok(step.args.includes(`-Dmaven.home=${env.MAVEN_HOME}`));
  assert.ok(step.args.includes('-DskipTests=false'));
  assert.ok(step.args.includes('-Dprofiles.active='));
  assert.ok(step.args.includes('-Dtest=AiChatRoundTripFixtureTest'));
  assert.ok(step.args.includes('-Dsurefire.failIfNoSpecifiedTests=false'));
  assert.ok(step.args.includes('-Dsurefire.timeout=90'));
  assert.equal(artifactOutput(step), output);
  assert.ok(!step.args.includes('clean'));
  assert.ok(!step.args.some((argument) => argument.endsWith('.cmd')));
});

test('编排先执行后端，再输出完整场景 JSON 并运行 Node 断言，成功后清理临时目录', async (t) => {
  const { root, env } = sandbox(t);
  const steps = [];
  let output;
  let printed = '';
  await runRoundTrip({
    env,
    tempBase: root,
    log: (text) => {
      printed += text;
    },
    run: async (step, options) => {
      steps.push(step);
      assert.equal(options.env.NODE_TEST_CONTEXT, undefined);
      if (steps.length === 1) {
        output = artifactOutput(step);
        assert.equal(existsSync(output), false, '每次使用全新产物路径');
        writeFileSync(output, JSON.stringify(controllerArtifact));
      } else {
        assert.equal(options.env.NEXUS_AI_ROUNDTRIP_ARTIFACT, output);
        assert.deepEqual(JSON.parse(readFileSync(output, 'utf8')), controllerArtifact);
        assert.ok(printed.includes(JSON.stringify(controllerArtifact)), 'Node 开始前输出全部后端场景');
        assert.equal(step.file, process.execPath);
        assert.ok(step.args.includes('--test'));
        assert.ok(step.args.includes('--test-reporter=tap'));
        assert.ok(step.args.some((argument) => argument.endsWith('roundtrip.test.mjs')));
        assert.equal(step.timeoutMs, 30_000);
      }
      return { stdout: steps.length === 1 ? backendSummary : frontendSummary };
    }
  });
  assert.equal(steps.length, 2);
  assert.equal(existsSync(dirname(output)), false);
});

test('后端失败保留退出码、阻止 Node 阶段并清理临时路径', async (t) => {
  const { root, env } = sandbox(t);
  let calls = 0;
  let output;
  const failure = Object.assign(new Error('synthetic-backend-failure'), { exitCode: 23 });
  await assert.rejects(
    runRoundTrip({
      env,
      tempBase: root,
      log() {},
      run: async (step) => {
        calls++;
        output = artifactOutput(step);
        throw failure;
      }
    }),
    (error) => error === failure && error.exitCode === 23
  );
  assert.equal(calls, 1);
  assert.equal(existsSync(dirname(output)), false);
});

for (const missing of [true, false]) {
  test(`后端返回零但产物${missing ? '缺失' : '无有效场景'}时必须失败，不能跳过后报成功`, async (t) => {
    const { root, env } = sandbox(t);
    let calls = 0;
    await assert.rejects(
      runRoundTrip({
        env,
        tempBase: root,
        log() {},
        run: async (step) => {
          calls++;
          if (!missing) writeFileSync(artifactOutput(step), JSON.stringify({ formatVersion: 1, synthetic: true, scenarios: [], timeline: [] }));
          return { stdout: backendSummary };
        }
      }),
      /产物|场景|ENOENT/
    );
    assert.equal(calls, 1);
    assert.ok(!readdirSync(root).some((entry) => entry.startsWith('nexus ai chat roundtrip ')));
  });
}

test('Node 失败原样传播且始终删除本轮 Java 产物', async (t) => {
  const { root, env } = sandbox(t);
  let output;
  const failure = Object.assign(new Error('synthetic-node-assertion-failure'), { exitCode: 19 });
  await assert.rejects(
    runRoundTrip({
      env,
      tempBase: root,
      log() {},
      run: async (step) => {
        if (!output) {
          output = artifactOutput(step);
          writeFileSync(output, JSON.stringify(controllerArtifact));
        } else throw failure;
        return { stdout: backendSummary };
      }
    }),
    (error) => error === failure && error.exitCode === 19
  );
  assert.equal(existsSync(dirname(output)), false);
});

test('真实子进程失败退出码必须原样返回', async () => {
  await assert.rejects(
    runCommand(
      { name: 'synthetic exit', file: process.execPath, args: ['-e', 'process.exit(17)'], cwd: resolve('.'), timeoutMs: 5000 },
      { stdio: 'ignore' }
    ),
    (error) => error.exitCode === 17
  );
});

test('真实子进程超时后终止父子进程且保留含空格与 & 的独立参数', async (t) => {
  const { root } = sandbox(t);
  const marker = join(root, 'process pids & spaces.json');
  const script = `
    const { spawn } = require('node:child_process');
    const { writeFileSync } = require('node:fs');
    const child = spawn(process.execPath, ['-e', 'setInterval(() => {}, 1000)'], { windowsHide: true, stdio: 'ignore', shell: false });
    writeFileSync(process.argv[1], JSON.stringify([process.pid, child.pid]));
    setInterval(() => {}, 1000);
  `;
  await assert.rejects(
    runCommand(
      { name: 'synthetic timeout', file: process.execPath, args: ['-e', script, marker], cwd: resolve('.'), timeoutMs: 1000 },
      { stdio: 'ignore' }
    ),
    /超时/
  );
  const pids = JSON.parse(readFileSync(marker, 'utf8'));
  assert.equal(pids.length, 2);
  for (const pid of pids) {
    assert.ok(Number.isInteger(pid) && pid > 0);
    // POSIX 的已退出孤儿可能短暂等待回收；Windows taskkill 同步返回后也核对实际存活状态。
    for (let attempt = 0; attempt < 40; attempt++) {
      try {
        process.kill(pid, 0);
      } catch (error) {
        assert.equal(error.code, 'ESRCH');
        break;
      }
      if (attempt === 39) assert.fail(`超时测试仍遗留进程 ${pid}`);
      await new Promise((accept) => setTimeout(accept, 25));
    }
  }
});

test('子进程启动失败必须拒绝并保留原始 ENOENT 证据', async (t) => {
  const { root } = sandbox(t);
  await assert.rejects(
    runCommand(
      { name: 'synthetic missing executable', file: join(root, 'missing executable'), args: [], cwd: root, timeoutMs: 5000 },
      { stdio: 'ignore' }
    ),
    (error) => error.code === 'ENOENT'
  );
});

const backendSummary =
  '[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1 s -- in com.nexus.ai.controller.AiChatRoundTripFixtureTest\n';
const frontendSummary = '# tests 14\n# pass 14\n# fail 0\n# cancelled 0\n# skipped 0\n# todo 0\n';

for (const [name, stdout] of [
  ['没有用例计数', '[INFO] BUILD SUCCESS\n'],
  ['零用例', backendSummary.replace('Tests run: 1', 'Tests run: 0')],
  ['全部跳过', backendSummary.replace('Skipped: 0', 'Skipped: 1')],
  ['出现失败', backendSummary.replace('Failures: 0', 'Failures: 1')],
  ['只执行其他类', backendSummary.replace('AiChatRoundTripFixtureTest', 'OtherTest')]
]) {
  test(`后端退出零但${name}时拒绝集成成功并清理本轮产物`, async (t) => {
    const { root, env } = sandbox(t);
    let calls = 0;
    await assert.rejects(
      runRoundTrip({
        env,
        tempBase: root,
        log() {},
        run: async (step) => {
          calls++;
          if (calls === 1) writeFileSync(artifactOutput(step), JSON.stringify(controllerArtifact));
          return { stdout: calls === 1 ? stdout : frontendSummary };
        }
      }),
      /后端|用例|跳过|计数/
    );
    assert.equal(calls, 1, '无后端执行证据时不能启动前端阶段');
    assert.ok(!readdirSync(root).some((entry) => entry.startsWith('nexus ai chat roundtrip ')));
  });
}

for (const [name, stdout] of [
  ['没有用例计数', 'synthetic success'],
  ['零用例', frontendSummary.replace('# tests 14', '# tests 0').replace('# pass 14', '# pass 0')],
  ['含跳过', frontendSummary.replace('# pass 14', '# pass 13').replace('# skipped 0', '# skipped 1')],
  ['被取消', frontendSummary.replace('# pass 14', '# pass 13').replace('# cancelled 0', '# cancelled 1')],
  ['待办用例', frontendSummary.replace('# pass 14', '# pass 13').replace('# todo 0', '# todo 1')]
]) {
  test(`前端退出零但${name}时拒绝集成成功并清理本轮产物`, async (t) => {
    const { root, env } = sandbox(t);
    let calls = 0;
    await assert.rejects(
      runRoundTrip({
        env,
        tempBase: root,
        log() {},
        run: async (step) => {
          calls++;
          if (calls === 1) writeFileSync(artifactOutput(step), JSON.stringify(controllerArtifact));
          return { stdout: calls === 1 ? backendSummary : stdout };
        }
      }),
      /前端|用例|跳过|计数/
    );
    assert.equal(calls, 2);
    assert.ok(!readdirSync(root).some((entry) => entry.startsWith('nexus ai chat roundtrip ')));
  });
}

test('真实子进程保留 stdout 与 stderr 供调用方核对非零测试计数', async () => {
  const result = await runCommand(
    {
      name: 'synthetic evidence',
      file: process.execPath,
      args: ['-e', 'console.log("synthetic stdout"); console.error("synthetic stderr")'],
      cwd: resolve('.'),
      timeoutMs: 5000
    },
    { stdio: 'ignore' }
  );
  assert.equal(result?.stdout.trim(), 'synthetic stdout');
  assert.equal(result?.stderr.trim(), 'synthetic stderr');
});
