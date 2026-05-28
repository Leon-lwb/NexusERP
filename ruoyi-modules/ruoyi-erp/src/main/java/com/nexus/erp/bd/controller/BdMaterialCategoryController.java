package com.nexus.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.bd.domain.BdMaterialCategory;
import com.nexus.erp.bd.domain.bo.BdMaterialCategoryBo;
import com.nexus.erp.bd.domain.vo.BdMaterialCategoryVo;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;
import com.nexus.erp.bd.domain.vo.CategoryWithMaterialsVo;
import com.nexus.erp.bd.service.IBdMaterialCategoryService;
import com.nexus.erp.bd.service.IBdMaterialService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 物料产品分类 Controller
 * <p>
 * 管理物料/产品的分类层级结构，支持树形分类的增删改查及子节点排除等操作。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/category")
public class BdMaterialCategoryController extends BaseController {

    private final IBdMaterialCategoryService bdMaterialCategoryService;
    private final IBdMaterialService bdMaterialService;

    /**
     * 分页查询物料产品分类列表
     * <p>权限标识：{@code bd:category:list}</p>
     *
     * @param bo        查询参数，包含分类名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 物料产品分类分页列表
     */
    @SaCheckPermission("bd:category:list")
    @GetMapping("/list")
    public TableDataInfo<BdMaterialCategoryVo> list(BdMaterialCategoryBo bo, PageQuery pageQuery) {
        return bdMaterialCategoryService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料产品分类数据到 Excel
     * <p>权限标识：{@code bd:category:export}</p>
     * <p>日志记录：title="物料产品分类"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("bd:category:export")
    @Log(title = "物料产品分类", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdMaterialCategoryBo bo, HttpServletResponse response) {
        List<BdMaterialCategoryVo> list = bdMaterialCategoryService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料产品分类数据", BdMaterialCategoryVo.class, response);
    }

    /**
     * 根据主键获取物料产品分类详细信息
     * <p>权限标识：{@code bd:category:query}</p>
     *
     * @param categoryId 分类 ID
     * @return 物料产品分类详细信息
     */
    @SaCheckPermission("bd:category:query")
    @GetMapping("/{categoryId}")
    public R<BdMaterialCategoryVo> getInfo(@PathVariable Long categoryId) {
        return R.ok(bdMaterialCategoryService.selectById(categoryId));
    }

    /**
     * 新增物料产品分类
     * <p>权限标识：{@code bd:category:add}</p>
     * <p>日志记录：title="物料产品分类"，businessType=INSERT</p>
     *
     * @param bo 物料产品分类业务对象，包含分类名称、编码及上级分类等字段
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:category:add")
    @Log(title = "物料产品分类", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdMaterialCategoryBo bo) {
        return toAjax(bdMaterialCategoryService.insertByBo(bo));
    }

    /**
     * 修改物料产品分类
     * <p>权限标识：{@code bd:category:edit}</p>
     * <p>日志记录：title="物料产品分类"，businessType=UPDATE</p>
     *
     * @param bo 物料产品分类业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:category:edit")
    @Log(title = "物料产品分类", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdMaterialCategoryBo bo) {
        return toAjax(bdMaterialCategoryService.updateByBo(bo));
    }

    /**
     * 批量删除物料产品分类
     * <p>权限标识：{@code bd:category:remove}</p>
     * <p>日志记录：title="物料产品分类"，businessType=DELETE</p>
     *
     * @param categoryIds 分类 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:category:remove")
    @Log(title = "物料产品分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryIds}")
    public R<Void> remove(@PathVariable Long[] categoryIds) {
        return toAjax(bdMaterialCategoryService.deleteByIds(categoryIds));
    }

    /**
     * 排除子节点查询分类列表
     * <p>
     * 查询所有分类，并从结果中排除指定分类及其所有子分类，适用于分类树节点移动或重新分配。</p>
     * <p>权限标识：{@code bd:category:list}</p>
     *
     * @param categoryId 需要排除的分类 ID 数组（支持多个排除）
     * @return 排除后的分类列表
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
     * 获取所有子分类 ID 及对应物料
     * <p>
     * 根据指定分类 ID 数组，递归查询所有子分类及其关联的物料信息。</p>
     *
     * @param categoryIds 分类 ID 数组
     * @return 分类及物料信息，包含子分类列表和关联物料列表
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
