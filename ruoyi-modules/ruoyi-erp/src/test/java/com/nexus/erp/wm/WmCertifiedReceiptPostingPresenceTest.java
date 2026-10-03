package com.nexus.erp.wm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 语义 RED：必须有真实新过账入口，而不是仅有原始来源读取器。 */
class WmCertifiedReceiptPostingPresenceTest {
    @Test void preciseReceiptPostingMustExistAsAnActualWriteService() {
        assertDoesNotThrow(() -> Class.forName("com.nexus.erp.wm.service.WmCertifiedReceiptPostingService"),
            "缺少真实原生精确收货过账入口，旧虚拟补造链不能认证");
    }
}
