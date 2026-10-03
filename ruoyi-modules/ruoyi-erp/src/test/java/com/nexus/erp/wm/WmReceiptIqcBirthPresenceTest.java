package com.nexus.erp.wm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** 出生来源必须有实际跨模块契约及ERP写入口，不能仅添加当前单位读取器。 */
class WmReceiptIqcBirthPresenceTest {
    @Test void birthContractAndNativeErpWriterMustExist() {
        assertDoesNotThrow(() -> {
            Class.forName("com.nexus.common.mq.iqc.ReceiptIqcBirthPort");
            Class.forName("com.nexus.erp.wm.service.WmReceiptIqcBirthService");
        });
    }
}
