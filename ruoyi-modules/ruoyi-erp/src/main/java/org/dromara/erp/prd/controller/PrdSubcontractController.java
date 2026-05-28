package org.dromara.erp.prd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.erp.prd.domain.bo.PrdSubcontractBo;
import org.dromara.erp.prd.domain.vo.PrdSubcontractVo;
import org.dromara.erp.prd.service.IPrdSubcontractService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 委外订单Controller
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/subcontract")
public class PrdSubcontractController extends BaseController {

    private final IPrdSubcontractService subcontractService;

    @SaCheckPermission("prd:subcontract:list")
    @GetMapping("/list")
    public TableDataInfo<PrdSubcontractVo> list(PrdSubcontractBo bo, PageQuery pageQuery) {
        return subcontractService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("prd:subcontract:export")
    @Log(title = "委外订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdSubcontractBo bo, HttpServletResponse response) {
        List<PrdSubcontractVo> list = subcontractService.selectList(bo);
        ExcelUtil.exportExcel(list, "委外订单数据", PrdSubcontractVo.class, response);
    }

    @SaCheckPermission("prd:subcontract:query")
    @GetMapping("/{subcontractId}")
    public R<PrdSubcontractVo> getInfo(@PathVariable Long subcontractId) {
        return R.ok(subcontractService.selectById(subcontractId));
    }

    @SaCheckPermission("prd:subcontract:add")
    @Log(title = "委外订单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<PrdSubcontractVo> add(@Validated @RequestBody PrdSubcontractBo bo) {
        subcontractService.insertByBo(bo);
        return R.ok(subcontractService.selectById(bo.getSubcontractId()));
    }

    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdSubcontractBo bo) {
        return toAjax(subcontractService.updateByBo(bo));
    }

    @SaCheckPermission("prd:subcontract:remove")
    @Log(title = "委外订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{subcontractIds}")
    public R<Void> remove(@PathVariable Long[] subcontractIds) {
        return toAjax(subcontractService.deleteByIds(subcontractIds));
    }

    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-下达", businessType = BusinessType.UPDATE)
    @PutMapping("/release/{subcontractId}")
    public R<Void> release(@PathVariable Long subcontractId) {
        subcontractService.release(subcontractId);
        return R.ok();
    }

    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-开始", businessType = BusinessType.UPDATE)
    @PutMapping("/start/{subcontractId}")
    public R<Void> start(@PathVariable Long subcontractId) {
        subcontractService.start(subcontractId);
        return R.ok();
    }

    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-完工", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{subcontractId}")
    public R<Void> complete(@PathVariable Long subcontractId) {
        subcontractService.complete(subcontractId);
        return R.ok();
    }

    @SaCheckPermission("prd:subcontract:edit")
    @Log(title = "委外订单-关闭", businessType = BusinessType.UPDATE)
    @PutMapping("/close/{subcontractId}")
    public R<Void> close(@PathVariable Long subcontractId) {
        subcontractService.close(subcontractId);
        return R.ok();
    }
}
