package com.nexus.mes.qc.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.qc.domain.bo.QcIpqcBo;
import com.nexus.mes.qc.domain.vo.QcIpqcVo;

import java.math.BigDecimal;

/**
 * MES过程检验 Service 业务层接口
 * <p>
 * 状态机: PENDING -> INSPECTING -> COMPLETED
 * 检验结果: PENDING -> QUALIFIED / UNQUALIFIED
 * IPQC纯MES内部检验, 不发MQ回传ERP
 *
 * @author Nexus
 */
public interface IQcIpqcService {

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
     * 分页查询IPQC列表
     */
    TableDataInfo<QcIpqcVo> selectPageList(QcIpqcBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取IPQC详情
     */
    QcIpqcVo selectById(Long ipqcId);

    /**
     * 新增IPQC检验单(生成编号)
     */
    QcIpqcVo insertByBo(QcIpqcBo bo);

    /**
     * 执行检验(PENDING/INSPECTING -> COMPLETED), 自动计算不良率
     *
     * @param ipqcId        IPQC ID
     * @param inspectResult 检验结果(QUALIFIED/UNQUALIFIED)
     * @param defectInfo    不良品信息(JSON String)
     * @param sampleQty     抽样数量
     * @param defectQty     不良数量(用于计算不良率)
     * @param remark        备注
     */
    void inspect(Long ipqcId, String inspectResult, String defectInfo,
                 BigDecimal sampleQty, BigDecimal defectQty, String remark);
}
