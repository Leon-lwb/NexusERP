package com.nexus.mes.eq.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.eq.domain.bo.EqOeeQueryBo;
import com.nexus.mes.eq.domain.vo.EqOeeDistributionVo;
import com.nexus.mes.eq.domain.vo.EqOeeOverviewVo;
import com.nexus.mes.eq.domain.vo.EqOeeRecordVo;
import com.nexus.mes.eq.domain.vo.EqOeeTopVo;
import com.nexus.mes.eq.domain.vo.EqOeeTrendVo;
import com.nexus.mes.eq.service.IEqOeeService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * MES设备 OEE & 稼动率看板 Controller
 *
 * <p>属于MES 设备管理模块，提供OEE相关的 REST 接口。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/eq/oee")
public class EqOeeController extends BaseController {

    private final IEqOeeService eqOeeService;

    /**
     * 看板概览 KPI
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:oee:query")
    @GetMapping("/overview")
    public R<EqOeeOverviewVo> overview(EqOeeQueryBo bo) {
        return R.ok(eqOeeService.overview(bo));
    }

    /**
     * 看板趋势(按日)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:oee:query")
    @GetMapping("/trend")
    public R<List<EqOeeTrendVo>> trend(EqOeeQueryBo bo) {
        return R.ok(eqOeeService.trend(bo));
    }

    /**
     * 看板时长分布(运行/停机/保养占比)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:oee:query")
    @GetMapping("/distribution")
    public R<EqOeeDistributionVo> distribution(EqOeeQueryBo bo) {
        return R.ok(eqOeeService.distribution(bo));
    }

    /**
     * 看板停机设备 TOP10
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:oee:query")
    @GetMapping("/topDowntime")
    public R<List<EqOeeTopVo>> topDowntime(EqOeeQueryBo bo) {
        return R.ok(eqOeeService.topDowntime(bo));
    }

    /**
     * 分页查询 OEE 明细记录
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @SaCheckPermission("mes:eq:oee:list")
    @GetMapping("/list")
    public TableDataInfo<EqOeeRecordVo> list(EqOeeQueryBo bo, PageQuery pageQuery) {
        return eqOeeService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出 OEE 明细数据
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param response HTTP 响应对象
     */
    @SaCheckPermission("mes:eq:oee:export")
    @Log(title = "OEE看板", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(EqOeeQueryBo bo, HttpServletResponse response) {
        List<EqOeeRecordVo> list = eqOeeService.selectList(bo);
        ExcelUtil.exportExcel(list, "OEE看板", EqOeeRecordVo.class, response);
    }

    /**
     * 手动聚合指定日期的 OEE(date 为空则聚合昨天)
     *
     * @param date 统计日期（null 表示聚合昨天）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:oee:aggregate")
    @Log(title = "OEE手动聚合", businessType = BusinessType.OTHER)
    @PostMapping("/aggregate")
    public R<Integer> aggregate(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date date) {
        return R.ok(eqOeeService.aggregate(date));
    }
}
