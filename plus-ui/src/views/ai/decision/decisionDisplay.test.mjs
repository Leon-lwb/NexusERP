import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { transform } from 'esbuild';

const source = await readFile(new URL('./decisionDisplay.ts', import.meta.url), 'utf8');
const { code } = await transform(source, { loader: 'ts', format: 'esm' });
const display = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);

test('新缺口比例展示函数必须存在', () => {
  assert.equal(typeof display.decisionShortageRankingDisplay, 'function');
});

test('旧运行原值不能变成新版可排名比例', () => {
  assert.equal(display.decisionShortageRankingDisplay({ kpis: { totalShortageGap: '38.2762' }, rank: 1 }), '历史口径：未记录无量纲比例');
});

test('不可排名原因必须展示而非借用旧原值', () => {
  assert.equal(
    display.decisionShortageRankingDisplay({ kpis: { totalShortageGap: '0' }, rank: 0, infeasibleReasons: ['SHORTAGE_GAP_RATIO_UNRANKABLE'] }),
    '不可排名：物料、单位或来源未核验/不可比'
  );
});

test('合法比例显示百分比且不依赖原始缺口大小', () => {
  assert.equal(
    display.decisionShortageRankingDisplay({ kpis: { shortageGapRatio: '0.25000000', shortageBasis: { runId: '9007199254740993' } }, rank: 1 }),
    '25.00%（逐物料等权）'
  );
});

test('裸比例、非有限和越界值不能伪装成可比较结果', () => {
  for (const ratio of ['NaN', 'Infinity', '-0.1', '1.1', '']) {
    assert.equal(
      display.decisionShortageRankingDisplay({ kpis: { shortageGapRatio: ratio, shortageBasis: { runId: '10' } }, rank: 1 }),
      '不可排名：物料、单位或来源未核验/不可比'
    );
  }
  assert.equal(display.decisionShortageRankingDisplay({ kpis: { shortageGapRatio: '0.25' }, rank: 1 }), '不可排名：物料、单位或来源未核验/不可比');
});
