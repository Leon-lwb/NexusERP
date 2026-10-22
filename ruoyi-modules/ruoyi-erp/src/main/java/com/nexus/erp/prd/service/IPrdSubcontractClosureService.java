package com.nexus.erp.prd.service;

import com.nexus.erp.prd.domain.bo.PrdSubcontractDocBo;
import com.nexus.erp.prd.domain.bo.PrdSubcontractQuoteBo;
import com.nexus.erp.prd.domain.vo.PrdSubcontractBomLineVo;
import com.nexus.erp.prd.domain.vo.PrdSubcontractDocVo;
import com.nexus.erp.prd.domain.vo.PrdSubcontractQuoteVo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 委外订单(PCBA)业务闭环 Service 接口
 * <p>
 * 覆盖: 资料带出(需求1) / 订单BOM快照与供应方标注(需求3) / 报价与选定供应商(需求7/8) /
 * 工程文件管理(需求2) / 发料完成推进(需求4) / 收货过账自动闭环(需求9)。
 *
 * @author nexus
 */
public interface IPrdSubcontractClosureService {

    /** 供应方: 自备料 */
    String SUPPLY_SELF = "SELF_PROVIDED";
    /** 供应方: 外协厂供 */
    String SUPPLY_VENDOR = "VENDOR_PROVIDED";
    /** 供应方: 客供 */
    String SUPPLY_CUSTOMER = "CUSTOMER_PROVIDED";

    /**
     * 选择物料带出委外相关资料: 当前生效BOM(含行)、工艺路线(含委外工序)、物料级工程文件库
     *
     * @param materialId 物料ID
     * @return {material, bom, bomLines, routingSteps(仅委外工序), docs}
     */
    Map<String, Object> getMaterialInfo(Long materialId);

    /**
     * 查询订单 BOM 快照行
     *
     * @param subcontractId 委外订单ID
     * @return 快照行列表
     */
    List<PrdSubcontractBomLineVo> getBomLines(Long subcontractId);

    /**
     * 按订单关联 BOM 生成/重建 BOM 快照(删除旧快照后重建, 需求量=计划数×单耗×(1+损耗))
     * <p>仅 DRAFT 状态允许重建; BOM 行 supply_type 一并快照固化。</p>
     *
     * @param subcontractId 委外订单ID
     * @return 快照行数
     */
    int generateBomSnapshot(Long subcontractId);

    /**
     * 查询订单报价记录
     *
     * @param subcontractId 委外订单ID
     * @return 报价列表
     */
    List<PrdSubcontractQuoteVo> listQuotes(Long subcontractId);

    /**
     * 保存报价记录(新增或更新)
     *
     * @param bo 报价业务对象
     * @return 主键
     */
    Long saveQuote(PrdSubcontractQuoteBo bo);

    /**
     * 批量删除报价记录
     *
     * @param quoteIds 报价ID数组
     * @return 删除行数
     */
    int deleteQuotes(Long[] quoteIds);

    /**
     * 选定供应商: 选中报价回写订单 vendor/单价/金额, 其余报价置 NOT_SELECTED
     *
     * @param quoteId 选定的报价ID
     */
    void selectQuote(Long quoteId);

    /**
     * 查询工程文件(物料级库 subcontractId=null 或 订单级快照)
     *
     * @param materialId    物料ID
     * @param subcontractId 委外订单ID(可空)
     * @return 文件列表
     */
    List<PrdSubcontractDocVo> listDocs(Long materialId, Long subcontractId);

    /**
     * 保存工程文件(新增或更新)
     *
     * @param bo 文件业务对象
     * @return 主键
     */
    Long saveDoc(PrdSubcontractDocBo bo);

    /**
     * 批量删除工程文件
     *
     * @param docIds 文件ID数组
     * @return 删除行数
     */
    int deleteDocs(Long[] docIds);

    /**
     * 委外发料单全部过账后的订单推进: 置 RELEASED + 自动建关联工单 + 发 MES 生成 SN 消息
     * <p>由 WmSubcontractIssueService.postIssue 在发料完成后调用(同一事务)。</p>
     *
     * @param subcontractId 委外订单ID
     */
    void advanceToReleased(Long subcontractId);

    /**
     * 收货过账回调: 原子累加 actualQty, 累计达计划量时自动完工(COMPLETED)
     *
     * @param subcontractId 委外订单ID
     * @param receivedQty 本次收货量
     */
    void accumulateActualQty(Long subcontractId, BigDecimal receivedQty);
}
