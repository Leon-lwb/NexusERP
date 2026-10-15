import test from 'node:test';
import assert from 'node:assert/strict';
import { build } from 'esbuild';
import { mkdir } from 'node:fs/promises';
import { webcrypto } from 'node:crypto';
await mkdir('node_modules/.tmp', { recursive: true });
await build({
  entryPoints: ['src/utils/requestKey.ts'],
  outfile: 'node_modules/.tmp/report-request-key.mjs',
  bundle: true,
  format: 'esm',
  platform: 'node'
});
/** 测试生产代码的浏览器能力检测和安全随机降级。 */
const { createRequestKey } = await import('../node_modules/.tmp/report-request-key.mjs');
/** 替换当前浏览器能力，测试结束恢复原环境。 */
function withCrypto(t, value) {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'crypto');
  Object.defineProperty(globalThis, 'crypto', { configurable: true, value });
  t.after(() => Object.defineProperty(globalThis, 'crypto', original));
}
/** 原生实现保留正确的调用接收者。 */
test('安全上下文优先使用原生UUID', (t) => {
  const provider = {
    randomUUID() {
      assert.equal(this, provider);
      return 'native-id';
    }
  };
  withCrypto(t, provider);
  assert.equal(createRequestKey(), 'native-id');
});
/** HTTP仍支持getRandomValues，生成符合UUIDv4的随机键且不碰Math.random。 */
test('HTTP安全随机字节生成UUIDv4', (t) => {
  withCrypto(t, {
    getRandomValues: (bytes) => {
      bytes.fill(255);
      return bytes;
    }
  });
  assert.equal(createRequestKey(), 'ffffffff-ffff-4fff-bfff-ffffffffffff');
});
/** 随机能力缺失时明确拒绝，不能用时间戳或普通伪随机伪造安全键。 */
test('安全随机能力缺失时明确失败', (t) => {
  withCrypto(t, undefined);
  assert.throws(() => createRequestKey(), /安全随机/);
});
/** 批量真实CSPRNG结果保持格式且不重复。 */
test('随机降级产生不同的合法请求键', (t) => {
  withCrypto(t, { getRandomValues: webcrypto.getRandomValues.bind(webcrypto) });
  const values = Array.from({ length: 128 }, () => createRequestKey());
  assert.equal(new Set(values).size, 128);
  for (const value of values) assert.match(value, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});
