package com.nexus.erp.so.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.so.domain.bo.SoContractBo;
import com.nexus.erp.so.domain.vo.SoContractVo;
import com.nexus.erp.so.service.ISoContractService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 销售合同 Controller
 * <p>
 * 管理销售合同信息，包含合同基本信息、明细行及审批流程。
 * </p>
 *
 * @author nexus
 * @date 2026-06-14
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/so/contract")
public class SoContractController extends BaseController {

    private final ISoContractService soContractService;

    /**
     * 分页查询销售合同列表
     * <p>权限标识：{@code so:contract:list}</p>
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售合同分页列表
     */
    @SaCheckPermission("so:contract:list")
    @GetMapping("/list")
    public TableDataInfo<SoContractVo> list(SoContractBo bo, PageQuery pageQuery) {
        return soContractService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出销售合同数据到 Excel
     * <p>权限标识：{@code so:contract:export}</p>
     *
     * @param bo       查询参数
     * @param response HTTP 响应对象
     */
    @SaCheckPermission("so:contract:export")
    @Log(title = "销售合同", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(SoContractBo bo, HttpServletResponse response) {
        List<SoContractVo> list = soContractService.selectList(bo);
        ExcelUtil.exportExcel(list, "销售合同数据", SoContractVo.class, response);
    }

    /**
     * 根据主键获取销售合同详细信息
     * <p>权限标识：{@code so:contract:query}</p>
     *
     * @param id 合同ID
     * @return 销售合同详细信息
     */
    @SaCheckPermission("so:contract:query")
    @GetMapping("/{id}")
    public R<SoContractVo> getInfo(@PathVariable Long id) {
        return R.ok(soContractService.selectById(id));
    }

    /**
     * 新增销售合同
     * <p>权限标识：{@code so:contract:add}</p>
     *
     * @param bo 销售合同业务对象
     * @return 操作结果
     */
    @SaCheckPermission("so:contract:add")
    @Log(title = "销售合同", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody SoContractBo bo) {
        return toAjax(soContractService.insertByBo(bo));
    }

    /**
     * 修改销售合同
     * <p>权限标识：{@code so:contract:edit}</p>
     *
     * @param bo 销售合同业务对象
     * @return 操作结果
     */
    @SaCheckPermission("so:contract:edit")
    @Log(title = "销售合同", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SoContractBo bo) {
        return toAjax(soContractService.updateByBo(bo));
    }

    /**
     * 批量删除销售合同
     * <p>权限标识：{@code so:contract:remove}</p>
     *
     * @param ids 合同ID数组
     * @return 操作结果
     */
    @SaCheckPermission("so:contract:remove")
    @Log(title = "销售合同", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(soContractService.deleteByIds(ids));
    }

    /**
     * 提交审批流程（与采购合同同一套提交方式；此前 Service 已实现但未暴露 REST 端点）
     * <p>权限标识：{@code so:contract:edit}</p>
     *
     * @param contractIdStr 合同ID字符串
     * @param selectFlowId  流程定义ID
     * @return 是否启动成功
     */
    @SaCheckPermission("so:contract:edit")
    @Log(title = "销售合同", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/submit")
    public R<Boolean> submit(@RequestParam String contractIdStr, @RequestParam String selectFlowId) {
        return R.ok(soContractService.submit(contractIdStr, selectFlowId));
    }

    /**
     * 获取流程实例ID（运行时优先，其次历史）
     * <p>权限标识：{@code so:contract:query}</p>
     *
     * @param contractIdStr 合同ID字符串
     * @return 流程实例ID，无则返回 null
     */
    @SaCheckPermission("so:contract:query")
    @GetMapping("/procInsId/{contractIdStr}")
    public R<String> getProcInsId(@PathVariable String contractIdStr) {
        // 使用 R.ok(String msg, T data) 重载，避免 R.ok(String) 把实例ID误塞进 msg
        return R.ok("查询成功", soContractService.getProcInsId(contractIdStr));
    }
}
