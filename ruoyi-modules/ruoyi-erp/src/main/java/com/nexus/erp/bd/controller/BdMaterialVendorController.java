package com.nexus.erp.bd.controller;

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
import com.nexus.erp.bd.domain.bo.BdMaterialVendorBo;
import com.nexus.erp.bd.domain.vo.BdMaterialVendorVo;
import com.nexus.erp.bd.service.IBdMaterialVendorService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料-供应商关系 Controller
 * <p>
 * 管理物料与供应商的对应关系，包含供货周期、单价、优先级等信息。
 * </p>
 *
 * @author nexus
 * @date 2026-06-14
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/materialVendor")
public class BdMaterialVendorController extends BaseController {

    private final IBdMaterialVendorService bdMaterialVendorService;

    /**
     * 分页查询物料-供应商关系列表
     * <p>权限标识：{@code bd:materialVendor:list}</p>
     *
     * @param bo        查询参数，包含物料ID、供应商ID等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 物料-供应商关系分页列表
     */
    @SaCheckPermission("bd:materialVendor:list")
    @GetMapping("/list")
    public TableDataInfo<BdMaterialVendorVo> list(BdMaterialVendorBo bo, PageQuery pageQuery) {
        return bdMaterialVendorService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料-供应商关系数据到 Excel
     * <p>权限标识：{@code bd:materialVendor:export}</p>
     * <p>日志记录：title="物料-供应商关系"，businessType=EXPORT</p>
     *
     * @param bo       查询参数
     * @param response HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("bd:materialVendor:export")
    @Log(title = "物料-供应商关系", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdMaterialVendorBo bo, HttpServletResponse response) {
        List<BdMaterialVendorVo> list = bdMaterialVendorService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料-供应商关系数据", BdMaterialVendorVo.class, response);
    }

    /**
     * 根据主键获取物料-供应商关系详细信息
     * <p>权限标识：{@code bd:materialVendor:query}</p>
     *
     * @param id 关系ID
     * @return 物料-供应商关系详细信息
     */
    @SaCheckPermission("bd:materialVendor:query")
    @GetMapping("/{id}")
    public R<BdMaterialVendorVo> getInfo(@PathVariable Long id) {
        return R.ok(bdMaterialVendorService.selectById(id));
    }

    /**
     * 新增物料-供应商关系
     * <p>权限标识：{@code bd:materialVendor:add}</p>
     * <p>日志记录：title="物料-供应商关系"，businessType=INSERT</p>
     *
     * @param bo 物料-供应商业务对象，包含关系基本信息字段
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:materialVendor:add")
    @Log(title = "物料-供应商关系", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdMaterialVendorBo bo) {
        return toAjax(bdMaterialVendorService.insertByBo(bo));
    }

    /**
     * 修改物料-供应商关系
     * <p>权限标识：{@code bd:materialVendor:edit}</p>
     * <p>日志记录：title="物料-供应商关系"，businessType=UPDATE</p>
     *
     * @param bo 物料-供应商业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:materialVendor:edit")
    @Log(title = "物料-供应商关系", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdMaterialVendorBo bo) {
        return toAjax(bdMaterialVendorService.updateByBo(bo));
    }

    /**
     * 批量删除物料-供应商关系
     * <p>权限标识：{@code bd:materialVendor:remove}</p>
     * <p>日志记录：title="物料-供应商关系"，businessType=DELETE</p>
     *
     * @param ids 关系ID数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:materialVendor:remove")
    @Log(title = "物料-供应商关系", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdMaterialVendorService.deleteByIds(ids));
    }
}
