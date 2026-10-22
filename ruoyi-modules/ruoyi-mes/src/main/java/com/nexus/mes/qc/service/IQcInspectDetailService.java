package com.nexus.mes.qc.service;

import com.nexus.mes.qc.domain.QcInspectDetail;

import java.math.BigDecimal;
import java.util.List;

/**
 * QC 检验单明细 Service 接口
 * <p>检验单创建时按检验模板快照生成明细; 录入实测值后判定。</p>
 */
public interface IQcInspectDetailService {

    /**
     * 按模板生成检验明细(幂等: 已有明细不重复生成)。
     * <p>匹配规则: 模板类型 = checkType 且状态 ACTIVE, 物料专属优先, 无则通用模板(material_id 为空)。</p>
     *
     * @param checkType  检验类别: IQC/IPQC/OQC
     * @param checkId    检验单 ID
     * @param materialId 物料 ID
     * @return 生成的明细条数(无匹配模板返回 0)
     */
    int generateDetails(String checkType, Long checkId, Long materialId);

    /**
     * 按模板生成检验明细(带批量, 用于 AQL 建议抽样量)。
     *
     * @param lotQty 检验批量(可为空; 给定时按 GB/T 2828.1 检验水平 II 计算 sampleQty 填入明细)
     */
    int generateDetails(String checkType, Long checkId, Long materialId, BigDecimal lotQty);

    /**
     * 按模板生成检验明细(带批量与工序/客户维度)。
     *
     * <p>匹配优先级: 物料+工序 &gt; 物料 &gt; 工序 &gt; 客户 &gt; 通用(三个维度均为空)。 每一档都要求其余维度为空, 避免通用模板抢走更具体的匹配。
     * 无匹配模板时记 warn 并返回 0, 检验单保持 PENDING(待人工判定), 绝不静默判合格。
     *
     * @param checkType 检验类别: IQC/IPQC/OQC
     * @param checkId 检验单 ID
     * @param materialId 物料 ID(可空)
     * @param lotQty 检验批量(可空, 给定时按 GB/T 2828.1 检验水平 II 计算建议抽样量)
     * @param processId 工序 ID(IPQC 用, 可空)
     * @param customerId 客户 ID(可空)
     * @return 生成的明细条数(无匹配模板返回 0)
     */
    int generateDetails(
            String checkType,
            Long checkId,
            Long materialId,
            BigDecimal lotQty,
            Long processId,
            Long customerId);

    /**
     * 查询检验单的明细列表(按序号升序)
     */
    List<QcInspectDetail> selectByCheck(String checkType, Long checkId);

    /**
     * 批量更新明细(实测值录入 + 判定结果)
     */
    void updateDetails(List<QcInspectDetail> details);
}
