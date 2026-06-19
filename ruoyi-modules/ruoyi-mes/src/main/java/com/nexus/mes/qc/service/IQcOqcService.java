package com.nexus.mes.qc.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.qc.domain.bo.QcOqcBo;
import com.nexus.mes.qc.domain.vo.QcOqcVo;

import java.math.BigDecimal;

/**
 * MES出货检验 Service 业务层接口
 * <p>
 * 状态机: PENDING -> INSPECTING -> COMPLETED
 * 检验结果: PENDING -> QUALIFIED / UNQUALIFIED
 * OQC纯MES内部检验, 不发MQ回传ERP
 *
 * @author Nexus
 */
public interface IQcOqcService {

    /** 状态: 待检验 */
    String STATUS_PENDING = "PENDING";
    /** 状态: 检验中 */
    String STATUS_INSPECTING = "INSPECTING";
    /** 状态: 已完成 */
    String STATUS_COMPLETED = "COMPLETED";

    /** 检验结果: 合格 */
    String RESULT_QUALIFIED = "QUALIFIED";
    /** 检验结果: 不合格 */
    String RESULT_UNQUALIFIED = "UNQUALIFIED";

    /**
     * 分页查询OQC列表
     */
    TableDataInfo<QcOqcVo> selectPageList(QcOqcBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取OQC详情
     */
    QcOqcVo selectById(Long oqcId);

    /**
     * 新增OQC检验单(生成编号)
     */
    QcOqcVo insertByBo(QcOqcBo bo);

    /**
     * 执行检验(PENDING/INSPECTING -> COMPLETED)
     *
     * @param oqcId         OQC ID
     * @param inspectResult 检验结果(QUALIFIED/UNQUALIFIED)
     * @param defectInfo    不良品信息(JSON String)
     * @param sampleQty     抽样数量
     * @param remark        备注
     */
    void inspect(Long oqcId, String inspectResult, String defectInfo,
                 BigDecimal sampleQty, String remark);
}
