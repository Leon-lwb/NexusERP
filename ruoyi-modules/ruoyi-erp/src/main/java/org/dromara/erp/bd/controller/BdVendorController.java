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
 * 供应商信息 Controller
 * <p>
 * 管理供应商基本信息，包含供应商编码、名称、联系方式及分类等。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/vendor")
public class BdVendorController extends BaseController {

    private final IBdVendorService bdVendorService;

    /**
     * 分页查询供应商列表
     * <p>权限标识：{@code bd:vendor:list}</p>
     *
     * @param bo        查询参数，包含供应商名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 供应商分页列表
     */
    @SaCheckPermission("bd:vendor:list")
    @GetMapping("/list")
    public TableDataInfo<BdVendorVo> list(BdVendorBo bo, PageQuery pageQuery) {
        return bdVendorService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出供应商数据到 Excel
     * <p>权限标识：{@code bd:vendor:export}</p>
     * <p>日志记录：title="供应商"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("bd:vendor:export")
    @Log(title = "供应商", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdVendorBo bo, HttpServletResponse response) {
        List<BdVendorVo> list = bdVendorService.selectList(bo);
        ExcelUtil.exportExcel(list, "供应商数据", BdVendorVo.class, response);
    }

    /**
     * 根据主键获取供应商详细信息
     * <p>权限标识：{@code bd:vendor:query}</p>
     *
     * @param id 供应商 ID
     * @return 供应商详细信息
     */
    @SaCheckPermission("bd:vendor:query")
    @GetMapping("/{id}")
    public R<BdVendorVo> getInfo(@PathVariable Long id) {
        return R.ok(bdVendorService.selectById(id));
    }

    /**
     * 新增供应商
     * <p>权限标识：{@code bd:vendor:add}</p>
     * <p>日志记录：title="供应商"，businessType=INSERT</p>
     *
     * @param bo 供应商业务对象，包含供应商基本信息字段
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:vendor:add")
    @Log(title = "供应商", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdVendorBo bo) {
        return toAjax(bdVendorService.insertByBo(bo));
    }

    /**
     * 修改供应商
     * <p>权限标识：{@code bd:vendor:edit}</p>
     * <p>日志记录：title="供应商"，businessType=UPDATE</p>
     *
     * @param bo 供应商业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:vendor:edit")
    @Log(title = "供应商", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdVendorBo bo) {
        return toAjax(bdVendorService.updateByBo(bo));
    }

    /**
     * 批量删除供应商
     * <p>权限标识：{@code bd:vendor:remove}</p>
     * <p>日志记录：title="供应商"，businessType=DELETE</p>
     *
     * @param ids 供应商 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:vendor:remove")
    @Log(title = "供应商", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdVendorService.deleteByIds(ids));
    }
}
