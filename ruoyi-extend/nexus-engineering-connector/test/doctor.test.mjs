import test from 'node:test';
import assert from 'node:assert/strict';
import { runCli } from '../dist/doctor.js';
/** 某些Electron版本让子进程继承输出管道；CLI退出后不应无限等待编辑器结束。 */
test('CLI退出后及时取到回执而不等待常驻窗口进程', async () => {
  const started = Date.now();
  const code = `const {spawn}=require('node:child_process');const p=spawn(process.execPath,['-e','setTimeout(()=>{},4000)'],{stdio:['ignore',1,2],windowsHide:true,detached:true});p.unref();process.stdout.write(JSON.stringify({ok:true}));`;
  const result = await runCli(process.execPath, ['-e', code], 6000);
  assert.equal(result.code, 0);
  assert.equal(JSON.parse(result.stdout).ok, true);
  assert.ok(Date.now() - started < 3000);
});

/** 仅终止本次CLI调用，不杀掉用户已打开的编辑器。 */
test('取消信号终止当前CLI并拒绝超时等待', async () => {
  const abort = new AbortController();
  const timer = setTimeout(() => abort.abort(), 50);
  const started = Date.now();
  try {
    await assert.rejects(runCli(process.execPath, ['-e', 'setTimeout(()=>{},2000)'], 4000, abort.signal), /abort/i);
    assert.ok(Date.now() - started < 1500);
  } finally {
    clearTimeout(timer);
  }
});
import { diagnose } from '../dist/doctor.js';
/** doctor只报告实际版本字段，不能把未知编辑器版本填成CLI版本。 */
test('诊断区分CLI桥接版本、未知编辑器版本和原生MCP不支持', async () => {
  const diagnostic = await diagnose({ protocolVersion: 'nexus-engineering/1', executable: 'fixture' }, async () => ({
    code: 0,
    stdout: JSON.stringify({ ok: true, value: { connected: true, bridgeVersion: '4.1.60.198f38ab', versionMatch: true } })
  }));
  assert.equal(diagnostic.cliVersion, '4.1.60.198f38ab');
  assert.equal(diagnostic.editorVersion, null);
  assert.equal(diagnostic.transportMode, 'CLI_COMPAT');
  assert.equal(diagnostic.diagnosticCode, 'NATIVE_MCP_UNSUPPORTED');
});

/** 停止不能等EDA心跳诊断的20秒超时，取消信号须传至实际CLI。 */
test('心跳诊断将停止信号传至CLI调用', async () => {
  const abort = new AbortController();
  abort.abort();
  await assert.rejects(
    diagnose({ executable: 'fixture' }, async (_executable, _args, _timeout, signal) => {
      assert.equal(signal, abort.signal);
      signal.throwIfAborted();
    }, abort.signal),
    (error) => error.name === 'AbortError'
  );
});
