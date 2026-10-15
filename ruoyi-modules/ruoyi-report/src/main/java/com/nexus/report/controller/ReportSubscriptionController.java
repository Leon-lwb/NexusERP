package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.subscription.*;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 持久报表订阅的原生编辑、发布、定时和手动运行管理。 */
@RestController
@RequestMapping("/report/subscriptions")
public final class ReportSubscriptionController {
    /** 保存当前actors状态或配置。 */
    private final ReportRequestActor actors;

    /** 保存当前service状态或配置。 */
    private final ReportSubscriptionService service;

    /** 保存当前properties状态或配置。 */
    private final ReportSubscriptionProperties properties;

    /**
     * 使用正常登录身份和显式模块开关。
     *
     * @param actors 当前契约的actors配置或状态
     * @param service 当前契约的service配置或状态
     * @param properties 当前契约的properties配置或状态
     */
    public ReportSubscriptionController(
            ReportRequestActor actors,
            ReportSubscriptionService service,
            ReportSubscriptionProperties properties) {
        this.actors = actors;
        this.service = service;
        this.properties = properties;
    }

    /**
     * 不含外部地址或脚本的自有草稿。
     *
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param revision 当前契约的revision配置或状态
     * @param definition 当前契约的definition配置或状态
     */
    public record Draft(
            String code, String title, long revision, SubscriptionDefinition definition) {}

    /**
     * 发布和资源状态携带修订。
     *
     * @param revision 当前契约的revision配置或状态
     * @param state 任务或资源状态
     */
    public record Revision(long revision, String state) {}

    /**
     * 手动执行保持独立幂等。
     *
     * @param requestKey 当前契约的requestKey配置或状态
     */
    public record Manual(String requestKey) {}

    /**
     * 只返回已安装功能开关，不返回环境凭证。
     *
     * @param schedulingEnabled 当前契约的schedulingEnabled配置或状态
     * @param notificationsEnabled 当前契约的notificationsEnabled配置或状态
     * @param minSeconds 当前契约的minSeconds配置或状态
     * @param maxRecipients 当前契约的maxRecipients配置或状态
     */
    public record Capability(
            boolean schedulingEnabled,
            boolean notificationsEnabled,
            int minSeconds,
            int maxRecipients) {}

    /**
     * 可见目录移除原始参数正文。
     *
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:list")
    @GetMapping
    public R<List<ReportResourceStore.Draft>> list() {
        return R.ok(service.list(actors.current()));
    }

    /**
     * 读取独立编辑草稿。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:manage")
    @GetMapping("/{id}")
    public R<ReportResourceStore.Draft> detail(@PathVariable String id) {
        return R.ok(service.detail(actors.current(), id));
    }

    /**
     * 固定版本的参数目录。
     *
     * @param version 当前契约的version配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:manage")
    @GetMapping("/sources/{version}")
    public R<ReportSubscriptionService.Source> source(@PathVariable String version) {
        return R.ok(service.source(actors.current(), version));
    }

    /**
     * 创建自有订阅。
     *
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:manage")
    @PostMapping
    public R<ReportResourceStore.Draft> create(@RequestBody Draft body) {
        return R.ok(service.create(actors.current(), body.code(), body.title(), body.definition()));
    }

    /**
     * revision保存不会更新当前调度。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:manage")
    @PutMapping("/{id}")
    public R<ReportResourceStore.Draft> save(@PathVariable String id, @RequestBody Draft body) {
        return R.ok(
                service.save(
                        actors.current(), id, body.revision(), body.title(), body.definition()));
    }

    /**
     * 冻结报告、参数规则、接收人和时区。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:publish")
    @PostMapping("/{id}/publish")
    public R<ReportResourceStore.Version> publish(
            @PathVariable String id, @RequestBody Revision body) {
        return R.ok(service.publish(actors.current(), id, body.revision()));
    }

    /**
     * 手动运行不改写计划时刻。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:manage")
    @PostMapping("/{id}/run")
    public R<Void> manual(@PathVariable String id, @RequestBody Manual body) {
        service.manual(actors.current(), id, body.requestKey());
        return R.ok();
    }

    /**
     * 调度nextAt来自主库。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:list")
    @GetMapping("/{id}/schedule")
    public R<ReportSubscriptionService.Schedule> schedule(@PathVariable String id) {
        return R.ok(service.schedule(actors.current(), id));
    }

    /**
     * 逐接收人执行结果。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:list")
    @GetMapping("/{id}/executions")
    public R<List<ReportSubscriptionService.Execution>> executions(@PathVariable String id) {
        return R.ok(service.executions(actors.current(), id));
    }

    /**
     * 显式下线令所有领取入口重新失败。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:publish")
    @PostMapping("/{id}/state")
    public R<ReportResourceStore.Draft> state(@PathVariable String id, @RequestBody Revision body) {
        return R.ok(service.state(actors.current(), id, body.revision(), body.state()));
    }

    /**
     * 返回调度及通知的实际开关状态。
     *
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:list")
    @GetMapping("/capabilities")
    public R<Capability> capabilities() {
        return R.ok(
                new Capability(
                        properties.isSchedulingEnabled(),
                        properties.isNotificationsEnabled(),
                        300,
                        20));
    }

    /**
     * 重试保持已有固定计划、接收人和时刻。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:manage")
    @PostMapping("/executions/{id}/retry")
    public R<Void> retry(@PathVariable String id) {
        service.retry(actors.current(), id);
        return R.ok();
    }

    /**
     * 取消特定接收人任务。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:subscription:manage")
    @PostMapping("/executions/{id}/cancel")
    public R<Void> cancel(@PathVariable String id) {
        service.cancel(actors.current(), id);
        return R.ok();
    }
}
