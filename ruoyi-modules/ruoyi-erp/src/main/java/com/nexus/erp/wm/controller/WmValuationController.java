package com.nexus.erp.wm.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.wm.domain.bo.WmMaterialStockBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;
import com.nexus.erp.wm.service.IInventoryValuationQueryService;
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
