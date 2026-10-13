package com.nexus.report.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.service.ReportDefinitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportDefinitionControllerTest {
    final ReportRequestActor actors=mock(ReportRequestActor.class);
    final ReportDefinitionService definitions=mock(ReportDefinitionService.class);
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final ReportCompiler compiler=new ReportCompiler(json);
    final ReportActor actor=new ReportActor("T",2,10,true,List.of());
    final ReportDefinitionController controller=new ReportDefinitionController(actors,definitions,compiler,new ReportProperties());

    @BeforeEach void setup(){when(actors.current()).thenReturn(actor);}

    @Test void sampleSerializesIdsAndDecimalsAsTypedTextAndUsesCurrentMaskedFields() throws Exception {
        var descriptor=new DatasetDescriptor("TEST","测试",1,"test:list",false,
            List.of(new DatasetField("id","ID",ValueType.ID),new DatasetField("amount","金额",ValueType.DECIMAL),
                new DatasetField("quantity","数量",ValueType.INTEGER),new DatasetField("name","名称",ValueType.STRING)),
            List.of(),List.of("id","amount","quantity","name"));
        var dataset=new Dataset("3","T","TEST","测试","TEST","4","ACTIVE",2);
        when(definitions.datasets(actor)).thenReturn(List.of(new ReportDefinitionService.DatasetView(dataset,descriptor,false)));
        var raw=new LinkedHashMap<String,Object>();
        raw.put("id",Long.MAX_VALUE);raw.put("amount",new BigDecimal("12345678901234567890.123400"));
        raw.put("quantity",9007199254740993L);raw.put("name",null);raw.put("hidden_cost",new BigDecimal("999.99"));
        var time=Instant.parse("2026-10-14T01:00:00Z");
        when(definitions.sample(actor,"3",Map.of())).thenReturn(new DatasetResult(List.of(raw),1,time));
        var result=json.valueToTree(controller.sample("3",Map.of()).getData());
        var row=result.get("rows").get(0);
        assertEquals("ID",row.get("id").get("type").asText());
        assertTrue(row.get("id").get("value").isTextual());
        assertEquals(Long.toString(Long.MAX_VALUE),row.get("id").get("value").asText());
        assertEquals("12345678901234567890.123400",row.get("amount").get("value").asText());
        assertEquals("9007199254740993",row.get("quantity").get("value").asText());
        assertEquals("STRING",row.get("name").get("type").asText());
        assertTrue(row.get("name").get("value").isNull());assertFalse(row.has("hidden_cost"));
        assertEquals(1,result.get("total").asLong());assertEquals(json.valueToTree(time),result.get("dataTime"));
    }

    @Test void sampleCannotQueryADatasetMissingFromTheCurrentVisibleDescriptors(){
        when(definitions.datasets(actor)).thenReturn(List.of());
        var fault=assertThrows(ReportFault.class,()->controller.sample("3",Map.of()));
        assertEquals("SCOPE_DENIED",fault.code());verify(definitions,never()).sample(any(),anyString(),any());
    }

    @Test void draftDetailRechecksDatasetAndFieldPermissionsBeforeReturningDsl(){
        var dsl=dsl();var definition=new Definition("1","T","TEST_REPORT","测试","TABLE",null,
            compiler.json(dsl),1,"4","PUBLISHED",2,Instant.now());
        when(definitions.detail(actor,"1")).thenReturn(definition);
        when(definitions.bind(eq(actor),any())).thenThrow(ReportFault.denied());
        assertEquals("SCOPE_DENIED",assertThrows(ReportFault.class,()->controller.detail("1")).code());
    }

    @Test void publishedDetailRechecksDatasetAndFieldPermissionsBeforeReturningDsl(){
        var version=new Version("4","1",1,1,"1.0.0",compiler.json(dsl()),"checksum","3",2,Instant.now());
        when(definitions.published(actor,"1","VIEW","report:runtime:preview")).thenReturn(version);
        when(definitions.bind(eq(actor),any())).thenThrow(ReportFault.denied());
        assertEquals("SCOPE_DENIED",assertThrows(ReportFault.class,()->controller.published("1")).code());
    }

    @Test void draftWritesRejectMissingRevisionBeforeCallingService(){
        var body=new ReportDefinitionController.DraftRequest(json.valueToTree(dsl()),null,null);
        assertThrows(ReportFault.class,()->controller.save("1",body));
        verify(definitions,never()).save(any(),anyString(),anyLong(),any());
    }

    private ReportDsl dsl(){return new ReportDsl(1,"TEST_REPORT","测试",ReportDsl.Kind.TABLE,"TEST","3",
        List.of(new ReportDsl.Column("id","ID",100,0,null,null)),List.of(),null,List.of(),List.of(),List.of(),true);}
}
