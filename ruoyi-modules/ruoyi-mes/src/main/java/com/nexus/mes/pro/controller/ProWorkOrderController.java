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
import com.nexus.mes.pro.domain.bo.UpdateStepFirmwareBo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;
import com.nexus.mes.pro.service.IProWorkOrderService;
import com.nexus.mes.pro.service.ProWorkOrderStepProgressService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MES工单 Controller
 * <p>属于 MES 生产管理模块，提供 工单 相关 REST 接口（分页查询、增删改及业务动作）。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/pro/work")
public class ProWorkOrderController extends BaseController {

    private final IProWorkOrderService proWorkOrderService;
    private final ProWorkOrderStepProgressService progressService;

    /** 仅重建工单步骤执行投影，不重复报工、完工、入库或发送消息。 */
    @SaCheckPermission("mes:pro:work:edit")
    @Log(title = "工单工序进度重建", businessType = BusinessType.UPDATE)
    @PostMapping("/{mesWoId}/progress/rebuild")
    public R<Void> rebuildProgress(@PathVariable Long mesWoId) {
        progressService.rebuild(mesWoId);
        return R.ok();
    }

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
     * 更新工单工序的固件版本(仅CREATED状态工单, 仅FLASH工序, 固件须RELEASED且物料匹配)
     * <p>生产负责人在工单下达前确认/更改固件版本; 下达后固件冻结不可再改。</p>
     */
    @SaCheckPermission("mes:pro:work:edit")
    @Log(title = "MES工单工序固件更改", businessType = BusinessType.UPDATE)
    @PutMapping("/{mesWoId}/step/{stepId}/firmware")
    public R<Void> updateStepFirmware(@PathVariable Long mesWoId,
                                      @PathVariable Long stepId,
                                      @Validated @RequestBody UpdateStepFirmwareBo bo) {
        bo.setStepId(stepId);
        proWorkOrderService.updateStepFirmware(mesWoId, bo);
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
        // 使用 R.ok(String msg, T data) 重载，避免 R.ok(String) 把 ID 误塞进 msg
        return R.ok("查询成功", proWorkOrderService.getProcInsId(String.valueOf(mesWoId)));
    }

    /**
     * 取消MES工单(CREATED/RELEASED/APPROVING -> CANCELLED)
     * <p>工单下存在在制流转卡时拒绝取消(MES-PRO-11)。</p>
     */
    @SaCheckPermission("mes:pro:work:cancel")
    @Log(title = "MES工单取消", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{mesWoId}")
    public R<Void> cancel(@PathVariable Long mesWoId,
                          @RequestParam(required = false) String reason) {
        proWorkOrderService.cancel(mesWoId, reason);
        return R.ok();
    }

    /**
     * 关闭MES工单(COMPLETED/PARTIAL_COMPLETED -> CLOSED)
     */
    @SaCheckPermission("mes:pro:work:close")
    @Log(title = "MES工单关闭", businessType = BusinessType.UPDATE)
    @PutMapping("/close/{mesWoId}")
    public R<Void> close(@PathVariable Long mesWoId) {
        proWorkOrderService.close(mesWoId);
        return R.ok();
    }
}
