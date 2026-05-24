package org.dromara.erp.pu.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.pu.domain.bo.PuPurchaseReturnBo;
import org.dromara.erp.pu.domain.vo.PuPurchaseReturnVo;
import org.dromara.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 采购退货服务接口
 * <p>
 * 定义采购退货单的业务操作，包括 CRUD、确认和执行退货出库等功能。
 * 执行退货时会扣减物料库存并生成库存交易记录。
 *
 * @author ruoyi
 */
public interface IPuPurchaseReturnService extends WfCallBackServiceI {

    /** 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 审批中 */
    String STATUS_APPROVING = "APPROVING";
    /** 已确认 */
    String STATUS_CONFIRMED = "CONFIRMED";
    /** 已执行 */
    String STATUS_EXECUTED = "EXECUTED";
    /** 已作废 */
    String STATUS_CANCELLED = "CANCELLED";

    /**
     * 分页查询采购退货单列表
     */
    TableDataInfo<PuPurchaseReturnVo> selectPageList(PuPurchaseReturnBo bo, PageQuery pageQuery);

    /**
     * 查询采购退货单列表（不分页）
     */
    List<PuPurchaseReturnVo> selectList(PuPurchaseReturnBo bo);

    /**
     * 根据主键查询采购退货单，包括明细行
     */
    PuPurchaseReturnVo selectById(Long id);

    /**
     * 新增采购退货单，含明细行
     */
    int insertByBo(PuPurchaseReturnBo bo);

    /**
     * 修改采购退货单，含明细行
     */
    int updateByBo(PuPurchaseReturnBo bo);

    /**
     * 批量删除采购退货单
     */
    int deleteByIds(Long[] ids);

    /**
     * 确认退货单（草稿 → 已确认）
     */
    void confirm(Long returnId);

    /**
     * 执行退货出库：扣减库存并生成库存交易记录
     */
    void execute(Long returnId);

    /**
     * 发起审批流程
     */
    boolean submit(String returnIdStr, String selectFlowId);

    /**
     * 获取流程实例ID
     */
    String getProcInsId(String returnIdStr);
}
