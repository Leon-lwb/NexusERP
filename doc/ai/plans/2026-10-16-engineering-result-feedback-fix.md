# Engineering result feedback implementation plan

> **For agentic workers:** Execute this plan in the existing isolated worktree with `executing-plans`, one task at a time. The user approved the result-persistence, result-display and completion-notification repair on 2026-10-16. Do not commit or push automatically.

**Goal:** Make connection-check tasks return a persistent, bounded diagnostic result and an explicit completion indication in the chat card and task detail.

**Architecture:** Keep the existing owner/tenant/device/lease/CAS checks. Add an optional typed diagnostic to the completion request, save its allowlisted fields in the existing task journal, and return it in the safe task view. Publish the existing engineering SSE event after the completion transaction commits. The connector sends bounded diagnostics; Vue displays action-specific results and notifies on a live transition to a terminal state. Old tasks remain readable without inventing historical version details. No schema change is needed.

**Tech Stack:** Java 21 / Spring / H2 / JUnit / Mockito; Node.js 24 / TypeScript / node:test; Vue 3 / Element Plus / existing engineering UI test harness.

## Confirmed scope and design

- Incident: task `2111024604854018050` is a successful `CHECK_CONNECTION`, completed at 2026-10-16 09:21:07 UTC, with no PCB artifacts.
- The connector currently discards `result.diagnostic` after sending a lease-only completion request. The safe task view has no diagnostic field; cards treat checks as PCB previews.
- Chosen approach: persist a typed diagnostic in `step_journal`, expose `connectionDiagnostic`, reuse `AI_ENGINEERING_TASK` after commit, and render explicit check results.
- A toast alone would lose results on refresh. A new result table/column would add migration work for a small bounded value. Existing journal metadata preserves history and the current schema.
- Diagnostic fields: `connected` (required Boolean), `cliVersion`, `editorVersion`, `transportMode`. Versions are bounded version strings; transport is the actually supported `CLI_COMPAT`. Raw CLI responses, paths, tokens, arbitrary messages and full DRC reports are excluded.
- Completion requests cannot choose task state. A disconnected diagnostic produces a failed connection check. Diagnostics on PCB actions are rejected. Missing diagnostics preserve legacy-client behavior.
- A journal entry can carry optional `connectionDiagnostic`; existing entries deserialize with null. On completion, attach it to the final journal entry, or create a real server completion entry if no stage was sent.
- Historical successful checks display completion plus an explicit note that detailed diagnostics were not saved. They do not inherit current device versions.
- Only explicit generation actions create designs. A connection check does not auto-create a PCB.

## Task 1: Reproduce the gaps

Files:
- Add `ruoyi-modules/ruoyi-ai/src/test/java/com/nexus/ai/engineering/EngineeringConnectionResultTest.java`.
- Extend `ruoyi-extend/nexus-engineering-connector/test/task-runner.test.mjs`.
- Extend `plus-ui/scripts/test-engineering-model.mjs` and existing UI tests.

- [x] Add a standalone controller + H2 regression: POST a diagnostic completion, then assert the response and a later task read retain it.
- [x] Add a completion-event regression: assert the owner's SSE event observes a committed final state; a rejected stale completion does not publish another event.
- [x] Add connector assertions for allowlisted diagnostic payload, offline failure, and exclusion of arbitrary fields.
- [x] Add frontend behavior tests for new results, missing historical diagnostics, terminal-transition notifications and check detail rendering.
- [x] Observe RED with:

```powershell
mvn.cmd -pl ruoyi-modules/ruoyi-ai -am test '-DskipTests=false' '-Dprofiles.active=' '-Dtest=EngineeringConnectionResultTest' '-Dsurefire.failIfNoSpecifiedTests=false'
# in connector
node --test test/task-runner.test.mjs
# in plus-ui
node --test scripts/test-engineering-model.mjs
```

## Task 2: Persist safe diagnostics

Files:
- Add `engineering/domain/EngineeringConnectionDiagnostic.java` and `EngineeringCompletionRequest.java` under `ruoyi-ai`.
- Update `EngineeringConnectorArtifactController`, `EngineeringArtifactService`, `EngineeringTaskService`, and `EngineeringTaskVo`.

- [x] Implement the bounded contract:

```java
public record EngineeringConnectionDiagnostic(
    Boolean connected, String cliVersion, String editorVersion, String transportMode) {}
public record EngineeringCompletionRequest(
    int attempt, String leaseToken, long expectedRevision,
    EngineeringConnectionDiagnostic diagnostic) {}
```

- [x] Hide lease material from completion-request `toString`; derive the existing `EngineeringLease` for authorization.
- [x] Validate the diagnostic only after task ownership/lease validation, reject non-check diagnostics, and derive failure from `connected=false`.
- [x] Extend journal metadata and the safe view with optional `connectionDiagnostic`; persist it atomically with terminal state and busy-slot release.
- [x] Keep the existing lease-only service overload for old callers and old tasks.

## Task 3: Notify after commit

Files:
- Add `engineering/service/EngineeringTaskNotifier.java`.
- Update `EngineeringTaskService` and the two task-service test fixture constructions.

- [x] Inject a notifier. Publish `AiSseMessageHelper.sendEngineeringTask(owner.userId(), task)` only after `repository.transaction(...)` returns successfully.
- [x] A notification failure must not undo an already committed task; polling still reads the durable result.
- [x] Use mocked notifiers in existing H2 fixtures and verify real notifier behavior with a mocked SSE boundary.

## Task 4: Send the connector result

Files:
- Update connector `src/task-runner.ts`; keep DRC bodies out of recovery metadata.

- [x] Normalize connection diagnostics into the four-field contract before saving recovery state and completing.
- [x] Send `{...lease(), diagnostic}` for `CHECK_CONNECTION`; send lease-only completion for other actions.
- [x] Resolve `cliVersion` from the actual CLI/bridge version, use null for missing values, and retain disconnected results on failure.

## Task 5: Show actionable results

Files:
- Update `plus-ui/src/api/ai/engineering.ts` and `src/views/ai/engineering/model.ts`.
- Add `src/components/ai/engineering/ConnectionCheckResult.vue`.
- Update `EngineeringTaskCard.vue` and `src/views/ai/engineering/preview/index.vue`.

- [x] Add typed diagnostics and pure result/notification presentation helpers.
- [x] Show the check conclusion, actual version fields and transport; give legacy tasks an explicit historical-data note.
- [x] Label task cards by action and change check navigation to “查看检查结果”. Notify on a pending/running -> terminal transition, without replaying notifications for loaded history.
- [x] Check details render the result and skip PCB artifact/DRC/model sections and manifest loading.

## Task 6: Verify and refresh local services

- [x] Run connector `npm test` and `npm run type-check`.
- [x] Run backend engineering tests with explicit nonzero JUnit counts.
- [x] Run frontend `npm run test:engineering`, `npm run type-check`, and `npm run build:prod` with engineering enabled.
- [x] Review the diff for owner/tenant/lease enforcement, bounded fields, old JSON compatibility, DRC recovery size and notification timing.
- [x] Package the current branch with Maven, restart the authorized local 8080 service, and restart the existing paired connector without re-pairing.
- [x] Verify service responses, device heartbeat and the historical task's safe view. Record any manual browser validation that remains.
- [x] Update this plan and the guide with actual verification results; do not claim historical diagnostics or end-to-end generation that were not observed.


## Verification checkpoint

- RED reproduced: backend 7 tests / 6 assertion failures / 0 errors (missing diagnostics, no SSE, disconnected result accepted); connector 4 tests / 2 assertion failures; frontend model/UI 28 tests / 3 assertion failures.
- An additional outer-transaction regression reproduced premature notification and notification on rollback, then passed with transaction synchronization.
- Backend engineering regression: 17 suites / 61 tests, 0 failures/errors/skips. The new result suite has 9 tests.
- Connector: 49 tests, 0 failures/skips; `tsc --noEmit` passed.
- Frontend: 75 engineering tests, 0 failures/skips, including actual Vue template rendering and one-time completion notification; `vue-tsc --noEmit` passed.
- Frontend production build with `VITE_APP_ENGINEERING_ENABLED=true` passed (4,633 modules). Targeted ESLint, formatting and `git diff --check` passed; existing Sass/chunk warnings remain.
- Backend packaging passed with `mvn -q -pl ruoyi-admin -am package -P local -DskipTests=true`; the 61-test engineering regression above was executed separately with tests explicitly enabled.
- Independent read-only review found no actionable issues in diagnostic persistence and connector security. Outer-transaction notification timing is covered by commit/rollback regression tests.
- Local deployment verified on 2026-10-16: backend `127.0.0.1:8080` (PID 39696), branch frontend `127.0.0.1:5173` (PID 14860), paired connector (PID 19104). Port 8081 is free. Backend startup recorded zero ERROR lines; root/auth-code responses and frontend/result-component responses were HTTP 200.
- At 2026-10-16 18:07:21 CST the device was ACTIVE, heartbeat age 3 seconds, editor available, transport CLI_COMPAT, actual CLI version 4.1.60.198f38ab. The editor version was unavailable and remains null.
- An authenticated device API read against the rebuilt backend confirmed historical task `2111024604854018050` remains SUCCEEDED / CHECK_CONNECTION, completed 2026-10-16 17:21:07 CST, with the new `connectionDiagnostic` field present and null. Historical details were not fabricated.
- Local evidence (ignored, no credentials): connector `.runtime/local-services/result-feedback-verification.json`, `result-feedback-runtime.json`, `result-feedback-legacy-task.json`, and `connector-verification.json`.
- No fresh user task was created after this deployment and no manual browser completion/SSE check was performed. Regression tests exercise new result persistence, recovery, rendering and completion notifications; live checks above verify deployment, historical API compatibility and editor heartbeat. A new connection check can now be initiated from the browser.


## Follow-up: real browser and PCB flow (2026-10-16)

The user requested fresh frontend/backend restarts and a real task through the complete flow after still seeing no data. All three application processes were stopped when inspected; backend 8080, engineering-enabled frontend 5173 and the existing paired connector were started again.

- Browser normal login and actual API creation produced connection-check task `2111091717933338625` at 21:47:42 CST. It completed at 21:47:50 CST with `connected=true`, CLI `4.1.60.198f38ab`, `transportMode=CLI_COMPAT`, editor version unavailable. The browser rendered the result and retained it after a full reload, with zero page errors. The original historical task also rendered its saved conclusion and missing-diagnostic note.
- Actual AI chat with the selected paired device produced PCB draft `2111093050056871937`, session `2111093021074231297`. Real EDA generation/export produced source ZIP, PNG, BOM, netlist, DRC and OBJ/MTL locally, but the task failed with EXPORT_FAILED and no confirmed server artifacts.
- Safe connector tracing reproduced HTTP 503 / STORAGE_UNAVAILABLE on artifact upload. Temporary JVM exception recording identified DataIntegrityViolationException in SysEngineeringStorageAdapter.write: `file_suffix` exceeds the existing `varchar(10)` budget. The adapter currently writes `.engineering` (12 characters). Private namespace and ext1 already identify the record; schema widening is unnecessary.
- [x] Reproduce the metadata boundary in isolated H2 with the installed suffix length, covering all artifact types.
- [x] Use a bounded canonical suffix for each validated artifact type, retaining the private namespace, empty public URL and ENGINEERING_PRIVATE marker.
- [x] Run storage/engineering regression tests, package and restart local backend, then repeat a complete AI-chat PCB task and validate private artifacts and actual 2D/3D browser rendering.
- [x] Update final source/runtime evidence and service process records. Keep failed task history and original generated projects; do not rewrite task states or execute database migrations.

- Storage regression RED: 11 cases / 8 assertion failures / 0 errors. GREEN with the correction: 75 backend engineering/storage cases, 0 failures/errors/skips across 20 suites. Tests use isolated H2 and do not write the existing MySQL database.


### Observed final browser flow

- Repackaged and restarted backend 8080 with the corrected system adapter. JAR SHA-256: `3b3a96772b93337c177284e979dec121a56821b4b2b1f0b6bad06c1271b4408a`; packaged adapter class was compared byte-for-byte with the corrected compiled class. Backend startup had zero ERROR lines. Frontend 5173 explicitly enables engineering; its ignored `.env.local` preserves the switch and loopback proxy for future local launches.
- Normal browser login, device selection and AI prompt using reviewed template `temperature-humidity-demo` version `1.0.0`, dimensions 50 × 35 mm and two layers created session `2111100751537172482`.
- Connection task `2111100775348236290`: SUCCEEDED at 22:23:48 CST, `connected=true`, CLI `4.1.60.198f38ab`, transport CLI_COMPAT, editor version unavailable. Clicking the actual chat card's “查看检查结果” displayed the conclusion and saved diagnostic; full reload retained them.
- PCB draft `2111100775474065409`: created 22:23:42 CST, completed 22:24:25 CST. Seven confirmed artifacts: source ZIP, preview PNG, BOM JSON, netlist, DRC JSON, OBJ and MTL. No required artifact types missing; all seven private content reads passed size/SHA-256 validation (883,063 bytes total).
- The actual chat “查看预览” button and actual task-list “详情” button opened the result page. PNG decoded at 1669 × 1373; BOM rendered four rows; the DRC report displayed eight errors and zero warnings; OBJ rendered in an active canvas. The 3D browser check used headless Chromium with software rendering. Reload retained the PNG result. The actual “下载源工程” button downloaded 26,479 bytes matching the confirmed source digest.
- Real owner-scoped engineering SSE events were observed for both WAITING_DEVICE and their final SUCCEEDED/PARTIAL_SUCCESS states. The validated browser run recorded zero page errors and all engineering HTTP responses were 200.
- The draft is intentionally PARTIAL_SUCCESS: actual DRC status VIOLATIONS, eight errors and zero warnings. Existing protocol uses EXPORT_FAILED for this partial-result summary; this does not mean the seven confirmed files failed to export. No electrical approval or production readiness is claimed.
- A read-only MySQL join verified seven confirmed rows and their sys_oss metadata: suffixes are 4–5 characters, all public URLs remain empty and all ENGINEERING_PRIVATE markers remain present. No DDL/migration was executed for this repair. Failed diagnostic attempts remain in history; their generated project was preserved.
- Fresh local runtime evidence is in connector `.runtime/local-services/flow-check-20261016-2143/`: `flow-result.json`, `pcb-flow-result.json`, `actual-entry-verification.json`, `backend-regression.json`, `database-runtime-readback.json`, corrected-package proof and page screenshots. Raw JVM recording was removed after extracting safe class/column diagnostics; no browser token, password or device credential is included in reports.
