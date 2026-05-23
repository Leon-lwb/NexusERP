package org.dromara.erp.pu.service;

/**
 * 供应商评估服务接口
 * <p>
 * 定义供应商评分的计算操作，当前为预留接口，
 * 后续将根据交货及时率、质量合格率、价格波动等维度综合计算供应商评分。
 *
 * @author ruoyi
 */
public interface IPuVendorEvaluationService {

    /**
     * 计算指定供应商的评估评分
     *
     * @param vendorId 供应商ID
     */
    void calculateByVendor(Long vendorId);
}
