package com.nexus.web.service;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.IdentityTraceReadPort;
import com.nexus.common.tenant.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * 编排各业务模块的只读追溯接口；跨库读取各自提交，不建立覆盖全部模块的事务。
 */
@Service
@RequiredArgsConstructor
public class MaterialIdentityTraceService {
    private final List<IdentityTraceReadPort> readers;

    /**
     * 按稳定身份连接各模块只读分节，并在返回前应用当前调用方的字段可见性。
     *
     * @param identity 跨模块稳定实物身份。
     * @param module 事实所属业务模块。
     * @param section 当前追溯事实分节。
     * @param cursor 绑定原租户、主体、分节及修订的分页游标。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 当前模块单件事实及对应分页状态。
     */
    public Map<String, Object> identity(
            Long identity, String module, String section, String cursor, int limit) {
        String tenant = tenant();
        List<IdentityTraceReadPort> visible = visible();
        if (section == null && cursor != null || section != null && module == null) {
            throw new ServiceException("分页须指定模块及范围");
        }
        if (module != null && !Set.of("ERP", "MES").contains(module)) {
            throw new ServiceException("追溯模块无效");
        }
        if ("MES".equals(module) && "TEST".equals(section)) {
            StpUtil.checkPermission("mes:flash:testrecord:query");
        }
        if ("MES".equals(module) && "IPQC_WORK_ORDER".equals(section)) {
            StpUtil.checkPermission("mes:qc:ipqc:query");
        }
        var parts = new ArrayList<IdentityTracePart>();
        for (var reader : visible) {
            if (module == null || module.equals(reader.sourceModule())) {
                parts.add(restrict(reader.identity(tenant, identity, section, cursor, limit)));
            }
        }
        if (parts.isEmpty()) {
            throw new ServiceException("当前身份无权查询该模块范围");
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("identityId", identity.toString());
        result.put("scope", "IDENTITY");
        result.put("parts", parts);
        result.put(
                "truncated",
                parts.stream()
                        .flatMap(part -> part.sections().values().stream())
                        .anyMatch(TracePage::hasMore));
        var gaps = new ArrayList<String>();
        parts.forEach(part -> gaps.addAll(part.evidenceGaps()));
        if (visible.size() < 2) {
            gaps.add("部分业务模块不在当前用户授权范围内");
        }
        result.put("evidenceGaps", gaps);
        return result;
    }

    /**
     * 按库存批次读取各模块事实，区分来源仓储与当前位置。
     *
     * @param batch 实际批次及其来源上下文。
     * @param module 事实所属业务模块。
     * @param section 当前追溯事实分节。
     * @param cursor 绑定原租户、主体、分节及修订的分页游标。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @return 当前模块批次事实及对应分页状态。
     */
    public Map<String, Object> batch(
            Long batch, String module, String section, String cursor, int limit) {
        String tenant = tenant();
        var visible = visible();
        if (batch == null
                || batch <= 0
                || limit < 1
                || limit > 500
                || section == null && cursor != null
                || section != null && module == null) {
            throw new ServiceException("批次分页须指定准确身份、模块及范围");
        }
        if (module != null && !Set.of("ERP", "MES").contains(module)) {
            throw new ServiceException("追溯模块无效");
        }
        var parts = new ArrayList<BatchTracePart>();
        var gaps = new ArrayList<String>();
        for (var reader : visible) {
            if (module == null || module.equals(reader.sourceModule())) {
                var part = reader.batch(tenant, batch, section, cursor, limit);
                var sections = new LinkedHashMap<>(part.sections());
                if ("ERP".equals(part.module()) && !costAllowed()) {
                    redactCosts(sections);
                }
                parts.add(
                        new BatchTracePart(
                                part.module(),
                                part.batchId(),
                                part.scope(),
                                sections,
                                part.evidenceGaps()));
                gaps.addAll(part.evidenceGaps());
            }
        }
        if (parts.isEmpty()) {
            throw new ServiceException("当前账号无权查询该批次模块范围");
        }
        if (visible.size() < 2) {
            gaps.add("部分业务模块不在当前用户授权范围内");
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("batchId", batch.toString());
        result.put("scope", "BATCH");
        result.put("parts", parts);
        result.put("evidenceGaps", gaps);
        result.put(
                "truncated",
                parts.stream()
                        .flatMap(part -> part.sections().values().stream())
                        .anyMatch(TracePage::hasMore));
        return result;
    }

    /**
     * 从实际批次来源遍历受影响身份，分别维护单件、工单范围和批次游标并声明截断。
     *
     * @param batch 实际批次及其来源上下文。
     * @param after 上次处理完成的稳定位置。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @param maxDepth 召回关系遍历深度上限。
     * @return 批次召回涉及的单件、工单和产品批次范围，以及各范围的后续游标。
     */
    public Map<String, Object> recall(Long batch, long after, int limit, int maxDepth) {
        return recall(batch, after, limit, maxDepth, null);
    }

    /**
     * 从实际批次来源遍历受影响身份，分别维护单件、工单范围和批次游标并声明截断。
     *
     * @param batch 实际批次及其来源上下文。
     * @param after 上次处理完成的稳定位置。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @param maxDepth 召回关系遍历深度上限。
     * @param scopeCursor 工单范围的独立分页位置。
     * @return 批次召回涉及的单件、工单和产品批次范围，以及各范围的后续游标。
     */
    public Map<String, Object> recall(
            Long batch, long after, int limit, int maxDepth, String scopeCursor) {
        return recall(batch, after, limit, maxDepth, scopeCursor, null);
    }

    /**
     * 从实际批次来源遍历受影响身份，分别维护单件、工单范围和批次游标并声明截断。
     *
     * @param batch 实际批次及其来源上下文。
     * @param after 上次处理完成的稳定位置。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @param maxDepth 召回关系遍历深度上限。
     * @param scopeCursor 工单范围的独立分页位置。
     * @param batchCursor 受影响批次的独立分页位置。
     * @return 批次召回涉及的单件、工单和产品批次范围，以及各范围的后续游标。
     */
    public Map<String, Object> recall(
            Long batch,
            long after,
            int limit,
            int maxDepth,
            String scopeCursor,
            String batchCursor) {
        return affected("BATCH", batch, after, limit, maxDepth, scopeCursor, batchCursor);
    }

    /**
     * 从实际组件身份正向查找产品及客户去向，保留精确分配与工单可能影响的区别。
     *
     * @param identity 跨模块稳定实物身份。
     * @param after 上次处理完成的稳定位置。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @param maxDepth 召回关系遍历深度上限。
     * @return 组件去向涉及的单件、工单和产品批次范围，以及各范围的后续游标。
     */
    public Map<String, Object> whereUsed(Long identity, long after, int limit, int maxDepth) {
        return whereUsed(identity, after, limit, maxDepth, null);
    }

    /**
     * 从实际组件身份正向查找产品及客户去向，保留精确分配与工单可能影响的区别。
     *
     * @param identity 跨模块稳定实物身份。
     * @param after 上次处理完成的稳定位置。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @param maxDepth 召回关系遍历深度上限。
     * @param scopeCursor 工单范围的独立分页位置。
     * @return 组件去向涉及的单件、工单和产品批次范围，以及各范围的后续游标。
     */
    public Map<String, Object> whereUsed(
            Long identity, long after, int limit, int maxDepth, String scopeCursor) {
        return whereUsed(identity, after, limit, maxDepth, scopeCursor, null);
    }

    /**
     * 从实际组件身份正向查找产品及客户去向，保留精确分配与工单可能影响的区别。
     *
     * @param identity 跨模块稳定实物身份。
     * @param after 上次处理完成的稳定位置。
     * @param limit 本页最多读取的记录数，完整结果需要继续分页。
     * @param maxDepth 召回关系遍历深度上限。
     * @param scopeCursor 工单范围的独立分页位置。
     * @param batchCursor 受影响批次的独立分页位置。
     * @return 组件去向涉及的单件、工单和产品批次范围，以及各范围的后续游标。
     */
    public Map<String, Object> whereUsed(
            Long identity,
            long after,
            int limit,
            int maxDepth,
            String scopeCursor,
            String batchCursor) {
        return affected("IDENTITY", identity, after, limit, maxDepth, scopeCursor, batchCursor);
    }

    /**
     * 召回遍历的稳定主体，保留其单件或批次范围。
     *
     * @param scope 证据范围，区分精确单件、批次与工单可能影响。
     * @param id 所属快照记录的稳定主键。
     * @param evidence 与该事实关联的原始来源凭据。
     */
    private record Subject(String scope, Long id, String evidence) {
        String key() {
            return scope + ":" + id;
        }
    }

    /**
     * 遍历时分别保留精确单件关系与工单范围的可能影响，避免扩大证据精度。
     */
    private Map<String, Object> affected(
            String scope,
            Long root,
            long after,
            int limit,
            int maxDepth,
            String scopeCursor,
            String batchCursor) {
        String tenant = tenant();
        var visible = visible();
        if (root == null
                || root <= 0
                || after < 0
                || limit < 1
                || limit > 500
                || maxDepth < 1
                || maxDepth > 10) {
            throw new ServiceException("召回范围或分页参数无效");
        }
        long afterBatch = TraceCursor.decode(batchCursor, tenant, root, scope + "_RECALL_BATCH");
        long afterScope = TraceCursor.decode(scopeCursor, tenant, root, scope + "_RECALL_SCOPE");
        var visited = new HashMap<String, String>();
        var affected = new TreeMap<Long, String>();
        var affectedBatches = new TreeMap<Long, String>();
        var impacts = new TreeMap<Long, WorkOrderTraceImpact>();
        var expandedOrders = new HashSet<Long>();
        Set<Subject> pending = new LinkedHashSet<>();
        pending.add(new Subject(scope, root, "IDENTITY"));
        boolean capacity = false;
        int depth = 0;
        while (!pending.isEmpty() && depth < maxDepth && !capacity) {
            Set<Subject> next = new LinkedHashSet<>();
            for (var subject : pending) {
                String previous = visited.get(subject.key());
                if (previous != null && rank(previous) <= rank(subject.evidence())) {
                    continue;
                }
                visited.put(subject.key(), subject.evidence());
                for (var reader : visible) {
                    var snapshot =
                            reader.recallSubject(tenant, subject.scope(), subject.id(), 10000);
                    capacity |= snapshot.truncatedByCapacity();
                    for (Long id : snapshot.identityIds()) {
                        if (scope.equals("IDENTITY") && root.equals(id)) {
                            continue;
                        }
                        affected.merge(
                                id, subject.evidence(), MaterialIdentityTraceService::stronger);
                        next.add(new Subject("IDENTITY", id, subject.evidence()));
                    }
                    for (var impact : snapshot.workOrders()) {
                        impacts.putIfAbsent(impact.consumptionId(), impact);
                        if (expandedOrders.add(impact.mesWoId())) {
                            var products =
                                    readIds(
                                            cursor ->
                                                    reader.productIdentitiesByWorkOrder(
                                                            tenant, impact.mesWoId(), cursor, 500));
                            capacity |= products.truncated();
                            for (Long id : products.rows()) {
                                if (scope.equals("IDENTITY") && root.equals(id)) {
                                    continue;
                                }
                                affected.putIfAbsent(id, "WORK_ORDER");
                                next.add(new Subject("IDENTITY", id, "WORK_ORDER"));
                            }
                            var batches =
                                    readIds(
                                            cursor ->
                                                    reader.productBatchesByWorkOrder(
                                                            tenant, impact.mesWoId(), cursor, 500));
                            capacity |= batches.truncated();
                            for (Long batch : batches.rows()) {
                                next.add(new Subject("BATCH", batch, "WORK_ORDER"));
                                if (!scope.equals("BATCH") || !batch.equals(root)) {
                                    affectedBatches.merge(
                                            batch,
                                            "WORK_ORDER",
                                            MaterialIdentityTraceService::stronger);
                                }
                            }
                        }
                        if (capacity
                                || affected.size() + affectedBatches.size() + impacts.size() > 10000
                                || next.size() + visited.size() > 10000) {
                            capacity = true;
                            break;
                        }
                    }
                    if (capacity
                            || affected.size() + affectedBatches.size() + impacts.size() > 10000
                            || next.size() + visited.size() > 10000) {
                        capacity = true;
                        break;
                    }
                }
                if (scope.equals("BATCH") && subject.scope().equals("IDENTITY")) {
                    for (var reader : visible) {
                        for (Long batch :
                                reader.batchesForIdentities(tenant, List.of(subject.id()))) {
                            if (batch != null && batch > 0) {
                                String evidence =
                                        subject.evidence().equals("WORK_ORDER")
                                                ? "WORK_ORDER"
                                                : "BATCH";
                                next.add(new Subject("BATCH", batch, evidence));
                                if (!batch.equals(root)) {
                                    affectedBatches.merge(
                                            batch,
                                            evidence,
                                            MaterialIdentityTraceService::stronger);
                                }
                            }
                        }
                    }
                }
                if (capacity) {
                    break;
                }
            }
            depth++;
            next.removeIf(
                    subject -> {
                        var prior = visited.get(subject.key());
                        return prior != null && rank(prior) <= rank(subject.evidence());
                    });
            pending = next;
        }
        var selected =
                affected.tailMap(after, false).entrySet().stream().limit(limit + 1L).toList();
        boolean more = selected.size() > limit;
        var rows =
                selected.stream()
                        .limit(limit)
                        .map(
                                entry -> {
                                    var row = new LinkedHashMap<String, Object>();
                                    row.put("identityId", entry.getKey().toString());
                                    if (!entry.getValue().equals("IDENTITY")) {
                                        row.put("scope", entry.getValue() + "_POSSIBLE");
                                    }
                                    return row;
                                })
                        .toList();
        var scopeRows =
                impacts.tailMap(afterScope, false).values().stream().limit(limit + 1L).toList();
        boolean moreScopes = scopeRows.size() > limit;
        var scopePage =
                new TracePage<>(
                        scopeRows.stream()
                                .limit(limit)
                                .map(MaterialIdentityTraceService::impactWire)
                                .toList(),
                        moreScopes
                                ? TraceCursor.encode(
                                        tenant,
                                        root,
                                        scope + "_RECALL_SCOPE",
                                        scopeRows.get(limit - 1).consumptionId())
                                : null,
                        moreScopes);
        var batchRows =
                affectedBatches.tailMap(afterBatch, false).entrySet().stream()
                        .limit(limit + 1L)
                        .toList();
        boolean moreBatches = batchRows.size() > limit;
        var batchPage =
                new TracePage<>(
                        batchRows.stream()
                                .limit(limit)
                                .map(
                                        entry ->
                                                Map.of(
                                                        "batchId",
                                                        entry.getKey().toString(),
                                                        "scope",
                                                        entry.getValue() + "_POSSIBLE"))
                                .toList(),
                        moreBatches
                                ? TraceCursor.encode(
                                        tenant,
                                        root,
                                        scope + "_RECALL_BATCH",
                                        batchRows.get(limit - 1).getKey())
                                : null,
                        moreBatches);
        boolean depthTruncated = !pending.isEmpty() && depth >= maxDepth;
        var gaps = new ArrayList<String>();
        gaps.add("仅沿实际身份和净有效消耗证据展开；未核实历史不按同码或BOM推定");
        if (affected.containsValue("BATCH")) {
            gaps.add("部分后续关联仅确认产品批次范围，已单独标记为可能受影响");
        }
        if (!impacts.isEmpty()) {
            gaps.add("存在未分配到单件的实际用料；工单范围及其后续批次列为可能受影响，不等同于已确认逐件消耗");
        }
        if (visible.size() < 2) {
            gaps.add("部分模块不在当前用户授权范围内，结果不代表完整跨模块召回");
        }
        var result = new LinkedHashMap<String, Object>();
        result.put(scope.equals("BATCH") ? "batchId" : "componentIdentityId", root.toString());
        result.put(
                "scope",
                scope.equals("BATCH") ? "BATCH_COMPONENT_RECALL" : "IDENTITY_COMPONENT_USAGE");
        result.put("rows", rows);
        result.put("hasMore", more);
        result.put("nextCursor", more ? selected.get(limit - 1).getKey().toString() : null);
        result.put("readConsistency", "LOCAL_REPEATABLE_READ_PER_SUBJECT");
        result.put("scopeImpacts", scopePage);
        result.put("batchImpacts", batchPage);
        result.put("depthReached", depth);
        result.put("truncated", more || moreScopes || moreBatches || depthTruncated || capacity);
        result.put("truncatedByDepth", depthTruncated);
        result.put("truncatedByCapacity", capacity);
        if (scope.equals("BATCH")) {
            var facts = batch(root, null, null, null, limit);
            result.put("parts", facts.get("parts"));
            if (Boolean.TRUE.equals(facts.get("truncated"))) {
                result.put("truncated", true);
            }
            @SuppressWarnings("unchecked")
            var factGaps = (List<String>) facts.get("evidenceGaps");
            gaps.addAll(factGaps);
        }
        result.put("evidenceGaps", gaps);
        return result;
    }

    private static int rank(String evidence) {
        return switch (evidence) {
            case "IDENTITY" -> 0;
            case "BATCH" -> 1;
            default -> 2;
        };
    }

    private static String stronger(String one, String two) {
        return rank(one) <= rank(two) ? one : two;
    }

    /**
     * 一次身份分页读取结果及其后续位置。
     *
     * @param rows 当前分页内的事实集合。
     * @param truncated 结果是否因深度或容量限制而不完整。
     */
    private record IdRead(List<Long> rows, boolean truncated) {}

    private IdRead readIds(java.util.function.LongFunction<TracePage<Long>> read) {
        var result = new ArrayList<Long>();
        long position = 0;
        boolean more;
        do {
            var page = read.apply(position);
            more = page.hasMore();
            long before = position;
            for (Long id : page.rows()) {
                if (id == null || id <= position) {
                    throw new ServiceException("召回结果身份或游标未前进");
                }
                position = id;
                result.add(id);
            }
            if (more && position <= before) {
                throw new ServiceException("召回游标未前进");
            }
            if (result.size() >= 10000 && more) {
                return new IdRead(result, true);
            }
        } while (more);
        return new IdRead(result, false);
    }

    private static Map<String, Object> impactWire(WorkOrderTraceImpact impact) {
        var result = new LinkedHashMap<String, Object>();
        result.put("scope", "WORK_ORDER");
        result.put("module", impact.module());
        result.put("consumptionId", impact.consumptionId().toString());
        result.put("mesWoId", impact.mesWoId().toString());
        result.put(
                "componentMaterialId",
                impact.componentMaterialId() == null
                        ? null
                        : impact.componentMaterialId().toString());
        result.put("consumedQuantity", impact.consumedQuantity().toPlainString());
        result.put("allocatedQuantity", impact.allocatedQuantity().toPlainString());
        result.put("unallocatedQuantity", impact.unallocatedQuantity().toPlainString());
        return result;
    }

    private IdentityTracePart restrict(IdentityTracePart part) {
        var sections = new LinkedHashMap<>(part.sections());
        var gaps = new ArrayList<>(part.evidenceGaps());
        if ("MES".equals(part.module())) {
            if (!StpUtil.hasPermission("mes:flash:testrecord:query")) {
                sections.remove("TEST");
                gaps.add("当前账号没有单件测试查询权限");
            }
            if (!StpUtil.hasPermission("mes:qc:ipqc:query")) {
                sections.remove("IPQC_WORK_ORDER");
                gaps.add("当前账号没有过程检验查询权限");
            }
        }
        if ("ERP".equals(part.module()) && !costAllowed()) {
            redactCosts(sections);
        }
        return new IdentityTracePart(
                part.module(),
                part.identityId(),
                part.localSnId(),
                part.canonicalSnCode(),
                part.scope(),
                sections,
                gaps,
                part.localSnCode());
    }

    private boolean costAllowed() {
        return StpUtil.hasPermission("wm:stock:list")
                || StpUtil.hasPermission("wm:stock:query")
                || StpUtil.hasPermission("wm:stocktx:list");
    }

    private static void redactCosts(Map<String, TracePage<Map<String, Object>>> sections) {
        Set<String> hidden = Set.of("unitcost", "totalcost", "txamount", "amount", "price");
        sections.replaceAll(
                (section, page) ->
                        new TracePage<>(
                                page.rows().stream()
                                        .map(
                                                row -> {
                                                    var copy = new LinkedHashMap<>(row);
                                                    copy.keySet()
                                                            .removeIf(
                                                                    key ->
                                                                            hidden.contains(
                                                                                    key.replace(
                                                                                                    "_",
                                                                                                    "")
                                                                                            .toLowerCase(
                                                                                                    Locale
                                                                                                            .ROOT)));
                                                    return Collections
                                                            .<String, Object>unmodifiableMap(copy);
                                                })
                                        .toList(),
                                page.nextCursor(),
                                page.hasMore()));
    }

    private List<IdentityTraceReadPort> visible() {
        StpUtil.checkLogin();
        var allowed = new HashSet<String>();
        if (StpUtil.hasPermission("wm:trace:query")) {
            allowed.add("ERP");
        }
        if (StpUtil.hasPermission("mes:pro:trace:query")) {
            allowed.add("MES");
        }
        if (allowed.isEmpty()) {
            StpUtil.checkPermission("wm:trace:query");
        }
        return readers.stream().filter(reader -> allowed.contains(reader.sourceModule())).toList();
    }

    private String tenant() {
        String tenant = TenantHelper.getTenantId();
        if (!TenantHelper.isEnable() || tenant == null || !tenant.matches("[0-9]{6,20}")) {
            throw new ServiceException("追溯需要可信租户上下文");
        }
        return tenant;
    }
}
