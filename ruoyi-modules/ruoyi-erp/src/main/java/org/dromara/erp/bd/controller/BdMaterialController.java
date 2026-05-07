package org.dromara.erp.bd.controller;

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
import org.dromara.erp.bd.domain.bo.BdMaterialBo;
import org.dromara.erp.bd.domain.vo.BdMaterialVo;
import org.dromara.erp.bd.service.IBdMaterialService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料产品主Controller
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/material")
public class BdMaterialController extends BaseController {

    private final IBdMaterialService bdMaterialService;

    /**
     * 查询物料产品主列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return 物料产品主分页列表
     */
    @SaCheckPermission("bd:material:list")
    @GetMapping("/list")
    public TableDataInfo<BdMaterialVo> list(BdMaterialBo bo, PageQuery pageQuery) {
        return bdMaterialService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出物料产品主数据
     *
     * @param bo 查询参数
     * @param response HTTP响应对象
     */
    @SaCheckPermission("bd:material:export")
    @Log(title = "物料产品主", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdMaterialBo bo, HttpServletResponse response) {
        List<BdMaterialVo> list = bdMaterialService.selectList(bo);
        ExcelUtil.exportExcel(list, "物料产品主数据", BdMaterialVo.class, response);
    }

    /**
     * 获取物料产品主详细信息
     *
     * @param materialId 物料产品主ID
     * @return 物料产品主详细信息
     */
    @SaCheckPermission("bd:material:query")
    @GetMapping("/{materialId}")
    public R<BdMaterialVo> getInfo(@PathVariable Long materialId) {
        return R.ok(bdMaterialService.selectById(materialId));
    }

    /**
     * 新增物料产品主
     *
     * @param bo 物料产品主信息
     * @return 操作结果
     */
    @SaCheckPermission("bd:material:add")
    @Log(title = "物料产品主", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdMaterialBo bo) {
        return toAjax(bdMaterialService.insertByBo(bo));
    }

    /**
     * 修改物料产品主
     *
     * @param bo 物料产品主信息
     * @return 操作结果
     */
    @SaCheckPermission("bd:material:edit")
    @Log(title = "物料产品主", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdMaterialBo bo) {
        return toAjax(bdMaterialService.updateByBo(bo));
    }

    /**
     * 删除物料产品主
     *
     * @param materialIds 物料产品主ID数组
     * @return 操作结果
     */
    @SaCheckPermission("bd:material:remove")
    @Log(title = "物料产品主", businessType = BusinessType.DELETE)
    @DeleteMapping("/{materialIds}")
    public R<Void> remove(@PathVariable Long[] materialIds) {
        return toAjax(bdMaterialService.deleteByIds(materialIds));
    }
}
