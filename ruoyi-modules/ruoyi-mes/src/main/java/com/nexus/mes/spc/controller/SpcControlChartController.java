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
 * MES SPC控制图 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/spc/control-chart")
public class SpcControlChartController extends BaseController {

    private final ISpcControlChartService spcControlChartService;

    @SaCheckPermission("mes:spc:chart:list")
    @GetMapping("/list")
    public TableDataInfo<SpcControlChartVo> list(SpcControlChartBo bo, PageQuery pageQuery) {
        return spcControlChartService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("mes:spc:chart:query")
    @GetMapping("/{chartId}")
    public R<SpcControlChartVo> getInfo(@PathVariable Long chartId) {
        return R.ok(spcControlChartService.selectById(chartId));
    }

    @SaCheckPermission("mes:spc:chart:add")
    @Log(title = "控制图新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<SpcControlChartVo> add(@RequestBody SpcControlChartBo bo) {
        return R.ok(spcControlChartService.insertByBo(bo));
    }

    @SaCheckPermission("mes:spc:chart:edit")
    @Log(title = "控制图编辑", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<SpcControlChartVo> edit(@RequestBody SpcControlChartBo bo) {
        return R.ok(spcControlChartService.updateByBo(bo));
    }

    @SaCheckPermission("mes:spc:chart:remove")
    @Log(title = "控制图删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        return toAjax(spcControlChartService.deleteByIds(ids));
    }

    /**
     * 分析控制图(计算控制限 + OOC判定)
     */
    @SaCheckPermission("mes:spc:chart:analyze")
    @Log(title = "控制图分析", businessType = BusinessType.OTHER)
    @GetMapping("/analyze/{chartId}")
    public R<Map<String, Object>> analyze(@PathVariable Long chartId) {
        return R.ok(spcControlChartService.analyze(chartId));
    }
}
