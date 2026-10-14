package com.nexus.report.export;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.RenderPlan;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.*;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** Explicit STRING cells prevent formula injection; values beyond Excel precision remain exact text. */
public final class ReportSpreadsheetExporter {
    private final com.nexus.report.storage.ReportArtifactStore artifacts;private final String tenant;private final long temporaryBudget;
    public ReportSpreadsheetExporter(){this(null,"standalone",200L*1024*1024);}
    public ReportSpreadsheetExporter(com.nexus.report.storage.ReportArtifactStore artifacts,String tenant,long temporaryBudget){this.artifacts=artifacts;this.tenant=tenant;this.temporaryBudget=temporaryBudget;}
    @FunctionalInterface public interface RowReader { void read(Consumer<Map<String,TypedValue>> consumer); }
    public void data(ReportDsl dsl, RowReader reader, OutputStream output, ReportCancellation cancellation) {
        cancellation.check();var staging=artifacts!=null?artifacts:new com.nexus.report.storage.LocalReportArtifactStore(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),"nexus-report-private"),temporaryBudget);
        var workbook=new ReportPrivateWorkbook(staging,tenant,temporaryBudget,cancellation);
        try {
            var sheet=workbook.createSheet("明细");sheet.createFreezePane(0,1);
            var header=sheet.createRow(0);for(int c=0;c<dsl.columns().size();c++){
                header.createCell(c,CellType.STRING).setCellValue(dsl.columns().get(c).label());
                sheet.setColumnWidth(c,Math.min(255*256,Math.max(12*256,(int)(dsl.columns().get(c).width()/5.25*256))));
            }
            Map<Integer,CellStyle> formats=new HashMap<>();int[] index={1};
            reader.read(values->{cancellation.check();if(index[0]>100000)throw ReportFault.capacity();var row=sheet.createRow(index[0]++);
                for(int c=0;c<dsl.columns().size();c++){
                    var column=dsl.columns().get(c);var cell=row.createCell(c);var value=values.get(column.field());setValue(cell,value);
                    if(cell.getCellType()==CellType.NUMERIC)cell.setCellStyle(formats.computeIfAbsent(column.scale(),scale->{var style=workbook.createCellStyle();style.setDataFormat(workbook.createDataFormat().getFormat(numberFormat(scale)));return style;}));
                }
            });
            cancellation.check();workbook.writeAvoidingTempFiles(output);
        } catch(IOException e){throw new ReportFault("XLSX_EXPORT_FAILED","Excel 生成失败",500);}
        finally{workbook.prepareRelease();try{workbook.close();}catch(IOException|RuntimeException ignored){}try{workbook.dispose();}catch(RuntimeException ignored){}finally{workbook.release();}}
    }
    public void layout(RenderPlan plan, OutputStream output, ReportCancellation cancellation) {
        try(var workbook=new XSSFWorkbook()){
            Map<String,CellStyle> styles=new HashMap<>();
            for(var page:plan.pages()){
                cancellation.check();var sheet=workbook.createSheet("第"+page.number()+"页");
                var xs=new TreeSet<Double>();var ys=new TreeSet<Double>();
                xs.add(0d);xs.add(round(page.width()));ys.add(0d);ys.add(round(page.height()));
                for(var cell:page.cells()){xs.add(round(cell.x()));xs.add(round(cell.x()+cell.width()));ys.add(round(cell.y()));ys.add(round(cell.y()+cell.height()));}
                // Excel caps each column width and row height. Split large coordinate intervals.
                var x=grid(xs,255*5.25);var y=grid(ys,409);
                for(int c=0;c<x.size()-1;c++)sheet.setColumnWidth(c,Math.min(255*256,Math.max(1,(int)(Math.round(x.get(c+1)/5.25*256)-Math.round(x.get(c)/5.25*256)))));
                for(int r=0;r<y.size()-1;r++)sheet.createRow(r).setHeightInPoints((Math.round(y.get(r+1)*20)-Math.round(y.get(r)*20))/20f);
                for(var source:page.cells()){
                    cancellation.check();int c=x.indexOf(round(source.x())),r=y.indexOf(round(source.y()));
                    int endC=x.indexOf(round(source.x()+source.width()))-1,endR=y.indexOf(round(source.y()+source.height()))-1;
                    var cell=sheet.getRow(r).createCell(c);
                    setLayoutValue(cell,source);
                    var style=source.style();int scale=source.value()!=null&&source.value().type()==ValueType.INTEGER?0:source.scale();
                    String key=style.toString()+":"+scale;
                    cell.setCellStyle(styles.computeIfAbsent(key,k->style(workbook,style,scale,plan.fontName())));
                    if(endC>c||endR>r)sheet.addMergedRegion(new CellRangeAddress(r,endR,c,endC));
                }
                for(short margin:new short[]{Sheet.LeftMargin,Sheet.RightMargin,Sheet.TopMargin,Sheet.BottomMargin,Sheet.HeaderMargin,Sheet.FooterMargin})sheet.setMargin(margin,0);
                workbook.setPrintArea(workbook.getSheetIndex(sheet),0,x.size()-2,0,y.size()-2);
                var print=sheet.getPrintSetup();print.setLandscape(page.width()>page.height());print.setPaperSize(PrintSetup.A4_PAPERSIZE);print.setFitWidth((short)1);print.setFitHeight((short)1);sheet.setFitToPage(true);
            }
            cancellation.check();workbook.write(output);
        }catch(IOException e){throw new ReportFault("XLSX_EXPORT_FAILED","Excel 生成失败",500);}
    }
    private static List<Double> grid(NavigableSet<Double> points,double maximum){
        var boundaries=new ArrayList<>(points);
        for(int i=1;i<boundaries.size();i++){
            double start=boundaries.get(i-1),gap=boundaries.get(i)-start;int pieces=(int)Math.ceil(gap/maximum);
            for(int p=1;p<pieces;p++)points.add(round(start+gap*p/pieces));
        }
        return new ArrayList<>(points);
    }
    private static void setLayoutValue(Cell cell,RenderPlan.Cell source){
        String text=String.join("\n",source.lines());var value=source.value();
        // The planned display fixes rounding and wrapping. Detail XLSX retains unformatted raw values.
        if(value!=null&&source.lines().size()==1&&!text.isEmpty()&&(value.type()==ValueType.DECIMAL||value.type()==ValueType.INTEGER)){
            setValue(cell,new TypedValue(value.type(),text));
        }else cell.setCellValue(text);
    }
    private static double round(double v){return Math.rint(v*1000)/1000;}
    private static String numberFormat(int scale){return scale==0?"0":"0."+"0".repeat(scale);}
    private static XSSFCellStyle style(XSSFWorkbook workbook,ReportDsl.Style source,int scale,String fontName){
        var style=workbook.createCellStyle();var font=workbook.createFont();font.setFontName(fontName);font.setFontHeight((short)Math.round(source.fontSize()*20));font.setBold(source.bold());font.setColor(color(source.color()));style.setFont(font);
        style.setAlignment(HorizontalAlignment.valueOf(source.align()));style.setVerticalAlignment(VerticalAlignment.TOP);style.setWrapText(true);style.setFillForegroundColor(color(source.background()));style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);style.setBorderTop(BorderStyle.THIN);style.setBorderLeft(BorderStyle.THIN);style.setBorderRight(BorderStyle.THIN);style.setDataFormat(workbook.createDataFormat().getFormat(numberFormat(scale)));return style;
    }
    private static XSSFColor color(String hex){return new XSSFColor(java.awt.Color.decode(hex),new DefaultIndexedColorMap());}
    private static void setValue(Cell cell,TypedValue value){
        if(value==null||value.value()==null){cell.setBlank();return;}
        if(value.type()==ValueType.BOOLEAN){cell.setCellValue(Boolean.parseBoolean(value.value()));return;}
        if(value.type()==ValueType.DECIMAL||value.type()==ValueType.INTEGER){
            var number=new BigDecimal(value.value());double converted=number.doubleValue();
            if(number.precision()<=15&&Double.isFinite(converted)&&BigDecimal.valueOf(converted).compareTo(number)==0){cell.setCellValue(converted);return;}
        }
        // setCellValue(String) never produces a formula, even for =,+,-,@ prefixes.
        cell.setCellValue(value.value());
    }
}
