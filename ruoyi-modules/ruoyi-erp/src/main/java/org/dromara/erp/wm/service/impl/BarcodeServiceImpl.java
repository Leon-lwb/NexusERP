package org.dromara.erp.wm.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.erp.wm.barcode.BarcodeGenerator;
import org.dromara.erp.wm.barcode.BarcodeRule;
import org.dromara.erp.wm.service.IBarcodeService;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 条码服务实现
 */
@RequiredArgsConstructor
@Service
public class BarcodeServiceImpl implements IBarcodeService {

    private final BarcodeGenerator barcodeGenerator = new BarcodeGenerator();

    @Override
    public String generate(BarcodeRule rule, Date bizDate) {
        return barcodeGenerator.generate(rule, bizDate);
    }
}
