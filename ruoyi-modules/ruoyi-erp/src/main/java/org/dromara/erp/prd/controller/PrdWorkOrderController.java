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
import org.dromara.erp.prd.domain.bo.PrdWorkOrderBo;
import org.dromara.erp.prd.domain.vo.PrdWorkOrderVo;
import org.dromara.erp.prd.service.IPrdWorkOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生产工单Controller
 *
 * @author ruoyi
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/work")
public class PrdWorkOrderController extends BaseController {

    private final IPrdWorkOrderService prdWorkOrderService;

    /**
     * 查询生产工单列表
     */
    @SaCheckPermission("prd:work:list")
    @GetMapping("/list")
    public TableDataInfo<PrdWorkOrderVo> list(PrdWorkOrderBo bo, PageQuery pageQuery) {
        return prdWorkOrderService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出生产工单列表
     */
    @SaCheckPermission("prd:work:export")
    @Log(title = "生产工单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PrdWorkOrderBo bo, HttpServletResponse response) {
        List<PrdWorkOrderVo> list = prdWorkOrderService.selectList(bo);
        ExcelUtil.exportExcel(list, "生产工单数据", PrdWorkOrderVo.class, response);
    }

    /**
     * 获取生产工单详细信息
     */
    @SaCheckPermission("prd:work:query")
    @GetMapping("/{woId}")
    public R<PrdWorkOrderVo> getInfo(@PathVariable Long woId) {
        return R.ok(prdWorkOrderService.selectById(woId));
    }

    /**
     * 新增生产工单
     */
    @SaCheckPermission("prd:work:add")
    @Log(title = "生产工单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<PrdWorkOrderVo> add(@Validated @RequestBody PrdWorkOrderBo bo) {
        prdWorkOrderService.insertByBo(bo);
        return R.ok(prdWorkOrderService.selectById(bo.getWoId()));
    }

    /**
     * 修改生产工单
     */
    @SaCheckPermission("prd:work:edit")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PrdWorkOrderBo bo) {
        return toAjax(prdWorkOrderService.updateByBo(bo));
    }

    /**
     * 删除生产工单
     */
    @SaCheckPermission("prd:work:remove")
    @Log(title = "生产工单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{woIds}")
    public R<Void> remove(@PathVariable Long[] woIds) {
        return toAjax(prdWorkOrderService.deleteByIds(woIds));
    }

    /**
     * 下达工单
     */
    @SaCheckPermission("prd:work:edit")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PutMapping("/release/{woId}")
    public R<Void> release(@PathVariable Long woId) {
        prdWorkOrderService.release(woId);
        return R.ok();
    }

    /**
     * 开始生产
     */
    @SaCheckPermission("prd:work:edit")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PutMapping("/start/{woId}")
    public R<Void> startProduction(@PathVariable Long woId) {
        prdWorkOrderService.startProduction(woId);
        return R.ok();
    }

    /**
     * 完工
     */
    @SaCheckPermission("prd:work:edit")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{woId}")
    public R<Void> complete(@PathVariable Long woId) {
        prdWorkOrderService.complete(woId);
        return R.ok();
    }

    /**
     * 关闭工单
     */
    @SaCheckPermission("prd:work:edit")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PutMapping("/close/{woId}")
    public R<Void> close(@PathVariable Long woId) {
        prdWorkOrderService.close(woId);
        return R.ok();
    }
}
