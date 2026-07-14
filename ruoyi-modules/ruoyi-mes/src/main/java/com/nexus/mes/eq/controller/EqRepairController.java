package com.nexus.mes.eq.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.eq.domain.bo.EqRepairBo;
import com.nexus.mes.eq.domain.vo.EqRepairStatisticsVo;
import com.nexus.mes.eq.domain.vo.EqRepairVo;
import com.nexus.mes.eq.service.IEqRepairService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Date;
import com.nexus.common.excel.utils.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.List;

/**
 * MES设备维修记录 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/eq/repair")
public class EqRepairController extends BaseController {

    private final IEqRepairService eqRepairService;

    /**
     * 分页查询维修记录列表
     */
    @SaCheckPermission("mes:eq:repair:list")
    @GetMapping("/list")
    public TableDataInfo<EqRepairVo> list(EqRepairBo bo, PageQuery pageQuery) {
        return eqRepairService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取维修详情
     */
    @SaCheckPermission("mes:eq:repair:query")
    @GetMapping("/{repairId}")
    public R<EqRepairVo> getInfo(@PathVariable Long repairId) {
        return R.ok(eqRepairService.selectById(repairId));
    }

    /**
     * 报修(新增维修单), 联动置设备FAULT
     */
    @SaCheckPermission("mes:eq:repair:add")
    @Log(title = "维修报修", businessType = BusinessType.INSERT)
    @PostMapping
    public R<EqRepairVo> add(@Validated @RequestBody EqRepairBo bo) {
        return R.ok(eqRepairService.insertByBo(bo));
    }

    /**
     * 维修开工(REPORTED -> REPAIRING)
     */
    @SaCheckPermission("mes:eq:repair:start")
    @Log(title = "维修开工", businessType = BusinessType.UPDATE)
    @PutMapping("/start/{repairId}")
    public R<Void> start(@PathVariable Long repairId) {
        eqRepairService.startRepair(repairId);
        return R.ok();
    }

    /**
     * 维修完成(REPAIRING -> COMPLETED), 联动恢复设备状态
     */
    @SaCheckPermission("mes:eq:repair:complete")
    @Log(title = "维修完成", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{repairId}")
    public R<Void> complete(@PathVariable Long repairId,
                            @RequestParam(required = false) BigDecimal downtimeHours,
                            @RequestParam(required = false) BigDecimal repairCost,
                            @RequestParam(required = false) String remark) {
        eqRepairService.completeRepair(repairId, downtimeHours, repairCost, remark);
        return R.ok();
    }

    /**
     * 导出维修记录数据
     */
    @SaCheckPermission("mes:eq:repair:export")
    @Log(title = "维修记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EqRepairBo bo, HttpServletResponse response) {
        List<EqRepairVo> list = eqRepairService.selectList(bo);
        ExcelUtil.exportExcel(list, "维修记录", EqRepairVo.class, response);
    }

    /**
     * 故障统计分析(KPI + 趋势 + Pareto + 设备TOP)
     */
    @SaCheckPermission("mes:eq:repair:query")
    @GetMapping("/statistics")
    public R<EqRepairStatisticsVo> statistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        return R.ok(eqRepairService.statistics(startDate, endDate));
    }
}
