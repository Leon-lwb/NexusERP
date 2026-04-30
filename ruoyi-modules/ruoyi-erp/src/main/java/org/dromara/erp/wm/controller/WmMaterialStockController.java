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
import org.dromara.erp.bd.domain.BdMaterialCategory;
import org.dromara.erp.bd.service.IBdMaterialCategoryService;
import org.dromara.erp.wm.domain.bo.WmMaterialStockBo;
import org.dromara.erp.wm.domain.vo.CategoryWithStockVo;
import org.dromara.erp.wm.domain.vo.WmMaterialStockVo;
import org.dromara.erp.wm.service.IWmMaterialStockService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存清单Controller
 *
 * @author ruoyi
 * @date 2025-12-03
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/stock")
public class WmMaterialStockController extends BaseController {

    private final IWmMaterialStockService wmMaterialStockService;
    private final IBdMaterialCategoryService bdMaterialCategoryService;

    /**
     * 查询库存清单列表
     */
    @SaCheckPermission("wm:stock:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialStockVo> list(WmMaterialStockBo bo, PageQuery pageQuery) {
        return wmMaterialStockService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出库存清单列表
     */
    @SaCheckPermission("wm:stock:export")
    @Log(title = "库存清单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMaterialStockBo bo, HttpServletResponse response) {
        List<WmMaterialStockVo> list = wmMaterialStockService.selectList(bo);
        ExcelUtil.exportExcel(list, "库存清单数据", WmMaterialStockVo.class, response);
    }

    /**
     * 获取库存清单详细信息
     */
    @SaCheckPermission("wm:stock:query")
    @GetMapping("/{stockId}")
    public R<WmMaterialStockVo> getInfo(@PathVariable Long stockId) {
        return R.ok(wmMaterialStockService.selectById(stockId));
    }

    /**
     * 新增库存清单
     */
    @SaCheckPermission("wm:stock:add")
    @Log(title = "库存清单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMaterialStockBo bo) {
        return toAjax(wmMaterialStockService.insertByBo(bo));
    }

    /**
     * 修改库存清单
     */
    @SaCheckPermission("wm:stock:edit")
    @Log(title = "库存清单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMaterialStockBo bo) {
        return toAjax(wmMaterialStockService.updateByBo(bo));
    }

    /**
     * 删除库存清单
     */
    @SaCheckPermission("wm:stock:remove")
    @Log(title = "库存清单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{stockIds}")
    public R<Void> remove(@PathVariable Long[] stockIds) {
        return toAjax(wmMaterialStockService.deleteByIds(stockIds));
    }

    /**
     * 获取指定分类及其子分类列表及库存数据
     */
    @SaCheckPermission("wm:stock:list")
    @GetMapping("/categoryStocks/{categoryIds}")
    public R<CategoryWithStockVo> getCategoryStocks(@PathVariable Long[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            return R.ok(new CategoryWithStockVo());
        }

        // 获取分类及其子分类
        List<BdMaterialCategory> categories = bdMaterialCategoryService.selectAllChildrenIds(categoryIds);

        // 查询这些分类下的库存数据
        List<Long> categoryIdsList = categories.stream()
                .map(BdMaterialCategory::getCategoryId)
                .collect(Collectors.toList());
        List<WmMaterialStockVo> stocks = wmMaterialStockService.selectByCategoryIds(categoryIdsList);

        CategoryWithStockVo result = new CategoryWithStockVo();
        result.setCategories(categories);
        result.setStocks(stocks);

        return R.ok(result);
    }
}
