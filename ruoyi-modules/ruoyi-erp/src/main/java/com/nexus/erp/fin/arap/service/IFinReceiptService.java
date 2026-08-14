package com.nexus.erp.fin.arap.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.fin.arap.domain.FinReceipt;
import com.nexus.erp.fin.arap.domain.bo.FinReceiptBo;
import com.nexus.erp.fin.arap.domain.vo.FinReceiptVo;

import java.util.List;

/**
 * 收付款单 Service
 *
 * @author nexus
 * @date 2026-07-30
 */
public interface IFinReceiptService {

    /**
     * 分页查询收付款单列表
     *
     * @param bo         查询条件
     * @param pageQuery  分页参数
     * @return 收付款单分页数据
     */
    TableDataInfo<FinReceiptVo> queryPageList(FinReceiptBo bo, PageQuery pageQuery);

    /**
     * 查询收付款单列表(不分页)
     *
     * @param bo 查询条件
     * @return 收付款单列表
     */
    List<FinReceiptVo> queryList(FinReceiptBo bo);

    /**
     * 根据ID查询收付款单
     *
     * @param receiptId 收付款单ID
     * @return 收付款单VO
     */
    FinReceiptVo queryById(Long receiptId);

    /**
     * 新增收付款单,自动填充单据编号/状态/来源/币种/汇率/日期/version
     *
     * @param bo 收付款单BO
     * @return 是否成功
     */
    Boolean insertByBo(FinReceiptBo bo);

    /**
     * 修改收付款单(仅 DRAFT 可修改)
     *
     * @param bo 收付款单BO
     * @return 是否成功
     * @throws ServiceException 单据不存在或非草稿状态时抛出
     */
    Boolean updateByBo(FinReceiptBo bo);

    /**
     * 批量删除收付款单(仅 DRAFT 可删除)
     *
     * @param ids 收付款单ID数组
     * @return 是否成功
     * @throws ServiceException 存在非草稿单据时抛出
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 确认收付款单(DRAFT -> CONFIRMED),触发 GL 异步过账(ReceiptPoster)。
     */
    void confirm(Long receiptId);

    /**
     * 作废收付款单(仅 DRAFT 可作废;已确认单据需先反核销+反过账,F1 暂不支持)。
     */
    void cancel(Long receiptId);

    /**
     * 直接保存收付款单实体（用于内部消费者等场景，不经过 BO 转换）
     */
    boolean save(FinReceipt entity);
}
