# Engineering UI and navigation implementation plan

> Execute inline in the existing `codex/easyeda-pcb-draft-preview-20261016` worktree. The user requests engineering UI improvements and explicitly identifies the canonical task list `/ai/chat/engineering/task`. Existing fixes and local data must be preserved. No commits, pushes, migrations or new business actions.

**Goal:** Make the engineering task list and results easier to scan, preserve all private artifact controls, and make every result-page return lead to the real task list.

**Design:** Follow existing Element Plus colors and application surfaces. Use a compact navigation bar, action-specific result title, task metadata grid, outcome notice and clearly separated artifact workspace. Connection diagnostics use three responsive value tiles and retain historical missing-data explanations. The list groups the action and ID, presents state/stage together and provides action-specific result links. Cards and pages share spacing and border styles. No global ERP redesign.

**Architecture:** A small navigation contract owns the canonical list path and legacy alias. The existing detail route stays valid. A pure presentation function derives clear result copy from persisted task/manifest facts: complete artifacts with DRC violations produce a warning, while unavailable manifests must not imply complete files. Existing server errors remain accessible. All authentication, polling, cancellation, file hashes, download permissions and GPU cleanup remain intact.

**Tech Stack:** Vue 3, TypeScript, Element Plus, existing Vue renderer tests, real authenticated Chromium verification using the already created local connection and PCB tasks.

## Task 1: Reproduce navigation and presentation issues

Files: `plus-ui/scripts/test-engineering-model.mjs`.

- [x] Add and run a regression for actual rendered return-button navigation to `/ai/chat/engineering/task`.
- [x] Test real route registration: detail sidebar active menu must be canonical and the old list address must redirect to the canonical list.
- [x] Reproduce result presentation for complete artifacts with real DRC violations; cover missing artifacts and missing manifests without inventing success.

## Task 2: Repair navigation and outcome presentation

Files: new `plus-ui/src/views/ai/engineering/navigation.ts`, new `task-presentation.ts`, updated `plus-ui/src/router/index.ts` and `preview/index.vue`.

- [x] Centralize the list path and legacy list path, point return and active-menu metadata at the canonical list, and register a permission-protected redirect for the old address.
- [x] Implement pure outcome presentation using current task state and confirmed manifest data; preserve raw error details without confusing DRC violations with missing exports.

## Task 3: Refine engineering UI

Files: `preview/index.vue`, `task/index.vue`, `ConnectionCheckResult.vue`, `EngineeringTaskCard.vue`.

- [x] Add the responsive result header and metadata grid, outcome notice and artifact workspace header. Retain all tabs, missing/error states, private downloads and lifecycle handling.
- [x] Group connection conclusions and actual diagnostic values in full and compact cards.
- [x] Refine list header, filters, action/status columns and result buttons while retaining pagination and controlled retry/permission boundaries.
- [x] Use scoped styles and existing theme variables, with narrow-screen layout rules. Avoid fake counts and unobserved stage progress.

## Task 4: Verify and document

- [x] Run `npm run test:engineering` with nonzero counts, `npm run type-check`, targeted ESLint and production build with the engineering switch enabled; run `git diff --check`.
- [x] Use normal browser login to view the existing connection/PCB tasks. Click actual return buttons, verify the canonical list and correct sidebar selection, verify legacy URL redirect and browser refresh.
- [x] Check desktop and narrow-screen layouts, 2D preview, DRC report, 3D preview and private source download without creating another task.
- [x] Save scoped screenshots and verification results in ignored local runtime evidence; record any limits. Preserve the eight actual DRC errors in the demonstration PCB.


## Verification results

- RED: 35 model/UI/navigation cases, 29 passed and six assertion failures. The rendered return action used the old path, sidebar metadata used the old path, the old list address did not redirect, and clear outcome presentation was absent. The route tests use the real Vue Router with in-memory history and isolate browser globals.
- GREEN: `npm run test:engineering` ran 81 cases, all passed with zero failures/skips. `npm run type-check` passed. Targeted ESLint and Prettier checks cover the changed TS/Vue/scripts only.
- `npm run build:prod` passed: 4,639 modules, 1m 17s. Existing Sass deprecations/chunk-size warnings were not broadened into unrelated changes. `git diff --check` passed.
- Browser checks used normal login at local frontend 5173 and existing connection task `2111100775348236290` plus PCB task `2111100775474065409`. No new engineering task or database migration was performed.
- Thirteen actual browser checks passed: login; connection return to `/ai/chat/engineering/task` and selected sidebar menu; list-to-connection entry and reload; legacy-list redirect; existing PCB line preview and truthful partial-result notice; DRC action switching to its report; OBJ rendering; source download with matching SHA-256; PCB return with model canvas removed; 390px connection/list/PCB layouts without document horizontal overflow; dark theme. The browser recorded zero page exceptions.
- The saved PCB keeps PARTIAL_SUCCESS and the eight actual DRC errors. The primary notice explicitly says seven files were archived and DRC requires correction; raw EXPORT_FAILED and the server message remain in expandable task records. Missing-file and unavailable-manifest cases retain their own warnings.
- Loaded list screenshots contain the eight existing owner tasks; initial loading screenshots are not used as evidence of empty data. Scoped desktop screenshots and mobile/dark screenshots are stored under connector `.runtime/local-services/ui-navigation-20261016/`, alongside browser/command logs and `verification.json`. Screenshots intended for sharing exclude the global account header.
- Current engineering frontend uses the existing branch and local switch. Backend and connector were preserved; UI changes require no backend restart. No commit/push was performed, and prior worktree changes remain intact.
