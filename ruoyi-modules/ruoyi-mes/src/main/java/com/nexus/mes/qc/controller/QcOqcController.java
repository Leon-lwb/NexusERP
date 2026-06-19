package com.nexus.mes.qc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.qc.domain.bo.QcOqcBo;
import com.nexus.mes.qc.domain.vo.QcOqcVo;
import com.nexus.mes.qc.service.IQcOqcService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * MES出货检验 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/qc/oqc")
public class QcOqcController extends BaseController {

    private final IQcOqcService qcOqcService;

    /**
     * 分页查询OQC列表
     */
    @SaCheckPermission("mes:qc:oqc:list")
    @GetMapping("/list")
    public TableDataInfo<QcOqcVo> list(QcOqcBo bo, PageQuery pageQuery) {
        return qcOqcService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取OQC详情
     */
    @SaCheckPermission("mes:qc:oqc:query")
    @GetMapping("/{oqcId}")
    public R<QcOqcVo> getInfo(@PathVariable Long oqcId) {
        return R.ok(qcOqcService.selectById(oqcId));
    }

    /**
     * 新增OQC检验单
     */
    @SaCheckPermission("mes:qc:oqc:add")
    @Log(title = "OQC新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<QcOqcVo> add(@RequestBody QcOqcBo bo) {
        return R.ok(qcOqcService.insertByBo(bo));
    }

    /**
     * 执行OQC检验
     */
    @SaCheckPermission("mes:qc:oqc:inspect")
    @Log(title = "OQC检验", businessType = BusinessType.UPDATE)
    @PutMapping("/inspect/{oqcId}")
    public R<Void> inspect(@PathVariable Long oqcId,
                           @RequestParam String inspectResult,
                           @RequestParam(required = false) String defectInfo,
                           @RequestParam(required = false) BigDecimal sampleQty,
                           @RequestParam(required = false) String remark) {
        qcOqcService.inspect(oqcId, inspectResult, defectInfo, sampleQty, remark);
        return R.ok();
    }
}
