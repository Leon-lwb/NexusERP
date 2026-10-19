# Approved legacy source-policy adoption contract — ERP worker / Mill

Scope: an explicit audited source snapshot for future operations, not a reconstruction of original release policy. Current material master is never used as evidence. No inventory, reservations, consumption, receipt or historical transaction is converted by adoption.

Runtime entry (same physical ERP write transaction):
`PrdWorkOrderPolicyAdoptionService.adoptApproved(Long adoptionId,String commandId,long expectedVersion) -> String snapshotHash`.
A locked `PrdWorkOrderTrackingPolicyService.previewAdoption(Long erpWoId)` returns exact WO membership source JSON and SHA256 for operator/offline planning; the hash is a CAS baseline, not a policy source.

New ERP table `wm_work_order_policy_adoption`, InnoDB. Mill owns schema_contract.py + generated DDL/metadata/offline census. This table and wm_work_order_tracking_policy are immutable source evidence to offline repair/conversion; only the apply status/version/time/result fields below change in the runtime source transaction.

| column | exact type | contract |
|---|---|---|
| adoption_id | bigint NOT NULL | primary key |
| tenant_id | varchar(20) COLLATE utf8mb4_bin NOT NULL | authenticated tenant |
| command_id | varchar(128) COLLATE utf8mb4_bin NOT NULL | unique with tenant |
| erp_wo_id | bigint NOT NULL | actual ERP WO |
| expected_wo_hash | char(64) COLLATE ascii_bin NOT NULL | SHA256 exact preview source JSON |
| source_json | longtext NOT NULL | original full policy evidence bundle, format below |
| source_hash | char(64) COLLATE ascii_bin NOT NULL | SHA256 source_json UTF8 |
| approval_json | longtext NOT NULL | separate approved decision bound to request_hash |
| approval_hash | char(64) COLLATE ascii_bin NOT NULL | SHA256 approval_json UTF8 |
| request_hash | char(64) COLLATE ascii_bin NOT NULL | intent fingerprint below |
| proposed_by | bigint NOT NULL | real proposer |
| approved_by | bigint NOT NULL | separate real approver, not proposer |
| approved_at | datetime(3) NOT NULL | actual approval time |
| expires_at | datetime(3) NOT NULL | authorization expiration, greater than approved_at |
| status | varchar(16) COLLATE ascii_bin NOT NULL | APPROVED -> APPLIED only |
| version | bigint NOT NULL DEFAULT 1 | apply CAS increments exactly once |
| applied_at | datetime(3) NULL | DB apply time, future-operation boundary |
| snapshot_hash | char(64) COLLATE ascii_bin NULL | resulting whole bank hash |

Unique `(tenant_id,command_id)`; index `(tenant_id,erp_wo_id)`. No soft delete. Immutable evidence may not be replaced by an UPSERT.

Source JSON exact semantic fields:
`{schemaVersion:1,tenantId:"000001",erpWoId:"1",materials:[{materialId:"10",policy:{batchManaged:true,serialManaged:false,allowMixedLot:false,version:"7",stage:"STRICT"},reference:"original-archive/ref",capturedAt:"2026-10-01T00:00:00Z",documentHash:"64hex"},...]}`.
Each material entry must also have `origin` = ORIGINAL_RELEASE_ARCHIVE / IMMUTABLE_SOURCE_SNAPSHOT / SIGNED_DHR_POLICY_SNAPSHOT. CURRENT_MASTER is rejected. Every frozen component/root material must have exactly one full original policy. No policy field/default can be omitted. Original reference/document hash are explicit approved archival evidence; a current master export is not an allowed historical source. Captured time cannot follow approval time. The operator-approved evidence bundle, not this writer, certifies the archived document's authenticity.

Intent request_hash uses common TraceHashes.fingerprint in this exact order:
`("WO_POLICY_ADOPTION_V1",tenantId,adoptionId,commandId,erpWoId,expectedWoHash,sourceHash,proposedBy,approvedBy,approvedAtEpochMillis,expiresAtEpochMillis)`.
Approval JSON:
`{decision:"APPROVED",requestHash:"...",approvedBy:"...",approvedAt:"ISO instant",reference:"separate-approved-decision/ref"}`.
The separate approval must assert this exact intent. The SQL/import pathway is an approved operational window; it is not a client JSON self-approval API.

Initial apply requires current real WO RELEASED/IN_PROGRESS, matching locked source CAS, empty bank for the entire WO, complete original evidence and valid unexpired approval. It atomically inserts SOURCE_POLICY_ADOPTION bank rows, freezes approval/source JSON in bank source_json, marks the approval APPLIED with version+1/time/hash. Repeating the same applied command verifies original whole-bank proof and result, without consulting current policy or reapplying any inventory effect. Any different command/version/content or original-release snapshot overwrite fails.

Inventory conversion boundary: existing converter supports audited available stock only (no reserved/frozen/WO-linked/WIP pool). It preserves original stock dimensions and old transactions/proofs. In-flight WO policy adoption enables future tracking decisions but does not authorize conversion of reservations or actual MES WIP; those need their own source-document evidence and conversion workflow. Do not label adopting policy as completing WIP inventory conversion.


Implementation files: PrdWorkOrderPolicyAdoptionService (approval application/evidence validation), PrdWorkOrderTrackingPolicyService (locked preview, immutable group persist/read). No HTTP endpoint accepts an approval envelope; a reviewed authorization row is the only writer input. Runtime caller supplies only authorization ID, its immutable command ID and original CAS version.

Initial red/green evidence: adoption-red executed22, four required behavior failures; adoption-green executed58 across adoption + bank + actual release + thin Spring context, all58 passed /0 skipped. Final expanded results are recorded by the worker before READY.


## Callable entry and dedicated permission (final)

- GET `/erp/prd/work/tracking-policy/preview/{woId}`: same-ERP REQUIRED transaction returns only real source JSON/hash/material IDs; no approval or bank row is created.
- POST `/erp/prd/work/tracking-policy/adopt`: body `{adoptionId:"700",commandId:"adopt-700",expectedVersion:"1"}` only. Return `R.data.snapshotHash`.
- Both require dedicated permission **`prd:work:trackingPolicyAdopt`**. The application also checks this through the real authenticated-context authorizer, so entry protection does not depend on controller AOP alone. Applying actor must equal the original independent approved_by; proposed_by must be a different real actor.
- Controller -> PrdWorkOrderPolicyAdoptionEntry REQUIRED -> PrdWorkOrderPolicyAdoptionService MANDATORY -> bank/auth CAS on one physically verified ERP connection. Ordinary body fields cannot approve or select a policy.
- Mill/main permission owner: register the dedicated permission for the intended operators; do not map existing generic work-order edit permission to it automatically. This worker does not edit master menus or grant roles.

The earlier service signature remains the domain writer; runtime HTTP callers use the REQUIRED entry. No HTTP endpoint submits/approves an evidence envelope. Approved authorization rows come only from the reviewed offline/import operating procedure and authentic original archives.


## Final code verification

`target/p2-workorder-policy/adoption-final-green-last.log`: isolated javac success;9 classes,113/113 executed successful,0 skipped. New service/entry/authorizer/controller and tests are ready for the main single reactor. No SQL/schema file was edited by this worker: Mill retains the DDL single source/offline export/emit-SQL and strict-constraint deployment work. Dedicated permission/menu registration remains an explicit main/schema-owner integration item.
