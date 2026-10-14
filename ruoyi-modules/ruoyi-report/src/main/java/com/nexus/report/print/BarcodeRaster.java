package com.nexus.report.print;

import com.google.zxing.*;
import com.nexus.report.domain.ReportFault;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

import javax.imageio.ImageIO;

/** 把受控业务编码转换为带留白的固定PNG，禁止令牌和外部URL。 */
public final class BarcodeRaster {
    /**
     * 编码内容长度、字符、尺寸及条码类型均受服务端白名单限制。
     *
     * @param kind 当前契约的kind配置或状态
     * @param value 当前契约的value配置或状态
     * @param width 布局宽度
     * @param height 布局高度
     * @return 当前受控处理的固定结果
     */
    public byte[] render(String kind, String value, int width, int height) {
        if (value == null
                || value.isBlank()
                || value.length() > 256
                || value.contains("://")
                || value.contains("eyJ") && value.chars().filter(c -> c == '.').count() >= 2
                || value.matches("(?is).*(authorization|bearer |password=|token=|secret=).*"))
            throw ReportFault.invalid("条码只允许批准业务编码，不允许URL或认证信息");
        if (width < 64 || height < 64 || width > 2048 || height > 2048)
            throw ReportFault.invalid("条码像素范围无效");
        var format =
                switch (kind) {
                    case "QR" -> BarcodeFormat.QR_CODE;
                    case "CODE128" -> BarcodeFormat.CODE_128;
                    default -> throw ReportFault.invalid("条码类型未批准");
                };
        if (format == BarcodeFormat.CODE_128
                && (!value.matches("[ -~]{1,80}") || width < value.length() * 12 + 80))
            throw ReportFault.invalid("Code128只支持80位以内ASCII业务编码，且需要足够宽度");
        try {
            var hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, format == BarcodeFormat.QR_CODE ? 4 : 12);
            var matrix = new MultiFormatWriter().encode(value, format, width, height, hints);
            var image =
                    new BufferedImage(
                            matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < matrix.getHeight(); y++)
                for (int x = 0; x < matrix.getWidth(); x++)
                    image.setRGB(x, y, matrix.get(x, y) ? 0xff000000 : 0xffffffff);
            var output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (WriterException | IOException | IllegalArgumentException failure) {
            throw ReportFault.invalid("条码内容不能编码或尺寸不足");
        }
    }
}
