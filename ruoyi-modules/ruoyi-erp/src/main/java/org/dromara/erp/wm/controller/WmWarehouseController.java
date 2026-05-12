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
import org.dromara.erp.wm.domain.WmWarehouse;
import org.dromara.erp.wm.domain.bo.WmWarehouseBo;
import org.dromara.erp.wm.domain.vo.WmWarehouseVo;
import org.dromara.erp.wm.service.IWmWarehouseService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 仓库设置Controller
 *
 * @author ruoyi
 * @date 2025-09-30
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/warehouse")
public class WmWarehouseController extends BaseController {

    private final IWmWarehouseService wmWarehouseService;

    /**
     * 查询仓库设置列表
     */
    @SaCheckPermission("wm:warehouse:list")
    @GetMapping("/list")
    public TableDataInfo<WmWarehouseVo> list(WmWarehouseBo bo, PageQuery pageQuery) {
        return wmWarehouseService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出仓库设置列表
     */
    @SaCheckPermission("wm:warehouse:export")
    @Log(title = "仓库设置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmWarehouseBo bo, HttpServletResponse response) {
        List<WmWarehouseVo> list = wmWarehouseService.selectList(bo);
        ExcelUtil.exportExcel(list, "仓库设置数据", WmWarehouseVo.class, response);
    }

    /**
     * 获取仓库设置详细信息
     */
    @SaCheckPermission("wm:warehouse:query")
    @GetMapping("/{id}")
    public R<WmWarehouseVo> getInfo(@PathVariable Long id) {
        return R.ok(wmWarehouseService.selectById(id));
    }

    /**
     * 新增仓库设置
     */
    @SaCheckPermission("wm:warehouse:add")
    @Log(title = "仓库设置", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmWarehouseBo bo) {
        return toAjax(wmWarehouseService.insertByBo(bo));
    }

    /**
     * 修改仓库设置
     */
    @SaCheckPermission("wm:warehouse:edit")
    @Log(title = "仓库设置", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmWarehouseBo bo) {
        return toAjax(wmWarehouseService.updateByBo(bo));
    }

    /**
     * 删除仓库设置
     */
    @SaCheckPermission("wm:warehouse:remove")
    @Log(title = "仓库设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(wmWarehouseService.deleteByIds(ids));
    }

    /**
     * 查询树型的列表
     */
    @SaCheckPermission("wm:warehouse:list")
    @GetMapping({"/tree", "/getWareHouseTree"})
    public R<List<WmWarehouse>> getWareHouseTree() {
        return R.ok(wmWarehouseService.getWareHouseTree());
    }
}
