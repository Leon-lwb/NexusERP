package com.nexus.erp.wm.controller.serial;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 验证真实行集合、分配明细与ID字符串解析。 */
class WmSerialBusinessPayloadTest {
    final ObjectMapper json=new ObjectMapper();
    @ParameterizedTest @ValueSource(strings={"receive","productionissue","return","transfer","stocktaking","salesIssue","miscissue","miscrecpt","productionrecpt","sc-issue","sc-receive","/erp/so/return","/erp/pu/return","/erp/prd/rework"})
    void originalBusinessRouteIsRecognized(String path) {assertNotNull(SerialBusinessRoute.ofPath(path.startsWith("/")?path:"/erp/wm/"+path));}
    @ParameterizedTest @ValueSource(strings={"/erp/wm/sn","/erp/wm/code/resolve","/erp/wm/trace/repair/audit","/erp/wm/receiveX","/erp/wm/barcode","/mes/label/task"})
    void unrelatedOrExcludedUrlIsUntouched(String path) {assertNull(SerialBusinessRoute.ofPath(path));}
    @Test void receiptAllocationIsAggregatedUnderItsActualBusinessLine() throws Exception {
        var p=parse("{\"wmMaterialReceiveLineList\":[{\"lineId\":\"9007199254740993\",\"detailVersion\":\"opaque:v-001\",\"wmMaterialReceiveLineDetailList\":[{\"snItems\":[{\"identityId\":\"9007199254740995\",\"targetLocationId\":\"7\"}]}]}]}","receive");
        assertEquals(9007199254740993L,p.lines().get(0).oldLine());assertEquals(9007199254740995L,p.lines().get(0).items().get(0).identityId());assertEquals("opaque:v-001",p.lines().get(0).version());
    }
    @Test void ambiguousFlatCollectionCannotPickOneOfMultipleLines() {
        assertThrows(RuntimeException.class,()->parse("{\"wmTransferLineList\":[{\"lineId\":\"11\"},{\"lineId\":\"12\"}],\"snItems\":[{\"identityId\":\"101\"}]}","transfer"));
    }
    @Test void numericOpaqueVersionIsRejected() {assertThrows(RuntimeException.class,()->parse("{\"detailVersion\":1}","transfer"));}
    @Test void repeatedPhysicalIdentityAcrossRowsIsRejected() {assertThrows(RuntimeException.class,()->parse("{\"wmTransferLineList\":[{\"lineId\":\"11\",\"snItems\":[{\"identityId\":\"101\"}]},{\"lineId\":\"12\",\"snItems\":[{\"identityId\":\"101\"}]}]}","transfer"));}
    @Test void warehouseFinishedBatchesAreNotSubjectToMixedComponentLotPolicy() throws Exception {
        var p=parse("{\"snItems\":[{\"identityId\":\"101\",\"batchId\":\"51\"},{\"identityId\":\"102\",\"batchId\":\"52\"}]}","transfer");assertEquals(2,p.lines().get(0).items().size());
    }
    WmSerialBusinessPayload parse(String body,String path) throws Exception {return new WmSerialBusinessPayload(json.readTree(body),SerialBusinessRoute.ofPath("/erp/wm/"+path));}
}
