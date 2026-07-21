package com.nexus.erp.wm.service.impl;

import lombok.RequiredArgsConstructor;
import com.nexus.erp.wm.barcode.BarcodeGenerator;
import com.nexus.erp.wm.barcode.BarcodeRule;
import com.nexus.erp.wm.barcode.BarcodeSequenceProvider;
import com.nexus.erp.wm.service.IBarcodeService;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 条码服务实现
 * <p>
 * 基于条码规则生成符合业务规范的条码字符串。
 *
 * @author nexus
 */
@RequiredArgsConstructor
@Service
public class BarcodeServiceImpl implements IBarcodeService {

    /** 条码生成器 */
    private final BarcodeGenerator barcodeGenerator;
    /** 条码序列提供器 */
    private final BarcodeSequenceProvider barcodeSequenceProvider;

    /**
     * 根据规则生成条码
     *
     * @param rule    条码规则配置
     * @param bizDate 业务日期
     * @return 生成的条码字符串
     */
    @Override
    public String generate(BarcodeRule rule, Date bizDate) {
        String configCode = rule.getRuleCode() != null ? rule.getRuleCode() : "WM_RECEIVE_DEFAULT";
        long sequence = barcodeSequenceProvider.nextSequence(configCode);
        return barcodeGenerator.generate(rule, bizDate, sequence);
    }
}
