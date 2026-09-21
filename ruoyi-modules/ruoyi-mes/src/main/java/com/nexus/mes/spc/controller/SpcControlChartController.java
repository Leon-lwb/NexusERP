package com.nexus.mes.spc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.spc.domain.bo.SpcControlChartBo;
import com.nexus.mes.spc.domain.vo.SpcControlChartVo;
import com.nexus.mes.spc.service.ISpcControlChartService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * MES 统计过程控制（SPC）控制图 Controller
 * <p>提供控制图的分页查询、详情、新增、编辑、删除及分析（计算控制限 + OOC 判定）等 REST 接口，
 * 属于 MES 统计过程控制模块。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/spc/control-chart")
public class SpcControlChartController extends BaseController {

    private final ISpcControlChartService spcControlChartService;

    /**
     * 分页查询 SPC 控制图列表。
     *
     * @param bo        控制图查询条件（名称/类型/状态/采集点/物料）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含控制图视图列表及分页元信息
     */
    @SaCheckPermission("mes:spc:chart:list")
    @GetMapping("/list")
    public TableDataInfo<SpcControlChartVo> list(SpcControlChartBo bo, PageQuery pageQuery) {
        return spcControlChartService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据主键 ID 获取 SPC 控制图详情。
     *
     * @param chartId 控制图 ID
     * @return 控制图详情
     */
    @SaCheckPermission("mes:spc:chart:query")
    @GetMapping("/{chartId}")
    public R<SpcControlChartVo> getInfo(@PathVariable Long chartId) {
        return R.ok(spcControlChartService.selectById(chartId));
    }

    /**
     * 新增 SPC 控制图。
     *
     * @param bo 控制图业务对象
     * @return 新增后的控制图详情
     */
    @SaCheckPermission("mes:spc:chart:add")
    @Log(title = "控制图新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<SpcControlChartVo> add(@RequestBody SpcControlChartBo bo) {
        return R.ok(spcControlChartService.insertByBo(bo));
    }

    /**
     * 编辑 SPC 控制图基础信息（不含控制限，控制限由分析接口回写）。
     *
     * @param bo 控制图业务对象（需包含 chartId）
     * @return 编辑后的控制图详情
     */
    @SaCheckPermission("mes:spc:chart:edit")
    @Log(title = "控制图编辑", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<SpcControlChartVo> edit(@RequestBody SpcControlChartBo bo) {
        return R.ok(spcControlChartService.updateByBo(bo));
    }

    /**
     * 批量删除 SPC 控制图（级联删除其下数据点）。
     *
     * @param ids 待删除的控制图 ID 数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:spc:chart:remove")
    @Log(title = "控制图删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(spcControlChartService.deleteByIds(ids));
    }

    /**
     * 分析 SPC 控制图：拉取采集点数据、按样本分组、计算 UCL/CL/LCL（含次级图 R/S/MR 控制限）并回写，
     * 按 sample_group 增量维护数据点并执行 OOC 判定（主图 + 组内离散度各判一遍）。
     * <p>写操作，使用 POST（原 GET 会被浏览器/网关预取或重放导致重复写库）。</p>
     *
     * @param chartId 控制图 ID
     * @return 分析结果，包含 chart / points / oocCount / ucl / cl / lcl / rUcl / rCl / rLcl / pp / ppk
     */
    @SaCheckPermission("mes:spc:chart:analyze")
    @Log(title = "控制图分析", businessType = BusinessType.OTHER)
    @PostMapping("/analyze/{chartId}")
    public R<Map<String, Object>> analyze(@PathVariable Long chartId) {
        return R.ok(spcControlChartService.analyze(chartId));
    }
}
