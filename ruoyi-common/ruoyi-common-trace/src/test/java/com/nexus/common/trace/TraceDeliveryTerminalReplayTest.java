package com.nexus.common.trace;
import com.nexus.common.trace.port.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TraceDeliveryTerminalReplayTest {
    MaterialTraceEvent event(){return new MaterialTraceEvent("original","000001","MES","COMPLETION_MANIFEST_FROZEN","COMPLETION_MANIFEST",10L,1,1,"{}",TraceHashes.sha256("{}"));}
    @Test void verifiedTerminalReplayReturnsTheOriginalEffectEvenAfterTheManifestWasCancelled(){
        var calls=new ArrayList<String>();var stored=new TraceDeliveryResult("APPLIED","{\"outcome\":\"CANCELLED\"}");
        var inbox=new TraceInboxPort(){
            public String targetModule(){return "ERP";}
            public TraceDeliveryResult findApplied(MaterialTraceEvent e){calls.add("terminal-read");return stored;}
            public TraceDeliveryResult accept(MaterialTraceEvent e,TraceEventHandler h,Object p){assertNull(p);calls.add("terminal-ack-transaction");return stored;}
        };
        var handler=new TraceEventHandler(){
            public String targetModule(){return "ERP";}public boolean supports(String t){return true;}
            public Object prepare(MaterialTraceEvent e){fail("A cancelled original snapshot must not be prepared again");return null;}
            public String apply(MaterialTraceEvent e,Object p){fail("A terminal original effect must not be executed twice");return null;}
        };
        var verifier=new TraceSourceVerificationPort(){public String sourceModule(){return "MES";}public void verify(MaterialTraceEvent e){calls.add("source-proof");}};
        assertEquals(stored,new TraceDeliveryCoordinator(List.of(inbox),List.of(handler),List.of(verifier)).deliver("ERP",event()));
        assertEquals(List.of("source-proof","terminal-read","terminal-ack-transaction"),calls);
    }
    @Test void anUnverifiedDuplicateCannotReadOrRecreateApplicationAcknowledgements(){
        var inbox=new TraceInboxPort(){
            public String targetModule(){return "ERP";}
            public TraceDeliveryResult findApplied(MaterialTraceEvent e){fail("Unverified source must not access target result");return null;}
            public TraceDeliveryResult accept(MaterialTraceEvent e,TraceEventHandler h,Object p){fail("No transaction may start");return null;}
        };
        var handler=new TraceEventHandler(){public String targetModule(){return "ERP";}public boolean supports(String t){return true;}public String apply(MaterialTraceEvent e,Object p){return null;}};
        var verifier=new TraceSourceVerificationPort(){public String sourceModule(){return "MES";}public void verify(MaterialTraceEvent e){throw new IllegalArgumentException("No owner outbox proof");}};
        assertThrows(IllegalArgumentException.class,()->new TraceDeliveryCoordinator(List.of(inbox),List.of(handler),List.of(verifier)).deliver("ERP",event()));
    }
}
