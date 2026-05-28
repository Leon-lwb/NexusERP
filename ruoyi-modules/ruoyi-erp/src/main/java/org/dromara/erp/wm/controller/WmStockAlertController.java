package org.dromara.erp.wm.controller;

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
import org.dromara.erp.wm.domain.bo.WmStockAlertBo;
import org.dromara.erp.wm.domain.vo.WmStockAlertVo;
import org.dromara.erp.wm.service.IWmStockAlertService;
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

    private final IWmStockAlertService stockAlertService;

    @SaCheckPermission("wm:stockAlert:list")
    @GetMapping("/list")
    public TableDataInfo<WmStockAlertVo> list(WmStockAlertBo bo, PageQuery pageQuery) {
        return stockAlertService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("wm:stockAlert:export")
    @Log(title = "库存预警", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmStockAlertBo bo, HttpServletResponse response) {
        List<WmStockAlertVo> list = stockAlertService.selectList(bo);
        ExcelUtil.exportExcel(list, "库存预警数据", WmStockAlertVo.class, response);
    }

    @SaCheckPermission("wm:stockAlert:query")
    @GetMapping("/{alertId}")
    public R<WmStockAlertVo> getInfo(@PathVariable Long alertId) {
        return R.ok(stockAlertService.selectById(alertId));
    }

    @SaCheckPermission("wm:stockAlert:edit")
    @Log(title = "库存预警", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmStockAlertBo bo) {
        return toAjax(stockAlertService.updateByBo(bo));
    }

    @SaCheckPermission("wm:stockAlert:remove")
    @Log(title = "库存预警", businessType = BusinessType.DELETE)
    @DeleteMapping("/{alertIds}")
    public R<Void> remove(@PathVariable Long[] alertIds) {
        return toAjax(stockAlertService.deleteByIds(alertIds));
    }

    @SaCheckPermission("wm:stockAlert:edit")
    @Log(title = "库存预警-执行检查", businessType = BusinessType.UPDATE)
    @PostMapping("/check")
    public R<Void> check() {
        stockAlertService.checkStockAlerts();
        return R.ok();
    }

    @SaCheckPermission("wm:stockAlert:edit")
    @Log(title = "库存预警-处理", businessType = BusinessType.UPDATE)
    @PostMapping("/handle/{alertId}")
    public R<Void> handle(@PathVariable Long alertId, @RequestParam(required = false) String handledRemark) {
        stockAlertService.handleAlert(alertId, handledRemark);
        return R.ok();
    }
}
