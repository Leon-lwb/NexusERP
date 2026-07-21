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
 * MES 来料检验（IQC）Controller
 * <p>提供来料检验单的分页查询、详情、新增与执行检验等 REST 接口，属于 MES 质量管理模块；
 * 执行检验完成（COMPLETED）后通过 MQ 回传 ERP 更新收货单质检状态。</p>
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
     * 分页查询 IQC 来料检验列表。
     *
     * @param bo        检验单查询条件
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含 IQC 检验单视图列表及分页元信息
     */
    @SaCheckPermission("mes:qc:iqc:list")
    @GetMapping("/list")
    public TableDataInfo<QcIqcVo> list(QcIqcBo bo, PageQuery pageQuery) {
        return qcIqcService.selectPageList(bo, pageQuery);
    }

    /**
     * 根据主键 ID 获取 IQC 来料检验单详情。
     *
     * @param iqcId IQC 检验单 ID
     * @return IQC 检验单详情
     */
    @SaCheckPermission("mes:qc:iqc:query")
    @GetMapping("/{iqcId}")
    public R<QcIqcVo> getInfo(@PathVariable Long iqcId) {
        return R.ok(qcIqcService.selectById(iqcId));
    }

    /**
     * 新增 IQC 来料检验单。
     *
     * @param bo IQC 检验单业务对象
     * @return 新增后的 IQC 检验单详情
     */
    @SaCheckPermission("mes:qc:iqc:add")
    @Log(title = "IQC新增", businessType = BusinessType.INSERT)
    @PostMapping
    public R<QcIqcVo> add(@RequestBody QcIqcBo bo) {
        return R.ok(qcIqcService.insertByBo(bo));
    }

    /**
     * 执行 IQC 检验：将状态推进为 COMPLETED，完成后通过 MQ 回传 ERP 更新收货单质检状态。
     *
     * @param iqcId         IQC 检验单 ID
     * @param inspectResult 检验结果 QUALIFIED（合格）/ UNQUALIFIED（不合格）
     * @param defectInfo    不良品信息（JSON 字符串，可为空）
     * @param sampleQty     抽样数量（可为空）
     * @param remark        备注（可为空）
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
