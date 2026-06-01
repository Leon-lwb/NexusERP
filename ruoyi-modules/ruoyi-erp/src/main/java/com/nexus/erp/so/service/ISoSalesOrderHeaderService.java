package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoSalesOrderHeaderBo;
import com.nexus.erp.so.domain.vo.SoSalesOrderHeaderVo;
import com.nexus.erp.so.domain.vo.SoSalesOrderLineVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 销售订单Service接口
 *
 * @author nexus
 */
public interface ISoSalesOrderHeaderService extends WfCallBackServiceI {

    /** 草稿状态 */
    String STATUS_DRAFT = "DRAFT";
    /** 已确认状态 */
    String STATUS_CONFIRMED = "CONFIRMED";
    /** 已审批状态 */
    String STATUS_APPROVED = "APPROVED";
    /** 审批中状态 */
    String STATUS_APPROVING = "APPROVING";
    /** 已驳回状态 */
    String STATUS_REJECTED = "REJECTED";
    /** 已履行状态 */
    String STATUS_FULFILLED = "FULFILLED";
    /** 已关闭状态 */
    String STATUS_CLOSED = "CLOSED";
    /** 已取消状态 */
    String STATUS_CANCELLED = "CANCELLED";

    /**
     * 分页查询销售订单列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售订单分页列表
     */
    TableDataInfo<SoSalesOrderHeaderVo> selectPageList(SoSalesOrderHeaderBo bo, PageQuery pageQuery);

    /**
     * 查询销售订单列表
     *
     * @param bo 查询参数
     * @return 销售订单列表
     */
    List<SoSalesOrderHeaderVo> selectList(SoSalesOrderHeaderBo bo);

    /**
     * 根据ID查询销售订单详情
     *
     * @param id 销售订单ID
     * @return 销售订单详情
     */
    SoSalesOrderHeaderVo selectById(Long id);

    /**
     * 新增销售订单
     *
     * @param bo 销售订单业务对象
     * @return 影响行数
     */
    int insertByBo(SoSalesOrderHeaderBo bo);

    /**
     * 修改销售订单
     *
     * @param bo 销售订单业务对象
     * @return 影响行数
     */
    int updateByBo(SoSalesOrderHeaderBo bo);

    /**
     * 批量删除销售订单
     *
     * @param ids 销售订单ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据销售订单编号查询行明细
     *
     * @param soNo 销售订单编号
     * @return 销售订单行列表
     */
    List<SoSalesOrderLineVo> selectLineListBySoNo(String soNo);

    /**
     * 确认销售订单（包含信用检查）
     *
     * @param soId 销售订单ID
     */
    void confirm(Long soId);

    /**
     * 履行销售订单
     *
     * @param soId 销售订单ID
     */
    void fulfill(Long soId);

    /**
     * 关闭销售订单
     *
     * @param soId 销售订单ID
     */
    void close(Long soId);

    /**
     * 取消销售订单
     *
     * @param soId   销售订单ID
     * @param reason 取消原因
     */
    void cancel(Long soId, String reason);

    // ==================== Flowable 工作流 ====================

    /**
     * 发起审批流程
     *
     * @param soIdStr      销售订单ID（字符串）
     * @param selectFlowId 流程定义ID
     * @return 是否成功
     */
    boolean submit(String soIdStr, String selectFlowId);

    /**
     * 获取流程实例ID
     *
     * @param soIdStr 销售订单ID（字符串）
     * @return 流程实例ID，无则返回null
     */
    String getProcInsId(String soIdStr);
}
