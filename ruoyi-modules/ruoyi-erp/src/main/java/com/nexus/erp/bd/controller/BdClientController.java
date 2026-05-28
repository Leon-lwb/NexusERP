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
import com.nexus.erp.bd.domain.bo.BdClientBo;
import com.nexus.erp.bd.domain.vo.BdClientVo;
import com.nexus.erp.bd.service.IBdClientService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 客户信息 Controller
 * <p>
 * 管理客户基本信息，包含客户编码、名称、联系方式及分类等。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/bd/client")
public class BdClientController extends BaseController {

    private final IBdClientService bdClientService;

    /**
     * 分页查询客户列表
     * <p>权限标识：{@code bd:client:list}</p>
     *
     * @param bo        查询参数，包含客户名称、编码等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 客户分页列表
     */
    @SaCheckPermission("bd:client:list")
    @GetMapping("/list")
    public TableDataInfo<BdClientVo> list(BdClientBo bo, PageQuery pageQuery) {
        return bdClientService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出客户数据到 Excel
     * <p>权限标识：{@code bd:client:export}</p>
     * <p>日志记录：title="客户信息"，businessType=EXPORT</p>
     *
     * @param bo        查询参数
     * @param response  HTTP 响应对象，用于输出 Excel 文件流
     */
    @SaCheckPermission("bd:client:export")
    @Log(title = "客户信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(BdClientBo bo, HttpServletResponse response) {
        List<BdClientVo> list = bdClientService.selectList(bo);
        ExcelUtil.exportExcel(list, "客户数据", BdClientVo.class, response);
    }

    /**
     * 根据主键获取客户详细信息
     * <p>权限标识：{@code bd:client:query}</p>
     *
     * @param id 客户 ID
     * @return 客户详细信息
     */
    @SaCheckPermission("bd:client:query")
    @GetMapping("/{id}")
    public R<BdClientVo> getInfo(@PathVariable Long id) {
        return R.ok(bdClientService.selectById(id));
    }

    /**
     * 新增客户
     * <p>权限标识：{@code bd:client:add}</p>
     * <p>日志记录：title="客户信息"，businessType=INSERT</p>
     *
     * @param bo 客户业务对象，包含客户基本信息字段
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:client:add")
    @Log(title = "客户信息", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody BdClientBo bo) {
        return toAjax(bdClientService.insertByBo(bo));
    }

    /**
     * 修改客户
     * <p>权限标识：{@code bd:client:edit}</p>
     * <p>日志记录：title="客户信息"，businessType=UPDATE</p>
     *
     * @param bo 客户业务对象，包含需要更新的字段信息
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:client:edit")
    @Log(title = "客户信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BdClientBo bo) {
        return toAjax(bdClientService.updateByBo(bo));
    }

    /**
     * 批量删除客户
     * <p>权限标识：{@code bd:client:remove}</p>
     * <p>日志记录：title="客户信息"，businessType=DELETE</p>
     *
     * @param ids 客户 ID 数组（支持批量删除）
     * @return 操作结果，成功返回 {@code R.success()}，失败返回 {@code R.fail()}
     */
    @SaCheckPermission("bd:client:remove")
    @Log(title = "客户信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(bdClientService.deleteByIds(ids));
    }
}
