package com.nexus.mes.report.adapter;
import com.nexus.common.report.*;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class QualityTraceDatasetProviderTest {
    @Test void qualityDocumentBindsIdTenantDepartmentAndNeverAssumesQuantityDimensions(){
        var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE qc_iqc(iqc_id BIGINT,tenant_id VARCHAR(20),create_dept BIGINT,create_by BIGINT,del_flag CHAR,iqc_no VARCHAR(50),material_code VARCHAR(50),quantity DECIMAL(20,6))");
        jdbc.update("INSERT INTO qc_iqc VALUES(1,'A',10,7,'0','QC-A','M-A',0.1),(2,'A',20,8,'0','QC-B','M-B',0.2),(1,'B',10,7,'0','QC-C','M-C',0.3)");
        var actor=new ReportActor("A",7,10,false,List.of(new ReportRole(2,Set.of("mes:qc:iqc:query"),new ReportScope(false,Set.of(10L),false,7))));
        var provider=new QualityTraceDatasetProvider(jdbc);var context=new ReportExecutionContext(actor,List.of(ReportScope.all(7)));
        var result=provider.query(context,new DatasetQuery(Map.of("iqcId","1"),Set.of("iqcNo","materialCode","quantity"),1,50,false,null,false));
        assertEquals(1,result.total());assertEquals("QC-A",result.rows().get(0).get("iqcNo"));assertFalse(provider.descriptor().field("quantity").aggregatable());
        assertEquals(0,provider.query(context,new DatasetQuery(Map.of("iqcId","2"),Set.of("iqcNo"),1,50,false,null,false)).total());
        assertThrows(DatasetAccessException.class,()->provider.query(context,new DatasetQuery(Map.of("iqcId","1 OR 1=1"),Set.of("iqcNo"),1,50,false,null,false)));
    }
}
