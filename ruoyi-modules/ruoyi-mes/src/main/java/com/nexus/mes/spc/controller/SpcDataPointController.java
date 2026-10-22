package com.nexus.mes.spc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.spc.domain.bo.SpcDataPointBo;
import com.nexus.mes.spc.domain.vo.SpcDataPointVo;
import com.nexus.mes.spc.service.ISpcDataPointService;

import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MES 统计过程控制（SPC）数据点 Controller
 * <p>提供 SPC 数据点的分页查询、详情及按控制图查询全部数据点（供前端绘图）等 REST 接口，
 * 属于 MES 统计过程控制模块。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/spc/data-point")
public class SpcDataPointController extends BaseController {

    private final ISpcDataPointService spcDataPointService;

    /**
     * 分页查询 SPC 数据点列表。
     *
     * @param bo        数据点查询条件（控制图/样本组/OOC 标志）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含数据点视图列表及分页元信息
     */
    @SaCheckPermission("mes:spc:point:list")
    @GetMapping("/list")
    public TableDataInfo<SpcDataPointVo> list(SpcDataPointBo bo, PageQuery pageQuery) {
        return spcDataPointService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据主键 ID 获取 SPC 数据点详情。
     *
     * @param dataId 数据点 ID
     * @return 数据点详情
     */
    @SaCheckPermission("mes:spc:point:query")
    @GetMapping("/{dataId}")
    public R<SpcDataPointVo> getInfo(@PathVariable Long dataId) {
        return R.ok(spcDataPointService.selectById(dataId));
    }

    /**
     * 按控制图查询其全部数据点（按样本组号升序），供前端绘制控制图。
     *
     * @param chartId 控制图 ID
     * @return 该控制图下的数据点列表
     */
    @SaCheckPermission("mes:spc:point:query")
    @GetMapping("/by-chart/{chartId}")
    public R<List<SpcDataPointVo>> listByChart(@PathVariable Long chartId) {
        return R.ok(spcDataPointService.listByChartId(chartId));
    }
}
