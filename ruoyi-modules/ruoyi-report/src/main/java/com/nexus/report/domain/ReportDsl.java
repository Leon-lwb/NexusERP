package com.nexus.report.domain;
import java.util.*;
/** Stable v1 model, independent of frontend editor state and third-party sheet formats. */
public record ReportDsl(int schemaVersion,String code,String title,Kind kind,String datasetCode,String datasetVersionId,
    List<Column> columns,List<Parameter> parameters,Page page,List<BandRow> headerRows,List<BandRow> footerRows,List<Summary> summaries,Boolean repeatHeader) {
    public enum Kind { TABLE, DOCUMENT }
    public ReportDsl {
        columns=List.copyOf(columns==null?List.of():columns);parameters=List.copyOf(parameters==null?List.of():parameters);
        page=page==null?Page.a4():page;headerRows=List.copyOf(headerRows==null?List.of():headerRows);footerRows=List.copyOf(footerRows==null?List.of():footerRows);
        summaries=List.copyOf(summaries==null?List.of():summaries);repeatHeader=repeatHeader==null?true:repeatHeader;
    }
    public record Parameter(String name,String label,String type,boolean required,String defaultValue) { }
    public record Column(String field,String label,double width,int scale,String expression,Style style) {
        public Column { width=width==0?96:width;style=style==null?Style.normal():style; }
    }
    public record Page(double width,double height,double marginTop,double marginRight,double marginBottom,double marginLeft,double rowHeight) {
        public static Page a4(){return new Page(595.28,841.89,36,36,36,36,24);}
        public double usableWidth(){return width-marginLeft-marginRight;}
    }
    public record Style(double fontSize,boolean bold,String align,String color,String background) {
        public Style { fontSize=fontSize==0?10:fontSize;align=align==null?"LEFT":align;color=color==null?"#1f2937":color;background=background==null?"#ffffff":background; }
        public static Style normal(){return new Style(10,false,"LEFT",null,null);}
    }
    public record BandRow(double height,List<Cell> cells){public BandRow{cells=List.copyOf(cells==null?List.of():cells);}}
    public record Cell(int column,int colSpan,int rowSpan,String text,String field,Style style){
        public Cell{colSpan=colSpan==0?1:colSpan;rowSpan=rowSpan==0?1:rowSpan;style=style==null?Style.normal():style;}
    }
    public record Summary(String field,String function,List<String> groupBy,String label,int scale){public Summary{groupBy=List.copyOf(groupBy==null?List.of():groupBy);}}
}
