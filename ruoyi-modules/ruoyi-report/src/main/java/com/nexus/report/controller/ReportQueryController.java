package com.nexus.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.query.*;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 受控取数原生接口，身份来自正常认证会话，不暴露物理SQL或账户。 */
@RestController
@RequestMapping("/report/queries")
public class ReportQueryController {
    /** 保存当前actors状态或配置。 */
    private final ReportRequestActor actors;

    /** 保存当前service状态或配置。 */
    private final ReportQueryService service;

    /**
     * 使用统一认证身份与查询生命周期服务。
     *
     * @param actors 当前契约的actors配置或状态
     * @param service 当前契约的service配置或状态
     */
    public ReportQueryController(ReportRequestActor actors, ReportQueryService service) {
        this.actors = actors;
        this.service = service;
    }

    /**
     * 创建和编辑仅接受受限查询模型。
     *
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param revision 当前契约的revision配置或状态
     * @param model 当前契约的model配置或状态
     */
    public record DraftRequest(String code, String title, Long revision, QueryModel model) {}

    /**
     * 发布必须携带预期草稿修订。
     *
     * @param revision 当前契约的revision配置或状态
     */
    public record RevisionRequest(long revision) {}

    /**
     * 模型预览只接受声明参数。
     *
     * @param model 当前契约的model配置或状态
     * @param parameters 固定参数契约
     */
    public record PreviewRequest(QueryModel model, Map<String, Object> parameters) {}

    /**
     * 生命周期转换同样受修订围栏保护。
     *
     * @param revision 当前契约的revision配置或状态
     * @param state 任务或资源状态
     */
    public record StateRequest(long revision, String state) {}

    /**
     * 返回当前身份可见的逻辑目录。
     *
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:query:list")
    @GetMapping("/catalog")
    public R<List<ReportQueryService.CatalogView>> catalog() {
        return R.ok(service.catalog(actors.current()));
    }

    /**
     * 列出仍有权限的查询草稿。
     *
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:query:list")
    @GetMapping
    public R<List<ReportResourceStore.Draft>> list() {
        return R.ok(service.list(actors.current()));
    }

    /**
     * 返回仍满足实体和字段权限的查询详情。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:query:list")
    @GetMapping("/{id}")
    public R<ReportResourceStore.Draft> detail(@PathVariable String id) {
        return R.ok(service.detail(actors.current(), id));
    }

    /**
     * 创建尚未发布的原生查询。
     *
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:query:add")
    @PostMapping
    public R<ReportResourceStore.Draft> create(@RequestBody DraftRequest body) {
        return R.ok(service.create(actors.current(), body.code(), body.title(), body.model()));
    }

    /**
     * 不覆盖其他人已保存的修订。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:query:edit")
    @PutMapping("/{id}")
    public R<ReportResourceStore.Draft> save(
            @PathVariable String id, @RequestBody DraftRequest body) {
        if (body.revision() == null) throw ReportFault.invalid("保存缺少修订");
        return R.ok(
                service.save(actors.current(), id, body.revision(), body.title(), body.model()));
    }

    /**
     * 固定查询发布并登记数据集版本。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:query:publish")
    @PostMapping("/{id}/publish")
    public R<ReportQueryService.Publication> publish(
            @PathVariable String id, @RequestBody RevisionRequest body) {
        return R.ok(service.publish(actors.current(), id, body.revision()));
    }

    /**
     * 返回历史固定查询版本。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:query:list")
    @GetMapping("/{id}/versions")
    public R<List<ReportResourceStore.Version>> versions(@PathVariable String id) {
        return R.ok(service.versions(actors.current(), id));
    }

    /**
     * 受控样例预览不返回SQL语句或数据库异常。
     *
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:query:edit")
    @PostMapping("/preview")
    public R<ReportQueryService.TypedPreview> preview(@RequestBody PreviewRequest body) {
        var data = service.preview(actors.current(), body.model(), body.parameters());
        return R.ok(service.typedPreview(actors.current(), body.model(), data));
    }

    /**
     * 显式下线或归档查询。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param body 当前契约的body配置或状态
     * @return 当前受控处理的固定结果
     */
    @SaCheckPermission("report:query:publish")
    @PostMapping("/{id}/state")
    public R<ReportResourceStore.Draft> state(
            @PathVariable String id, @RequestBody StateRequest body) {
        return R.ok(service.state(actors.current(), id, body.revision(), body.state()));
    }
}
