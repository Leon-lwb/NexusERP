package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoInvoiceBo;
import com.nexus.erp.so.domain.vo.SoInvoiceVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 销售发票Service接口
 *
 * @author nexus
 * @date 2026-06-14
 */
public interface ISoInvoiceService extends WfCallBackServiceI {

    /** 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 审批中 */
    String STATUS_APPROVING = "APPROVING";
    /** 已开票 */
    String STATUS_ISSUED = "ISSUED";
    /** 已核销 */
    String STATUS_VERIFIED = "VERIFIED";
    /** 已驳回 */
    String STATUS_REJECTED = "REJECTED";
    /** 已作废 */
    String STATUS_CANCELLED = "CANCELLED";

    /**
     * 分页查询销售发票列表
     */
    TableDataInfo<SoInvoiceVo> selectPageList(SoInvoiceBo bo, PageQuery pageQuery);

    /**
     * 查询销售发票列表（不分页）
     */
    List<SoInvoiceVo> selectList(SoInvoiceBo bo);

    /**
     * 根据ID查询销售发票
     */
    SoInvoiceVo selectById(Long id);

    /**
     * 新增销售发票
     */
    int insertByBo(SoInvoiceBo bo);

    /**
     * 修改销售发票
     */
    int updateByBo(SoInvoiceBo bo);

    /**
     * 批量删除销售发票
     */
    int deleteByIds(Long[] ids);

    /**
     * 提交流程审批（启动 Flowable 审批流）
     *
     * @param invoiceIdStr  发票ID字符串
     * @param selectFlowId  选择的审批流ID
     * @return 是否成功
     */
    boolean submit(String invoiceIdStr, String selectFlowId);

    /**
     * 获取流程实例ID
     *
     * @param invoiceIdStr 发票ID字符串
     * @return 流程实例ID
     */
    String getProcInsId(String invoiceIdStr);
}
