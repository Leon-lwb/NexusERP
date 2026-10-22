import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { transform } from 'esbuild';

const source = readFileSync(new URL('../src/views/ai/model/model-config.ts', import.meta.url), 'utf8');
const { code } = await transform(source, { loader: 'ts', format: 'esm' });
const model = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);

test('embedding and rerank templates keep their distinct output types and controls', () => {
  const embedding = model.applyModelTypeTemplate(' embedding ');
  assert.deepEqual(embedding.outputCapabilities, ['VECTOR']);
  assert.equal(embedding.requiresEmbeddingDimension, true);
  assert.equal(embedding.lockCapabilities, true);
  assert.equal(embedding.hideTemperature, true);
  assert.equal(embedding.hideMaxTokens, true);
  const rerank = model.applyModelTypeTemplate('RERANK');
  assert.deepEqual(rerank.outputCapabilities, ['RANKING']);
  assert.equal(rerank.requiresEmbeddingDimension, false);
});

test('model templates reject unknown types and return isolated arrays for each form', () => {
  for (const value of ['', '  ', 'UNKNOWN', null, undefined]) assert.throws(() => model.applyModelTypeTemplate(value));
  const first = model.applyModelTypeTemplate('VISION');
  first.inputCapabilities.length = 0;
  assert.deepEqual(model.applyModelTypeTemplate('VISION').inputCapabilities, ['TEXT', 'IMAGE']);
});

test('capability normalization preserves supported values in canonical order', () => {
  assert.deepEqual(model.normalizeCapabilityValues(['ranking', ' IMAGE ', 'TEXT', 'text', 'unknown', null]), ['TEXT', 'IMAGE', 'RANKING']);
  assert.deepEqual(model.normalizeCapabilityValues(null), []);
  assert.deepEqual(model.splitCapabilities('text, image, ,vector'), ['TEXT', 'IMAGE', 'VECTOR']);
  assert.equal(model.joinCapabilities(['TEXT', ' ', 'IMAGE']), 'TEXT,IMAGE');
});
