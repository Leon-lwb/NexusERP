package com.nexus.common.trace;
import com.nexus.common.trace.port.CompletionAllocationPort;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;
class CompletionAllocationIntegrityTest {
    CompletionMaterialAllocation row(long id,Long identity,BigDecimal qty){return new CompletionMaterialAllocation(id,8L,2L,id,null,identity,1000+id,4L,qty,1,"a".repeat(64),"SOURCE"+id);}
    CompletionAllocationPort port(List<CompletionMaterialAllocation> rows){return new CompletionAllocationPort(){
        public CompletionAllocationManifest allocationManifest(String t,Long id,int r){return new CompletionAllocationManifest(t,id,r,rows.size(),CompletionAllocationIntegrity.hash(rows));}
        public TracePage<CompletionMaterialAllocation> allocations(String t,Long id,int r,String c,int l){int start=c==null?0:Integer.parseInt(c),end=Math.min(start+l,rows.size());return new TracePage<>(rows.subList(start,end),end<rows.size()?Integer.toString(end):null,end<rows.size());}
    };}
    @Test void verifiesEveryActualConsumptionAfterMultiplePages(){var rows=LongStream.rangeClosed(1,1101).mapToObj(id->row(id,null,BigDecimal.ONE)).toList();assertEquals(1101,CompletionAllocationIntegrity.read(port(rows),"000001",9L,1,CompletionAllocationIntegrity.hash(rows)).size());}
    @Test void theSameComponentSerialCannotBeConsumedAsTwoPhysicalUnits(){var rows=List.of(row(1,77L,BigDecimal.ONE),row(2,77L,BigDecimal.ONE));assertThrows(IllegalStateException.class,()->CompletionAllocationIntegrity.read(port(rows),"000001",9L,1,CompletionAllocationIntegrity.hash(rows)));}
    @Test void trackedComponentSerialCannotBeAFractionOfAUnit(){var rows=List.of(row(1,77L,new BigDecimal("0.5")));assertThrows(IllegalStateException.class,()->CompletionAllocationIntegrity.read(port(rows),"000001",9L,1,CompletionAllocationIntegrity.hash(rows)));}
    @Test void evenAnEmptyComponentSnapshotMustMatchTheFrozenEventHash(){assertThrows(IllegalStateException.class,()->CompletionAllocationIntegrity.read(port(List.of()),"000001",9L,1,"b".repeat(64)));assertTrue(CompletionAllocationIntegrity.read(port(List.of()),"000001",9L,1,CompletionAllocationIntegrity.hash(List.of())).isEmpty());}
}
