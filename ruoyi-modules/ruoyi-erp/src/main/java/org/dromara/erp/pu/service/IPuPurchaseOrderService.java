package org.dromara.erp.pu.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.pu.domain.bo.PuPurchaseOrderBo;
import org.dromara.erp.pu.domain.vo.PuPurchaseOrderVo;

import java.util.List;

/**
 * 采购订单服务接口
 * <p>
 * 定义采购订单的业务操作，包括 CRUD、状态流转（提交/审批/关闭/作废）、
 * 生成收货联动单据以及从采购申请转单生成采购订单等功能。
 *
 * @author ruoyi
 */
public interface IPuPurchaseOrderService {

    /** 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 已提交 */
    String STATUS_SUBMITTED = "SUBMITTED";
    /** 审批中 */
    String STATUS_APPROVING = "APPROVING";
    /** 已批准 */
    String STATUS_APPROVED = "APPROVED";
    /** 部分到货 */
    String STATUS_PARTIALLY_RECEIVED = "PARTIALLY_RECEIVED";
    /** 已全部到货 */
    String STATUS_RECEIVED = "RECEIVED";
    /** 已关闭 */
    String STATUS_CLOSED = "CLOSED";
    /** 已作废 */
    String STATUS_CANCELLED = "CANCELLED";

    /**
     * 分页查询采购订单列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<PuPurchaseOrderVo> selectPageList(PuPurchaseOrderBo bo, PageQuery pageQuery);

    /**
     * 查询采购订单列表（不分页）
     *
     * @param bo 查询条件
     * @return 采购订单列表
     */
    List<PuPurchaseOrderVo> selectList(PuPurchaseOrderBo bo);

    /**
     * 根据主键查询采购订单，包括明细行
     *
     * @param id 订单ID
     * @return 采购订单视图对象
     */
    PuPurchaseOrderVo selectById(Long id);

    /**
     * 新增采购订单，含明细行
     *
     * @param bo 采购订单业务对象
     * @return 影响行数
     */
    int insertByBo(PuPurchaseOrderBo bo);

    /**
     * 修改采购订单，含明细行
     *
     * @param bo 采购订单业务对象
     * @return 影响行数
     */
    int updateByBo(PuPurchaseOrderBo bo);

    /**
     * 批量删除采购订单（逻辑删除明细行）
     *
     * @param ids 订单ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 提交采购订单（草稿 → 已提交）
     *
     * @param orderId 订单ID
     */
    void submit(Long orderId);

    /**
     * 审批通过采购订单（已提交 → 已批准）
     *
     * @param orderId 订单ID
     */
    void approve(Long orderId);

    /**
     * 关闭采购订单
     *
     * @param orderId 订单ID
     */
    void close(Long orderId);

    /**
     * 作废采购订单
     *
     * @param orderId 订单ID
     */
    void cancel(Long orderId);

    /**
     * 根据采购订单生成收货联动单据（到货通知 + 物料收货单）
     *
     * @param orderId 订单ID
     * @return 到货通知ID
     */
    Long generateReceivingLink(Long orderId);

    /**
     * 根据采购申请生成采购订单草稿
     *
     * @param requisitionId 采购申请ID
     * @return 采购订单业务对象（未持久化）
     */
    PuPurchaseOrderBo generateFromRequisition(Long requisitionId);
}
