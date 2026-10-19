package com.nexus.mes.trace;

import com.nexus.common.trace.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class MesMaterialPolicyGuardTest {
    MesTraceBusinessTest b;long first,second;
    @BeforeEach void setup(){
        b=new MesTraceBusinessTest();b.setup();b.sn(1,"IN_PRODUCTION");
        String source=b.source(1,10,0);first=b.allocate("map-first",source,8,5);second=b.allocate("map-second",source,9,5);
    }
    @AfterEach void cleanup(){b.cleanup();}
    void pick(String command,long lot,long allocation,TrackingPolicy policy){b.inTx(()->b.materials.pick(b.pick(command,lot,allocation,1,1L),policy));}
    TrackingPolicy policy(boolean mixed,long version){return new TrackingPolicy(true,false,mixed,version,TrackingStage.STRICT);}
    void frozenGuard(boolean mixed,long version){
        assertEquals(mixed,b.jdbc.queryForObject("SELECT allow_mixed_lot FROM lt_sn_material_guard",Boolean.class));
        assertEquals(version,b.jdbc.queryForObject("SELECT policy_version FROM lt_sn_material_guard",Long.class));
        assertEquals(0,b.jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption WHERE policy_version<>?",Integer.class,version));
    }
    @Test void tighteningCurrentMixPolicyCannotChangeAnExistingIdentityComponentSnapshot(){
        pick("first",8,first,policy(true,1));
        assertDoesNotThrow(()->pick("second",9,second,policy(false,2)));
        frozenGuard(true,1);assertEquals(2,b.jdbc.queryForObject("SELECT COUNT(*) FROM lt_sn_consumption",Integer.class));
    }
    @Test void relaxingCurrentPolicyCannotChangeFrozenNoMixOrItsVersion(){
        pick("first",8,first,policy(false,7));pick("same-lot",8,first,policy(true,99));frozenGuard(false,7);
        assertThrows(RuntimeException.class,()->pick("other-lot",9,second,policy(true,99)));
        frozenGuard(false,7);assertEquals(2,b.jdbc.queryForObject("SELECT COUNT(*) FROM lt_wo_consumption",Integer.class));
    }
    @Test void explicitSnAllocationAndItsOutgoingFactUseTheFrozenGuardVersion(){
        pick("first",8,first,policy(true,3));
        long consumption=b.inTx(()->b.materials.pick(b.pick("actual-second",8,first,1,null),policy(true,99))).get(0);
        long entry=b.inTx(()->b.materials.allocateToSn("assign",consumption,1L,BigDecimal.ONE,30L,"step",1L,policy(false,99)));
        assertEquals(3L,b.jdbc.queryForObject("SELECT policy_version FROM lt_sn_consumption WHERE entry_id=?",Long.class,entry));
        String payload=b.jdbc.queryForObject("SELECT payload FROM mes_trace_outbox WHERE event_type='SN_CONSUMPTION_ALLOCATED' AND aggregate_id=?",String.class,entry);
        assertEquals(3,b.events.read(payload).path("policyVersion").asLong());frozenGuard(true,3);
    }
    @Test void pickFactDoesNotClaimTheLaterCurrentPolicyVersion(){
        pick("first",8,first,policy(true,3));pick("second",8,first,policy(false,99));
        String payload=b.jdbc.queryForList("SELECT payload FROM mes_trace_outbox WHERE event_type='MATERIAL_CONSUMPTION_RECORDED'",String.class)
            .stream().filter(p->"second".equals(b.events.read(p).path("commandId").asText())).findFirst().orElseThrow();
        assertEquals(3,b.events.read(payload).path("policyVersion").asLong());frozenGuard(true,3);
    }
    @Test void missingFrozenGuardVersionCannotBeGuessedFromCurrentPolicy(){
        pick("first",8,first,policy(true,1));b.jdbc.update("UPDATE lt_sn_material_guard SET policy_version=NULL");
        assertThrows(RuntimeException.class,()->pick("second",8,first,policy(true,99)));
        assertEquals(1,b.jdbc.queryForObject("SELECT COUNT(*) FROM lt_wo_consumption",Integer.class));
        assertEquals(0,BigDecimal.ONE.compareTo(b.jdbc.queryForObject("SELECT consumed_qty FROM lt_lot WHERE lot_id=8",BigDecimal.class)));
    }
}
