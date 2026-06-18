package com.nexus.mes.pro.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.bo.ProDispatchBo;
import com.nexus.mes.pro.domain.vo.ProDispatchVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES派工 Service接口
 * <p>
 * 状态机: PENDING -> IN_PROGRESS -> COMPLETED / CANCELLED
 * 完工聚合: 派工完工 -> 累加数量到工序+流转卡 -> 工序所有派工完工 -> 自动完成工序
 *
 * @author Nexus
 */
public interface IProDispatchService {

    String STATUS_PENDING = "PENDING";
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    String STATUS_COMPLETED = "COMPLETED";
    String STATUS_CANCELLED = "CANCELLED";

    /**
     * 分页查询派工单列表
     */
    TableDataInfo<ProDispatchVo> selectPageList(ProDispatchBo bo, PageQuery pageQuery);

    /**
     * 根据ID查询派工单详情
     */
    ProDispatchVo selectById(Long dispatchId);

    /**
     * 按工序记录ID查询派工单列表
     */
    List<ProDispatchVo> selectByStepRecordId(Long stepRecordId);

    /**
     * 按操作员ID查询派工单列表
     */
    List<ProDispatchVo> selectByOperatorId(Long operatorId);

    /**
     * 创建派工单(从流转卡工序)
     *
     * @param cardId       流转卡ID
     * @param stepRecordId 工序记录ID
     * @param operatorId   操作员ID(可为空,开工时确定)
     * @param operatorName 操作员姓名(可为空)
     * @param quantity     派工数量
     * @return 派工单VO
     */
    ProDispatchVo createDispatch(Long cardId, Long stepRecordId, Long operatorId,
                                  String operatorName, BigDecimal quantity);

    /**
     * 开工派工(PENDING -> IN_PROGRESS), 同步开工工序
     */
    void startDispatch(Long dispatchId);

    /**
     * 完工派工(IN_PROGRESS -> COMPLETED), 累加数量到工序+流转卡, 自动完成工序
     */
    void completeDispatch(Long dispatchId, BigDecimal goodQty, BigDecimal defectQty, BigDecimal scrapQty);

    /**
     * 取消派工(仅PENDING可取消)
     */
    void cancelDispatch(Long dispatchId);
}
