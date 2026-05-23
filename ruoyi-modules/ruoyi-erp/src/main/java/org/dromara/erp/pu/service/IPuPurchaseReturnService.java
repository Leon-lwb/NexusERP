package org.dromara.erp.pu.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.pu.domain.bo.PuPurchaseReturnBo;
import org.dromara.erp.pu.domain.vo.PuPurchaseReturnVo;

import java.util.List;

/**
 * 采购退货服务接口
 * <p>
 * 定义采购退货单的业务操作，包括 CRUD、确认和执行退货出库等功能。
 * 执行退货时会扣减物料库存并生成库存交易记录。
 *
 * @author ruoyi
 */
public interface IPuPurchaseReturnService {

    /** 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 已确认 */
    String STATUS_CONFIRMED = "CONFIRMED";
    /** 已执行 */
    String STATUS_EXECUTED = "EXECUTED";
    /** 已作废 */
    String STATUS_CANCELLED = "CANCELLED";

    /**
     * 分页查询采购退货单列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<PuPurchaseReturnVo> selectPageList(PuPurchaseReturnBo bo, PageQuery pageQuery);

    /**
     * 查询采购退货单列表（不分页）
     *
     * @param bo 查询条件
     * @return 采购退货单列表
     */
    List<PuPurchaseReturnVo> selectList(PuPurchaseReturnBo bo);

    /**
     * 根据主键查询采购退货单，包括明细行
     *
     * @param id 退货单ID
     * @return 采购退货单视图对象
     */
    PuPurchaseReturnVo selectById(Long id);

    /**
     * 新增采购退货单，含明细行
     *
     * @param bo 采购退货业务对象
     * @return 影响行数
     */
    int insertByBo(PuPurchaseReturnBo bo);

    /**
     * 修改采购退货单，含明细行
     *
     * @param bo 采购退货业务对象
     * @return 影响行数
     */
    int updateByBo(PuPurchaseReturnBo bo);

    /**
     * 批量删除采购退货单
     *
     * @param ids 退货单ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 确认退货单（草稿 → 已确认）
     *
     * @param returnId 退货单ID
     */
    void confirm(Long returnId);

    /**
     * 执行退货出库：扣减库存并生成库存交易记录
     *
     * @param returnId 退货单ID
     */
    void execute(Long returnId);
}
