import test from "node:test";
import assert from "node:assert/strict";
import { TaskRunner } from "../dist/task-runner.js";
/** 任务执行和服务端状态分离，工具结束不代表板子已生成。 */
test("串行回报修订号，CHECK不要求文件且只执行一次", async () => {
    let executions = 0;
    let revision = 1;
    const events = [];
    const api = {
        request: async (path, body) => {
            if (path.endsWith("/events")) {
                assert.equal(body.expectedRevision, revision);
                events.push(body);
                return { revision: ++revision, cancelRequested: false };
            }
            if (path.endsWith("/complete")) {
                assert.equal(body.expectedRevision, revision);
                return { state: "SUCCEEDED" };
            }
            throw Error(path);
        },
    };
    const adapter = {
        execute: async (task, signal, stage) => {
            executions++;
            await stage("CHECKING");
            return {
                artifacts: [],
                errors: [],
                diagnostic: { connected: true },
            };
        },
    };
    const runner = new TaskRunner(api, adapter, "/fixture", {
        renewIntervalMs: 0,
    });
    const result = await runner.executeLease({
        taskId: "1",
        action: "CHECK_CONNECTION",
        attempt: 1,
        revision: 1,
        leaseToken: "lease",
        leaseExpiresAt: new Date(Date.now() + 90000).toISOString(),
        deadlineAt: new Date(Date.now() + 600000).toISOString(),
        parameters: {},
    });
    assert.equal(executions, 1);
    assert.equal(result.state, "SUCCEEDED");
    assert.ok(events.length > 0);
    assert.deepEqual(
        events.map((x) => x.sequence),
        events.map((_, i) => i + 1),
    );
});

/** A finished check must retain its actual result, without transporting raw CLI data. */
test("CHECK completion carries bounded diagnostics and recovery excludes private CLI fields", async () => {
    const { backend } = await import("./helpers/task-backend.mjs");
    const b = backend();
    const saved = [];
    const runner = new TaskRunner(
        b.api,
        {
            execute: async () => ({
                artifacts: [],
                errors: [],
                diagnostic: {
                    connected: true,
                    bridgeVersion: "4.1.60.198f38ab",
                    editorVersion: "4.1.60",
                    transportMode: "CLI_COMPAT",
                    deviceToken: "SYNTHETIC_DEVICE_CREDENTIAL",
                    privatePath: "SYNTHETIC_PRIVATE_PATH",
                    details: "x".repeat(128000),
                },
            }),
        },
        "/fixture",
        {
            renewIntervalMs: 0,
            resumeStore: {
                load: async () => null,
                save: async (state) => saved.push(structuredClone(state)),
                clear: async () => {},
            },
        },
    );
    await runner.executeLease(b.lease);
    const completion = b.state.calls.find((call) =>
        call.path.endsWith("/complete"),
    ).body;
    assert.deepEqual(completion.diagnostic, {
        connected: true,
        cliVersion: "4.1.60.198f38ab",
        editorVersion: "4.1.60",
        transportMode: "CLI_COMPAT",
    });
    const executed = saved.find((state) => state.phase === "EXECUTED");
    assert.ok(executed);
    const metadata = JSON.stringify(executed);
    assert.ok(metadata.length < 16000);
    assert.equal(metadata.includes("SYNTHETIC_DEVICE_CREDENTIAL"), false);
    assert.equal(metadata.includes("SYNTHETIC_PRIVATE_PATH"), false);
});

test("offline CHECK completion preserves the disconnected diagnostic", async () => {
    const { backend } = await import("./helpers/task-backend.mjs");
    const b = backend();
    await new TaskRunner(
        b.api,
        {
            execute: async () => ({
                artifacts: [],
                diagnostic: { connected: false, transportMode: "CLI_COMPAT" },
            }),
        },
        "/fixture",
        { renewIntervalMs: 0 },
    ).executeLease(b.lease);
    const completion = b.state.calls.find((call) =>
        call.path.endsWith("/complete"),
    ).body;
    assert.equal(completion.diagnostic?.connected, false);
    assert.equal(b.state.errorCode, "EDITOR_UNAVAILABLE");
});

test("non-check completion does not forward local diagnostic objects", async () => {
    const { backend } = await import("./helpers/task-backend.mjs");
    const b = backend("OPEN_PROJECT");
    await new TaskRunner(
        b.api,
        {
            execute: async () => ({
                artifacts: [],
                diagnostic: {
                    opened: true,
                    privatePath: "SYNTHETIC_PRIVATE_PATH",
                },
            }),
        },
        "/fixture",
        { renewIntervalMs: 0 },
    ).executeLease(b.lease);
    assert.equal(
        "diagnostic" in
            b.state.calls.find((call) => call.path.endsWith("/complete")).body,
        false,
    );
});
