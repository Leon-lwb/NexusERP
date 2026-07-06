package com.nexus.erp.pu.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.domain.vo.PuPurchaseRequisitionVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 采购申请服务接口
 * <p>
 * 定义采购申请的业务操作，包括 CRUD、状态流转（提交/审批/关闭/作废）、
 * 以及将已批准的采购申请转换为采购订单等功能。
 * <p>
 * 实现 {@link WfCallBackServiceI} 接口以支持 Flowable 审批流程集成。
 *
 * @author nexus
 */
public interface IPuPurchaseRequisitionService extends WfCallBackServiceI {

    /** 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 已提交 */
    String STATUS_SUBMITTED = "SUBMITTED";
    /** 审批中 */
    String STATUS_APPROVING = "APPROVING";
    /** 已批准 */
    String STATUS_APPROVED = "APPROVED";
    /** 已转单 */
    String STATUS_ORDERED = "ORDERED";
    /** 已关闭 */
    String STATUS_CLOSED = "CLOSED";
    /** 已作废 */
    String STATUS_CANCELLED = "CANCELLED";
    /** 已驳回 */
    String STATUS_REJECTED = "REJECTED";

    /**
     * 分页查询采购申请列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<PuPurchaseRequisitionVo> selectPageList(PuPurchaseRequisitionBo bo, PageQuery pageQuery);

    /**
     * 查询采购申请列表（不分页）
     *
     * @param bo 查询条件
     * @return 采购申请列表
     */
    List<PuPurchaseRequisitionVo> selectList(PuPurchaseRequisitionBo bo);

    /**
     * 根据主键查询采购申请，包括明细行
     *
     * @param id 申请ID
     * @return 采购申请视图对象
     */
    PuPurchaseRequisitionVo selectById(Long id);

    /**
     * 新增采购申请，含明细行
     *
     * @param bo 采购申请业务对象
     * @return 影响行数
     */
    int insertByBo(PuPurchaseRequisitionBo bo);

    /**
     * 修改采购申请，含明细行
     *
     * @param bo 采购申请业务对象
     * @return 影响行数
     */
    int updateByBo(PuPurchaseRequisitionBo bo);

    /**
     * 批量删除采购申请
     *
     * @param ids 申请ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    // /**
    //  * 提交采购申请（草稿 → 已提交，已废弃，请使用 Flowable 工作流）
    //  */
    // void submit(Long requisitionId);

    /**
     * 提交流程审批（启动 Flowable 审批流）
     *
     * @param requisitionIdStr 申请ID字符串
     * @param selectFlowId     选择的审批流ID
     * @return 是否成功
     */
    boolean submit(String requisitionIdStr, String selectFlowId);

    /**
     * 获取流程实例ID
     *
     * @param requisitionIdStr 申请ID字符串
     * @return 流程实例ID
     */
    String getProcInsId(String requisitionIdStr);

    // /**
    //  * 审批通过采购申请（已废弃，请使用 Flowable 工作流审批）
    //  */
    // void approve(Long requisitionId);

    /**
     * 关闭采购申请
     *
     * @param requisitionId 申请ID
     */
    void close(Long requisitionId);

    /**
     * 作废采购申请
     *
     * @param requisitionId 申请ID
     */
    void cancel(Long requisitionId);

    /**
     * 将已批准的采购申请转换为采购订单
     *
     * @param requisitionId 申请ID
     * @return 生成的采购订单ID
     */
    Long convertToPurchaseOrder(Long requisitionId);

    /**
     * 从低库存自动生成采购申请
     * <p>
     * 扫描所有物料，当 WmMaterialStock.stockQuantity < BdMaterial.safetyStock 时，
     * 自动生成采购申请单和明细行，建议采购量 = maxStock - currentStock。
     *
     * @return 生成的采购申请ID列表
     */
    List<Long> generateFromLowStock();
}
