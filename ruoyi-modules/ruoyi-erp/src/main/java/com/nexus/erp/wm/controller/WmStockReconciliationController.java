package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.wm.domain.bo.WmStockReconciliationBo;
import com.nexus.erp.wm.domain.vo.WmStockReconciliationVo;
import com.nexus.erp.wm.service.IStockReconciliationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存台账校验Controller
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/reconciliation")
public class WmStockReconciliationController extends BaseController {

    /** 对账业务服务，提供对账相关能力 */
    private final IStockReconciliationService reconciliationService;

    /**
     * 分页查询库存对账列表
     *
     * @param bo 库存对账业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @SaCheckPermission("wm:reconciliation:list")
    @GetMapping("/list")
    public TableDataInfo<WmStockReconciliationVo> list(WmStockReconciliationBo bo, PageQuery pageQuery) {
        return reconciliationService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出库存对账列表
     *
     * @param bo 库存对账业务对象（BO）
     * @param response 参数 response
     * @return 结果
     */
    @SaCheckPermission("wm:reconciliation:export")
    @Log(title = "库存台账校验", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmStockReconciliationBo bo, HttpServletResponse response) {
        List<WmStockReconciliationVo> list = reconciliationService.selectList(bo);
        ExcelUtil.exportExcel(list, "库存台账校验数据", WmStockReconciliationVo.class, response);
    }

    /**
     * 获取库存对账详情
     *
     * @param reconId recon ID
     * @return 结果对象
     */
    @SaCheckPermission("wm:reconciliation:query")
    @GetMapping("/{reconId}")
    public R<WmStockReconciliationVo> getInfo(@PathVariable Long reconId) {
        return R.ok(reconciliationService.selectById(reconId));
    }

    /**
     * 删除库存对账
     *
     * @param reconIds 参数 reconIds
     * @return 结果对象
     */
    @SaCheckPermission("wm:reconciliation:remove")
    @Log(title = "库存台账校验", businessType = BusinessType.DELETE)
    @DeleteMapping("/{reconIds}")
    public R<Void> remove(@PathVariable Long[] reconIds) {
        return toAjax(reconciliationService.deleteByIds(reconIds));
    }

    /**
     * 库存对账处理（reconcile）
     * @return 结果对象
     */
    @SaCheckPermission("wm:reconciliation:edit")
    @Log(title = "库存台账校验-全量", businessType = BusinessType.UPDATE)
    @PostMapping("/reconcile")
    public R<Void> reconcile() {
        reconciliationService.reconcile();
        return R.ok();
    }

    /**
     * 库存对账处理（reconcileByMaterial）
     *
     * @param materialId 物料 ID
     * @return 结果对象
     */
    @SaCheckPermission("wm:reconciliation:edit")
    @Log(title = "库存台账校验-按物料", businessType = BusinessType.UPDATE)
    @PostMapping("/reconcile/{materialId}")
    public R<Void> reconcileByMaterial(@PathVariable Long materialId) {
        reconciliationService.reconcileByMaterial(materialId);
        return R.ok();
    }
}
