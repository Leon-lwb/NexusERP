package org.dromara.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.erp.bd.domain.BdMaterialCategory;
import org.dromara.erp.bd.domain.bo.BdMaterialCategoryBo;
import org.dromara.erp.bd.domain.vo.BdMaterialCategoryVo;
import org.dromara.erp.bd.domain.vo.BdMaterialVo;
import org.dromara.erp.bd.domain.vo.CategoryWithMaterialsVo;
import org.dromara.erp.bd.service.IBdMaterialCategoryService;
import org.dromara.erp.bd.service.IBdMaterialService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 物料产品分类Controller
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/category")
public class BdMaterialCategoryController extends BaseController {

    private final IBdMaterialCategoryService bdMaterialCategoryService;
    private final IBdMaterialService bdMaterialService;

    /**
     * 查询物料产品分类列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return 物料产品分类分页列表
     */
    @SaCheckPermission("bd:category:list")
    @GetMapping("/list")
    public TableDataInfo<BdMaterialCategoryVo> list(BdMaterialCategoryBo bo, PageQuery pageQuery) {
        return bdMaterialCategoryService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料产品分类数据
     *
     * @param bo 查询参数
     * @param response HTTP响应对象
     */
    @SaCheckPermission("bd:category:export")
    @Log(title = "物料产品分类", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdMaterialCategoryBo bo, HttpServletResponse response) {
        List<BdMaterialCategoryVo> list = bdMaterialCategoryService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料产品分类数据", BdMaterialCategoryVo.class, response);
    }

    /**
     * 获取物料产品分类详细信息
     *
     * @param categoryId 分类ID
     * @return 物料产品分类详细信息
     */
    @SaCheckPermission("bd:category:query")
    @GetMapping("/{categoryId}")
    public R<BdMaterialCategoryVo> getInfo(@PathVariable Long categoryId) {
        return R.ok(bdMaterialCategoryService.selectById(categoryId));
    }

    /**
     * 新增物料产品分类
     *
     * @param bo 物料产品分类信息
     * @return 操作结果
     */
    @SaCheckPermission("bd:category:add")
    @Log(title = "物料产品分类", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdMaterialCategoryBo bo) {
        return toAjax(bdMaterialCategoryService.insertByBo(bo));
    }

    /**
     * 修改物料产品分类
     *
     * @param bo 物料产品分类信息
     * @return 操作结果
     */
    @SaCheckPermission("bd:category:edit")
    @Log(title = "物料产品分类", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdMaterialCategoryBo bo) {
        return toAjax(bdMaterialCategoryService.updateByBo(bo));
    }

    /**
     * 删除物料产品分类
     *
     * @param categoryIds 分类ID数组
     * @return 操作结果
     */
    @SaCheckPermission("bd:category:remove")
    @Log(title = "物料产品分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryIds}")
    public R<Void> remove(@PathVariable Long[] categoryIds) {
        return toAjax(bdMaterialCategoryService.deleteByIds(categoryIds));
    }

    /**
     * 排除子节点查询分类列表
     *
     * @param categoryId 需要排除的分类ID
     * @return 分类列表
     */
    @SaCheckPermission("bd:category:list")
    @GetMapping("/list/exclude/{categoryId}")
    public R<List<BdMaterialCategoryVo>> excludeChild(@PathVariable(value = "categoryId", required = false) Long[] categoryId) {
        List<BdMaterialCategoryVo> list = bdMaterialCategoryService.selectList(new BdMaterialCategoryBo());
        list.removeIf(c -> ArrayUtils.contains(categoryId, c.getCategoryId())
                || ArrayUtils.contains(StringUtils.split(c.getPathIds(), ","), String.valueOf(categoryId)));
        return R.ok(list);
    }

    /**
     * 获取所有子分类ID及对应物料
     *
     * @param categoryIds 分类ID数组
     * @return 分类及物料信息
     */
    @GetMapping("/allChildrenIds/{categoryIds}")
    public R<CategoryWithMaterialsVo> allChildrenIds(@PathVariable Long[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            return R.ok(new CategoryWithMaterialsVo());
        }
        List<BdMaterialCategory> categories = bdMaterialCategoryService.selectAllChildrenIds(categoryIds);
        List<Long> categoryIdsList = categories.stream()
                .map(BdMaterialCategory::getCategoryId)
                .collect(Collectors.toList());
        List<BdMaterialVo> materials = bdMaterialService.selectByCategoryIds(categoryIdsList);
        CategoryWithMaterialsVo result = new CategoryWithMaterialsVo();
        result.setCategories(categories);
        result.setMaterials(materials);
        return R.ok(result);
    }
}
