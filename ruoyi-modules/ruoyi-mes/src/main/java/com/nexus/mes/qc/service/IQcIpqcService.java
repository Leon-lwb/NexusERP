package com.nexus.mes.qc.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.domain.bo.QcIpqcBo;
import com.nexus.mes.qc.domain.vo.QcIpqcVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES过程检验 Service 业务层接口
 * <p>
 * 状态机: PENDING -> INSPECTING -> COMPLETED
 * 检验结果: PENDING -> QUALIFIED / UNQUALIFIED
 * IPQC检验完成(COMPLETED)后若关联ERP工单(经 mesWoId -> ProWorkOrder.erpWoId)则通过MQ回传ERP, 更新工单聚合质检状态。
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
     * 分页查询 IPQC 过程检验列表。
     *
     * @param bo        检验单查询条件（编号/状态/结果/工单/流转卡/物料）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含 IPQC 检验单视图列表及分页元信息
     */
    TableDataInfo<QcIpqcVo> selectPageList(QcIpqcBo bo, PageQuery pageQuery);

    /**
     * 根据主键 ID 获取 IPQC 过程检验单详情。
     *
     * @param ipqcId IPQC 检验单 ID
     * @return IPQC 检验单视图对象
     */
    QcIpqcVo selectById(Long ipqcId);

    /**
     * 新增 IPQC 过程检验单，自动生成检验编号并初始化为待检验状态。
     *
     * @param bo IPQC 检验单业务对象，包含工单、工序、物料、数量等信息
     * @return 新增后的 IPQC 检验单视图对象
     */
    QcIpqcVo insertByBo(QcIpqcBo bo);

    /**
     * 幂等查询：指定工单 + 工序是否已存在 IPQC。
     * <p>
     * 供关键工序报工自动建单去重（同一工单同一关键工序仅建一条 IPQC）。
     *
     * @param mesWoId   MES 工单 ID
     * @param processId 工序 ID
     * @return 已存在返回 true
     */
    boolean existsByWoAndProcess(Long mesWoId, Long processId);

    /**
     * 查询工单下全部 IPQC（供 completeWo 门禁按关键工序校验检验结果）。
     *
     * @param mesWoId MES 工单 ID
     * @return IPQC 列表
     */
    List<QcIpqc> selectByMesWoId(Long mesWoId);

    /**
     * 关键工序报工时自动建 IPQC（幂等：同工单同工序已存在则跳过）。
     * <p>
     * 独立事务(REQUIRES_NEW)：IPQC 创建失败不回滚工序报工事务，由 completeWo 门禁兜底。
     *
     * @param bo IPQC 业务对象（至少含 mesWoId/processId/processCode/processName/materialId 等）
     */
    void autoCreateForKeyProcess(QcIpqcBo bo);

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
