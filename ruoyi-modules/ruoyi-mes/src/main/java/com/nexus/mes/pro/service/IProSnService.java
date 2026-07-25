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

    /** 状态: 生产中 */
    String STATUS_IN_PRODUCTION = "IN_PRODUCTION";
    /** 状态: 已完成 */
    String STATUS_COMPLETED = "COMPLETED";
    /** 状态: 已报废 */
    String STATUS_SCRAPPED = "SCRAPPED";
    /** 状态: 已发货 */
    String STATUS_SHIPPED = "SHIPPED";

    /** 来源: 手动单建 */
    String SOURCE_MANUAL = "MANUAL";
    /** 来源: 批量生成 */
    String SOURCE_BATCH = "BATCH";

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
    List<ProSnVo> generateSnForCard(Long cardId, Integer quantity, String initialStatus, Long lotId);

    /**
     * 手动创建单个SN(可指定parentSnId用于组装追溯)
     *
     * @param lotId 批次ID(可空; 非空时发布 SnCreatedEvent, 由 lt 模块写 SN↔批次绑定)
     */
    ProSnVo createSn(Long mesWoId, Long cardId, Long materialId,
                     String snCode, Long parentSnId, String remark, Long lotId);

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
