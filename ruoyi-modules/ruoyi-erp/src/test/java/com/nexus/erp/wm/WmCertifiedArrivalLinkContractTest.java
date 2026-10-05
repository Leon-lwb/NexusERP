package com.nexus.erp.wm;

import com.nexus.erp.wm.service.WmCertifiedReceiptDraftService;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 接线及旧请求构造器的可编译 RED；实际行为另由服务反例覆盖。 */
class WmCertifiedArrivalLinkContractTest {
    @Test void draftRequestHasPairedNullableNoticeReferences(){var names=Arrays.stream(WmCertifiedReceiptDraftService.Request.class.getRecordComponents()).map(c->c.getName()).toList();assertTrue(names.containsAll(List.of("noticeId","noticeLineId")),"认证收货请求缺少通知绑定入口");}
    @Test void postingActuallyUpdatesNoticeInsidePostingTransaction() throws Exception {String source=Files.readString(Path.of("src/main/java/com/nexus/erp/wm/service/WmCertifiedReceiptPostingService.java"));assertTrue(source.contains("receiveLocked("),"实际过账未调用通知累计，不能仅增加读取器");}
    @Test void noNoticeOriginalConstructorRemainsAvailable(){assertTrue(Arrays.stream(WmCertifiedReceiptDraftService.Request.class.getConstructors()).anyMatch(c->c.getParameterCount()==10));}
}
