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
import org.dromara.erp.bd.domain.bo.BdVendorBo;
import org.dromara.erp.bd.domain.vo.BdVendorVo;
import org.dromara.erp.bd.service.IBdVendorService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 供应商信息Controller
 *
 * @author wanghuan
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/vendor")
public class BdVendorController extends BaseController {

    private final IBdVendorService bdVendorService;

    /**
     * 查询供应商列表
     *
     * @param bo 查询参数
     * @param pageQuery 分页参数
     * @return 供应商分页列表
     */
    @SaCheckPermission("bd:vendor:list")
    @GetMapping("/list")
    public TableDataInfo<BdVendorVo> list(BdVendorBo bo, PageQuery pageQuery) {
        return bdVendorService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出供应商数据
     *
     * @param bo 查询参数
     * @param response HTTP响应对象
     */
    @SaCheckPermission("bd:vendor:export")
    @Log(title = "供应商", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdVendorBo bo, HttpServletResponse response) {
        List<BdVendorVo> list = bdVendorService.selectList(bo);
        ExcelUtil.exportExcel(list, "供应商数据", BdVendorVo.class, response);
    }

    /**
     * 获取供应商详细信息
     *
     * @param id 供应商ID
     * @return 供应商详细信息
     */
    @SaCheckPermission("bd:vendor:query")
    @GetMapping("/{id}")
    public R<BdVendorVo> getInfo(@PathVariable Long id) {
        return R.ok(bdVendorService.selectById(id));
    }

    /**
     * 新增供应商
     *
     * @param bo 供应商信息
     * @return 操作结果
     */
    @SaCheckPermission("bd:vendor:add")
    @Log(title = "供应商", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdVendorBo bo) {
        return toAjax(bdVendorService.insertByBo(bo));
    }

    /**
     * 修改供应商
     *
     * @param bo 供应商信息
     * @return 操作结果
     */
    @SaCheckPermission("bd:vendor:edit")
    @Log(title = "供应商", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdVendorBo bo) {
        return toAjax(bdVendorService.updateByBo(bo));
    }

    /**
     * 删除供应商
     *
     * @param ids 供应商ID数组
     * @return 操作结果
     */
    @SaCheckPermission("bd:vendor:remove")
    @Log(title = "供应商", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdVendorService.deleteByIds(ids));
    }
}
