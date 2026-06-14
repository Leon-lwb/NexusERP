package com.nexus.erp.po.controller;

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
import com.nexus.erp.po.domain.bo.PoRequisitionBo;
import com.nexus.erp.po.domain.vo.PoRequisitionVo;
import com.nexus.erp.po.service.IPoRequisitionService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门请购单 Controller
 * <p>
 * 管理部门请购单信息，包含请购单基本信息、明细行及状态流转。
 * </p>
 *
 * @author nexus
 * @date 2026-06-14
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/po/requisition")
public class PoRequisitionController extends BaseController {

    private final IPoRequisitionService poRequisitionService;

    /**
     * 分页查询部门请购单列表
     * <p>权限标识：{@code po:requisition:list}</p>
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 部门请购单分页列表
     */
    @SaCheckPermission("po:requisition:list")
    @GetMapping("/list")
    public TableDataInfo<PoRequisitionVo> list(PoRequisitionBo bo, PageQuery pageQuery) {
        return poRequisitionService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出部门请购单数据到 Excel
     * <p>权限标识：{@code po:requisition:export}</p>
     *
     * @param bo       查询参数
     * @param response HTTP 响应对象
     */
    @SaCheckPermission("po:requisition:export")
    @Log(title = "部门请购单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PoRequisitionBo bo, HttpServletResponse response) {
        List<PoRequisitionVo> list = poRequisitionService.selectList(bo);
        ExcelUtil.exportExcel(list, "部门请购单数据", PoRequisitionVo.class, response);
    }

    /**
     * 根据主键获取部门请购单详细信息
     * <p>权限标识：{@code po:requisition:query}</p>
     *
     * @param id 请购单ID
     * @return 部门请购单详细信息（含明细行）
     */
    @SaCheckPermission("po:requisition:query")
    @GetMapping("/{id}")
    public R<PoRequisitionVo> getInfo(@PathVariable Long id) {
        return R.ok(poRequisitionService.selectById(id));
    }

    /**
     * 新增部门请购单
     * <p>权限标识：{@code po:requisition:add}</p>
     *
     * @param bo 部门请购单业务对象
     * @return 操作结果
     */
    @SaCheckPermission("po:requisition:add")
    @Log(title = "部门请购单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PoRequisitionBo bo) {
        return toAjax(poRequisitionService.insertByBo(bo));
    }

    /**
     * 修改部门请购单
     * <p>权限标识：{@code po:requisition:edit}</p>
     *
     * @param bo 部门请购单业务对象
     * @return 操作结果
     */
    @SaCheckPermission("po:requisition:edit")
    @Log(title = "部门请购单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PoRequisitionBo bo) {
        return toAjax(poRequisitionService.updateByBo(bo));
    }

    /**
     * 批量删除部门请购单
     * <p>权限标识：{@code po:requisition:remove}</p>
     *
     * @param ids 请购单ID数组
     * @return 操作结果
     */
    @SaCheckPermission("po:requisition:remove")
    @Log(title = "部门请购单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(poRequisitionService.deleteByIds(ids));
    }
}
