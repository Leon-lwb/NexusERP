package com.nexus.erp.prd.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.erp.wm.trace.ErpTraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * 采纳独立审批的历史策略包；审批证据和原档策略必须存在，不从客户端或现行策略补造。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class PrdWorkOrderPolicyAdoptionService {
    private final JdbcTemplate jdbc;
    private final ErpTraceContext context;
    private final ObjectMapper json;
    private final PrdWorkOrderTrackingPolicyService bank;
    private final PrdWorkOrderPolicyAdoptionAuthorizer authorizer;

    /**
     * 授权记录仅由经批准的离线导入操作流程写入。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public String adoptApproved(Long adoptionId, String commandId, long expectedVersion) {
        String tenant = context.tenant();
        context.requireWrite(tenant);
        Long actor = authorizer.requireApprover();
        require(
                adoptionId != null
                        && adoptionId > 0
                        && commandId != null
                        && !commandId.isBlank()
                        && commandId.length() <= 128
                        && expectedVersion > 0,
                "采纳命令身份或版本无效");
        var observed = load(tenant, adoptionId, false);
        var work =
                jdbc.queryForList(
                        "SELECT * FROM prd_work_order WHERE tenant_id=? AND wo_id=? FOR UPDATE",
                        tenant,
                        observed.woId());
        require(work.size() == 1 && tenant.equals(work.get(0).get("tenant_id")), "采纳原工单不存在或跨租户");
        var approval = load(tenant, adoptionId, true);
        require(
                approval.woId().equals(observed.woId()) && commandId.equals(approval.command()),
                "采纳命令与原审批身份不符");
        require(actor != null && actor.equals(approval.approvedBy()), "只有原独立审批人可应用本人的策略采纳审批");
        SortedMap<Long, String> policies = validate(json, approval);
        if ("APPLIED".equals(approval.status())) {
            require(
                    approval.version() == expectedVersion + 1
                            && approval.appliedAt() != null
                            && approval.resultHash() != null,
                    "采纳重放不是原申请版本");
            return bank.verifyAdoptionResult(
                    tenant,
                    approval.woId(),
                    adoptionId,
                    approval.requestHash(),
                    approval.resultHash(),
                    approval.appliedAt());
        }
        Instant now = bank.databaseNow();
        require(
                "APPROVED".equals(approval.status())
                        && approval.version() == expectedVersion
                        && approval.appliedAt() == null
                        && approval.resultHash() == null
                        && !now.isBefore(approval.approvedAt())
                        && now.isBefore(approval.expiresAt()),
                "采纳尚未批准、已过期或CAS版本已改变");
        require(
                Set.of("RELEASED", "IN_PROGRESS")
                                .contains(String.valueOf(work.get(0).get("wo_status")))
                        && "0".equals(work.get(0).get("del_flag")),
                "仅明确在途WO可显式采纳未来策略");
        var current = bank.previewAdoption(approval.woId());
        require(current.sourceHash().equals(approval.woHash()), "采纳审批后的真实WO/BOM/需求来源已改变");
        var audit = audit(approval, current.sourceJson());
        String result =
                bank.persistAdopted(
                        tenant,
                        approval.woId(),
                        current,
                        policies,
                        audit,
                        approval.approvedBy(),
                        adoptionId,
                        now);
        require(
                jdbc.update(
                                "UPDATE wm_work_order_policy_adoption SET"
                                    + " status='APPLIED',version=version+1,applied_at=?,snapshot_hash=?"
                                    + " WHERE tenant_id=? AND adoption_id=? AND status='APPROVED'"
                                    + " AND version=?",
                                Timestamp.from(now),
                                result,
                                tenant,
                                adoptionId,
                                expectedVersion)
                        == 1,
                "采纳审批CAS已改变，全部策略写入回滚");
        return result;
    }

    /**
     * 独立审批的原始工件与执行条件。
     *
     * @param id 所属快照记录的稳定主键。
     * @param tenant 当前认证租户。
     * @param command 本次业务命令号，重试时保持原值。
     * @param woId ERP 生产工单主键。
     * @param sourceHash 原业务来源快照摘要。
     * @param approvalJson 不可变的原审批工件。
     * @param approvalHash 原审批工件摘要。
     * @param requestHash 原请求摘要。
     * @param proposedBy 原策略采纳申请人标识。
     * @param approvedBy 独立审批人标识。
     * @param approvedAt 原审批发生时间。
     * @param expiresAt 原来源或授权的到期时间。
     * @param status 解析或业务处理的明确状态。
     * @param version 当前事实或规则版本。
     * @param appliedAt 目标业务成功应用时间。
     * @param resultHash 目标不可变结果的完整摘要。
     */
    private record Approval(
            Long id,
            String tenant,
            String command,
            Long woId,
            String woHash,
            String sourceJson,
            String sourceHash,
            String approvalJson,
            String approvalHash,
            String requestHash,
            Long proposedBy,
            Long approvedBy,
            Instant approvedAt,
            Instant expiresAt,
            String status,
            long version,
            Instant appliedAt,
            String resultHash) {}

    private Approval load(String tenant, Long id, boolean lock) {
        var rows =
                jdbc.query(
                        "SELECT * FROM wm_work_order_policy_adoption WHERE tenant_id=? AND"
                                + " adoption_id=?"
                                + (lock ? " FOR UPDATE" : ""),
                        (rs, i) ->
                                new Approval(
                                        rs.getLong("adoption_id"),
                                        rs.getString("tenant_id"),
                                        rs.getString("command_id"),
                                        rs.getLong("erp_wo_id"),
                                        rs.getString("expected_wo_hash"),
                                        rs.getString("source_json"),
                                        rs.getString("source_hash"),
                                        rs.getString("approval_json"),
                                        rs.getString("approval_hash"),
                                        rs.getString("request_hash"),
                                        rs.getLong("proposed_by"),
                                        rs.getLong("approved_by"),
                                        rs.getTimestamp("approved_at").toInstant(),
                                        rs.getTimestamp("expires_at").toInstant(),
                                        rs.getString("status"),
                                        rs.getLong("version"),
                                        rs.getTimestamp("applied_at") == null
                                                ? null
                                                : rs.getTimestamp("applied_at").toInstant(),
                                        rs.getString("snapshot_hash")),
                        tenant,
                        id);
        require(rows.size() == 1 && tenant.equals(rows.get(0).tenant()), "缺少同租户唯一已审批策略采纳凭据");
        return rows.get(0);
    }

    private static String requestHash(Approval a) {
        return TraceHashes.fingerprint(
                "WO_POLICY_ADOPTION_V1",
                a.tenant(),
                a.id(),
                a.command(),
                a.woId(),
                a.woHash(),
                a.sourceHash(),
                a.proposedBy(),
                a.approvedBy(),
                a.approvedAt().toEpochMilli(),
                a.expiresAt().toEpochMilli());
    }

    private static SortedMap<Long, String> validate(ObjectMapper json, Approval a) {
        require(
                a.tenant() != null
                        && a.tenant().matches("[0-9]{6,20}")
                        && a.id() > 0
                        && a.woId() > 0
                        && a.proposedBy() > 0
                        && a.approvedBy() > 0
                        && !a.proposedBy().equals(a.approvedBy())
                        && hash(a.woHash())
                        && a.expiresAt().isAfter(a.approvedAt())
                        && a.sourceHash().equals(TraceHashes.sha256(a.sourceJson()))
                        && a.approvalHash().equals(TraceHashes.sha256(a.approvalJson()))
                        && a.requestHash().equals(requestHash(a)),
                "原始策略、独立审批或申请意图摘要不符");
        var decision = tree(json, a.approvalJson());
        require(
                "APPROVED".equals(text(decision, "decision"))
                        && a.requestHash().equals(text(decision, "requestHash"))
                        && a.approvedBy().toString().equals(text(decision, "approvedBy"))
                        && a.approvedAt().equals(instant(text(decision, "approvedAt")))
                        && !text(decision, "reference").isBlank(),
                "审批未明确同意该份来源策略申请");
        var source = tree(json, a.sourceJson());
        require(
                source.path("schemaVersion").asInt() == 1
                        && a.tenant().equals(text(source, "tenantId"))
                        && a.woId().toString().equals(text(source, "erpWoId"))
                        && source.path("materials").isArray()
                        && !source.path("materials").isEmpty(),
                "原始策略证据租户、工单或完整物料集合缺失");
        var policies = new TreeMap<Long, String>();
        for (var item : source.path("materials")) {
            Long material = positive(text(item, "materialId"));
            var policy = item.path("policy");
            require(
                    Set.of(
                                            "ORIGINAL_RELEASE_ARCHIVE",
                                            "IMMUTABLE_SOURCE_SNAPSHOT",
                                            "SIGNED_DHR_POLICY_SNAPSHOT")
                                    .contains(text(item, "origin"))
                            && !text(item, "reference").isBlank()
                            && hash(text(item, "documentHash"))
                            && !instant(text(item, "capturedAt")).isAfter(a.approvedAt()),
                    "必须有可核实的原始策略归档凭据，不能采用当前主数据或审批后的未来证据");
            require(
                    policy.isObject()
                            && policy.size() == 5
                            && policy.path("batchManaged").isBoolean()
                            && policy.path("serialManaged").isBoolean()
                            && policy.path("allowMixedLot").isBoolean()
                            && positive(text(policy, "version")) > 0
                            && Set.of("AUDIT", "STRICT").contains(text(policy, "stage")),
                    "原始TrackingPolicy必须完整，不能用默认值推定历史开关");
            String encoded = encode(json, policy);
            require(policies.put(material, encoded) == null, "原始策略物料重复");
        }
        return policies;
    }

    private static Map<String, Object> audit(Approval a, String woJson) {
        var result = new LinkedHashMap<String, Object>();
        result.put("adoptionId", a.id().toString());
        result.put("tenantId", a.tenant());
        result.put("erpWoId", a.woId().toString());
        result.put("commandId", a.command());
        result.put("expectedWoHash", a.woHash());
        result.put("woSourceJson", woJson);
        result.put("sourceJson", a.sourceJson());
        result.put("sourceHash", a.sourceHash());
        result.put("approvalJson", a.approvalJson());
        result.put("approvalHash", a.approvalHash());
        result.put("requestHash", a.requestHash());
        result.put("proposedBy", a.proposedBy().toString());
        result.put("approvedBy", a.approvedBy().toString());
        result.put("approvedAt", a.approvedAt().toString());
        result.put("expiresAt", a.expiresAt().toString());
        return result;
    }

    static void verifyFrozen(
            ObjectMapper json,
            String tenant,
            Long wo,
            Instant frozen,
            Long actor,
            String evidenceRef,
            JsonNode source,
            SortedMap<Long, TrackingPolicy> bank) {
        var node = source.path("adoption");
        var approval =
                new Approval(
                        positive(text(node, "adoptionId")),
                        text(node, "tenantId"),
                        text(node, "commandId"),
                        positive(text(node, "erpWoId")),
                        text(node, "expectedWoHash"),
                        text(node, "sourceJson"),
                        text(node, "sourceHash"),
                        text(node, "approvalJson"),
                        text(node, "approvalHash"),
                        text(node, "requestHash"),
                        positive(text(node, "proposedBy")),
                        positive(text(node, "approvedBy")),
                        instant(text(node, "approvedAt")),
                        instant(text(node, "expiresAt")),
                        "APPLIED",
                        2,
                        frozen,
                        null);
        var policies = validate(json, approval);
        var parent = source.deepCopy();
        ((ObjectNode) parent).remove("adoption");
        require(
                tenant.equals(approval.tenant())
                        && wo.equals(approval.woId())
                        && Objects.equals(actor, approval.approvedBy())
                        && evidenceRef.equals("SOURCE_POLICY_ADOPTION/" + approval.id())
                        && !frozen.isBefore(approval.approvedAt())
                        && frozen.isBefore(approval.expiresAt())
                        && approval.woHash().equals(TraceHashes.sha256(text(node, "woSourceJson")))
                        && parent.equals(tree(json, text(node, "woSourceJson")))
                        && bank.keySet().equals(policies.keySet()),
                "已采纳策略的审批、未来生效边界或WO来源证据不符");
        for (var entry : policies.entrySet()) {
            try {
                require(
                        bank.get(entry.getKey())
                                .equals(json.readValue(entry.getValue(), TrackingPolicy.class)),
                        "已采纳策略不等于原始批准策略");
            } catch (ServiceException failure) {
                throw failure;
            } catch (Exception invalid) {
                throw new ServiceException("原策略JSON证据无效");
            }
        }
    }

    private static String text(JsonNode node, String key) {
        var value = node.path(key);
        require(
                !value.isNull() && !value.isMissingNode() && value.isValueNode(),
                "采纳证据缺少字段:" + key);
        return value.asText();
    }

    private static Long positive(String text) {
        try {
            long value = Long.parseLong(text);
            require(text.matches("[1-9][0-9]*") && value > 0, "采纳来源ID/版本无效");
            return value;
        } catch (NumberFormatException invalid) {
            throw new ServiceException("采纳来源ID/版本无效");
        }
    }

    private static Instant instant(String text) {
        try {
            return Instant.parse(text);
        } catch (Exception invalid) {
            throw new ServiceException("采纳原始证据或审批时点无效");
        }
    }

    private static boolean hash(String text) {
        return text != null && text.matches("[0-9a-f]{64}");
    }

    private static JsonNode tree(ObjectMapper json, String value) {
        try {
            return json.readTree(value);
        } catch (Exception invalid) {
            throw new ServiceException("采纳JSON证据无效");
        }
    }

    private static String encode(ObjectMapper json, Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception invalid) {
            throw new ServiceException("采纳JSON冻结失败");
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new ServiceException(message);
        }
    }
}
