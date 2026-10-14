package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.report.dashboard.*;
import com.nexus.report.extension.ReportResourceStore;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 仪表盘原生定义、固定发布和按用户权限刷新入口。 */
@RestController
@RequestMapping("/report/dashboards")
public final class ReportDashboardController {
    /** 保存当前actors状态或配置。 */
    private final ReportRequestActor actors;

    /** 保存当前service状态或配置。 */
    private final ReportDashboardService service;

    /**
     * 注入正常登录身份，所有服务方法仍独立刷新权限。
     *
     * @param actors 当前契约的actors配置或状态
     * @param service 当前契约的service配置或状态
     */
    public ReportDashboardController(ReportRequestActor actors, ReportDashboardService service) {
        this.actors = actors;
        this.service = service;
    }

    /**
     * 草稿和修订输入。
     *
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param revision 当前契约的revision配置或状态
     * @param definition 当前契约的definition配置或状态
     */
    public record Draft(String code, String title, long revision, DashboardDefinition definition) {}

    /**
     * 发布和状态输入。
     *
     * @param revision 当前契约的revision配置或状态
     * @param state 任务或资源状态
     */
    public record Revision(long revision, String state) {}

    /**
     * 参数和类型化联动输入。
     *
     * @param parameters 固定参数契约
     * @param selections 当前契约的selections配置或状态
     */
    public record Input(
            Map<String, Object> parameters, List<ReportDashboardService.Selection> selections) {}

    /**
     * 可见目录不透出其他人的编辑正文。
     *
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:dashboard:list")
    @GetMapping
    public R<List<ReportResourceStore.Draft>> list() {
        return R.ok(service.list(actors.current()));
    }

    /**
     * 获取经契约权限校验的编辑定义。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:dashboard:edit")
    @GetMapping("/{id}")
    public R<ReportResourceStore.Draft> detail(@PathVariable String id) {
        return R.ok(service.detail(actors.current(), id));
    }

    /**
     * 固定历史报告的可见契约供设计器选择字段。
     *
     * @param versionId 不可变发布版本标识
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:dashboard:edit")
    @GetMapping("/sources/{versionId}")
    public R<ReportDashboardService.Source> source(@PathVariable String versionId) {
        return R.ok(service.source(actors.current(), versionId));
    }

    /**
     * 读取当前发布布局和参数，撤权组件字段被隐藏。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:dashboard:list")
    @GetMapping("/{id}/published")
    public R<DashboardDefinition> published(@PathVariable String id) {
        return R.ok(service.published(actors.current(), id));
    }

    /**
     * 创建自有组件草稿。
     *
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:dashboard:edit")
    @PostMapping
    public R<ReportResourceStore.Draft> create(@RequestBody Draft body) {
        return R.ok(service.create(actors.current(), body.code(), body.title(), body.definition()));
    }

    /**
     * 按修订保存，不覆盖已发布版本。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:dashboard:edit")
    @PutMapping("/{id}")
    public R<ReportResourceStore.Draft> save(@PathVariable String id, @RequestBody Draft body) {
        return R.ok(
                service.save(
                        actors.current(), id, body.revision(), body.title(), body.definition()));
    }

    /**
     * 冻结报告、数据集和组件依赖。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:dashboard:publish")
    @PostMapping("/{id}/publish")
    public R<ReportResourceStore.Version> publish(
            @PathVariable String id, @RequestBody Revision body) {
        return R.ok(service.publish(actors.current(), id, body.revision()));
    }

    /**
     * 分组件精确计算，不伪装为跨库同一时刻。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:dashboard:list")
    @PostMapping("/{id}/render")
    public R<ReportDashboardService.Render> render(
            @PathVariable String id, @RequestBody Input body) {
        return R.ok(service.render(actors.current(), id, body.parameters(), body.selections()));
    }

    /**
     * 获取个人收藏并过滤失效授权。
     *
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:dashboard:list")
    @GetMapping("/favorites")
    public R<List<String>> favorites() {
        return R.ok(service.favorites(actors.current()));
    }

    /**
     * 收藏仅是个人标记。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param enabled 当前契约的enabled配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:dashboard:list")
    @PutMapping("/{id}/favorite")
    public R<Void> favorite(@PathVariable String id, @RequestParam boolean enabled) {
        service.favorite(actors.current(), id, enabled);
        return R.ok();
    }

    /**
     * 下线由发布权限和资源ACL共同控制。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:dashboard:publish")
    @PostMapping("/{id}/state")
    public R<ReportResourceStore.Draft> state(@PathVariable String id, @RequestBody Revision body) {
        return R.ok(service.state(actors.current(), id, body.revision(), body.state()));
    }
}
