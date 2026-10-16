import assert from 'node:assert/strict';
import { NexusError } from '../../dist/nexus-client.js';
/** 创建与真实后端相同的修订号、事件幂等和设备详情边界。 */
export function backend(action = 'CHECK_CONNECTION') {
  const lease = {
    taskId: '1',
    action,
    attempt: 1,
    revision: 1,
    leaseToken: 'synthetic-lease',
    leaseExpiresAt: new Date(Date.now() + 90_000).toISOString(),
    deadlineAt: new Date(Date.now() + 1_200_000).toISOString(),
    parameters: {}
  };
  const state = { revision: 1, cancelRequested: false, state: 'RUNNING', stage: 'CHECKING', events: [], calls: [], renewals: 0, errorCode: null };
  const receipts = new Map();
  const api = {
    request: async (path, body, signal) => {
      signal?.throwIfAborted();
      state.calls.push({ path, body: body === undefined ? undefined : body instanceof FormData ? { multipart: true } : structuredClone(body) });
      if (path === '/connector/claim') return null;
      if (path === '/connector/tasks/1')
        return { ...state, taskId: '1', attempt: 1, action, deviceId: 'device', events: structuredClone(state.events) };
      if (path.endsWith('/manifest')) return { artifacts: [] };
      if (path.endsWith('/artifacts')) return { artifactId: '2' };
      if (path.endsWith('/events') && receipts.has(body.sequence)) {
        const prior = receipts.get(body.sequence);
        if (JSON.stringify(body) !== prior.body) throw new NexusError(409, 'IDEMPOTENCY_CONFLICT');
        return prior.receipt;
      }
      if (body.expectedRevision !== state.revision) throw new NexusError(409, 'STALE_REVISION');
      if (path.endsWith('/renew')) {
        state.renewals++;
        return {
          ...lease,
          revision: ++state.revision,
          cancelRequested: state.cancelRequested,
          leaseExpiresAt: new Date(Date.now() + 90_000).toISOString()
        };
      }
      if (path.endsWith('/upload-ticket')) return { uploadTicket: 'synthetic-ticket' };
      if (path.endsWith('/events')) {
        assert.equal(body.sequence, state.events.length + 1);
        if (body.cancelAcknowledged) {
          assert.equal(state.cancelRequested, true);
          state.state = 'CANCELED';
        }
        if (body.errorCode) state.errorCode = body.errorCode;
        state.stage = body.stage;
        const receipt = { revision: ++state.revision, cancelRequested: state.cancelRequested, state: state.state };
        state.events.push({ sequence: body.sequence, stage: body.stage, message: body.message, revision: receipt.revision });
        receipts.set(body.sequence, { body: JSON.stringify(body), receipt });
        return receipt;
      }
      if (path.endsWith('/complete')) {
        assert.equal(state.cancelRequested, false);
        state.state = state.errorCode ? 'FAILED' : 'SUCCEEDED';
        state.revision++;
        return { state: state.state, errorCode: state.errorCode };
      }
      throw Error(path);
    }
  };
  return {
    lease,
    state,
    api,
    cancel() {
      state.cancelRequested = true;
      state.revision++;
    }
  };
}
