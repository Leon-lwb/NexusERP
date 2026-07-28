package com.nexus.mes.ncr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.ncr.domain.bo.Ncr8dReportBo;
import com.nexus.mes.ncr.domain.vo.Ncr8dReportVo;
import com.nexus.mes.ncr.service.INcr8dReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * MES NCR 8D报告 Controller
 * <p>属于 MES NCR 不合格品管理模块，提供 8D 报告的获取/创建、编辑、完成操作接口。</p>
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/ncr/8d")
public class Ncr8dReportController extends BaseController {

    private final INcr8dReportService ncr8dReportService;

    /**
     * 获取或创建8D报告(按NCR单ID)
     *
     * @param ncrId NCR单ID
     * @return 8D报告
     */
    @SaCheckPermission("mes:ncr:8d:query")
    @GetMapping("/{ncrId}")
    public R<Ncr8dReportVo> getByNcr(@PathVariable Long ncrId) {
        return R.ok(ncr8dReportService.getOrCreateByNcr(ncrId));
    }

    /**
     * 更新8D报告内容
     *
     * @param bo 8D报告业务对象
     * @return 操作结果
     */
    @SaCheckPermission("mes:ncr:8d:edit")
    @Log(title = "8D报告更新", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody @Validated Ncr8dReportBo bo) {
        return toAjax(ncr8dReportService.updateByBo(bo));
    }

    /**
     * 完成8D报告
     *
     * @param reportId 报告ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:ncr:8d:complete")
    @Log(title = "8D报告完成", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{reportId}")
    public R<Void> complete(@PathVariable Long reportId) {
        return toAjax(ncr8dReportService.complete(reportId));
    }
}