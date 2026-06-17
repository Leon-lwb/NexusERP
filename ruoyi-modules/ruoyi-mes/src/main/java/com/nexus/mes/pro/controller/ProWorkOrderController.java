package com.nexus.mes.pro.controller;

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
import com.nexus.mes.pro.domain.bo.ProWorkOrderBo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;
import com.nexus.mes.pro.service.IProWorkOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MES工单 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/pro/work")
public class ProWorkOrderController extends BaseController {

    private final IProWorkOrderService proWorkOrderService;

    /**
     * 分页查询MES工单列表
     */
    @SaCheckPermission("mes:pro:work:list")
    @GetMapping("/list")
    public TableDataInfo<ProWorkOrderVo> list(ProWorkOrderBo bo, PageQuery pageQuery) {
        return proWorkOrderService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出MES工单数据到Excel
     */
    @SaCheckPermission("mes:pro:work:export")
    @Log(title = "MES工单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(ProWorkOrderBo bo, HttpServletResponse response) {
        List<ProWorkOrderVo> list = proWorkOrderService.selectList(bo);
        ExcelUtil.exportExcel(list, "MES工单数据", ProWorkOrderVo.class, response);
    }

    /**
     * 根据ID获取MES工单详情(含工序步骤)
     */
    @SaCheckPermission("mes:pro:work:query")
    @GetMapping("/{mesWoId}")
    public R<ProWorkOrderVo> getInfo(@PathVariable Long mesWoId) {
        return R.ok(proWorkOrderService.selectById(mesWoId));
    }

    /**
     * 新增MES工单
     */
    @SaCheckPermission("mes:pro:work:add")
    @Log(title = "MES工单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<ProWorkOrderVo> add(@Validated @RequestBody ProWorkOrderBo bo) {
        proWorkOrderService.insertByBo(bo);
        return R.ok(proWorkOrderService.selectById(bo.getMesWoId()));
    }

    /**
     * 修改MES工单
     */
    @SaCheckPermission("mes:pro:work:edit")
    @Log(title = "MES工单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@Validated @RequestBody ProWorkOrderBo bo) {
        return toAjax(proWorkOrderService.updateByBo(bo));
    }

    /**
     * 批量删除MES工单
     */
    @SaCheckPermission("mes:pro:work:remove")
    @Log(title = "MES工单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{mesWoIds}")
    public R<Void> remove(@PathVariable Long[] mesWoIds) {
        return toAjax(proWorkOrderService.deleteByIds(mesWoIds));
    }

    /**
     * 下达MES工单(CREATED -> RELEASED)
     */
    @SaCheckPermission("mes:pro:work:edit")
    @Log(title = "MES工单下达", businessType = BusinessType.UPDATE)
    @PutMapping("/release/{mesWoId}")
    public R<Void> release(@PathVariable Long mesWoId) {
        proWorkOrderService.release(mesWoId);
        return R.ok();
    }

    /**
     * 提交审批(CREATED -> APPROVING)
     */
    @SaCheckPermission("mes:pro:work:submit")
    @Log(title = "MES工单提交审批", businessType = BusinessType.UPDATE)
    @PostMapping("/{mesWoId}/submit")
    public R<Void> submit(@PathVariable Long mesWoId,
                          @RequestParam(required = false) String selectFlowId) {
        proWorkOrderService.submit(String.valueOf(mesWoId), selectFlowId);
        return R.ok();
    }

    /**
     * 查询流程实例ID
     */
    @SaCheckPermission("mes:pro:work:query")
    @GetMapping("/{mesWoId}/proc")
    public R<String> getProcInsId(@PathVariable Long mesWoId) {
        return R.ok(proWorkOrderService.getProcInsId(String.valueOf(mesWoId)));
    }
}
