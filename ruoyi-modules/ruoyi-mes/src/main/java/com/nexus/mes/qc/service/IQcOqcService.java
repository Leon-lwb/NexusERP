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
 * OQC检验完成(COMPLETED)后若关联销售订单(soId)则通过MQ回传ERP, 更新销售订单行OQC状态。
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
     * 分页查询 OQC 出货检验列表。
     *
     * @param bo        检验单查询条件（编号/状态/结果/销售订单/物料）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含 OQC 检验单视图列表及分页元信息
     */
    TableDataInfo<QcOqcVo> selectPageList(QcOqcBo bo, PageQuery pageQuery);

    /**
     * 根据主键 ID 获取 OQC 出货检验单详情。
     *
     * @param oqcId OQC 检验单 ID
     * @return OQC 检验单视图对象
     */
    QcOqcVo selectById(Long oqcId);

    /**
     * 新增 OQC 出货检验单，自动生成检验编号并初始化为待检验状态。
     *
     * @param bo OQC 检验单业务对象，包含销售订单、物料、数量等信息
     * @return 新增后的 OQC 检验单视图对象
     */
    QcOqcVo insertByBo(QcOqcBo bo);

    /**
     * 幂等查询：指定销售订单 + 物料是否已存在 OQC（销售出库审批自动建单去重）。
     *
     * @param soId       ERP 销售订单头 ID
     * @param materialId 物料 ID
     * @return 已存在返回 true
     */
    boolean existsBySoAndMaterial(Long soId, Long materialId);

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

    /**
     * 按检验明细执行检验(自动判定): 录入各项目实测值, 系统比对标准自动判定
     * 合格(QUALIFIED)/不合格(UNQUALIFIED), 判定结果走原有 NCR/MQ 回写链路。
     *
     * @param oqcId      OQC 检验单 ID
     * @param entries    明细实测值列表({detailId, measuredValue, sampleQty, remark})
     * @param defectInfo 不良品信息(JSON, 可为空)
     * @param remark     备注(可为空)
     */
    void inspectWithDetails(Long oqcId, java.util.List<com.nexus.mes.qc.domain.bo.QcInspectEntryBo> entries,
                            String defectInfo, String remark);

    /**
     * 查询 OQC 检验单明细(按检验模板快照生成)
     *
     * @param oqcId OQC 检验单 ID
     * @return 明细列表(按序号升序)
     */
    java.util.List<com.nexus.mes.qc.domain.vo.QcInspectDetailVo> selectDetails(Long oqcId);
}
