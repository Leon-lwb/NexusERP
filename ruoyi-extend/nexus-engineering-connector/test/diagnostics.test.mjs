import test from 'node:test';
import assert from 'node:assert/strict';
import { NexusError } from '../dist/nexus-client.js';

test('配对无效与接口未启用显示明确原因', async () => {
  const {connectorErrorMessage} = await import('../dist/diagnostics.js');
  const {EngineeringEndpointError} = await import('../dist/server-endpoint.js');
  const invalid = connectorErrorMessage(new NexusError(401, 'PAIRING_INVALID'));
  assert.match(invalid, /PAIRING_INVALID/); assert.match(invalid, /配对码/);
  assert.match(connectorErrorMessage(new EngineeringEndpointError('ENGINEERING_DISABLED')), /后端.*工程.*启用/);
  assert.match(connectorErrorMessage(new EngineeringEndpointError('API_NOT_FOUND')), /prod-api/);
  const unavailable=connectorErrorMessage(new EngineeringEndpointError('API_UNAVAILABLE',502));
  assert.match(unavailable,/502/); assert.match(unavailable,/后端/);
});

test('诊断只输出受控信息，不回显异常正文和任意机器码', async () => {
  const {connectorErrorMessage} = await import('../dist/diagnostics.js');
  for (const error of [new Error('synthetic-private-credential'), new NexusError(500, 'synthetic-private-credential')])
    assert.equal(connectorErrorMessage(error).includes('synthetic-private-credential'), false);
});
