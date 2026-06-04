package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmBatchBo;
import com.nexus.erp.wm.domain.vo.WmBatchVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 批次管理Service接口
 *
 * @author nexus
 * @date 2026-06-02
 */
public interface IWmBatchService {

    /**
     * 分页查询批次管理列表
     */
    TableDataInfo<WmBatchVo> selectPageList(WmBatchBo bo, PageQuery pageQuery);

    /**
     * 查询批次管理列表
     */
    List<WmBatchVo> selectList(WmBatchBo bo);

    /**
     * 查询批次管理
     */
    WmBatchVo selectById(Long id);

    /**
     * 新增批次管理
     */
    int insertByBo(WmBatchBo bo);

    /**
     * 修改批次管理
     */
    int updateByBo(WmBatchBo bo);

    /**
     * 批量删除批次管理
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据批次编码查询批次
     *
     * @param batchCode 批次编码
     * @return 批次信息
     */
    WmBatchVo selectByCode(String batchCode);

    /**
     * 创建批次（自动生成批次编码）
     *
     * @param bo 批次业务对象
     * @return 创建后的批次信息
     */
    WmBatchVo createBatch(WmBatchBo bo);

    /**
     * 根据物料信息自动创建批次（入库上架时调用）
     * 自动填充物料编码/名称/规格，按来源单据类型选择规则编码生成批次号
     *
     * @param materialId      物料ID
     * @param sourceDocType   来源单据类型（如 PURCHASE_RECEIVE、PRODUCTION_RECEIVE、MISC_RECEIVE）
     * @param sourceDocId     来源单据ID
     * @param sourceDocNo     来源单据编号
     * @param warehouseId     仓库ID
     * @param vendorBatchCode 供应商原始批次号（可为空）
     * @return 创建后的批次信息
     */
    WmBatchVo createBatchByMaterial(Long materialId, String sourceDocType, Long sourceDocId, String sourceDocNo, Long warehouseId, String vendorBatchCode);

    /**
     * 更新批次数量
     *
     * @param batchId  批次ID
     * @param deltaQty 变动数量
     * @param isReceive true为收货，false为发料
     */
    void updateQuantity(Long batchId, BigDecimal deltaQty, boolean isReceive);

    /**
     * 变更批次状态
     *
     * @param batchId 批次ID
     * @param status  目标状态
     */
    void changeStatus(Long batchId, String status);

    /**
     * 检查过期批次（定时任务调用）
     * 查找所有ACTIVE状态且有效期小于当天的批次，更新状态为EXPIRED
     */
    void checkExpiry();
}
