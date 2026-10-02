# Task D — E / unified writer routing contract

## Stable Java API

`com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService`

```java
EventReceipt apply(Command command);
EventReceipt applyLocked(Command command);
EventReceipt consumeReceipt(ReceiptCommand command);
EventReceipt consumeReceiptLocked(ReceiptCommand command);

record Command(String tenantId, Long actorId, Long materialId,
    String allocationKey, String requestKey, Integer expectedVersion,
    Operation operation, Long businessLineId) {}
record ReceiptCommand(String tenantId, Long materialId, String allocationKey,
    String requestKey, Integer expectedVersion, Long receiveLineId) {}
```

Public methods create their own `PuRequirementErpTransactionExecutor.executeRepeatableRead` callback.
They must be entered **outside** existing Spring/dynamic transactions; no outer REQUIRED / REQUIRES_NEW shortcut.
Locked methods require the current executor-owned ERP RR connection and never issue a new transaction.
No method accepts quantities, verified flags, requested statuses or supplied permissions.

For APPROVE / TRANSFER / CLOSE / CANCEL / REJECT, route the **real business state change and the locked call
in the same executor callback**. D does not create an order, invoke a workflow or allocate a cross-db number.
Public `apply` reconciles only an already persisted real state; it is not a replacement for the business
approval/transfer state machine. The routing layer must not first commit a business change then call D
and call that sequence atomic.

`businessLineId` is the real **order line** for TRANSFER_TO_ORDER, the real **receive line** for RECEIPT_POSTED,
and the real **return line** for RETURN_POSTED. It must be null for other operations.
`requestKey` is the frozen header/ledger `decision_idempotency_key`, including MANUAL/MRP/LOW_STOCK entries.

## Permissions and automatic receipt processing

Interactive `apply*` checks the current Sa-Token login, current actor ID and login/dynamic tenant.
Operation permissions: `pu:requisition:{approve,convert,close,cancel,remove}`, `pu:order:{close,cancel,receive}`,
`pu:return:execute`. REJECT_REQUISITION uses `pu:requisition:approve`.
`consumeReceipt*` is an internal MQ-only entry with no externally supplied actor. The actor comes from
locked matching stock transaction rows; tenant must already be bound by the trusted consumer.
Do not expose it as an unauthenticated controller endpoint or regard transaction qualification as authentication.

D **owns order received_quantity / returned_quantity updates** for certified lines, using real persisted
FINISHED / EXECUTED documents and stock movement results, exact old-counter CAS and permanent events.
Do not also execute the old ReceiveWriteback / Return counter increments for those lines.
D does not re-execute inventory posting or return financial adjustments.

## Status / replay contract

- CREATE remains `CREATE:<allocationKey>`, eventType CREATE, null fromStatus, REQUEST_INTENT toStatus, 0 to coverage.
- APPROVE: REQUEST_INTENT unchanged, source occupancy unchanged, append event + increment ledger version.
- TRANSFER_TO_ORDER: same ledger becomes ORDER_COMMITTED_NOT_TIMELY_SUPPLY; exact coverage inherited, no new ledger.
- RECEIPT_POSTED: occupied decreases, fulfilled increases; state stays ORDER_COMMITTED_NOT_TIMELY_SUPPLY.
- CLOSE_ORDER / CANCEL_ORDER: only occupied moves to released. RELEASED may retain orderLineId,
  transferTime and fulfilled > 0.
- RETURN_POSTED: active order reduces fulfilled and restores occupied. Physical RELEASED-to-RELEASED
  returns are rejected by the bounded event contract because occupied delta 0 does not freeze posted quantity.
  The pure arithmetic policy is not an authorization to execute that unsupported physical lifecycle.
- Source requisition closing must never release an order-inherited ledger.
- Ordinary release never deletes or changes the COMMITTED command. Compensation is separate.

Event keys are `LIFECYCLE:` + length-prefixed SHA256(tenantId, allocationKey, operation.name(), real source ID).
No arbitrary message ID or 24h Redis TTL decides permanent consumption. Source ID is order line / receipt line /
return line for line operations; order ID for CLOSE/CANCEL_ORDER; requisition ID for requisition operations.
Replay rechecks permanent command/frozen result, current source identities and the complete event chain,
then returns the original event even if expectedVersion is old. Conflicting actor/identity/result is rejected.
Existing event schema stores only occupied snapshots: EventReceipt deliberately does not fabricate
historical fulfilled/released/version snapshots.

## Current bounded source support

Ledger source_type MRP_DETAIL, allocation_no 1; requisition line MRP/sourceId=detailId.
Writer line.unit must remain the decimal-string baseUomId, not a unit display name. Root UOM is independently read
by ID, is_base=1, primary_id=self, conversion_rate=1, precision and version/verification present.
All frozen requisition lines are checked against command.result_fingerprint using the writer's canonical digest.

One allocation per requisition line and order line; no MOQ excess, unit conversion, split/merged order lines,
unknown historical provenance or timely-supply certification. Unknown states or persisted quantity inconsistencies reject.
Current receive stock rows have source_doc_line_id NULL. Only a single-line receive document can then be matched
using the complete VIRTUAL_OUT/TARGET_IN pair and exact root-unit amounts. Multi-line receipts are explicitly rejected.
Closing orders rejects any unconsumed or unclosed downstream physical document.

## Main-thread integration requirements

D does not edit SourceCertification / lineage reader. Existing readLocked rejects fulfilled != 0 and
RELEASED-with-order-line, and currently does not verify MRP line.sourceId against ledger.detailId.
Extend that source contract before new sourcing can accept partial receipt/closed-order facts. Do not remove
its gate merely because D recognizes the local arithmetic state.

D requires the parent's `pu_certified_purchase_command` transaction table with result_fingerprint.
No additional schema is required for the bounded EventReceipt contract. Complete historical result snapshots
or arbitrary multi-line receipt attribution require a separately reviewed schema/result contract.
No SQL migration, database write, service start/stop, shared Maven target, commit or push is performed by D.


## Frozen document orchestrator and actual delivery boundary

Primary production entry is now `PuCertifiedPurchaseWorkflowService` (not per-ledger calls):
`approve/convert/close/cancel(Long requisitionId, Integer expectedVersion)`.
Controller: `POST /erp/pu/requisition/certified/{id}/approve|convert|close|cancel`.
Body: `{ "expectedVersion": 0 }`; tenant, actor, username and department are read from the current login.
All request sources are prevalidated under one owned ERP RR; the document head changes once by CAS,
then all lines/ledger/events succeed or the executor rejects and rolls the callback back.
Convert creates one real DRAFT order and every source order line; server order numbering is outside RR.
Already ORDERED replay first returns the original real order and does not reserve a new number.
Only untransferred DRAFT/APPROVED requests can be cancelled/closed here. Configured Flowable is refused.

Whole-document compensation is available separately:
`compensateDraft/compensateDraftLocked(CompensationCommand)` with tenant, actor, material, original requestKey,
requisitionId, expectedDocumentVersion and the complete allocationKey -> expectedVersion map.
It keeps every ledger/event and soft-deletes the unchanged whole draft only in the same transaction,
CAS-marks the permanent command COMPENSATED, and returns original persisted compensation events on replay.
No command or event DELETE is allowed. This is not yet an AI compensation route.

Actual schema gate is `PuCertifiedPurchaseLifecycleSourceGate`: fixed empty reads pin the table definitions;
actual InnoDB engines and complete non-prefix tenant unique keys are checked on the owned connection.
Core operations verify command/ledger/event in addition to the existing material-guard source gate.
Downstream reads verify receive/return/stock transaction engines; physical effect operations additionally
require every native quantity source to be DECIMAL(18,4), never Java BigDecimal or CAST as an upgrade.
APPROVE/TRANSFER independently lock real vendor/current terms and validate frozen whole-request quantity
against root-UOM MOQ/multiple/packaging at a fresh database clock, including a terminal recheck after CAS/events.
An expired term cannot be hidden by the creation fingerprint or a previous source snapshot.

Not delivered: new certified order approval/pricing entry, actual MQ consumer routing, purchase/IQC/stock
producer integration, multi-line receipt batching, legacy Flowable cross-master approval, derived UOM or
unknown historical evidence. Order DRAFT is not approved supply; internal receipt services are not evidence
that a physical business operation occurred. Current DB AUTH1045/login401 and loaded HEAD are not acceptance.
Final full-source shared Maven/reactor and independent review belong to the parent thread; D does not claim
production deployment, real database verification or full procurement lifecycle completion.


## Review repair: occupied global capacity and frozen catalog

Approve before its first business CAS, and convert in BOTH the number preflight and the actual creation RR,
now call `ErpPurchaseSourceCertificationService.prepareLocked` with the real tenant/material/vendor,
original command.window_end and ALL document MRP detail IDs. All own ledger commitments remain in the
competition pool. MRP shrinkage or new stock making C exceed the current uncovered demand must reject
OVERCOMMITTED; no draft quantity is reallocated or reoccupied. Successful scope.lines may be empty.

D CommandSource ends with catalogFingerprint and windowEnd (the latter read by DATE_FORMAT to ISO day).
The current scope.catalog is compared to the persisted catalog_fingerprint using the parent's static
catalogFingerprint helper. Missing fingerprint/window, or a changed directory contract even with the same
terms version string, refuses a new approval/transfer and requires reapproval, not an automatic hash refresh.
Read-only replay of an already committed lifecycle event does not issue new business state changes.
Workflow constructor now has an eighth dependency: ErpPurchaseSourceCertificationService.
The parent owns writer capture, command schema and SQL; D changes none of those files.


## SPEC issue 3 repair: head and line soft-delete facts

ReturnLine and MovementSource retain delFlag for the header and append lineDelFlag for the line.
lockReturnLine / lockReceiptSources / lockReturnSources explicitly select h.del_flag AS del_flag
and l.del_flag AS line_del_flag. No JOIN/WHERE deletion filter hides a corrupt/deleted row.
Returned physical effects and downstream movement checks require BOTH flags to be exactly 0;
unknown, header-deleted or line-deleted facts refuse with no counter/ledger/event/guard write.
The occupied-global-capacity and frozen-catalog preflight repairs remain in place.
D production code is frozen again after this issue; further integrations remain outside delivered scope.


## QUALITY Important repairs: causal sequence and physical replay

Event entity now carries eventSequence. CREATE explicitly writes 0 (parent writer); transitions and whole-draft
compensation explicitly write expected ledger version + 1. EventMapper orders both locked and read-only history
by event_sequence, never occurred_at or Snowflake event_id. History requires sequence 0..ledger.version without
NULL, duplicates or gaps. Same-second cross-JVM ID reversal is irrelevant; legacy NULL is rejected, not inferred.
Actual SourceGate additionally requires the complete non-prefix unique index
(tenant_id, allocation_id, event_sequence), in addition to the event_key unique index.

Physical priorHit is not a shortcut to success: it rechecks order counters against both net fulfilled and gross
persistent RECEIPT/RETURN quantities, then compares the currently locked posted effect quantity with the original
positive occupied delta. Source return 3 cannot replay original RETURN delta 2 even if stock effect also changed to 3.
A changed received counter cannot replay a matching receipt key. Deleted head/line continues to reject.
Physical RELEASED-to-RELEASED RETURN has no frozen posted-quantity evidence in this schema, so NEW writes and
zero-delta replay are explicitly unsupported. No quantity or fingerprint evidence is fabricated.

Migration: script/sql/update/2026-10-02-certified-allocation-event-sequence-ry-erp.sql (manual, single-use baseline).
Old event_sequence remains NULL; no timestamp/ID/version-based update or backfill. Fresh install event table alone
is patched with NOT NULL sequence and the unique tuple; bytes outside the event table were preserved, including
parent command catalog_fingerprint and B native-quantity definitions. Registry entry is for the parent to append.
Private verification compiles fresh entity via Lombok and D source with javac --release 17, uses isolated output,
and does not use Maven, execute SQL, start/stop services or commit. Final parent reactor and review remain required.
