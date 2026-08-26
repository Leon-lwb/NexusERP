/**
 * src/utils/safeExpression.ts 单元断言（零依赖，node 直接运行）。
 * 运行方式：npm run test:unit
 * 原理：先用 esbuild 把 TS 编译为 ESM，再由本脚本 import 断言。
 */
import { strict as assert } from 'node:assert';
import { safeArithmeticEval } from '../node_modules/.tmp/safe-expression.mjs';

let pass = 0;
const failures = [];

/** @param {string} name @param {() => void} fn */
function test(name, fn) {
  try {
    fn();
    pass++;
    console.log(`  ✓ ${name}`);
  } catch (e) {
    failures.push({ name, error: e });
    console.error(`  ✗ ${name}\n    ${e.message}`);
  }
}

const eq = (actual, expected, msg) => {
  if (typeof expected === 'number') {
    assert.ok(
      Math.abs(actual - expected) < 1e-9,
      `${msg || ''} 实际=${actual} 期望=${expected}`
    );
  } else {
    assert.strictEqual(actual, expected, msg);
  }
};

console.log('\n[1] 正常四则运算');
test('加法与优先级 1+2*3 = 7', () => eq(safeArithmeticEval('1+2*3'), 7));
test('括号分组 (1+2)*3 = 9', () => eq(safeArithmeticEval('(1+2)*3'), 9));
test('除法 10/4 = 2.5', () => eq(safeArithmeticEval('10/4'), 2.5));
test('取模 100%7 = 2', () => eq(safeArithmeticEval('100%7'), 2));
test('小数 3.5*2 = 7', () => eq(safeArithmeticEval('3.5*2'), 7));
test('空白容忍 " 2 + 3 " = 5', () => eq(safeArithmeticEval(' 2 + 3 '), 5));
test('嵌套括号 ((2+3)*(4-1))/5 = 3', () => eq(safeArithmeticEval('((2+3)*(4-1))/5'), 3));

console.log('[2] 一元正负号');
test('-5+3 = -2', () => eq(safeArithmeticEval('-5+3'), -2));
test('2*-3 = -6', () => eq(safeArithmeticEval('2*-3'), -6));
test('--5 = 5', () => eq(safeArithmeticEval('--5'), 5));

console.log('[3] 注入载荷全部拒绝并回退 fallback=0');
for (const payload of [
  'alert(1)',
  'fetch("http://evil")',
  'process.exit()',
  'constructor.constructor("return 1")()',
  '"" || 1',
  '1;DROP TABLE x',
  'this.constructor',
  'globalThis',
  'eval("1")',
  'Function("return 1")()'
]) {
  test(`拒绝: ${JSON.stringify(payload)}`, () => eq(safeArithmeticEval(payload), 0, `注入载荷应回退 0: ${payload}`));
}

console.log('[4] 非法语法/边界回退');
test('截断表达式 "1 +" 回退', () => eq(safeArithmeticEval('1 +'), 0));
test('空串回退', () => eq(safeArithmeticEval(''), 0));
test('null 回退', () => eq(safeArithmeticEval(null), 0));
test('非法数字 1.2.3 回退', () => eq(safeArithmeticEval('1.2.3'), 0));
test('字母混入回退', () => eq(safeArithmeticEval('2a+1'), 0));
test('除零 Infinity 视为非法回退 0', () => eq(safeArithmeticEval('1/0'), 0));
test('自定义 fallback 生效', () => eq(safeArithmeticEval('alert(1)', -1), -1));
test('中文数字字符回退（不静默求值）', () => eq(safeArithmeticEval('２＋２'), 0));

console.log('');
if (failures.length > 0) {
  console.error(`✗ ${failures.length}/${pass + failures.length} 个用例失败`);
  process.exit(1);
} else {
  console.log(`✓ 全部 ${pass} 个用例通过`);
}
