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
import com.nexus.mes.eq.domain.bo.EqRepairCompleteBo;
import com.nexus.mes.eq.domain.vo.EqRepairStatisticsVo;
import com.nexus.mes.eq.domain.vo.EqRepairVo;
import com.nexus.mes.eq.service.IEqRepairService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import com.nexus.common.excel.utils.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.List;
import java.util.Map;

/**
 * MES设备维修记录 Controller
 *
 * <p>属于MES 设备管理模块，提供维修相关的 REST 接口。</p>
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
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @SaCheckPermission("mes:eq:repair:list")
    @GetMapping("/list")
    public TableDataInfo<EqRepairVo> list(EqRepairBo bo, PageQuery pageQuery) {
        return eqRepairService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取维修详情
     *
     * @param repairId 维修记录ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:repair:query")
    @GetMapping("/{repairId}")
    public R<EqRepairVo> getInfo(@PathVariable Long repairId) {
        return R.ok(eqRepairService.selectById(repairId));
    }

    /**
     * 报修(新增维修单), 联动置设备FAULT
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:repair:add")
    @Log(title = "维修报修", businessType = BusinessType.INSERT)
    @PostMapping
    public R<EqRepairVo> add(@Validated @RequestBody EqRepairBo bo) {
        return R.ok(eqRepairService.insertByBo(bo));
    }

    /**
     * 维修开工(REPORTED -> REPAIRING)
     *
     * @param repairId 维修记录ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:repair:start")
    @Log(title = "维修开工", businessType = BusinessType.UPDATE)
    @PutMapping("/start/{repairId}")
    public R<Void> start(@PathVariable Long repairId) {
        eqRepairService.startRepair(repairId);
        return R.ok();
    }

    /**
     * 维修完成(REPAIRING -> COMPLETED), 联动恢复设备状态; 可携带更换备件明细(P1-2 R1)
     *
     * @param repairId 维修记录ID
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:repair:complete")
    @Log(title = "维修完成", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{repairId}")
    public R<Void> complete(@PathVariable Long repairId, @RequestBody EqRepairCompleteBo bo) {
        eqRepairService.completeRepair(repairId, bo);
        return R.ok();
    }

    /**
     * 导出维修记录数据
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param response HTTP 响应对象
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

    /**
     * 维修状态计数汇总(监控大屏用, 全量 COUNT 不受分页限制)
     *
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:repair:list")
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        return R.ok(eqRepairService.statusSummary());
    }
}
