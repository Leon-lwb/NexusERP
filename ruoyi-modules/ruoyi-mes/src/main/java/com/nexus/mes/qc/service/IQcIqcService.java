package com.nexus.mes.qc.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.qc.domain.bo.QcInspectEntryBo;
import com.nexus.mes.qc.domain.bo.QcIqcBo;
import com.nexus.mes.qc.domain.vo.QcInspectDetailVo;
import com.nexus.mes.qc.domain.vo.QcIqcVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES来料检验 Service 业务层接口
 * <p>
 * 状态机: PENDING -> INSPECTING -> COMPLETED
 * 检验结果: PENDING -> QUALIFIED / UNQUALIFIED
 * IQC检验完成(COMPLETED)后通过MQ回传ERP, 更新收货单质检状态
 *
 * @author Nexus
 */
public interface IQcIqcService {

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
     * 分页查询 IQC 来料检验列表。
     *
     * @param bo        检验单查询条件（编号/状态/结果/收货单/物料/供应商）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含 IQC 检验单视图列表及分页元信息
     */
    TableDataInfo<QcIqcVo> selectPageList(QcIqcBo bo, PageQuery pageQuery);

    /**
     * 根据主键 ID 获取 IQC 来料检验单详情。
     *
     * @param iqcId IQC 检验单 ID
     * @return IQC 检验单视图对象
     */
    QcIqcVo selectById(Long iqcId);

    /**
     * 新增 IQC 来料检验单，自动生成检验编号并初始化为待检验状态。
     *
     * @param bo IQC 检验单业务对象，包含物料、供应商、数量等信息
     * @return 新增后的 IQC 检验单视图对象
     */
    QcIqcVo insertByBo(QcIqcBo bo);

    /**
     * 幂等查询：指定收货单 + 物料是否已存在 IQC 检验单。
     * <p>
     * 供 MQ 消费者（IqcAutoCreateConsumer）调用——消费者线程无 {@code @DS} 上下文，
     * 直接调 Mapper 会落到默认 master 库（ry-vue）报 "Table 'ry-vue.qc_iqc' doesn't exist"，
     * 故必须经此 Service 方法走类级 {@code @DS("mes")} 切换到 ry-mes 库。
     *
     * @param receiveId  ERP 收货单 ID
     * @param materialId 物料 ID
     * @return 已存在返回 true
     */
    boolean existsByReceiveAndMaterial(Long receiveId, Long materialId);

    /**
     * 执行检验(PENDING/INSPECTING -> COMPLETED), 完成后MQ回传ERP
     *
     * @param iqcId IQC ID
     * @param inspectResult 检验结果(QUALIFIED/UNQUALIFIED)
     * @param defectInfo 不良品信息(JSON String)
     * @param sampleQty 抽样数量
     * @param remark 备注
     */
    void inspect(
            Long iqcId,
            String inspectResult,
            String defectInfo,
            BigDecimal sampleQty,
            String remark);

    /**
     * 按检验明细执行检验(自动判定): 录入实测值 -> 判定引擎逐项判 PASS/FAIL ->
     * 汇总单级结果(全 PASS=QUALIFIED, 任一 CRITICAL FAIL=UNQUALIFIED, 仅 MAJOR/MINOR FAIL=PARTIAL) ->
     * 继续走原有 NCR/MQ 回写链路。
     *
     * @param iqcId     IQC ID
     * @param entries   明细实测值列表(detailId + measuredValue)
     * @param defectInfo 不良品信息(JSON String, 可为空)
     * @param remark    备注(可为空)
     */
    void inspectWithDetails(Long iqcId, List<QcInspectEntryBo> entries, String defectInfo, String remark);

    /**
     * 查询 IQC 检验单明细(按检验模板快照生成)
     */
    List<QcInspectDetailVo> selectDetails(Long iqcId);
}
