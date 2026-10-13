package com.nexus.report.engine;
import com.nexus.common.report.TypedValue;
import com.nexus.report.domain.ReportDsl;
import java.util.*;
/** Coordinates use points from the top-left. All formats consume this layout. */
public record RenderPlan(int schemaVersion,long rowCount,String fontName,List<Page> pages,List<ReportDsl.Column> columns,List<ReportCalculator.SummaryValue> summaries) {
    public RenderPlan { pages=List.copyOf(pages);columns=List.copyOf(columns);summaries=List.copyOf(summaries); }
    public record Page(int number,double width,double height,List<Cell> cells){public Page{cells=List.copyOf(cells);}}
    public record Cell(String band,int rowIndex,int column,int colSpan,int rowSpan,double x,double y,double width,double height,
        List<String> lines,ReportDsl.Style style,TypedValue value,int scale){public Cell{lines=List.copyOf(lines);}}
}
