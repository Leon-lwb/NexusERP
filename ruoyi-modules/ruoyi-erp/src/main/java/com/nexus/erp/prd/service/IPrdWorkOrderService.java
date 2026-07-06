package com.nexus.erp.prd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdWorkOrderBo;
import com.nexus.erp.prd.domain.vo.PrdWorkOrderVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 生产工单 Service 接口
 * <p>
 * 定义生产工单相关的业务逻辑操作，包括工单的查询、新增、修改、删除、状态流转及工作流审批等。
 *
 * @author nexus
 */
public interface IPrdWorkOrderService extends WfCallBackServiceI {

    /** 工单状态: 新建 */
    String STATUS_CREATED = "CREATED";
    /** 工单状态: 审批中 */
    String STATUS_APPROVING = "APPROVING";
    /** 工单状态: 已批准 */
    String STATUS_APPROVED = "APPROVED";
    /** 工单状态: 已下达 */
    String STATUS_RELEASED = "RELEASED";
    /** 工单状态: 生产中 */
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 工单状态: 已完工 */
    String STATUS_COMPLETED = "COMPLETED";
    /** 工单状态: 已关闭 */
    String STATUS_CLOSED = "CLOSED";

    /**
     * 分页查询生产工单列表
     *
     * @param bo        查询参数，包含工单编号、产品、状态等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 生产工单分页列表
     */
    TableDataInfo<PrdWorkOrderVo> selectPageList(PrdWorkOrderBo bo, PageQuery pageQuery);

    /**
     * 查询生产工单列表（不分页）
     *
     * @param bo 查询参数，包含工单编号、产品、状态等筛选条件
     * @return 生产工单列表
     */
    List<PrdWorkOrderVo> selectList(PrdWorkOrderBo bo);

    /**
     * 根据 ID 查询生产工单详情
     *
     * @param id 工单 ID
     * @return 生产工单详细信息
     */
    PrdWorkOrderVo selectById(Long id);

    /**
     * 根据工单 ID 查询工单实体
     *
     * @param woId 工单 ID
     * @return 生产工单实体
     */
    PrdWorkOrder selectPrdWorkOrderByWoId(Long woId);

    /**
     * 查询生产工单列表（按实体条件）
     *
     * @param prdWorkOrder 工单查询实体
     * @return 生产工单列表
     */
    List<PrdWorkOrder> selectPrdWorkOrderList(PrdWorkOrder prdWorkOrder);

    /**
     * 新增生产工单
     *
     * @param prdWorkOrder 生产工单实体
     * @return 影响的行数
     */
    int insertPrdWorkOrder(PrdWorkOrder prdWorkOrder);

    /**
     * 新增生产工单（通过 BO）
     * <p>
     * 初始状态设置为 CREATED（新建）。
     *
     * @param bo 生产工单业务对象
     * @return 影响的行数
     */
    int insertByBo(PrdWorkOrderBo bo);

    /**
     * 修改生产工单（通过 BO）
     *
     * @param bo 生产工单业务对象
     * @return 影响的行数
     */
    int updateByBo(PrdWorkOrderBo bo);

    /**
     * 修改生产工单
     *
     * @param prdWorkOrder 生产工单实体
     * @return 影响的行数
     */
    int updatePrdWorkOrder(PrdWorkOrder prdWorkOrder);

    /**
     * 批量删除生产工单
     *
     * @param ids 要删除的工单 ID 数组
     * @return 影响的行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据工单 ID 数组批量删除生产工单
     *
     * @param woIds 工单 ID 数组
     * @return 影响的行数
     */
    int deletePrdWorkOrderByWoIds(Long[] woIds);

    /**
     * 根据工单 ID 删除生产工单
     *
     * @param woId 工单 ID
     * @return 影响的行数
     */
    int deletePrdWorkOrderByWoId(Long woId);

    /**
     * 下达工单
     * <p>
     * 将工单状态从 CREATED 或 APPROVED 更新为 RELEASED（已下达），
     * 并自动生成生产领料单（排除倒冲物料）。
     *
     * @param woId 工单 ID
     */
    void release(Long woId);

    /**
     * 开始生产
     * <p>
     * 将工单状态从 RELEASED 更新为 IN_PROGRESS（生产中），
     * 非倒冲工单需校验是否已生成领料单。
     *
     * @param woId 工单 ID
     */
    void startProduction(Long woId);

    /**
     * 标记工单为生产中(轻量状态流转)
     * <p>
     * 仅将 RELEASED 状态工单置为 IN_PROGRESS 并记录实际开工时间，不校验领料单。
     * 供 MES 完工回写等权威外部信号触发完工时前置调用，使状态机合法
     * (RELEASED → IN_PROGRESS → COMPLETED)。对已处于 IN_PROGRESS 的工单幂等。
     *
     * @param woId 工单 ID
     */
    void markInProgress(Long woId);

    /**
     * 完工
     * <p>
     * 将工单状态从 IN_PROGRESS 更新为 COMPLETED（已完工），
     * 执行完工倒冲领料并生成成品入库记录。
     *
     * @param woId 工单 ID
     */
    void complete(Long woId);

    /**
     * 工单完工(指定完工数量)
     * <p>
     * 使用 MQ 消息或报工中的实际完工数量进行原子累加, 避免并发覆盖。
     *
     * @param woId       工单 ID
     * @param completeQty 本次完工数量
     * @param scrapQty   本次报废数量
     */
    void complete(Long woId, java.math.BigDecimal completeQty, java.math.BigDecimal scrapQty);

    /**
     * 关闭工单
     * <p>
     * 将工单状态更新为 CLOSED（已关闭）。
     * 生产中状态工单不能直接关闭，需先完工。
     *
     * @param woId 工单 ID
     */
    void close(Long woId);

    // ==================== Flowable 工作流 ====================

    /**
     * 发起审批流程
     *
     * @param woIdStr      工单 ID 字符串
     * @param selectFlowId 选择的流程模板 ID
     * @return 是否提交成功
     */
    boolean submit(String woIdStr, String selectFlowId);

    /**
     * 获取流程实例 ID
     *
     * @param woIdStr 工单 ID 字符串
     * @return 流程实例 ID
     */
    String getProcInsId(String woIdStr);
}
