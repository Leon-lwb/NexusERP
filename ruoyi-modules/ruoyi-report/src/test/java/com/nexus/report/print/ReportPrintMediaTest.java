package com.nexus.report.print;

import static org.junit.jupiter.api.Assertions.*;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.nexus.report.asset.ImageNormalizer;
import com.nexus.report.domain.ReportFault;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.*;

import javax.imageio.ImageIO;

/** 私有图片和业务条码必须解码验证，禁止URL、令牌和任意脚本。 */
class ReportPrintMediaTest {
    /** 执行qrAndCode128AreActuallyDecodableAndRejectUnsafePayloads对应的受控处理。 */
    @Test
    void qrAndCode128AreActuallyDecodableAndRejectUnsafePayloads() throws Exception {
        var engine = new BarcodeRaster();
        for (String kind : new String[] {"QR", "CODE128"}) {
            String value = kind.equals("QR") ? "PO-20261015-中文" : "PO-20261015-001";
            var image =
                    ImageIO.read(new ByteArrayInputStream(engine.render(kind, value, 600, 180)));
            var luminance =
                    new RGBLuminanceSource(
                            image.getWidth(),
                            image.getHeight(),
                            image.getRGB(
                                    0,
                                    0,
                                    image.getWidth(),
                                    image.getHeight(),
                                    null,
                                    0,
                                    image.getWidth()));
            assertEquals(
                    value,
                    new MultiFormatReader()
                            .decode(new BinaryBitmap(new HybridBinarizer(luminance)))
                            .getText());
        }
        assertThrows(
                ReportFault.class,
                () -> engine.render("QR", "https://example.test/secret", 200, 200));
        assertThrows(
                ReportFault.class,
                () ->
                        engine.render(
                                "QR", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.signature", 200, 200));
        assertThrows(ReportFault.class, () -> engine.render("CODE128", "中文", 400, 120));
    }

    /** 执行rasterNormalizerStripsMetadataAndChecksPixelsBeforeFullDecode对应的受控处理。 */
    @Test
    void rasterNormalizerStripsMetadataAndChecksPixelsBeforeFullDecode() throws Exception {
        var image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        var normalized = new ImageNormalizer().normalize(bytes.toByteArray());
        assertEquals(20, normalized.width());
        assertEquals(10, normalized.height());
        assertEquals("png", normalized.format());
        assertThrows(
                ReportFault.class,
                () -> new ImageNormalizer().normalize("<svg onload='run()'/>".getBytes()));
        assertThrows(
                ReportFault.class,
                () -> new ImageNormalizer().normalize(new byte[5 * 1024 * 1024 + 1]));
    }
}
