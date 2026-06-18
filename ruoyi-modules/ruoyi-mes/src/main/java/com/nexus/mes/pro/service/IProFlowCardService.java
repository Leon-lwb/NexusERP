package com.nexus.mes.pro.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.bo.ProFlowCardBo;
import com.nexus.mes.pro.domain.vo.ProFlowCardVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES流转卡 Service 接口
 *
 * @author Nexus
 */
public interface IProFlowCardService {

    /** 流转卡状态: 已创建 */
    String STATUS_CREATED = "CREATED";
    /** 流转卡状态: 已下达 */
    String STATUS_RELEASED = "RELEASED";
    /** 流转卡状态: 生产中 */
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 流转卡状态: 已完工 */
    String STATUS_COMPLETED = "COMPLETED";
    /** 流转卡状态: 已关闭 */
    String STATUS_CLOSED = "CLOSED";

    /** 工序状态: 待开始 */
    String STEP_PENDING = "PENDING";
    /** 工序状态: 进行中 */
    String STEP_IN_PROGRESS = "IN_PROGRESS";
    /** 工序状态: 已完成 */
    String STEP_COMPLETED = "COMPLETED";
    /** 工序状态: 已跳过 */
    String STEP_SKIPPED = "SKIPPED";

    /**
     * 分页查询流转卡列表
     */
    TableDataInfo<ProFlowCardVo> selectPageList(ProFlowCardBo bo, PageQuery pageQuery);

    /**
     * 根据ID查询流转卡详情(含工序步骤)
     */
    ProFlowCardVo selectById(Long cardId);

    /**
     * 按工单查询流转卡列表
     */
    List<ProFlowCardVo> selectByWoId(Long mesWoId);

    /**
     * 创建流转卡(从MES工单)
     * <p>
     * 1. 校验工单为RELEASED状态
     * 2. 创建流转卡(CREATED)
     * 3. 根据工单工序步骤自动生成流转卡工序记录(PENDING)
     *
     * @param mesWoId  MES工单ID
     * @param quantity 计划数量
     * @return 流转卡详情
     */
    ProFlowCardVo createFlowCard(Long mesWoId, BigDecimal quantity);

    /**
     * 下达流转卡(CREATED -> RELEASED)
     */
    void releaseCard(Long cardId);

    /**
     * 开始工序
     * <p>
     * 1. 工序状态 PENDING -> IN_PROGRESS
     * 2. 若流转卡为RELEASED，流转卡状态 -> IN_PROGRESS
     *
     * @param cardId        流转卡ID
     * @param stepRecordId  工序记录ID
     * @param operatorId    操作员ID
     */
    void startStep(Long cardId, Long stepRecordId, Long operatorId);

    /**
     * 完成工序
     * <p>
     * 1. 工序状态 IN_PROGRESS -> COMPLETED
     * 2. 累加良品/不良/报废数量
     * 3. 更新流转卡 completed_qty
     * 4. 检查是否所有工序完成 -> 触发 completeCard
     *
     * @param cardId        流转卡ID
     * @param stepRecordId  工序记录ID
     * @param goodQty       良品数量
     * @param defectQty     不良品数量
     * @param scrapQty      报废数量
     */
    void completeStep(Long cardId, Long stepRecordId,
                      BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty);

    /**
     * 完成流转卡
     * <p>
     * 1. 流转卡状态 -> COMPLETED
     * 2. 聚合到工单 completed_qty
     * 3. 检查工单所有流转卡完成 -> 触发工单完工
     */
    void completeCard(Long cardId);

    /**
     * 关闭流转卡(COMPLETED -> CLOSED)
     */
    void closeCard(Long cardId);

    /** 按条码查询流转卡(含工序) */
    ProFlowCardVo selectByBarcode(String barcode);

    /** 检查并完成工序(派工完工聚合后调用) — CAS工序→COMPLETED + 检查所有工序完成→completeCard */
    void checkAndCompleteStep(Long cardId, Long stepRecordId);
}
