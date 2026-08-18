package com.nexus.mes.pro.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.mes.pro.domain.bo.ProWorkOrderBo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderStepVo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * MES工单 Service 接口
 * <p>属于 MES 生产管理模块，定义 工单 的业务服务契约，供 Controller 与 MQ 消费方调用。</p>
 *
 * @author Nexus
 */
public interface IProWorkOrderService extends WfCallBackServiceI {

    /** 工单状态: 新建 */
    String STATUS_CREATED = "CREATED";
    /** 工单状态: 审批中 */
    String STATUS_APPROVING = "APPROVING";
    /** 工单状态: 已下达 */
    String STATUS_RELEASED = "RELEASED";
    /** 工单状态: 生产中 */
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 工单状态: 部分完成(部分流转卡已完工, 仍有在制流转卡; 全部完工才转 COMPLETED) */
    String STATUS_PARTIAL_COMPLETED = "PARTIAL_COMPLETED";
    /** 工单状态: 已完工 */
    String STATUS_COMPLETED = "COMPLETED";
    /** 工单状态: 已取消 */
    String STATUS_CANCELLED = "CANCELLED";

    /** 工序状态: 待开始 */
    String STEP_PENDING = "PENDING";
    /** 工序状态: 就绪 */
    String STEP_READY = "READY";
    /** 工序状态: 进行中 */
    String STEP_IN_PROGRESS = "IN_PROGRESS";
    /** 工序状态: 已完成 */
    String STEP_COMPLETED = "COMPLETED";
    /** 工序状态: 已跳过 */
    String STEP_SKIPPED = "SKIPPED";

    /**
     * 分页查询MES工单列表
     */
    TableDataInfo<ProWorkOrderVo> selectPageList(ProWorkOrderBo bo, PageQuery pageQuery);

    /**
     * 查询MES工单列表(不分页)
     */
    List<ProWorkOrderVo> selectList(ProWorkOrderBo bo);

    /**
     * 根据ID查询MES工单详情(含工序步骤)
     */
    ProWorkOrderVo selectById(Long mesWoId);

    /**
     * 根据ERP工单ID查询MES工单(幂等校验用)
     */
    ProWorkOrderVo selectByErpWoId(Long erpWoId);

    /**
     * 查询工单的工序步骤列表
     */
    List<ProWorkOrderStepVo> selectStepsByWoId(Long mesWoId);

    /**
     * 新增MES工单
     */
    int insertByBo(ProWorkOrderBo bo);

    /**
     * 修改MES工单
     */
    int updateByBo(ProWorkOrderBo bo);

    /**
     * 批量删除MES工单
     */
    int deleteByIds(Long[] ids);

    /**
     * 下达工单(CREATED -> RELEASED)
     */
    void release(Long mesWoId);

    /**
     * 消费ERP工单下达消息，创建MES工单及工序
     * <p>
     * 幂等：通过erpWoId查重，已存在则跳过。
     *
     * @param message ERP工单下达消息
     */
    void createFromErpMessage(WorkOrderReleaseMessage message);

    /**
     * 提交审批(CREATED -> APPROVING)
     *
     * @param mesWoIdStr   MES工单ID字符串
     * @param selectFlowId 审批流程模板ID
     * @return 是否成功
     */
    boolean submit(String mesWoIdStr, String selectFlowId);

    /**
     * 查询流程实例ID
     *
     * @param mesWoIdStr MES工单ID字符串
     * @return 流程实例ID，可能为null
     */
    String getProcInsId(String mesWoIdStr);

    /**
     * 工单完工(IN_PROGRESS/PARTIAL_COMPLETED -> COMPLETED)
     * <p>
     * 由流转卡完工聚合触发(仅当工单下所有流转卡均完工时调用)。完工后发送MQ消息通知ERP。
     * 部分完工(PARTIAL_COMPLETED)由 completeCard 在仍有在制流转卡时置位, 不走此方法。
     *
     * @param mesWoId MES工单ID
     */
    void completeWo(Long mesWoId);
}
