package com.nexus.erp.pu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.web.core.BaseController;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.pu.domain.bo.PuContractBo;
import com.nexus.erp.pu.domain.vo.PuContractVo;
import com.nexus.erp.pu.service.IPuContractService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采购合同管理
 *
 * @author nexus
 * @date 2026-06-15
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/pu/contract")
public class PuContractController extends BaseController {

    private final IPuContractService puContractService;

    /**
     * 查询采购合同列表
     */
    @SaCheckPermission("pu:contract:list")
    @GetMapping("/list")
    public TableDataInfo<PuContractVo> list(PuContractBo bo, PageQuery pageQuery) {
        return puContractService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出采购合同列表
     */
    @SaCheckPermission("pu:contract:export")
    @Log(title = "采购合同", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(PuContractBo bo, HttpServletResponse response) {
        List<PuContractVo> list = puContractService.selectList(bo);
        ExcelUtil.exportExcel(list, "采购合同", PuContractVo.class, response);
    }

    /**
     * 获取采购合同详细信息
     */
    @SaCheckPermission("pu:contract:query")
    @GetMapping("/{id}")
    public R<PuContractVo> getInfo(@PathVariable Long id) {
        return R.ok(puContractService.selectById(id));
    }

    /**
     * 新增采购合同
     */
    @SaCheckPermission("pu:contract:add")
    @Log(title = "采购合同", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@Validated @RequestBody PuContractBo bo) {
        return toAjax(puContractService.insertByBo(bo));
    }

    /**
     * 修改采购合同
     */
    @SaCheckPermission("pu:contract:edit")
    @Log(title = "采购合同", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody PuContractBo bo) {
        return toAjax(puContractService.updateByBo(bo));
    }

    /**
     * 删除采购合同
     */
    @SaCheckPermission("pu:contract:remove")
    @Log(title = "采购合同", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(puContractService.deleteByIds(ids));
    }

    /**
     * 提交审批流程
     */
    @SaCheckPermission("pu:contract:edit")
    @Log(title = "采购合同", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/submit")
    public R<Boolean> submit(@RequestParam String contractIdStr, @RequestParam String selectFlowId) {
        return R.ok(puContractService.submit(contractIdStr, selectFlowId));
    }

    /**
     * 获取流程实例ID
     */
    @SaCheckPermission("pu:contract:query")
    @GetMapping("/procInsId/{contractIdStr}")
    public R<String> getProcInsId(@PathVariable String contractIdStr) {
        // 使用 R.ok(String msg, T data) 重载，避免 R.ok(String) 把 ID 误塞进 msg
        return R.ok("查询成功", puContractService.getProcInsId(contractIdStr));
    }

    /**
     * 从合同生成采购订单
     */
    @SaCheckPermission("pu:contract:edit")
    @Log(title = "合同生成采购订单", businessType = BusinessType.INSERT)
    @RepeatSubmit
    @PostMapping("/createOrder/{contractId}")
    public R<Long> createOrder(@PathVariable Long contractId) {
        return R.ok(puContractService.createPurchaseOrderFromContract(contractId));
    }
}
