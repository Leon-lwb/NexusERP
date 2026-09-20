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
 * MES 过程检验（IPQC）Controller
 * <p>提供过程检验单的分页查询、详情、新增与执行检验等 REST 接口，属于 MES 质量管理模块；
 * 执行检验完成（COMPLETED）后若关联 ERP 工单，则通过 MQ 回传 ERP 更新工单聚合质检状态，检验时自动计算不良率。</p>
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
     * 分页查询 IPQC 过程检验列表。
     *
     * @param bo        检验单查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含 IPQC 检验单视图列表及分页元信息
     */
    @SaCheckPermission("mes:qc:ipqc:list")
    @GetMapping("/list")
    public TableDataInfo<QcIpqcVo> list(QcIpqcBo bo, PageQuery pageQuery) {
        return qcIpqcService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据主键 ID 获取 IPQC 过程检验单详情。
     *
     * @param ipqcId IPQC 检验单 ID
     * @return IPQC 检验单详情
     */
    @SaCheckPermission("mes:qc:ipqc:query")
    @GetMapping("/{ipqcId}")
    public R<QcIpqcVo> getInfo(@PathVariable Long ipqcId) {
        return R.ok(qcIpqcService.selectById(ipqcId));
    }

    /**
     * 新增 IPQC 过程检验单。
     *
     * @param bo IPQC 检验单业务对象
     * @return 新增后的 IPQC 检验单详情
     */
    @SaCheckPermission("mes:qc:ipqc:add")
    @Log(title = "IPQC新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<QcIpqcVo> add(@RequestBody @Validated QcIpqcBo bo) {
        return R.ok(qcIpqcService.insertByBo(bo));
    }

    /**
     * 执行 IPQC 检验：将状态推进为 COMPLETED，并自动计算不良率（不良数/抽样数乘100）。
     *
     * @param ipqcId        IPQC 检验单 ID
     * @param inspectResult 检验结果 QUALIFIED（合格）/ UNQUALIFIED（不合格）
     * @param defectInfo    不良品信息（JSON 字符串，可为空）
     * @param sampleQty     抽样数量（可为空）
     * @param defectQty     不良数量（可为空）
     * @param remark        备注（可为空）
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

    /**
     * 查询 IPQC 检验单明细(按检验模板快照生成的检验项目)。
     *
     * @param ipqcId IPQC 检验单 ID
     * @return 检验明细列表
     */
    @SaCheckPermission("mes:qc:ipqc:query")
    @GetMapping("/{ipqcId}/details")
    public R<java.util.List<com.nexus.mes.qc.domain.vo.QcInspectDetailVo>> getDetails(@PathVariable Long ipqcId) {
        return R.ok(qcIpqcService.selectDetails(ipqcId));
    }

    /**
     * 按检验明细执行检验(自动判定): 录入各项目实测值, 系统比对标准自动判定
     * 合格(QUALIFIED)/不合格(UNQUALIFIED), 判定结果走原有 NCR/MQ 回写链路。
     *
     * @param ipqcId IPQC 检验单 ID
     * @param body   请求体: { details: [{detailId, measuredValue, sampleQty, remark}], defectInfo, remark }
     */
    @SaCheckPermission("mes:qc:ipqc:inspect")
    @Log(title = "IPQC检验(自动判定)", businessType = BusinessType.UPDATE)
    @PutMapping("/inspect-details/{ipqcId}")
    public R<Void> inspectWithDetails(@PathVariable Long ipqcId,
                                      @RequestBody InspectDetailsRequest body) {
        qcIpqcService.inspectWithDetails(ipqcId, body.getDetails(), body.getDefectInfo(), body.getRemark());
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
