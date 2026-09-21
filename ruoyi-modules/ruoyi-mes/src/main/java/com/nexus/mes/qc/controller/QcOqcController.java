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
 * MES 出货检验（OQC）Controller
 * <p>提供出货检验单的分页查询、详情、新增与执行检验等 REST 接口，属于 MES 质量管理模块；
 * 执行检验完成（COMPLETED）后若关联销售订单，则通过 MQ 回传 ERP 更新销售订单行 OQC 状态。</p>
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
     * 分页查询 OQC 出货检验列表。
     *
     * @param bo        检验单查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含 OQC 检验单视图列表及分页元信息
     */
    @SaCheckPermission("mes:qc:oqc:list")
    @GetMapping("/list")
    public TableDataInfo<QcOqcVo> list(QcOqcBo bo, PageQuery pageQuery) {
        return qcOqcService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据主键 ID 获取 OQC 出货检验单详情。
     *
     * @param oqcId OQC 检验单 ID
     * @return OQC 检验单详情
     */
    @SaCheckPermission("mes:qc:oqc:query")
    @GetMapping("/{oqcId}")
    public R<QcOqcVo> getInfo(@PathVariable Long oqcId) {
        return R.ok(qcOqcService.selectById(oqcId));
    }

    /**
     * 新增 OQC 出货检验单。
     *
     * @param bo OQC 检验单业务对象
     * @return 新增后的 OQC 检验单详情
     */
    @SaCheckPermission("mes:qc:oqc:add")
    @Log(title = "OQC新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<QcOqcVo> add(@RequestBody @Validated QcOqcBo bo) {
        return R.ok(qcOqcService.insertByBo(bo));
    }

    /**
     * 执行 OQC 检验：将状态推进为 COMPLETED，记录检验员与检验时间。
     *
     * @param oqcId         OQC 检验单 ID
     * @param inspectResult 检验结果 QUALIFIED（合格）/ UNQUALIFIED（不合格）
     * @param defectInfo    不良品信息（JSON 字符串，可为空）
     * @param sampleQty     抽样数量（可为空）
     * @param remark        备注（可为空）
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

    /**
     * 查询 OQC 检验单明细(按检验模板快照生成的检验项目)。
     *
     * @param oqcId OQC 检验单 ID
     * @return 检验明细列表
     */
    @SaCheckPermission("mes:qc:oqc:query")
    @GetMapping("/{oqcId}/details")
    public R<java.util.List<com.nexus.mes.qc.domain.vo.QcInspectDetailVo>> getDetails(@PathVariable Long oqcId) {
        return R.ok(qcOqcService.selectDetails(oqcId));
    }

    /**
     * 按检验明细执行检验(自动判定): 录入各项目实测值, 系统比对标准自动判定
     * 合格(QUALIFIED)/不合格(UNQUALIFIED), 判定结果走原有 NCR/MQ 回写链路。
     *
     * @param oqcId OQC 检验单 ID
     * @param body  请求体: { details: [{detailId, measuredValue, sampleQty, remark}], defectInfo, remark }
     */
    @SaCheckPermission("mes:qc:oqc:inspect")
    @Log(title = "OQC检验(自动判定)", businessType = BusinessType.UPDATE)
    @PutMapping("/inspect-details/{oqcId}")
    public R<Void> inspectWithDetails(@PathVariable Long oqcId,
                                      @RequestBody InspectDetailsRequest body) {
        qcOqcService.inspectWithDetails(oqcId, body.getDetails(), body.getDefectInfo(), body.getRemark());
        return R.ok();
    }

    /**
     * 明细检验请求体
     */
    @lombok.Data
    public static class InspectDetailsRequest {
        /** 明细实测值列表 */
        private java.util.List<com.nexus.mes.qc.domain.bo.QcInspectEntryBo> details;
        /** 不良品信息(JSON, 可为空) */
        private String defectInfo;
        /** 备注(可为空) */
        private String remark;
    }
}
