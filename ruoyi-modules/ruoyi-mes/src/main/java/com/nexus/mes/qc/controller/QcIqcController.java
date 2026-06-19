package com.nexus.mes.qc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.qc.domain.bo.QcIqcBo;
import com.nexus.mes.qc.domain.vo.QcIqcVo;
import com.nexus.mes.qc.service.IQcIqcService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * MES来料检验 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/qc/iqc")
public class QcIqcController extends BaseController {

    private final IQcIqcService qcIqcService;

    /**
     * 分页查询IQC列表
     */
    @SaCheckPermission("mes:qc:iqc:list")
    @GetMapping("/list")
    public TableDataInfo<QcIqcVo> list(QcIqcBo bo, PageQuery pageQuery) {
        return qcIqcService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取IQC详情
     */
    @SaCheckPermission("mes:qc:iqc:query")
    @GetMapping("/{iqcId}")
    public R<QcIqcVo> getInfo(@PathVariable Long iqcId) {
        return R.ok(qcIqcService.selectById(iqcId));
    }

    /**
     * 新增IQC检验单
     */
    @SaCheckPermission("mes:qc:iqc:add")
    @Log(title = "IQC新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<QcIqcVo> add(@RequestBody QcIqcBo bo) {
        return R.ok(qcIqcService.insertByBo(bo));
    }

    /**
     * 执行IQC检验(PENDING/INSPECTING -> COMPLETED), 完成后MQ回传ERP
     */
    @SaCheckPermission("mes:qc:iqc:inspect")
    @Log(title = "IQC检验", businessType = BusinessType.UPDATE)
    @PutMapping("/inspect/{iqcId}")
    public R<Void> inspect(@PathVariable Long iqcId,
                           @RequestParam String inspectResult,
                           @RequestParam(required = false) String defectInfo,
                           @RequestParam(required = false) BigDecimal sampleQty,
                           @RequestParam(required = false) String remark) {
        qcIqcService.inspect(iqcId, inspectResult, defectInfo, sampleQty, remark);
        return R.ok();
    }
}
