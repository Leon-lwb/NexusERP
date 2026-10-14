package com.nexus.report.export;

import com.nexus.common.report.ReportCancellation;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.engine.FontSupport;
import com.nexus.report.engine.RenderPlan;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;

import java.awt.Color;
import java.io.*;
import java.nio.file.Path;

/** PDF 消费服务端固定坐标及断行，不再执行另一套布局。 */
public final class ReportPdfExporter {
    /**
     * 向受控输出流写入固定报表结果。
     *
     * @param plan 当前操作的 {@code plan} 输入
     * @param output 受控输出流
     * @param fontPath 当前操作的 {@code fontPath} 输入
     * @param cancellation 执行取消信号
     */
    public void write(
            RenderPlan plan, OutputStream output, Path fontPath, ReportCancellation cancellation) {
        try (var document = new PDDocument();
                var handle = FontSupport.load(document, fontPath)) {
            document.getDocumentInformation()
                    .setProducer("Nexus Report Engine v" + plan.schemaVersion());
            for (var page : plan.pages()) {
                cancellation.check();
                var pdfPage =
                        new PDPage(new PDRectangle((float) page.width(), (float) page.height()));
                document.addPage(pdfPage);
                try (var canvas = new PDPageContentStream(document, pdfPage)) {
                    for (var cell : page.cells()) {
                        cancellation.check();
                        float x = (float) cell.x(),
                                y = (float) (page.height() - cell.y() - cell.height());
                        canvas.setNonStrokingColor(Color.decode(cell.style().background()));
                        canvas.addRect(x, y, (float) cell.width(), (float) cell.height());
                        canvas.fill();
                        canvas.setStrokingColor(new Color(210, 216, 224));
                        canvas.setLineWidth(.4f);
                        canvas.addRect(x, y, (float) cell.width(), (float) cell.height());
                        canvas.stroke();
                        canvas.setNonStrokingColor(Color.decode(cell.style().color()));
                        canvas.setStrokingColor(Color.decode(cell.style().color()));
                        float size = (float) cell.style().fontSize();
                        float baseline = (float) (page.height() - cell.y() - size - 4);
                        for (String line : cell.lines()) {
                            float width = handle.font().getStringWidth(line) / 1000 * size;
                            float left =
                                    switch (cell.style().align()) {
                                        case "CENTER" -> x + ((float) cell.width() - width) / 2;
                                        case "RIGHT" -> x + (float) cell.width() - width - 4;
                                        default -> x + 4;
                                    };
                            canvas.beginText();
                            canvas.setFont(handle.font(), size);
                            canvas.setLineWidth(.18f);
                            canvas.setRenderingMode(
                                    cell.style().bold()
                                            ? RenderingMode.FILL_STROKE
                                            : RenderingMode.FILL);
                            canvas.newLineAtOffset(left, baseline);
                            canvas.showText(line);
                            canvas.endText();
                            baseline -= size * 1.2f;
                        }
                    }
                    for (var media : page.media()) {
                        cancellation.check();
                        var raster = plan.rasters().get(media.resource());
                        if (raster == null) throw new ReportFault("PLAN_INVALID", "计划媒体资源缺失", 409);
                        var image =
                                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
                                        .createFromByteArray(
                                                document,
                                                java.util.Base64.getDecoder().decode(raster.png()),
                                                media.resource());
                        canvas.drawImage(
                                image,
                                (float) media.x(),
                                (float) (page.height() - media.y() - media.height()),
                                (float) media.width(),
                                (float) media.height());
                    }
                }
            }
            cancellation.check();
            document.save(output);
        } catch (IOException | IllegalArgumentException e) {
            throw new ReportFault("PDF_EXPORT_FAILED", "PDF 生成失败，请检查报表字体和字符", 500);
        }
    }
}
