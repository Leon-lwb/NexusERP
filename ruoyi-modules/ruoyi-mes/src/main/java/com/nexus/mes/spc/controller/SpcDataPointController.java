package com.nexus.mes.spc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.spc.domain.bo.SpcDataPointBo;
import com.nexus.mes.spc.domain.vo.SpcDataPointVo;
import com.nexus.mes.spc.service.ISpcDataPointService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MES SPC数据点 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/spc/data-point")
public class SpcDataPointController extends BaseController {

    private final ISpcDataPointService spcDataPointService;

    @SaCheckPermission("mes:spc:point:list")
    @GetMapping("/list")
    public TableDataInfo<SpcDataPointVo> list(SpcDataPointBo bo, PageQuery pageQuery) {
        return spcDataPointService.selectPageList(bo, pageQuery);
    }

    @SaCheckPermission("mes:spc:point:query")
    @GetMapping("/{dataId}")
    public R<SpcDataPointVo> getInfo(@PathVariable Long dataId) {
        return R.ok(spcDataPointService.selectById(dataId));
    }

    /**
     * 按控制图查询全部数据点(按sample_group升序, 供前端绘图)
     */
    @SaCheckPermission("mes:spc:point:query")
    @GetMapping("/by-chart/{chartId}")
    public R<java.util.List<SpcDataPointVo>> listByChart(@PathVariable Long chartId) {
        return R.ok(spcDataPointService.listByChartId(chartId));
    }
}
