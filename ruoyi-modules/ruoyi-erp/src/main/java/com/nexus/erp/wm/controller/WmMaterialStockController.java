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
import com.nexus.erp.bd.domain.vo.BdMaterialCategoryVo;
import com.nexus.erp.bd.service.IBdMaterialCategoryService;
import com.nexus.erp.wm.domain.bo.WmMaterialStockBo;
import com.nexus.erp.wm.domain.vo.CategoryWithStockVo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;
import com.nexus.erp.wm.service.IWmMaterialStockService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存清单 Controller
 * <p>
 * 提供物料库存清单的 RESTful API 接口，包括分页查询、详情查询、新增、编辑、删除及按分类查看库存功能。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/wm/stock")
public class WmMaterialStockController extends BaseController {

    private final IWmMaterialStockService wmMaterialStockService;
    private final IBdMaterialCategoryService bdMaterialCategoryService;

    /**
     * 分页查询库存清单列表
     * <p>
     * 权限标识：{@code wm:stock:list}</p>
     *
     * @param bo        查询参数，包含物料编码、仓库、库位等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 库存清单分页列表
     */
    @SaCheckPermission("wm:stock:list")
    @GetMapping("/list")
    public TableDataInfo<WmMaterialStockVo> list(WmMaterialStockBo bo, PageQuery pageQuery) {
        return wmMaterialStockService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出库存清单数据到 Excel
     * <p>权限标识：{@code wm:stock:export}</p>
     * <p>日志记录：title="库存清单"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("wm:stock:export")
    @Log(title = "库存清单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(WmMaterialStockBo bo, HttpServletResponse response) {
        List<WmMaterialStockVo> list = wmMaterialStockService.selectList(bo);
        ExcelUtil.exportExcel(list, "库存清单数据", WmMaterialStockVo.class, response);
    }

    /**
     * 根据主键获取库存清单详细信息
     * <p>
     * 权限标识：{@code wm:stock:query}</p>
     *
     * @param stockId 库存记录 ID
     * @return 库存清单详细信息
     */
    @SaCheckPermission("wm:stock:query")
    @GetMapping("/{stockId}")
    public R<WmMaterialStockVo> getInfo(@PathVariable Long stockId) {
        return R.ok(wmMaterialStockService.selectById(stockId));
    }

    /**
     * 新增库存清单
     * <p>
     * 权限标识：{@code wm:stock:add}</p>
     * <p>日志记录：title="库存清单"，businessType=INSERT</p>
     *
     * @param bo 库存业务对象，包含物料、仓库、数量等信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:stock:add")
    @Log(title = "库存清单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody WmMaterialStockBo bo) {
        return toAjax(wmMaterialStockService.insertByBo(bo));
    }

    /**
     * 修改库存清单
     * <p>
     * 权限标识：{@code wm:stock:edit}</p>
     * <p>日志记录：title="库存清单"，businessType=UPDATE</p>
     *
     * @param bo 库存业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:stock:edit")
    @Log(title = "库存清单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody WmMaterialStockBo bo) {
        return toAjax(wmMaterialStockService.updateByBo(bo));
    }

    /**
     * 批量删除库存清单
     * <p>
     * 权限标识：{@code wm:stock:remove}</p>
     * <p>日志记录：title="库存清单"，businessType=DELETE</p>
     *
     * @param stockIds 要删除的库存记录 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("wm:stock:remove")
    @Log(title = "库存清单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{stockIds}")
    public R<Void> remove(@PathVariable Long[] stockIds) {
        return toAjax(wmMaterialStockService.deleteByIds(stockIds));
    }

    /**
     * 获取指定分类及其子分类的库存数据
     * <p>
     * 权限标识：{@code wm:stock:list}</p>
     *
     * @param categoryIds 分类 ID 数组
     * @return 包含分类及库存数据的复合对象
     */
    @SaCheckPermission("wm:stock:list")
    @GetMapping("/categoryStocks/{categoryIds}")
    public R<CategoryWithStockVo> getCategoryStocks(@PathVariable Long[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            return R.ok(new CategoryWithStockVo());
        }

        // 获取分类及其子分类
        List<BdMaterialCategoryVo> categories = bdMaterialCategoryService.selectAllChildrenIds(categoryIds);

        // 查询这些分类下的库存数据
        List<Long> categoryIdsList = categories.stream()
                .map(BdMaterialCategoryVo::getCategoryId)
                .collect(Collectors.toList());
        List<WmMaterialStockVo> stocks = wmMaterialStockService.selectByCategoryIds(categoryIdsList);

        CategoryWithStockVo result = new CategoryWithStockVo();
        result.setCategories(categories);
        result.setStocks(stocks);

        return R.ok(result);
    }
}
