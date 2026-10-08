# Flow History Node Layout Repair Plan

**Goal:** Separate nodes with missing or colliding coordinates in instance history while retaining valid coordinates and the original snapshot.

**Architecture:** Reuse the existing `useFlowStore.fromConfigJson` display-only layout in both snapshot and legacy current-config loading paths. Fit the readonly canvas after Vue has updated. Do not change backend data, flow versions, permissions, or execution behavior.

**Tech Stack:** Vue 3, Pinia, Vue Flow, Node.js test runner, esbuild, Vue SFC compiler.

## Context and boundaries

- Branch: `codex/full-flow-acceptance-20260928`; initial HEAD: `78e88211`.
- Preserve existing changes to instance trigger labels, npm scripts, industrial flow names, and all unrelated work.
- Designer already opts into display-only layout. History does not, so missing positions become `(0, 0)`.
- History also needs explicit viewport fitting after its asynchronous graph load.
- No commit, push, deployment, database writes, or changes to existing services.

## Implementation and verification

- [x] Add offline regressions executing the real instance-detail `loadDetail` and Pinia store; replace only network, router, and browser lifecycle boundaries.
- [x] Run regressions before production edits and confirm overlap/viewport failures.
- [x] Modify `plus-ui/src/views/ai/flow/monitor/InstanceDetail.vue`: opt into display-only layout in both branches, bind a canvas ref, fit after `nextTick`.
- [x] Register regressions in the existing standard unit entry without replacing prior npm script changes.
- [x] Run targeted regressions, standard unit tests, frontend type checking, targeted ESLint/Prettier, and production build.
- [x] Review snapshot priority, saved positions, original JSON preservation, status coloring, and change scope.

## Unverified boundary

The provided intranet HTML is reachable (HTTP 200), but this is not authenticated instance API access or browser acceptance. Do not claim deployment to that URL.

## Verification evidence (2026-10-08, local workspace)

- Before the fix: `node --test plus-ui/scripts/test-flow-instance-layout.mjs` ran 9 tests, 1 passed and 8 failed. Coordinate tests failed with `History nodes context and baseline must not overlap`; viewport fitting was absent.
- After the fix: `node --test scripts/test-flow-instance-layout.mjs` in `plus-ui` passed 9/9, with 0 skipped.
- `npm run test:unit`: exit 0. Node test groups reported 73, 127, 433, 9, and 145 tests (787 total); all passed, none skipped. Existing legacy assertion scripts also completed successfully.
- `npm run type-check`: exit 0 (`vue-tsc --noEmit`).
- `npx eslint src/views/ai/flow/monitor/InstanceDetail.vue scripts/test-flow-instance-layout.mjs`: exit 0.
- `npx prettier --check src/views/ai/flow/monitor/InstanceDetail.vue scripts/test-flow-instance-layout.mjs package.json`: exit 0 after formatting only the newly added regression script.
- `npm run build:prod`: exit 0. Existing Sass deprecation/browser-externalization warnings remain; no dependency or styling changes were made for those warnings.
- Isolated headless Edge + temporary Vite harness rendered the real `InstanceDetail.vue`, Pinia, and Vue Flow components with synthetic offline API responses. At both 1440px and 960px, 9 nodes were visible, 0 node pairs overlapped, all nodes fit the viewport, original snapshot JSON was unchanged, and no browser runtime exception occurred.
- Browser harness and logs are generated under ignored `plus-ui/node_modules/.tmp/`; screenshot is outside the repository in the app visualization output directory.
- Independent readonly review found no blocking issue and confirmed the regression executes the production loader and real store.
- No deployed/authenticated instance acceptance, database write, commit, push, or deployment was performed.

## User-authorized local Git commit verification

- The user subsequently requested a Git commit. Scope is limited to the layout repair, its regression test, npm test registration, and this record; unrelated changes remain uncommitted.
- Shared tracked files are staged selectively from HEAD, excluding the pre-existing trigger-label changes and trigger-display test registration.
- The regression resolves the optional display helper only if the actual loaded SFC imports it, so the commit does not depend on an unrelated untracked helper.
- Exported the exact staged SFC, store, package manifest, and regression into an ignored temporary directory; all 9 tests passed without the uncommitted display helper.
- Re-ran `npm run test:unit` and `npm run type-check` before the commit; both exited 0. Targeted ESLint and Prettier checks also exited 0.
- Verified the two shared working files remained byte-for-byte unchanged by selective staging.
- Remote push and deployment are not included in this authorization.
