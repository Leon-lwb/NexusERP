package com.nexus.erp.wm.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.CodeResolverPort;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Date;

/**
 * 物料标记的只读解析内核；正式码和有上下文别名同等参与歧义检查，不靠前缀选表。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class ErpCodeResolverService implements CodeResolverPort {
    private final JdbcTemplate jdbc;
    private final ErpTraceContext context;

    @Override
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ)
    public CodeResolution resolve(
            String tenant, CodeResolveRequest request, Set<CodeObjectType> allowedTypes) {
        context.requireTenant(tenant);
        if (request == null
                || request.rawCode() == null
                || request.rawCode().isBlank()
                || request.rawCode().length() > 512) {
            return CodeResolution.invalid("扫码内容为空或过长");
        }
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return CodeResolution.invalid("当前身份没有该类对象的查询权限");
        }
        if (request.supplierId() != null && request.customerId() != null) {
            return CodeResolution.invalid("供应商和客户别名上下文不能同时指定");
        }
        String raw = request.rawCode().trim();
        var gs1 = Gs1Identity.parse(raw);
        boolean marked = raw.startsWith("(") || raw.startsWith("]C1") || raw.startsWith("]d2");
        if (marked && gs1.isEmpty()) {
            return CodeResolution.invalid("复合条码格式无效或包含未支持的身份字段");
        }
        try {
            if (marked) {
                return interpret(tenant, request, allowedTypes, raw, gs1);
            }
            // 没有显式GS1标记时，完整原码仍可能是正式SN；两种有效解释必须共同参与歧义判断。
            CodeResolution literal =
                    interpret(tenant, request, allowedTypes, raw, Optional.empty());
            if (gs1.isEmpty()) {
                return literal;
            }
            CodeResolution inferred = interpret(tenant, request, allowedTypes, raw, gs1);
            LinkedHashMap<String, CodeCandidate> merged = new LinkedHashMap<>();
            for (CodeCandidate candidate : literal.candidates()) {
                add(merged, candidate);
            }
            for (CodeCandidate candidate : inferred.candidates()) {
                add(merged, candidate);
            }
            if (merged.size() > 1) {
                return CodeResolution.ambiguous(new ArrayList<>(merged.values()));
            }
            if (merged.size() == 1) {
                return CodeResolution.resolved(merged.values().iterator().next());
            }
            if (literal.status() == ResolveStatus.INVALID) {
                return literal;
            }
            return inferred.status() == ResolveStatus.INVALID ? inferred : literal;
        } catch (ResolutionRejected rejected) {
            return CodeResolution.invalid(rejected.getMessage());
        }
    }

    private CodeResolution interpret(
            String tenant,
            CodeResolveRequest request,
            Set<CodeObjectType> allowedTypes,
            String raw,
            Optional<Gs1Identity> gs1) {
        Long expectedMaterial = request.materialId();
        Set<Long> gtinMaterials = new HashSet<>();
        if (gs1.isPresent() && gs1.get().gtin() != null) {
            Set<Long> mapped = new HashSet<>();
            for (var row : barcodes(tenant, gs1.get().gtin())) {
                if (!validBarcode(row)) {
                    continue;
                }
                // GTIN是物料级断言，不能把SN/批次主对象的material上下文当作另一份物料登记。
                CodeCandidate material =
                        carrierCandidate(tenant, row, Set.of(CodeObjectType.MATERIAL));
                if (material != null && material.materialId() != null) {
                    mapped.add(material.materialId());
                }
            }
            if (mapped.isEmpty()) {
                return CodeResolution.invalid("GTIN尚无有效物料登记，不能忽略复合标签的物料断言");
            }
            gtinMaterials.addAll(mapped);
            if (expectedMaterial != null && !mapped.contains(expectedMaterial)) {
                return CodeResolution.invalid("GTIN与当前物料不一致");
            }
            if (expectedMaterial == null && mapped.size() == 1) {
                expectedMaterial = mapped.iterator().next();
            }
        }
        String lookup =
                gs1.map(
                                g ->
                                        g.serial() != null
                                                ? g.serial()
                                                : g.batch() != null ? g.batch() : g.gtin())
                        .orElse(raw);
        LinkedHashMap<String, CodeCandidate> candidates = new LinkedHashMap<>();
        boolean contextMismatch = false, invalidCarrier = false;
        if (allowedTypes.contains(CodeObjectType.SN)
                && (gs1.isEmpty() || gs1.get().serial() != null)) {
            List<Map<String, Object>> serials =
                    new ArrayList<>(
                            bounded(
                                    jdbc.queryForList(
                                            "SELECT * FROM wm_sn WHERE tenant_id=? AND sn_code=?"
                                                    + " AND del_flag='0' LIMIT 101",
                                            tenant,
                                            lookup)));
            StringBuilder sql =
                    new StringBuilder(
                            "SELECT DISTINCT s.* FROM wm_sn_alias a JOIN wm_sn s ON"
                                    + " s.tenant_id=a.tenant_id AND s.sn_id=a.identity_id WHERE"
                                    + " a.tenant_id=? AND a.alias_code=? AND s.del_flag='0'");
            List<Object> args = new ArrayList<>(List.of(tenant, lookup));
            if (request.supplierId() != null) {
                sql.append(" AND a.source_type='SUPPLIER' AND a.source_id=?");
                args.add(request.supplierId());
            }
            if (request.customerId() != null) {
                sql.append(" AND a.source_type='CUSTOMER' AND a.source_id=?");
                args.add(request.customerId());
            }
            if (expectedMaterial != null) {
                sql.append(" AND a.material_id=?");
                args.add(expectedMaterial);
            }
            sql.append(" LIMIT 101");
            serials.addAll(bounded(optionalRows(sql.toString(), args.toArray())));
            for (var row : serials) {
                if (!matchesMaterial(row, expectedMaterial)) {
                    contextMismatch = true;
                    continue;
                }
                if (gs1.isPresent()
                        && gs1.get().batch() != null
                        && !gs1.get().batch().equals(text(row, "batch_code"))) {
                    contextMismatch = true;
                    continue;
                }
                add(candidates, serialCandidate(tenant, row));
            }
        }
        if (gs1.isEmpty()) {
            for (var barcode : barcodes(tenant, raw)) {
                if (!validBarcode(barcode)) {
                    invalidCarrier = true;
                    continue;
                }
                if (expectedMaterial != null
                        && barcode.get("material_id") != null
                        && !expectedMaterial.equals(id(barcode, "material_id"))) {
                    contextMismatch = true;
                    continue;
                }
                CodeCandidate carrier = carrierCandidate(tenant, barcode, allowedTypes);
                if (carrier != null) {
                    add(candidates, carrier);
                }
            }
        }
        if (allowedTypes.contains(CodeObjectType.BATCH)
                && (gs1.isEmpty() || gs1.get().serial() == null && gs1.get().batch() != null)) {
            String sql =
                    "SELECT * FROM wm_batch WHERE tenant_id=? AND del_flag='0' AND batch_code=?";
            List<Object> args = new ArrayList<>(List.of(tenant, lookup));
            if (request.supplierId() != null) {
                sql =
                        "SELECT * FROM wm_batch WHERE tenant_id=? AND del_flag='0' AND"
                                + " (batch_code=? OR (vendor_batch_code=? AND vendor_id=?))";
                args.add(lookup);
                args.add(request.supplierId());
            }
            for (var row : bounded(jdbc.queryForList(sql + " LIMIT 101", args.toArray()))) {
                if (!matchesMaterial(row, expectedMaterial)) {
                    contextMismatch = true;
                    continue;
                }
                add(candidates, batchCandidate(tenant, row));
            }
        }
        if (allowedTypes.contains(CodeObjectType.MATERIAL)
                && (gs1.isEmpty() || gs1.get().serial() == null && gs1.get().batch() == null)) {
            for (var row :
                    bounded(
                            jdbc.queryForList(
                                    "SELECT * FROM bd_material WHERE tenant_id=? AND"
                                            + " material_code=? AND del_flag='0' LIMIT 101",
                                    tenant,
                                    lookup))) {
                if (!matchesMaterial(row, expectedMaterial)) {
                    contextMismatch = true;
                    continue;
                }
                add(candidates, materialCandidate(row));
            }
            if (gs1.isPresent() && expectedMaterial != null) {
                for (var row : materials(tenant, expectedMaterial)) {
                    add(candidates, materialCandidate(row));
                }
            }
        }
        Long materialAssertion = expectedMaterial;
        if (materialAssertion != null) {
            boolean removed =
                    candidates
                            .entrySet()
                            .removeIf(
                                    entry ->
                                            !materialAssertion.equals(
                                                    entry.getValue().materialId()));
            contextMismatch |= removed;
        }
        if (!gtinMaterials.isEmpty()) {
            boolean removed =
                    candidates
                            .entrySet()
                            .removeIf(
                                    entry ->
                                            !gtinMaterials.contains(entry.getValue().materialId()));
            contextMismatch |= removed;
        }
        if (invalidCarrier) {
            throw new ResolutionRejected("条码已失效或其身份上下文不一致，请核对原标签");
        }
        if (candidates.isEmpty()) {
            return contextMismatch
                    ? CodeResolution.invalid("扫码身份与当前物料或批次不一致")
                    : CodeResolution.missing("当前授权范围未找到对应身份");
        }
        if (candidates.size() > 1) {
            return CodeResolution.ambiguous(new ArrayList<>(candidates.values()));
        }
        return CodeResolution.resolved(candidates.values().iterator().next());
    }

    private CodeCandidate carrierCandidate(
            String tenant, Map<String, Object> barcode, Set<CodeObjectType> allowed) {
        String declared = text(barcode, "primary_object_type");
        String selected =
                declared == null
                        ? (barcode.get("sn_id") != null
                                ? "SN"
                                : barcode.get("batch_id") != null
                                        ? "BATCH"
                                        : barcode.get("material_id") != null ? "MATERIAL" : null)
                        : declared;
        if (selected == null) {
            return null;
        }
        if (!Set.of("SN", "BATCH", "MATERIAL", "DOCUMENT").contains(selected)) {
            throw new ResolutionRejected("条码登记的主对象类型无效");
        }
        if ("DOCUMENT".equals(selected)) {
            if (barcode.get("sn_id") != null
                    || barcode.get("batch_id") != null
                    || barcode.get("material_id") != null
                    || barcode.get("stock_id") != null) {
                throw new ResolutionRejected("单据条码包含未经核实的实物主体字段");
            }
            return null;
        }
        CodeObjectType type = CodeObjectType.valueOf(selected);
        if (!allowed.contains(type)) {
            return null;
        }
        CodeCandidate candidate;
        if (type == CodeObjectType.SN) {
            var sn =
                    carrierRow(
                            "SELECT * FROM wm_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0'",
                            tenant,
                            barcode.get("sn_id"));
            if (!sameIfPresent(barcode, "material_id", sn, "material_id")
                    || !sameIfPresent(barcode, "batch_id", sn, "batch_id")) {
                throw new ResolutionRejected("条码与正式SN上下文不一致");
            }
            candidate = serialCandidate(tenant, sn);
        } else if (type == CodeObjectType.BATCH) {
            var batch =
                    carrierRow(
                            "SELECT * FROM wm_batch WHERE tenant_id=? AND batch_id=? AND"
                                    + " del_flag='0'",
                            tenant,
                            barcode.get("batch_id"));
            if (!sameIfPresent(barcode, "material_id", batch, "material_id")) {
                throw new ResolutionRejected("条码与批次物料上下文不一致");
            }
            candidate = batchCandidate(tenant, batch);
        } else {
            var material =
                    carrierRow(
                            "SELECT * FROM bd_material WHERE tenant_id=? AND material_id=? AND"
                                    + " del_flag='0'",
                            tenant,
                            barcode.get("material_id"));
            candidate = materialCandidate(material);
        }
        if (declared != null) {
            if (barcode.get("sn_id") != null) {
                var sn =
                        carrierRow(
                                "SELECT * FROM wm_sn WHERE tenant_id=? AND sn_id=? AND"
                                        + " del_flag='0'",
                                tenant,
                                barcode.get("sn_id"));
                if (!Objects.equals(candidate.materialId(), id(sn, "material_id"))
                        || !sameIfPresent(barcode, "batch_id", sn, "batch_id")
                        || barcode.get("batch_code") != null
                                && !Objects.equals(
                                        text(barcode, "batch_code"), text(sn, "batch_code"))) {
                    throw new ResolutionRejected("条码的SN上下文断言不一致");
                }
            }
            if (barcode.get("batch_id") != null) {
                var batch =
                        carrierRow(
                                "SELECT * FROM wm_batch WHERE tenant_id=? AND batch_id=? AND"
                                        + " del_flag='0'",
                                tenant,
                                barcode.get("batch_id"));
                if (!Objects.equals(candidate.materialId(), id(batch, "material_id"))
                        || barcode.get("batch_code") != null
                                && !Objects.equals(
                                        text(barcode, "batch_code"), text(batch, "batch_code"))) {
                    throw new ResolutionRejected("条码的批次上下文断言不一致");
                }
            }
        }
        return candidate;
    }

    private Map<String, Object> carrierRow(String sql, String tenant, Object id) {
        if (id == null) {
            throw new ResolutionRejected("条码主对象缺少真实ID");
        }
        var rows = jdbc.queryForList(sql, tenant, id);
        if (rows.size() != 1) {
            throw new ResolutionRejected("条码主对象不存在、已删除或跨租户");
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> materials(String tenant, Long id) {
        return jdbc.queryForList(
                "SELECT * FROM bd_material WHERE tenant_id=? AND material_id=? AND del_flag='0'",
                tenant,
                id);
    }

    private List<Map<String, Object>> barcodes(String tenant, String code) {
        return bounded(
                jdbc.queryForList(
                        "SELECT * FROM wm_barcode WHERE tenant_id=? AND barcode_code=? AND"
                                + " del_flag='0' LIMIT 101",
                        tenant,
                        code));
    }

    private static List<Map<String, Object>> bounded(List<Map<String, Object>> rows) {
        // 101是溢出探针，不是完整候选集；权限过滤后也不能把不完整集合当作唯一结果。
        if (rows.size() >= 101) {
            throw new ResolutionRejected("候选过多，请补充来源主体、物料或单据上下文后重新扫码");
        }
        return rows;
    }

    /**
     * 解析过程中的内部拒绝信号，调用方仍按固定解析状态处理。
     */
    private static final class ResolutionRejected extends RuntimeException {
        private ResolutionRejected(String message) {
            super(message);
        }
    }

    private List<Map<String, Object>> optionalRows(String sql, Object... args) {
        try {
            return jdbc.queryForList(sql, args);
        } catch (BadSqlGrammarException e) {
            var cause = e.getSQLException();
            if (cause != null
                    && (cause.getErrorCode() == 1146
                            || cause.getErrorCode() == 42102
                            || "42S02".equals(cause.getSQLState()))) {
                return List.of();
            }
            throw e;
        }
    }

    private CodeCandidate serialCandidate(String tenant, Map<String, Object> row) {
        var material = materials(tenant, id(row, "material_id"));
        TrackingPolicy policy =
                material.size() == 1 ? ErpMaterialPolicyService.fromRow(material.get(0)) : null;
        return new CodeCandidate(
                CodeObjectType.SN,
                id(row, "sn_id"),
                null,
                text(row, "sn_code"),
                id(row, "material_id"),
                text(row, "material_code"),
                id(row, "batch_id"),
                text(row, "batch_code"),
                policy);
    }

    private CodeCandidate batchCandidate(String tenant, Map<String, Object> row) {
        var material = materials(tenant, id(row, "material_id"));
        return new CodeCandidate(
                CodeObjectType.BATCH,
                null,
                null,
                null,
                id(row, "material_id"),
                text(row, "material_code"),
                id(row, "batch_id"),
                text(row, "batch_code"),
                material.size() == 1 ? ErpMaterialPolicyService.fromRow(material.get(0)) : null);
    }

    private CodeCandidate materialCandidate(Map<String, Object> row) {
        return new CodeCandidate(
                CodeObjectType.MATERIAL,
                null,
                null,
                null,
                id(row, "material_id"),
                text(row, "material_code"),
                null,
                null,
                ErpMaterialPolicyService.fromRow(row));
    }

    private static boolean validBarcode(Map<String, Object> row) {
        if (!"ACTIVE".equals(text(row, "status"))) {
            return false;
        }
        Object expires = row.get("expire_time");
        return !(expires instanceof Date date) || !date.before(new Date());
    }

    private static boolean matchesMaterial(Map<String, Object> row, Long expected) {
        return expected == null || expected.equals(id(row, "material_id"));
    }

    private static boolean sameIfPresent(
            Map<String, Object> a, String ak, Map<String, Object> b, String bk) {
        return a.get(ak) == null || Objects.equals(id(a, ak), id(b, bk));
    }

    private static Long id(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : ((Number) v).longValue();
    }

    private static String text(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }

    private static void add(Map<String, CodeCandidate> results, CodeCandidate c) {
        results.putIfAbsent(c.objectType() + ":" + c.objectId(), c);
    }
}
