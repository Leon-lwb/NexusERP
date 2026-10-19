package com.nexus.erp.wm.trace;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;
class StructuredLabelResolverTest {
    final StructuredLabelResolver resolver=new StructuredLabelResolver(new ObjectMapper());
    CodeCandidate sn(Long id,Long material,Long batch){return new CodeCandidate(CodeObjectType.SN,id,null,"SN"+id,material,"M"+material,batch,batch==null?null:"B"+batch,null);}
    CodeResolveRequest request(String raw){return new CodeResolveRequest(raw,"RECEIVE",null,99L,null,"WM_MATERIAL_RECEIVE",55L,66L);}
    CodeResolution run(String raw,Map<String,CodeResolution> data){return resolver.resolve(request(raw),Set.of(CodeObjectType.SN,CodeObjectType.BATCH,CodeObjectType.MATERIAL), (r,types)->{assertEquals(99L,r.supplierId());assertEquals(55L,r.sourceDocId());return data.getOrDefault(r.rawCode(),CodeResolution.missing("missing"));});}
    @Test void completeOcrObjectResolvesOnlyAfterAllMasterDataAssertionsMatch(){
        var data=Map.of("EXT",CodeResolution.resolved(sn(1L,2L,3L)),"M2",CodeResolution.resolved(new CodeCandidate(CodeObjectType.MATERIAL,null,null,null,2L,"M2",null,null,null)),"B3",CodeResolution.resolved(new CodeCandidate(CodeObjectType.BATCH,null,null,null,2L,"M2",3L,"B3",null)));
        var result=run("{\"snCode\":\"EXT\",\"materialCode\":\"M2\",\"batchCode\":\"B3\",\"matchedMaterial\":{\"materialId\":\"2\",\"materialCode\":\"M2\"}}",data);
        assertEquals(ResolveStatus.RESOLVED,result.status());assertEquals(1L,result.identityId());
    }
    @Test void mismatchedOcrCodeAndMatchedIdNeverGetCombinedIntoAnotherMaterial(){
        var data=Map.of("M2",CodeResolution.resolved(new CodeCandidate(CodeObjectType.MATERIAL,null,null,null,2L,"M2",null,null,null)));
        assertEquals(ResolveStatus.INVALID,run("{\"materialCode\":\"M2\",\"matchedMaterial\":{\"materialId\":\"9\"}}",data).status());
    }
    @Test void sourceBatchAssertionCannotAttachAnUnboundOrDifferentSn(){
        var data=Map.of("EXT",CodeResolution.resolved(sn(1L,2L,null)),"B3",CodeResolution.resolved(new CodeCandidate(CodeObjectType.BATCH,null,null,null,2L,"M2",3L,"B3",null)));
        assertEquals(ResolveStatus.INVALID,run("{\"snCode\":\"EXT\",\"batchCode\":\"B3\"}",data).status());
    }
    @Test void secondaryMaterialEvidenceCanDisambiguateButNeverSelectTheFirstUnit(){
        var data=Map.of("EXT",CodeResolution.ambiguous(List.of(sn(1L,2L,null),sn(9L,8L,null))),"M8",CodeResolution.resolved(new CodeCandidate(CodeObjectType.MATERIAL,null,null,null,8L,"M8",null,null,null)));
        assertEquals(9L,run("{\"snCode\":\"EXT\",\"materialCode\":\"M8\"}",data).identityId());
        assertEquals(ResolveStatus.AMBIGUOUS,run("{\"snCode\":\"EXT\"}",data).status());
    }
    @Test void malformedAndUnsafeNumericEvidenceIsRejected(){
        assertEquals(ResolveStatus.INVALID,run("{bad}",Map.of()).status());
        assertEquals(ResolveStatus.INVALID,run("{\"snCode\":\"EXT\",\"materialId\":1900000000000000123}",Map.of("EXT",CodeResolution.resolved(sn(1L,2L,null)))).status());
    }
    @Test void ocrAssertionsCannotExpandTheCallersObjectPermissions(){
        var result=resolver.resolve(request("{\"snCode\":\"EXT\",\"materialCode\":\"M2\",\"batchCode\":\"B3\"}"),Set.of(CodeObjectType.SN),(r,types)->{
            assertEquals(Set.of(CodeObjectType.SN),types);return CodeResolution.resolved(sn(1L,2L,3L));
        });
        assertEquals(ResolveStatus.RESOLVED,result.status());
    }

}
