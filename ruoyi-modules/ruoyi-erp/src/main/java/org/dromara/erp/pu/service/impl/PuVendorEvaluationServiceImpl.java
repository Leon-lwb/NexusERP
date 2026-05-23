package org.dromara.erp.pu.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import org.dromara.erp.pu.service.IPuVendorEvaluationService;
import org.springframework.stereotype.Service;

/**
 * 供应商评估服务实现类
 * <p>
 * 当前为预留实现，仅校验供应商ID不为空。
 * 后续将根据交货及时率、质量合格率、价格波动等维度综合计算供应商评分。
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class PuVendorEvaluationServiceImpl implements IPuVendorEvaluationService {

    /**
     * 计算指定供应商的评估评分
     * <p>
     * 当前为预留方法，仅校验参数不为空，具体评分逻辑待后续实现。
     *
     * @param vendorId 供应商ID
     */
    @Override
    public void calculateByVendor(Long vendorId) {
        if (vendorId == null) {
            throw new RuntimeException("供应商ID不能为空");
        }
        // 预留：后续根据交货及时率、质量合格率、价格波动计算供应商评分
    }
}
