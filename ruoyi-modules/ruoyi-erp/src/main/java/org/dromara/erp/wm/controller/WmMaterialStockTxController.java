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
import org.dromara.erp.wm.domain.bo.WmMaterialStockTxBo;
import org.dromara.erp.wm.domain.vo.WmMaterialStockTxVo;
import org.dromara.erp.wm.service.IWmMaterialStockTxService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 库存事务Controller
 *
 * @author ruoyi
 * @date 2025-12-12
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/stocktx")
public class WmMaterialStockTxController extends BaseController {

    private final IWmMaterialStockTxService wmMaterialStockTxService;

    /**
     * 查询库存事务列表
     */
    @SaCheckPermission("wm:stocktx:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialStockTxVo> list(WmMaterialStockTxBo bo, PageQuery pageQuery) {
        return wmMaterialStockTxService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出库存事务列表
     */
    @SaCheckPermission("wm:stocktx:export")
    @Log(title = "库存事务", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMaterialStockTxBo bo, HttpServletResponse response) {
        List<WmMaterialStockTxVo> list = wmMaterialStockTxService.selectList(bo);
        ExcelUtil.exportExcel(list, "库存事务数据", WmMaterialStockTxVo.class, response);
    }

    /**
     * 获取库存事务详细信息
     */
    @SaCheckPermission("wm:stocktx:query")
    @GetMapping("/{txId}")
    public R<WmMaterialStockTxVo> getInfo(@PathVariable Long txId) {
        return R.ok(wmMaterialStockTxService.selectById(txId));
    }

    /**
     * 新增库存事务
     */
    @SaCheckPermission("wm:stocktx:add")
    @Log(title = "库存事务", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMaterialStockTxBo bo) {
        return toAjax(wmMaterialStockTxService.insertByBo(bo));
    }

    /**
     * 修改库存事务
     */
    @SaCheckPermission("wm:stocktx:edit")
    @Log(title = "库存事务", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMaterialStockTxBo bo) {
        return toAjax(wmMaterialStockTxService.updateByBo(bo));
    }

    /**
     * 删除库存事务
     */
    @SaCheckPermission("wm:stocktx:remove")
    @Log(title = "库存事务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{txIds}")
    public R<Void> remove(@PathVariable Long[] txIds) {
        return toAjax(wmMaterialStockTxService.deleteByIds(txIds));
    }
}
