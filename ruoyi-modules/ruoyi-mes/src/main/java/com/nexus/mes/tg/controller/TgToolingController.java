package com.nexus.mes.tg.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.tg.domain.bo.TgCalibrationBo;
import com.nexus.mes.tg.domain.bo.TgCheckoutBo;
import com.nexus.mes.tg.domain.bo.TgMaintenanceRecordBo;
import com.nexus.mes.tg.domain.bo.TgToolingBo;
import com.nexus.mes.tg.domain.vo.TgToolingVo;
import com.nexus.mes.tg.service.ITgToolingService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * MES工装台账 Controller
 * <p>提供 工装台账 CRUD + 领用/归还/保养/报废 状态机接口 + P0-3 进站校验契约。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/tg/tooling")
public class TgToolingController extends BaseController {

    private final ITgToolingService tgToolingService;

    /**
     * 分页查询工装台账列表
     */
    @SaCheckPermission("mes:tg:tooling:list")
    @GetMapping("/list")
    public TableDataInfo<TgToolingVo> list(TgToolingBo bo, PageQuery pageQuery) {
        return tgToolingService.selectPageList(bo, pageQuery);
    }

    /**
     * 保养到期预警查询
     */
    @SaCheckPermission("mes:tg:tooling:list")
    @GetMapping("/due")
    public R<List<TgToolingVo>> due() {
        return R.ok(tgToolingService.listMaintenanceDue());
    }

    /**
     * 校准到期/超期预警查询(今天 ~ 今天+7 天, 含已超期) (MES-TG-01/TG-05)
     */
    @SaCheckPermission("mes:tg:tooling:list")
    @GetMapping("/calibration/due")
    public R<List<TgToolingVo>> calibrationDue() {
        return R.ok(tgToolingService.listCalibrationDue());
    }

    /**
     * 登记校准(IDLE -> CALIBRATING -> IDLE, 服务端计算下次校准日) (MES-TG-01)
     */
    @SaCheckPermission("mes:tg:tooling:calibrate")
    @Log(title = "工装校准登记", businessType = BusinessType.UPDATE)
    @PutMapping("/calibration/{toolingId}")
    public R<Void> registerCalibration(@PathVariable Long toolingId,
                                       @RequestBody(required = false) TgCalibrationBo bo) {
        return toAjax(tgToolingService.registerCalibration(toolingId, bo));
    }

    /**
     * 根据ID获取工装详情
     */
    @SaCheckPermission("mes:tg:tooling:query")
    @GetMapping("/{toolingId}")
    public R<TgToolingVo> getInfo(@PathVariable Long toolingId) {
        return R.ok(tgToolingService.selectById(toolingId));
    }

    /**
     * 新增工装(编码自动生成)
     */
    @SaCheckPermission("mes:tg:tooling:add")
    @Log(title = "工装新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Void> add(@RequestBody TgToolingBo bo) {
        return toAjax(tgToolingService.insertByBo(bo));
    }

    /**
     * 修改工装(仅 IDLE 可编辑)
     */
    @SaCheckPermission("mes:tg:tooling:edit")
    @Log(title = "工装修改", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody TgToolingBo bo) {
        return toAjax(tgToolingService.updateByBo(bo));
    }

    /**
     * 批量删除工装(孤儿守卫:存在使用/保养记录则禁止)
     */
    @SaCheckPermission("mes:tg:tooling:remove")
    @Log(title = "工装删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(tgToolingService.deleteByIds(ids));
    }

    /**
     * 领用: IDLE -> IN_USE
     */
    @SaCheckPermission("mes:tg:tooling:checkout")
    @Log(title = "工装领用", businessType = BusinessType.UPDATE)
    @PutMapping("/checkout/{toolingId}")
    public R<Void> checkout(@PathVariable Long toolingId, @RequestBody TgCheckoutBo bo) {
        return toAjax(tgToolingService.checkout(toolingId, bo));
    }

    /**
     * 归还: IN_USE -> IDLE(累加 used_count/hours)
     */
    @SaCheckPermission("mes:tg:tooling:return")
    @Log(title = "工装归还", businessType = BusinessType.UPDATE)
    @PutMapping("/return/{toolingId}")
    public R<Void> doReturn(@PathVariable Long toolingId,
                            @RequestParam(required = false) Integer usageCount,
                            @RequestParam(required = false) BigDecimal usageHours,
                            @RequestParam(required = false) String remark) {
        return toAjax(tgToolingService.doReturn(toolingId, usageCount, usageHours, remark));
    }

    /**
     * 开始保养: IDLE -> MAINTENANCE
     */
    @SaCheckPermission("mes:tg:tooling:maintain")
    @Log(title = "工装开始保养", businessType = BusinessType.UPDATE)
    @PutMapping("/maintain/start/{toolingId}")
    public R<Void> startMaintain(@PathVariable Long toolingId,
                                 @RequestParam(required = false) String remark) {
        return toAjax(tgToolingService.startMaintain(toolingId, remark));
    }

    /**
     * 完成保养: MAINTENANCE -> IDLE(归零 used_count/used_hours + 写保养记录)
     */
    @SaCheckPermission("mes:tg:tooling:maintain")
    @Log(title = "工装完成保养", businessType = BusinessType.UPDATE)
    @PutMapping("/maintain/complete/{toolingId}")
    public R<Void> completeMaintain(@PathVariable Long toolingId, @RequestBody TgMaintenanceRecordBo bo) {
        return toAjax(tgToolingService.completeMaintain(toolingId, bo));
    }

    /**
     * 报废: -> SCRAPPED(终态,IN_USE 需先归还)
     */
    @SaCheckPermission("mes:tg:tooling:scrap")
    @Log(title = "工装报废", businessType = BusinessType.UPDATE)
    @PutMapping("/scrap/{toolingId}")
    public R<Void> scrap(@PathVariable Long toolingId,
                         @RequestParam(required = false) String reason) {
        return toAjax(tgToolingService.scrap(toolingId, reason));
    }

    /**
     * 校验工装可用性(契约: P0-3 进站出站校验调用)
     */
    @SaCheckPermission("mes:tg:tooling:query")
    @GetMapping("/check/{toolingId}")
    public R<Map<String, Object>> checkUsable(@PathVariable Long toolingId) {
        return R.ok(tgToolingService.checkToolingAvailable(toolingId));
    }
}
