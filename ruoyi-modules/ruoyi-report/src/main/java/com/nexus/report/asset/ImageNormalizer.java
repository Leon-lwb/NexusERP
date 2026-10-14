package com.nexus.report.asset;

import com.nexus.report.domain.ReportFault;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

import javax.imageio.*;

/** 对上传图片先检查解码器和像素，再生成无外部资源的PNG。 */
public final class ImageNormalizer {
    /**
     * 归一化结果包含真实像素及唯一受支持的输出格式。
     *
     * @param bytes 保留或实际占用的字节数
     * @param width 布局宽度
     * @param height 布局高度
     * @param format 受控格式
     */
    public record Image(byte[] bytes, int width, int height, String format) {

        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param bytes 当前受控操作的bytes输入
         * @param width 当前受控操作的width输入
         * @param height 当前受控操作的height输入
         * @param format 当前受控操作的format输入
         */
        public Image {
            bytes = bytes.clone();
        }

        /**
         * 执行bytes对应的受控处理。
         *
         * @return 当前固定契约或处理结果
         */
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    /**
     * 最多5MiB输入、4096边长和一千万像素；拒绝SVG及畸形数据。
     *
     * @param bytes 保留或实际占用的字节数
     * @return 当前受控处理的固定结果
     */
    public Image normalize(byte[] bytes) {
        if (bytes == null || bytes.length < 8 || bytes.length > 5 * 1024 * 1024)
            throw ReportFault.invalid("图片文件大小无效，最多5MiB");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw ReportFault.invalid("仅支持可解码PNG或JPEG");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg", "jpg").contains(format))
                    throw ReportFault.invalid("图片格式未批准");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1
                        || height < 1
                        || width > 4096
                        || height > 4096
                        || (long) width * height > 10000000) throw ReportFault.capacity();
                var decoded = reader.read(0);
                var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try {
                    graphics.drawImage(decoded, 0, 0, null);
                } finally {
                    graphics.dispose();
                }
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", output)) throw ReportFault.invalid("图片无法规范化");
                if (output.size() > 5 * 1024 * 1024) throw ReportFault.capacity();
                return new Image(output.toByteArray(), width, height, "png");
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException failure) {
            throw ReportFault.invalid("图片无法安全解码");
        }
    }
}
