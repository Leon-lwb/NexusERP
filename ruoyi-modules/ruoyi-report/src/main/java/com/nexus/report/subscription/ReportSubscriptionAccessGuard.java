package com.nexus.report.subscription;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.runtime.ReportRuntimeService.SourceConstraint;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/** 订阅来源约束同时保护交付中心及通用导出中心，避免下线后绕过领取入口。 */
public final class ReportSubscriptionAccessGuard {
    /** 保存当前resources状态或配置。 */
    private final ReportResourceStore resources;

    /** 保存当前jdbc状态或配置。 */
    private final JdbcTemplate jdbc;

    /** 保存当前authorization状态或配置。 */
    private final ReportAuthorizationPort authorization;

    /** 保存当前json状态或配置。 */
    private final ReportCompiler json;

    /** 固定报表绑定与当前权限校验服务。 */
    private final com.nexus.report.service.ReportDefinitionService definitions;

    /**
     * 依赖身份端口和主库元数据，不依赖登录ThreadLocal或运行服务。
     *
     * @param resources 当前契约的resources配置或状态
     * @param jdbc 当前契约的jdbc配置或状态
     * @param authorization 当前契约的authorization配置或状态
     * @param json 固定版本的契约正文
     * @param definitions 当前契约的definitions配置或状态
     */
    public ReportSubscriptionAccessGuard(
            ReportResourceStore resources,
            JdbcTemplate jdbc,
            ReportAuthorizationPort authorization,
            ReportCompiler json,
            com.nexus.report.service.ReportDefinitionService definitions) {
        this.resources = resources;
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.json = json;
        this.definitions = definitions;
    }

    /**
     * 每次运行访问都校验当前订阅状态、管理主体及接收人名册。
     *
     * @param actor 执行用户
     * @param source 当前契约的source配置或状态
     */
    public void check(ReportActor actor, SourceConstraint source) {
        if (source == null || !"SUBSCRIPTION".equals(source.type())) throw ReportFault.denied();
        var draft =
                resources
                        .read("SUBSCRIPTION", actor.tenantId(), source.resourceId())
                        .orElseThrow(ReportFault::denied);
        if (!draft.status().equals("PUBLISHED") || draft.publishedVersion() == null)
            throw new ReportFault("SUBSCRIPTION_OFFLINE", "订阅已停用", 409);
        var owner = authorization.resolve(actor.tenantId(), draft.owner());
        if (owner == null || !owner.has("report:subscription:publish")) throw ReportFault.denied();
        resources.access("SUBSCRIPTION", owner, draft.id(), "PUBLISH");
        var fixed =
                resources
                        .version("SUBSCRIPTION", actor.tenantId(), source.versionId())
                        .orElseThrow(ReportFault::denied);
        if (!fixed.resourceId().equals(draft.id())) throw ReportFault.denied();
        var fixedModel = json.read(fixed.json(), SubscriptionDefinition.class);
        var ownerBinding = definitions.fixedBinding(owner, fixedModel.reportVersionId());
        definitions.requireDatasetExport(owner, ownerBinding);
        var dependency =
                json.read(fixed.dependency(), com.fasterxml.jackson.databind.JsonNode.class);
        if (!definitions
                .fingerprint(owner, ownerBinding)
                .equals(dependency.path("ownerFingerprint").asText()))
            throw new ReportFault("SCOPE_CHANGED", "订阅管理主体范围已变化", 403);
        var currentModel =
                json.read(
                        resources
                                .version("SUBSCRIPTION", actor.tenantId(), draft.publishedVersion())
                                .orElseThrow()
                                .json(),
                        SubscriptionDefinition.class);
        if (!fixedModel.recipients().contains(Long.toString(actor.userId()))
                || !currentModel.recipients().contains(Long.toString(actor.userId())))
            throw ReportFault.denied();
        var enabled =
                jdbc.queryForList(
                        "SELECT enabled FROM rpt_subscription_schedule WHERE tenant_id=? AND"
                                + " subscription_id=?",
                        Integer.class,
                        actor.tenantId(),
                        Long.parseLong(draft.id()));
        if (enabled.size() != 1 || enabled.get(0) != 1)
            throw new ReportFault("SUBSCRIPTION_OFFLINE", "订阅调度已停用", 409);
    }
}
