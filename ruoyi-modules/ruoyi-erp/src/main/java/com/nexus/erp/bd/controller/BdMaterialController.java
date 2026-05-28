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
import com.nexus.erp.bd.domain.bo.BdMaterialBo;
import com.nexus.erp.bd.domain.vo.BdMaterialVo;
import com.nexus.erp.bd.service.IBdMaterialService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料产品主 Controller
 * <p>
 * 管理物料/产品主数据，包含物料编码、名称、规格型号、计量单位等基础信息。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/material")
public class BdMaterialController extends BaseController {

    private final IBdMaterialService bdMaterialService;

    /**
     * 分页查询物料产品主列表
     * <p>权限标识：{@code bd:material:list}</p>
     *
     * @param bo        查询参数，包含物料编码、名称等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 物料产品主分页列表
     */
    @SaCheckPermission("bd:material:list")
    @GetMapping("/list")
    public TableDataInfo<BdMaterialVo> list(BdMaterialBo bo, PageQuery pageQuery) {
        return bdMaterialService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料产品主数据到 Excel
     * <p>权限标识：{@code bd:material:export}</p>
     * <p>日志记录：title="物料产品主"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("bd:material:export")
    @Log(title = "物料产品主", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdMaterialBo bo, HttpServletResponse response) {
        List<BdMaterialVo> list = bdMaterialService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料产品主数据", BdMaterialVo.class, response);
    }

    /**
     * 根据主键获取物料产品主详细信息
     * <p>权限标识：{@code bd:material:query}</p>
     *
     * @param materialId 物料产品主 ID
     * @return 物料产品主详细信息
     */
    @SaCheckPermission("bd:material:query")
    @GetMapping("/{materialId}")
    public R<BdMaterialVo> getInfo(@PathVariable Long materialId) {
        return R.ok(bdMaterialService.selectById(materialId));
    }

    /**
     * 新增物料产品主
     * <p>权限标识：{@code bd:material:add}</p>
     * <p>日志记录：title="物料产品主"，businessType=INSERT</p>
     *
     * @param bo 物料产品主业务对象，包含物料基本信息字段
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:material:add")
    @Log(title = "物料产品主", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdMaterialBo bo) {
        return toAjax(bdMaterialService.insertByBo(bo));
    }

    /**
     * 修改物料产品主
     * <p>权限标识：{@code bd:material:edit}</p>
     * <p>日志记录：title="物料产品主"，businessType=UPDATE</p>
     *
     * @param bo 物料产品主业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:material:edit")
    @Log(title = "物料产品主", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdMaterialBo bo) {
        return toAjax(bdMaterialService.updateByBo(bo));
    }

    /**
     * 批量删除物料产品主
     * <p>权限标识：{@code bd:material:remove}</p>
     * <p>日志记录：title="物料产品主"，businessType=DELETE</p>
     *
     * @param materialIds 物料产品主 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:material:remove")
    @Log(title = "物料产品主", businessType = BusinessType.DELETE)
    @DeleteMapping("/{materialIds}")
    public R<Void> remove(@PathVariable Long[] materialIds) {
        return toAjax(bdMaterialService.deleteByIds(materialIds));
    }
}
