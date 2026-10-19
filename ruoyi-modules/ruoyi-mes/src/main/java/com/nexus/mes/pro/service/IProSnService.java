package com.nexus.mes.pro.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.bo.ProSnBo;
import com.nexus.mes.pro.domain.vo.ProSnVo;

import java.util.List;
import java.util.Map;

/**
 * MES SN序列号 Service 业务层接口
 * <p>
 * 状态机: IN_PRODUCTION -> COMPLETED -> SHIPPED
 *                          └-> SCRAPPED (终态)
 *          COMPLETED -> SCRAPPED
 *
 * @author Nexus
 */
public interface IProSnService {

    /**
     * 状态: 生产中
     */
    String STATUS_IN_PRODUCTION = "IN_PRODUCTION";

    /**
     * 状态: 已完成
     */
    String STATUS_COMPLETED = "COMPLETED";

    /**
     * 状态: 已报废
     */
    String STATUS_SCRAPPED = "SCRAPPED";

    /**
     * 状态: 已发货
     */
    String STATUS_SHIPPED = "SHIPPED";

    /**
     * 来源: 手动单建
     */
    String SOURCE_MANUAL = "MANUAL";

    /**
     * 来源: 批量生成
     */
    String SOURCE_BATCH = "BATCH";

    /**
     * 来源: 委外(PCBA)
     */
    String SOURCE_SUBCONTRACT = "SUBCONTRACT";

    /**
     * 分页查询SN列表
     */
    TableDataInfo<ProSnVo> selectPageList(ProSnBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取SN详情
     */
    ProSnVo selectById(Long snId);

    /**
     * 根据SN编码查询
     */
    ProSnVo selectByCode(String snCode);

    /**
     * 从流转卡批量生成SN(操作员触发, 不自动耦合completeCard)
     *
     * @param cardId        流转卡ID
     * @param quantity      生成数量
     * @param initialStatus 初始状态(默认 IN_PRODUCTION)
     * @param lotId         批次ID(可空; 非空时发布 SnCreatedEvent, 由 lt 模块写 SN↔批次绑定)
     * @return 生成的SN列表
     */
    /**
     * 查询SN列表(不分页, 导出用)
     *
     * @param bo 查询参数
     * @return SN列表
     */
    List<ProSnVo> selectList(ProSnBo bo);

    List<ProSnVo> generateSnForCard(
            Long cardId, Integer quantity, String initialStatus, Long lotId);

    List<ProSnVo> generateSnForCard(
            String commandId, Long cardId, Integer quantity, String initialStatus, Long lotId);

    List<ProSnVo> generateSnForSubcontract(
            String commandId,
            Long id,
            String no,
            Long materialId,
            String materialCode,
            String materialName,
            int quantity);

    ProSnVo createSn(
            String commandId,
            Long mesWoId,
            Long cardId,
            Long materialId,
            String snCode,
            Long parentSnId,
            String remark,
            Long lotId);

    /**
     * 按委外订单批量生成SN (PCBA 委外闭环, MQ 触发)
     * <p>编码规则: SN-{委外单号}-{4位序号}; source=SUBCONTRACT, 冗余 subcontractNo 供直达追溯。
     * 幂等: 按 subcontractNo 已生成数量续号, @Lock4j 订单维度防并发。</p>
     *
     * @param subcontractId 委外订单ID
     * @param subcontractNo 委外单号
     * @param materialId     产出物料ID
     * @param materialCode   物料编码
     * @param materialName   物料名称
     * @param quantity       本次生成数量
     * @return 本次生成的 SN 列表
     */
    java.util.List<com.nexus.mes.pro.domain.vo.ProSnVo> generateSnForSubcontract(
            Long subcontractId,
            String subcontractNo,
            Long materialId,
            String materialCode,
            String materialName,
            int quantity);

    /**
     * 手动创建单个SN(可指定parentSnId用于组装追溯)
     *
     * @param lotId 批次ID(可空; 非空时发布 SnCreatedEvent, 由 lt 模块写 SN↔批次绑定)
     */
    ProSnVo createSn(
            Long mesWoId,
            Long cardId,
            Long materialId,
            String snCode,
            Long parentSnId,
            String remark,
            Long lotId);

    /**
     * 完工(IN_PRODUCTION -> COMPLETED)
     */
    void completeSn(Long snId);

    /**
     * 报废(IN_PRODUCTION/COMPLETED -> SCRAPPED)
     */
    void scrapSn(Long snId, String remark);

    /**
     * 发货(COMPLETED -> SHIPPED)
     */
    void shipSn(Long snId);

    /**
     * 追溯: 前向(SN->工序->操作员 + 子SN) + 后向(父SN链)
     *
     * @return { sn, forwardSteps, children, parentChain }
     */
    Map<String, Object> trace(Long snId);
}
