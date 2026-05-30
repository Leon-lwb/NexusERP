package com.nexus.erp.wm.service.impl;

import lombok.RequiredArgsConstructor;
import com.nexus.erp.wm.barcode.BarcodeGenerator;
import com.nexus.erp.wm.barcode.BarcodeRule;
import com.nexus.erp.wm.service.IBarcodeService;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 条码服务实现
 * <p>
 * 基于条码规则生成符合业务规范的条码字符串。
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@Service
public class BarcodeServiceImpl implements IBarcodeService {

    /** 条码生成器实例 */
    private final BarcodeGenerator barcodeGenerator = new BarcodeGenerator();

    /**
     * 根据规则生成条码
     *
     * @param rule    条码规则配置
     * @param bizDate 业务日期
     * @return 生成的条码字符串
     */
    @Override
    public String generate(BarcodeRule rule, Date bizDate) {
        return barcodeGenerator.generate(rule, bizDate);
    }
}
