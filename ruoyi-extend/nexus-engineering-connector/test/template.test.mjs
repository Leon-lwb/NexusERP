import test from 'node:test';
import assert from 'node:assert/strict';
import { validateParameters } from '../dist/easyeda/capabilities.js';
/** 参数约束是连接器的执行边界，不信任模型或HTTP正文。 */
test('模板仅接受声明的双层板尺寸，脚本参数被拒绝', () => {
  assert.deepEqual(validateParameters({ boardWidthMm: '50.0', boardHeightMm: '35.0', layers: 2 }), {
    boardWidthMm: 50,
    boardHeightMm: 35,
    layers: 2
  });
  assert.throws(() => validateParameters({ boardWidthMm: '0', boardHeightMm: '35', layers: 2 }), /范围/);
  assert.throws(() => validateParameters({ boardWidthMm: '50', boardHeightMm: '35', layers: 4 }), /双层/);
  assert.throws(() => validateParameters({ boardWidthMm: '50', boardHeightMm: '35', layers: 2, code: 'evil' }), /未知/);
});
