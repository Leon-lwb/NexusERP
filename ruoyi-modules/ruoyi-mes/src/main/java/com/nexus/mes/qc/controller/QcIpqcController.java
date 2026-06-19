package com.nexus.mes.qc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.qc.domain.bo.QcIpqcBo;
import com.nexus.mes.qc.domain.vo.QcIpqcVo;
import com.nexus.mes.qc.service.IQcIpqcService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * MES过程检验 Controller
 *
 * @author Nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/qc/ipqc")
public class QcIpqcController extends BaseController {

    private final IQcIpqcService qcIpqcService;

    /**
     * 分页查询IPQC列表
     */
    @SaCheckPermission("mes:qc:ipqc:list")
    @GetMapping("/list")
    public TableDataInfo<QcIpqcVo> list(QcIpqcBo bo, PageQuery pageQuery) {
        return qcIpqcService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据ID获取IPQC详情
     */
    @SaCheckPermission("mes:qc:ipqc:query")
    @GetMapping("/{ipqcId}")
    public R<QcIpqcVo> getInfo(@PathVariable Long ipqcId) {
        return R.ok(qcIpqcService.selectById(ipqcId));
    }

    /**
     * 新增IPQC检验单
     */
    @SaCheckPermission("mes:qc:ipqc:add")
    @Log(title = "IPQC新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<QcIpqcVo> add(@RequestBody QcIpqcBo bo) {
        return R.ok(qcIpqcService.insertByBo(bo));
    }

    /**
     * 执行IPQC检验(自动计算不良率)
     */
    @SaCheckPermission("mes:qc:ipqc:inspect")
    @Log(title = "IPQC检验", businessType = BusinessType.UPDATE)
    @PutMapping("/inspect/{ipqcId}")
    public R<Void> inspect(@PathVariable Long ipqcId,
                           @RequestParam String inspectResult,
                           @RequestParam(required = false) String defectInfo,
                           @RequestParam(required = false) BigDecimal sampleQty,
                           @RequestParam(required = false) BigDecimal defectQty,
                           @RequestParam(required = false) String remark) {
        qcIpqcService.inspect(ipqcId, inspectResult, defectInfo, sampleQty, defectQty, remark);
        return R.ok();
    }
}
