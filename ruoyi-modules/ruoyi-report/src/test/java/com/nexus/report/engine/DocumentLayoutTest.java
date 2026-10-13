package com.nexus.report.engine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DocumentLayoutTest {
    private CompiledReport compiled(){
        var descriptor=new DatasetDescriptor("TEST","测试",1,"wm:stock:list",false,List.of(new DatasetField("code","编号",ValueType.STRING),new DatasetField("amount","金额",ValueType.DECIMAL)),List.of(),List.of("code","amount"));
        var dsl=new ReportDsl(1,"TEST_REPORT","中文多页报表",ReportDsl.Kind.DOCUMENT,"TEST","1",List.of(new ReportDsl.Column("code","编号",130,0,null,null),new ReportDsl.Column("amount","金额",130,2,null,null)),List.of(),new ReportDsl.Page(300,260,10,10,10,10,24),List.of(new ReportDsl.BandRow(24,List.of(new ReportDsl.Cell(0,2,1,"静态合并标题",null,null)))),List.of(),List.of(),true);
        return new ReportCompiler(new ObjectMapper()).compile(dsl,descriptor,new ReportActor("A",1,10,true,List.of()));
    }
    @Test void everyLogicalRowAppearsOnceAcrossChinesePages(){
        List<Map<String,Object>> rows=new ArrayList<>();for(int i=0;i<100;i++)rows.add(Map.of("code","材料编号"+i,"amount",new BigDecimal("12.34")));
        var compiled=compiled();var data=new ReportCalculator().calculate(compiled,rows,Map.of());
        var plan=new DocumentLayoutEngine().layout(compiled,data,Map.of(),FontSupport.find(null));
        assertTrue(plan.pages().size()>1);assertEquals(100,plan.rowCount());
        var cells=plan.pages().stream().flatMap(page->page.cells().stream()).filter(cell->cell.band().equals("DETAIL")).toList();
        assertEquals(200,cells.size());assertEquals(100,cells.stream().map(RenderPlan.Cell::rowIndex).distinct().count());
        for(var page:plan.pages())for(var cell:page.cells()){assertTrue(cell.x()>=0&&cell.y()>=0);assertTrue(cell.x()+cell.width()<=page.width()+0.1);assertTrue(cell.y()+cell.height()<=page.height()+0.1);}
    }
    @Test void noDataProducesAVisibleEmptyStateWithoutAFabricatedBusinessRow(){
        var compiled=compiled();var data=new ReportCalculator().calculate(compiled,List.of(),Map.of());
        var plan=new DocumentLayoutEngine().layout(compiled,data,Map.of(),FontSupport.find(null));
        assertEquals(0,plan.rowCount());assertEquals(1,plan.pages().size());assertTrue(plan.pages().get(0).cells().stream().anyMatch(cell->cell.band().equals("EMPTY")));
        assertFalse(plan.pages().get(0).cells().stream().anyMatch(cell->cell.band().equals("DETAIL")));
    }
    @Test void aRowHigherThanThePrintableAreaFailsInsteadOfLosingContent(){
        var compiled=compiled();var data=new ReportCalculator().calculate(compiled,List.of(Map.of("code","长".repeat(3000),"amount",new BigDecimal("1"))),Map.of());
        assertEquals("ROW_TOO_TALL",assertThrows(ReportFault.class,()->new DocumentLayoutEngine().layout(compiled,data,Map.of(),FontSupport.find(null))).code());
    }
}
