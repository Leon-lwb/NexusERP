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
 * @author ruoyi
 */
public interface ISoSalesOrderHeaderService extends WfCallBackServiceI {

    String STATUS_DRAFT = "DRAFT";
    String STATUS_CONFIRMED = "CONFIRMED";
    String STATUS_APPROVED = "APPROVED";
    String STATUS_APPROVING = "APPROVING";
    String STATUS_REJECTED = "REJECTED";
    String STATUS_FULFILLED = "FULFILLED";
    String STATUS_CLOSED = "CLOSED";
    String STATUS_CANCELLED = "CANCELLED";

    TableDataInfo<SoSalesOrderHeaderVo> selectPageList(SoSalesOrderHeaderBo bo, PageQuery pageQuery);

    List<SoSalesOrderHeaderVo> selectList(SoSalesOrderHeaderBo bo);

    SoSalesOrderHeaderVo selectById(Long id);

    int insertByBo(SoSalesOrderHeaderBo bo);

    int updateByBo(SoSalesOrderHeaderBo bo);

    int deleteByIds(Long[] ids);

    List<SoSalesOrderLineVo> selectLineListBySoNo(String soNo);

    void confirm(Long soId);

    void fulfill(Long soId);

    void close(Long soId);

    void cancel(Long soId, String reason);

    // ==================== Flowable 工作流 ====================

    /**
     * 发起审批流程
     * @param soIdStr 销售订单ID（字符串）
     * @param selectFlowId 流程定义ID
     * @return 是否成功
     */
    boolean submit(String soIdStr, String selectFlowId);

    /**
     * 获取流程实例ID
     * @param soIdStr 销售订单ID（字符串）
     * @return 流程实例ID，无则返回null
     */
    String getProcInsId(String soIdStr);
}
