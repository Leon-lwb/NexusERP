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
import com.nexus.erp.wm.domain.bo.WmStockAlertBo;
import com.nexus.erp.wm.domain.vo.WmStockAlertVo;
import com.nexus.erp.wm.service.IWmStockAlertService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 安全库存预警Controller
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/stock-alert")
public class WmStockAlertController extends BaseController {

    /** 库存预警业务服务，提供库存预警相关能力 */
    private final IWmStockAlertService stockAlertService;

    /**
     * 分页查询库存预警列表
     *
     * @param bo 库存预警业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @SaCheckPermission("wm:stockAlert:list")
    @GetMapping("/list")
    public TableDataInfo<WmStockAlertVo> list(WmStockAlertBo bo, PageQuery pageQuery) {
        return stockAlertService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出库存预警列表
     *
     * @param bo 库存预警业务对象（BO）
     * @param response 参数 response
     * @return 结果
     */
    @SaCheckPermission("wm:stockAlert:export")
    @Log(title = "库存预警", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmStockAlertBo bo, HttpServletResponse response) {
        List<WmStockAlertVo> list = stockAlertService.selectList(bo);
        ExcelUtil.exportExcel(list, "库存预警数据", WmStockAlertVo.class, response);
    }

    /**
     * 获取库存预警详情
     *
     * @param alertId 预警 ID
     * @return 结果对象
     */
    @SaCheckPermission("wm:stockAlert:query")
    @GetMapping("/{alertId}")
    public R<WmStockAlertVo> getInfo(@PathVariable Long alertId) {
        return R.ok(stockAlertService.selectById(alertId));
    }

    /**
     * 修改库存预警
     *
     * @param bo 库存预警业务对象（BO）
     * @return 结果对象
     */
    @SaCheckPermission("wm:stockAlert:edit")
    @Log(title = "库存预警", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmStockAlertBo bo) {
        return toAjax(stockAlertService.updateByBo(bo));
    }

    /**
     * 删除库存预警
     *
     * @param alertIds 参数 alertIds
     * @return 结果对象
     */
    @SaCheckPermission("wm:stockAlert:remove")
    @Log(title = "库存预警", businessType = BusinessType.DELETE)
    @DeleteMapping("/{alertIds}")
    public R<Void> remove(@PathVariable Long[] alertIds) {
        return toAjax(stockAlertService.deleteByIds(alertIds));
    }

    /**
     * 校验库存预警
     * @return 结果对象
     */
    @SaCheckPermission("wm:stockAlert:edit")
    @Log(title = "库存预警-执行检查", businessType = BusinessType.UPDATE)
    @PostMapping("/check")
    public R<Void> check() {
        stockAlertService.checkStockAlerts();
        return R.ok();
    }

    /**
     * 执行库存预警相关操作
     *
     * @param alertId 预警 ID
     * @param false 参数 false
     * @return 结果对象
     */
    @SaCheckPermission("wm:stockAlert:edit")
    @Log(title = "库存预警-处理", businessType = BusinessType.UPDATE)
    @PostMapping("/handle/{alertId}")
    public R<Void> handle(@PathVariable Long alertId, @RequestParam(required = false) String handledRemark) {
        stockAlertService.handleAlert(alertId, handledRemark);
        return R.ok();
    }
}
