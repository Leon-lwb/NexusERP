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
/**
 * 计价管理 Controller
 * <p>
 * 提供计价相关的 REST 接口，包括分页查询、新增、修改、删除、导入导出等操作。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/valuation")
public class WmValuationController extends BaseController {

    /** 库存计价查询业务服务，提供库存计价查询相关能力 */
    private final IInventoryValuationQueryService inventoryValuationQueryService;

    /**
     * 分页查询计价列表
     *
     * @param bo 计价业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @SaCheckPermission("wm:valuation:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialStockVo> list(WmMaterialStockBo bo, PageQuery pageQuery) {
        return inventoryValuationQueryService.selectValuationPageList(bo, pageQuery);
    }

    /**
     * 导出计价列表
     *
     * @param bo 计价业务对象（BO）
     * @param response 参数 response
     * @return 结果
     */
    @SaCheckPermission("wm:valuation:export")
    @Log(title = "库存估值", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMaterialStockBo bo, HttpServletResponse response) {
        List<WmMaterialStockVo> list = inventoryValuationQueryService.selectValuationList(bo);
        ExcelUtil.exportExcel(list, "库存估值数据", WmMaterialStockVo.class, response);
    }
}
