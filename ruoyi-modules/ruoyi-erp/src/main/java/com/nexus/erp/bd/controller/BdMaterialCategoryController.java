package com.nexus.erp.bd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.bd.domain.bo.BdMaterialCategoryBo;
import com.nexus.erp.bd.domain.vo.BdMaterialCategoryVo;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;
import com.nexus.erp.bd.domain.vo.CategoryWithMaterialsVo;
import com.nexus.erp.bd.service.IBdMaterialCategoryService;
import com.nexus.erp.bd.service.IBdMaterialService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
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
     * 查询物料产品分类列表（树形，不分页）
     * <p>权限标识：{@code bd:category:list}</p>
     *
     * @param bo 查询参数，包含分类名称、编码等筛选条件
     * @return 物料产品分类列表
     */
    @SaCheckPermission("bd:category:list")
    @GetMapping("/list")
    public R<List<BdMaterialCategoryVo>> list(BdMaterialCategoryBo bo) {
        List<BdMaterialCategoryVo> list = bdMaterialCategoryService.selectList(bo);
        // 物料数实时填充（不进分类缓存，避免物料增删后陈旧）
        bdMaterialCategoryService.fillMaterialCount(list);
        return R.ok(list);
    }

    /**
     * 导出物料产品分类数据到 Excel
     * <p>权限标识：{@code bd:category:export}</p>
     * <p>日志记录：title="物料产品分类"，businessType=EXPORT</p>
     *
     * @param bo       查询参数
     * @param response HTTP 响应对象，用于输出 Excel 文件流
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
     * <p>编码唯一性、父级存在性、循环引用等校验在 Service 层抛出业务异常。</p>
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
     * <p>父级变更或重命名时，会级联刷新所有子孙的 path_ids/path_names/level_num。</p>
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
     * 修改分类状态（内联开关，轻量更新，不走 path 级联）
     * <p>权限标识：{@code bd:category:edit}</p>
     * <p>日志记录：title="物料产品分类"，businessType=UPDATE</p>
     *
     * @param categoryId 分类 ID
     * @param status     目标状态 0:停用 1:启用
     * @return 操作结果
     */
    @SaCheckPermission("bd:category:edit")
    @Log(title = "物料产品分类", businessType = BusinessType.UPDATE)
    @PutMapping("/{categoryId}/status")
    public R<Void> changeStatus(@PathVariable Long categoryId, @RequestParam Long status) {
        return toAjax(bdMaterialCategoryService.updateStatus(categoryId, status));
    }

    /**
     * 移动分类到新父级（拖拽改父级）
     * <p>权限标识：{@code bd:category:edit}</p>
     * <p>日志记录：title="物料产品分类"，businessType=UPDATE</p>
     * <p>复用 path 级联刷新与循环引用校验；sortOrder 自动追加到新父级子级末尾。</p>
     *
     * @param categoryId 被移动分类 ID
     * @param parentId   新父分类 ID（0=顶级）
     * @return 操作结果
     */
    @SaCheckPermission("bd:category:edit")
    @Log(title = "物料产品分类", businessType = BusinessType.UPDATE)
    @PutMapping("/{categoryId}/move")
    public R<Void> move(@PathVariable Long categoryId, @RequestParam Long parentId) {
        return toAjax(bdMaterialCategoryService.moveCategory(categoryId, parentId));
    }

    /**
     * 批量删除物料产品分类
     * <p>权限标识：{@code bd:category:remove}</p>
     * <p>日志记录：title="物料产品分类"，businessType=DELETE</p>
     * <p>存在下级分类或关联物料时不允许删除（不级联删除）。</p>
     *
     * @param categoryIds 分类 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:category:remove")
    @Log(title = "物料产品分类", businessType = BusinessType.DELETE)
    @DeleteMapping("/{categoryIds}")
    public R<Void> remove(@PathVariable Long[] categoryIds) {
        if (bdMaterialCategoryService.hasChildByCategoryIds(categoryIds)) {
            return R.warn("存在下级分类,不允许删除");
        }
        if (bdMaterialService.hasMaterialByCategoryIds(Arrays.asList(categoryIds))) {
            return R.warn("分类下存在物料,不允许删除");
        }
        return toAjax(bdMaterialCategoryService.deleteByIds(categoryIds));
    }

    /**
     * 排除子节点查询分类列表
     * <p>
     * 查询所有分类，并从结果中排除指定分类及其所有子孙，适用于分类树节点移动或重新分配时选择父级。</p>
     * <p>权限标识：{@code bd:category:list}</p>
     *
     * @param categoryId 需要排除的分类 ID 数组（支持多个排除）
     * @return 排除后的分类列表
     */
    @SaCheckPermission("bd:category:list")
    @GetMapping("/list/exclude/{categoryId}")
    public R<List<BdMaterialCategoryVo>> excludeChild(@PathVariable(value = "categoryId", required = false) Long[] categoryId) {
        return R.ok(bdMaterialCategoryService.selectListExcludeChild(categoryId));
    }

    /**
     * 获取所有子分类 ID 及对应物料
     * <p>
     * 根据指定分类 ID 数组，递归查询所有子分类及其关联的物料信息。</p>
     * <p>权限标识：{@code bd:category:query}</p>
     *
     * @param categoryIds 分类 ID 数组
     * @return 分类及物料信息，包含子分类列表和关联物料列表
     */
    @SaCheckPermission("bd:category:query")
    @GetMapping("/allChildrenIds/{categoryIds}")
    public R<CategoryWithMaterialsVo> allChildrenIds(@PathVariable Long[] categoryIds) {
        if (categoryIds == null || categoryIds.length == 0) {
            return R.ok(new CategoryWithMaterialsVo());
        }
        List<BdMaterialCategoryVo> categories = bdMaterialCategoryService.selectAllChildrenIds(categoryIds);
        List<Long> categoryIdsList = categories.stream()
                .map(BdMaterialCategoryVo::getCategoryId)
                .collect(Collectors.toList());
        List<BdMaterialVo> materials = bdMaterialService.selectByCategoryIds(categoryIdsList);
        CategoryWithMaterialsVo result = new CategoryWithMaterialsVo();
        result.setCategories(categories);
        result.setMaterials(materials);
        return R.ok(result);
    }
}
