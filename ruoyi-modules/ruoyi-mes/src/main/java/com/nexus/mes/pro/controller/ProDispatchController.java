package com.nexus.mes.pro.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.pro.domain.bo.ProDispatchBo;
import com.nexus.mes.pro.domain.vo.ProDispatchVo;
import com.nexus.mes.pro.service.IProDispatchService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES派工 Controller
 * <p>属于 MES 生产管理模块，提供 工序派工 相关 REST 接口（分页查询、增删改及业务动作）。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/pro/dispatch")
public class ProDispatchController extends BaseController {

    private final IProDispatchService proDispatchService;

    /**
     * 分页查询派工单列表
     */
    @SaCheckPermission("mes:pro:dispatch:list")
    @GetMapping("/list")
    public TableDataInfo<ProDispatchVo> list(ProDispatchBo bo, PageQuery pageQuery) {
        return proDispatchService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取派工单详情
     */
    @SaCheckPermission("mes:pro:dispatch:query")
    @GetMapping("/{dispatchId}")
    public R<ProDispatchVo> getInfo(@PathVariable Long dispatchId) {
        return R.ok(proDispatchService.selectById(dispatchId));
    }

    /**
     * 按工序查询派工单列表
     */
    @SaCheckPermission("mes:pro:dispatch:list")
    @GetMapping("/by-step/{stepRecordId}")
    public R<List<ProDispatchVo>> listByStep(@PathVariable Long stepRecordId) {
        return R.ok(proDispatchService.selectByStepRecordId(stepRecordId));
    }

    /**
     * 按操作员查询派工单列表
     */
    @SaCheckPermission("mes:pro:dispatch:list")
    @GetMapping("/by-operator/{operatorId}")
    public R<List<ProDispatchVo>> listByOperator(@PathVariable Long operatorId) {
        return R.ok(proDispatchService.selectByOperatorId(operatorId));
    }

    /**
     * 创建派工单(从流转卡工序)
     */
    @SaCheckPermission("mes:pro:dispatch:add")
    @Log(title = "MES派工", businessType = BusinessType.INSERT)
    @PostMapping
    public R<ProDispatchVo> add(@RequestParam Long cardId,
                                @RequestParam Long stepRecordId,
                                @RequestParam(required = false) Long operatorId,
                                @RequestParam(required = false) String operatorName,
                                @RequestParam BigDecimal quantity) {
        return R.ok(proDispatchService.createDispatch(cardId, stepRecordId, operatorId, operatorName, quantity));
    }

    /**
     * 开工派工(PENDING -> IN_PROGRESS)
     */
    @SaCheckPermission("mes:pro:dispatch:start")
    @Log(title = "派工开工", businessType = BusinessType.UPDATE)
    @PutMapping("/start/{dispatchId}")
    public R<Void> start(@PathVariable Long dispatchId) {
        proDispatchService.startDispatch(dispatchId);
        return R.ok();
    }

    /**
     * 完工派工(IN_PROGRESS -> COMPLETED)
     */
    @SaCheckPermission("mes:pro:dispatch:complete")
    @Log(title = "派工完工", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{dispatchId}")
    public R<Void> complete(@PathVariable Long dispatchId,
                            @RequestParam BigDecimal goodQty,
                            @RequestParam(required = false, defaultValue = "0") BigDecimal defectQty,
                            @RequestParam(required = false, defaultValue = "0") BigDecimal scrapQty) {
        proDispatchService.completeDispatch(dispatchId, goodQty, defectQty, scrapQty);
        return R.ok();
    }

    /**
     * 取消派工(仅PENDING可取消)
     */
    @SaCheckPermission("mes:pro:dispatch:cancel")
    @Log(title = "派工取消", businessType = BusinessType.UPDATE)
    @PutMapping("/cancel/{dispatchId}")
    public R<Void> cancel(@PathVariable Long dispatchId) {
        proDispatchService.cancelDispatch(dispatchId);
        return R.ok();
    }
}
