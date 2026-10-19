# ERP work-order tracking policy snapshot implementation plan

Approved worker scope: common new read contract; new ERP snapshot writer/reader; PrdWorkOrderServiceImpl.release hook; new isolated tests. No Maven, commits, DB operations, converter, MES writer, or other owners' files.

Goal: freeze root plus actual BOM/requirement component TrackingPolicy in the real ERP release transaction; preserve immutable policy evidence when material master data changes. Missing legacy evidence is UNKNOWN. Audited source-policy adoption is explicitly future-only.

Architecture: a mandatory ERP transaction writer locks a CREATED/APPROVED work order, source requirement rows and materials in stable order. It freezes a complete membership/source JSON and policy JSON with hashes. release calls it before claiming RELEASED, so any claim/issue failure rolls everything back. A read-only REQUIRES_NEW ERP port returns immutable evidence, validates hashes/completeness, and never queries current material policy as fallback. The existing material-policy reader is REQUIRES_NEW and is therefore not used for transactional freeze.

- [x] Add common WorkOrderTrackingPolicyPort and immutable WorkOrderTrackingPolicySnapshot contract.
- [x] Write new failing H2 transaction tests for release membership, changed master policy, tenant, UNKNOWN, proof tamper, rollback and legacy rejection.
- [x] Implement small ERP persistence service and full-hash reader. ERP_RELEASE is the only provenance written by this slice; no historical policy is synthesized.
- [x] Follow-up source-policy adoption is implemented in PrdWorkOrderPolicyAdoptionService with a dedicated authenticated REQUIRED entry/controller, archival evidence, independent approval, CAS/idempotency and future-only immutable snapshots. Exact schema/operation contract: 2026-10-19-work-order-policy-adoption-contract.md.
- [x] Add release hook gated by nexus.trace.relay.enabled; required production dependency, no nullable safety bypass.
- [x] Verify real release hook, unchanged legacy release behavior, local DS/transaction boundary using isolated javac/JUnit.
- [x] Hand off the exact schema contract below for Mill.
- [ ] Mill-owned integration: schema_contract.py DDL single source, generated SQL + offline REQUIRED_TABLES/READ_ONLY_SOURCE_TABLES and metadata. No manual generated metadata edit by this worker.
The earlier independent V1 conversion/replay review remains separate and has not been claimed complete; the latest steering restricts this worker to the policy bank/common port/release/schema handoff.

## Exact common contract
`WorkOrderTrackingPolicyPort.find(String tenantId, Long erpWoId, Long materialId) -> WorkOrderTrackingPolicySnapshot`.
Snapshot provenance: ERP_RELEASE / SOURCE_POLICY_ADOPTION / UNKNOWN. UNKNOWN has no policy/version/hashes/timestamps. Source adoption never asserts the original release policy and applies only from its committed adoption time.

## Mill schema contract (single new ERP table)
Table `wm_work_order_tracking_policy`, InnoDB. No ORM tenant/delete/audit columns are implied.

| column | type | meaning |
| --- | --- | --- |
| snapshot_id | bigint NOT NULL | assigned ID, primary key |
| tenant_id | varchar(20) COLLATE utf8mb4_bin NOT NULL | authenticated tenant |
| erp_wo_id | bigint NOT NULL | real prd_work_order.wo_id |
| material_id | bigint NOT NULL | root or component ID |
| snapshot_version | int NOT NULL | format revision, currently1 |
| policy_version | bigint NOT NULL | frozen TrackingPolicy.version |
| policy_json | longtext NOT NULL | application ObjectMapper JSON |
| policy_hash | char(64) COLLATE ascii_bin NOT NULL | SHA256 exact policy JSON UTF8 |
| source_json | longtext NOT NULL | immutable WO/root/BOM/requirement membership evidence |
| source_hash | char(64) COLLATE ascii_bin NOT NULL | SHA256 exact source JSON UTF8 |
| snapshot_hash | char(64) COLLATE ascii_bin NOT NULL | whole membership + all policy hashes |
| provenance | varchar(32) COLLATE ascii_bin NOT NULL | ERP_RELEASE or SOURCE_POLICY_ADOPTION |
| evidence_ref | varchar(255) COLLATE utf8mb4_bin NOT NULL | source evidence reference |
| frozen_at | datetime(3) NOT NULL | database freeze time |
| effective_from | datetime(3) NOT NULL | future operation boundary, not fabricated historical time |
| actor_id | bigint NULL | release may be system-sourced/null; audited adoption requires actor |

Unique `(tenant_id,erp_wo_id,material_id)`; index `(tenant_id,erp_wo_id)`. Rows are immutable. Missing table/column is a deployment error, not UNKNOWN. UNKNOWN is returned only for an absent row.

Please add this table to script/tools/material_trace/schema_contract.py single source, regenerated SQL + metadata and offline REQUIRED_TABLES/READ_ONLY_SOURCE_TABLES census. It is read-only source evidence; conversion/repair must never synthesize or rewrite it. SQL worker owns these files; this worker does not edit them.


## Verified local readiness (2026-10-19)

Code and relevant fixtures READY for main's single reactor. No Maven was run by this worker. Three legacy work-order fixtures now declare mock PrdWorkOrderTrackingPolicyService. New thin Spring transaction context registers the required bank, serial-inventory and completion mapper beans, verifies release runs with an actual local transaction and its callback runs after commit, and requires startup failure when bank is absent. Context failures close the context; these new fixtures create no static TenantHelper mocks.

Fresh command:
`python -X utf8 ruoyi-modules/ruoyi-erp/target/p2-workorder-policy/run.py policy-context-green com.nexus.erp.prd.PrdWorkOrderTrackingPolicyContextTest com.nexus.erp.prd.PrdWorkOrderTrackingPolicyReleaseTest com.nexus.erp.prd.PrdWorkOrderTrackingPolicyServiceTest com.nexus.erp.prd.PrdWorkOrderServiceImplTest com.nexus.erp.prd.PrdWorkOrderReleaseRoutingTest com.nexus.erp.prd.PrdWorkOrderCompletionPostingWarningTest`

Result: fresh isolated javac succeeded; 84 tests executed, 84 successful, 0 failures, 0 skipped. Log: `ruoyi-modules/ruoyi-erp/target/p2-workorder-policy/policy-context-green-last.log`.

Red-before-green evidence: policy-red (20 executed,12 failures), release-hook-red (5 executed,2 failures), release-source-race-red (1 actual stale source failure), policy-metadata-red (28 executed,6 failures). Final fixes bind the released object to locked root/BOM/source/plan/backflush facts and reject missing/null policy metadata, without modifying MaterialPolicyPort's legacy adapter.

Boundary: H2 uses real ERP source SQL/physical connection/transactions and mocks the BOM service expansion boundary; real MySQL5.7 and the main full reactor are not claimed by this result. Schema/offline/MES consumers remain owned by their assigned agents.


## Follow-up implementation (after reactor06)

The prior release-only limitation is superseded by the explicitly requested approved source adoption writer and callable entry. GET `/erp/prd/work/tracking-policy/preview/{woId}` obtains the real source CAS without policy fallback; POST `/erp/prd/work/tracking-policy/adopt` accepts only authorization ID/command/original CAS version. Dedicated permission is prd:work:trackingPolicyAdopt and the authenticated actor must be the original independent approver. The reviewed authorization comes from original archives and approved offline/import procedure, not a current master export. Details and Mill's new wm_work_order_policy_adoption table contract are in the separate adoption document.

Fresh expanded isolated result: adoption-final-green executed113 tests, all113 passed /0 skipped, fresh javac succeeded. Covers actual REQUIRED -> MANDATORY local ERP transaction, physical connection, preview with zero writes, independent approval identity and permission, original evidence, missing/foreign/expired/tampered records, source CAS, immutable full scope, rollback and idempotent replay after the WO completes and current master is unavailable. No Maven or business database operation was run by this worker.
