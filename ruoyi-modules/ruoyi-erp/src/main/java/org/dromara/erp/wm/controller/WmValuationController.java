package org.dromara.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.erp.wm.domain.bo.WmMaterialStockBo;
import org.dromara.erp.wm.domain.vo.WmMaterialStockVo;
import org.dromara.erp.wm.service.IInventoryValuationQueryService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/valuation")
public class WmValuationController extends BaseController {

    private final IInventoryValuationQueryService inventoryValuationQueryService;

    @SaCheckPermission("wm:valuation:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialStockVo> list(WmMaterialStockBo bo, PageQuery pageQuery) {
        return inventoryValuationQueryService.selectValuationPageList(bo, pageQuery);
    }

    @SaCheckPermission("wm:valuation:export")
    @Log(title = "库存估值", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMaterialStockBo bo, HttpServletResponse response) {
        List<WmMaterialStockVo> list = inventoryValuationQueryService.selectValuationList(bo);
        ExcelUtil.exportExcel(list, "库存估值数据", WmMaterialStockVo.class, response);
    }
}
