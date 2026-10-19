package com.nexus.common.trace;

import com.nexus.common.trace.port.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TraceDeliveryCoordinatorTest {
    MaterialTraceEvent event() {return new MaterialTraceEvent("e","000001","MES","FROZEN","MANIFEST",1L,1,1,"{}",TraceHashes.sha256("{}"));}
    @Test void foreignPreparationHappensBeforeTargetTransactionAndUsesFrozenSnapshot() {
        List<String> calls=new ArrayList<>();Object frozen=new Object();
        var handler=new TraceEventHandler() {
            public String targetModule(){return "ERP";}
            public boolean supports(String type){return type.equals("FROZEN");}
            public Object prepare(MaterialTraceEvent e){calls.add("foreign-read");return frozen;}
            public String apply(MaterialTraceEvent e,Object p){assertSame(frozen,p);calls.add("target-effect");return "OK";}
        };
        var inbox=new TraceInboxPort() {
            public String targetModule(){return "ERP";}
            public TraceDeliveryResult accept(MaterialTraceEvent e,TraceEventHandler h,Object p){calls.add("target-begin");String result=h.apply(e,p);calls.add("target-commit");return new TraceDeliveryResult("APPLIED",result);}
        };
        assertTrue(new TraceDeliveryCoordinator(List.of(inbox),List.of(handler),List.of(new TraceSourceVerificationPort(){public String sourceModule(){return "MES";}public void verify(MaterialTraceEvent e){calls.add("source-proof");}})).deliver("ERP",event()).applied());
        assertEquals(List.of("source-proof","foreign-read","target-begin","target-effect","target-commit"),calls);
    }
    @Test void unknownAndDuplicateHandlersFailBeforeTargetTransaction() {
        var coordinator=new TraceDeliveryCoordinator(List.of(new TraceInboxPort(){
            public String targetModule(){return "ERP";}
            public TraceDeliveryResult accept(MaterialTraceEvent e,TraceEventHandler h,Object p){throw new AssertionError("must not open transaction");}
        }),List.of(),List.of());
        assertThrows(IllegalArgumentException.class,()->coordinator.deliver("ERP",event()));
        assertThrows(IllegalArgumentException.class,()->coordinator.deliver("MES",event()));
    }
}
