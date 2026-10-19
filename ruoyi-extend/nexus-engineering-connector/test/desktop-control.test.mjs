import test from 'node:test';
import assert from 'node:assert/strict';
import { PassThrough } from 'node:stream';

/** 桌面界面只能发送固定停止指令，不能注入业务动作或命令。 */
test('桌面控制仅响应完整stop行，并只停止一次', async () => {
  const { attachDesktopControl } = await import('../dist/desktop-control.js');
  const input = new PassThrough();
  let stopped = 0;
  const detach = attachDesktopControl(input, () => stopped++);
  input.write('start\n{"command":"stop"}\nsto');
  assert.equal(stopped, 0);
  input.write('p\nstop\n');
  assert.equal(stopped, 1);
  detach();
  input.destroy();
});

/** GUI 退出或崩溃后不能留下继续领取任务的孤儿进程。 */
test('桌面父进程关闭输入管道时停止领取', async () => {
  const { attachDesktopControl } = await import('../dist/desktop-control.js');
  const input = new PassThrough();
  let stopped = false;
  const detach = attachDesktopControl(input, () => { stopped = true; });
  input.end();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(stopped, true);
  detach();
});

/** 正常释放监听不应被误判为新的停止请求。 */
test('桌面控制释放后忽略后续输入和结束事件', async () => {
  const { attachDesktopControl } = await import('../dist/desktop-control.js');
  const input = new PassThrough();
  let stopped = false;
  const detach = attachDesktopControl(input, () => { stopped = true; });
  detach();
  input.end('stop\n');
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(stopped, false);
});

/** 输入错误必须进入正常停止流程，不能成为未处理的readline异常。 */
test('桌面输入管道报错时只请求停止且不抛出异常', async () => {
  const { attachDesktopControl } = await import('../dist/desktop-control.js');
  const input = new PassThrough();
  let stopped = 0;
  const detach = attachDesktopControl(input, () => stopped++);
  assert.doesNotThrow(() => input.emit('error', new Error('synthetic pipe error')));
  assert.equal(stopped, 1);
  detach();
  input.destroy();
});

/** GUI可能在Node读取配对码之前就退出，已结束的父管道也必须立即取消。 */
test('接管已经结束的桌面输入管道时立即停止', async()=>{
  const {attachDesktopControl}=await import('../dist/desktop-control.js');
  const input=new PassThrough(); input.end(); input.resume();
  await new Promise(resolve=>setImmediate(resolve));
  let stopped=0;
  const detach=attachDesktopControl(input,()=>stopped++);
  assert.equal(stopped,1);
  detach();
});
