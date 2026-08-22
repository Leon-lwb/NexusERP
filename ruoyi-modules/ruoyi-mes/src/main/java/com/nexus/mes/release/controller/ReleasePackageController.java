package com.nexus.mes.release.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.release.domain.bo.ReleasePackageBo;
import com.nexus.mes.release.domain.vo.ReleasePackageVo;
import com.nexus.mes.release.service.IReleasePackageService;
import com.nexus.workflow.domain.WfCustomForm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MES客户定制化发布包 Controller
 * <p>提供发布包 CRUD + 审批/归档/默认 状态机接口 + 客户物料匹配接口。</p>
 *
 * @author Nexus
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/release/package")
public class ReleasePackageController extends BaseController {

    private final IReleasePackageService releasePackageService;

    /**
     * 分页查询发布包列表
     */
    @SaCheckPermission("mes:release:package:list")
    @GetMapping("/list")
    public TableDataInfo<ReleasePackageVo> list(ReleasePackageBo bo, PageQuery pageQuery) {
        return releasePackageService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取发布包详情(含固件映射/BOM选配/配置项子表)
     */
    @SaCheckPermission("mes:release:package:query")
    @GetMapping("/{packageId}")
    public R<ReleasePackageVo> getInfo(@PathVariable Long packageId) {
        return R.ok(releasePackageService.selectDetailById(packageId));
    }

    /**
     * 新增发布包(编码自动生成 + 状态 DRAFT + 子表级联保存)
     */
    @SaCheckPermission("mes:release:package:add")
    @Log(title = "发布包新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody ReleasePackageBo bo) {
        return toAjax(releasePackageService.insertByBo(bo));
    }

    /**
     * 修改发布包(仅 DRAFT/ARCHIVED 可编辑; 子表先删后插)
     */
    @SaCheckPermission("mes:release:package:edit")
    @Log(title = "发布包修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody ReleasePackageBo bo) {
        return toAjax(releasePackageService.updateByBo(bo));
    }

    /**
     * 批量删除发布包(仅 DRAFT 可删)
     */
    @SaCheckPermission("mes:release:package:remove")
    @Log(title = "发布包删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(releasePackageService.deleteByIds(ids));
    }

    /**
     * 提交审批: DRAFT -> IN_REVIEW (selectFlowId 非空走 Flowable, 否则仅改状态)
     */
    @SaCheckPermission("mes:release:package:submit")
    @Log(title = "发布包提交审批", businessType = BusinessType.UPDATE)
    @PostMapping("/submit/{packageId}")
    public R<Void> submit(@PathVariable Long packageId,
                          @RequestParam(required = false) String selectFlowId) {
        return toAjax(releasePackageService.submitForApproval(packageId, selectFlowId));
    }

    /**
     * 兜底审批通过(管理员强制): IN_REVIEW -> RELEASED
     */
    @SaCheckPermission("mes:release:package:approve")
    @Log(title = "发布包审批通过", businessType = BusinessType.UPDATE)
    @PutMapping("/approve/{packageId}")
    public R<Void> approve(@PathVariable Long packageId,
                           @RequestParam(required = false) String remark) {
        return toAjax(releasePackageService.approve(packageId, remark));
    }

    /**
     * 兜底驳回: IN_REVIEW -> DRAFT
     */
    @SaCheckPermission("mes:release:package:reject")
    @Log(title = "发布包审批驳回", businessType = BusinessType.UPDATE)
    @PutMapping("/reject/{packageId}")
    public R<Void> reject(@PathVariable Long packageId,
                          @RequestParam(required = false) String reason) {
        return toAjax(releasePackageService.reject(packageId, reason));
    }

    /**
     * 归档: RELEASED -> ARCHIVED (归档后不参与匹配)
     */
    @SaCheckPermission("mes:release:package:archive")
    @Log(title = "发布包归档", businessType = BusinessType.UPDATE)
    @PutMapping("/archive/{packageId}")
    public R<Void> archive(@PathVariable Long packageId,
                           @RequestParam(required = false) String reason) {
        return toAjax(releasePackageService.archive(packageId, reason));
    }

    /**
     * 设为客户+物料默认发布包(仅 RELEASED, 客户+物料内唯一)
     */
    @SaCheckPermission("mes:release:package:default")
    @Log(title = "发布包设为默认", businessType = BusinessType.UPDATE)
    @PutMapping("/default/{packageId}")
    public R<Void> setDefault(@PathVariable Long packageId) {
        return toAjax(releasePackageService.setDefault(packageId));
    }

    /**
     * 按客户+物料(+客户产品代号)匹配已发布发布包(销售订单/工单侧联动查询用)
     */
    @SaCheckPermission("mes:release:package:list")
    @GetMapping("/match")
    public R<ReleasePackageVo> matchPackage(@RequestParam Long clientId,
                                            @RequestParam Long materialId,
                                            @RequestParam(required = false) String customerProductCode) {
        return R.ok(releasePackageService.matchPackage(clientId, materialId, customerProductCode));
    }

    /**
     * 按客户+物料查询所有已发布(RELEASED)发布包(前端选择列表)
     */
    @SaCheckPermission("mes:release:package:list")
    @GetMapping("/list-by-client-material")
    public R<List<ReleasePackageVo>> listByClientMaterial(@RequestParam Long clientId,
                                                          @RequestParam Long materialId) {
        return R.ok(releasePackageService.selectByClientMaterial(clientId, materialId));
    }

    /**
     * 查询可发起审批流程列表(mesReleasePackageService 已注册的自定义表单, 提交审批弹窗用)
     */
    @SaCheckPermission("mes:release:package:list")
    @GetMapping("/flows")
    public R<List<WfCustomForm>> flows() {
        return R.ok(releasePackageService.getFlows());
    }

    /**
     * 查询流程实例ID(审批表单页"查看流程"跳转用)
     * 使用 R.ok(String msg, T data) 重载, 避免 R.ok(String) 把 ID 误塞进 msg
     */
    @SaCheckPermission("mes:release:package:query")
    @GetMapping("/{packageId}/proc")
    public R<String> getProcInsId(@PathVariable Long packageId) {
        return R.ok("查询成功", releasePackageService.getProcInsId(String.valueOf(packageId)));
    }
}
