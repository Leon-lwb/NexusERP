package com.nexus.system.strategy;

import com.nexus.system.domain.SysAutoCodePart;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 当前日期处理器
 * <p>按组成配置的日期格式（如 yyyyMMdd）生成当前时间日期片段。属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Component
@Order(1)
public class PartTypeNowDateHandler implements PartTypeTemplate {

    /**
     * 按组成配置的日期格式生成当前日期时间片段。
     *
     * @param sysAutoCodePart 编码组成配置（含 dateFormat 日期格式）
     * @return 格式化后的当前日期时间字符串
     */
    @Override
    public String partHandle(SysAutoCodePart sysAutoCodePart) {
        String formatDate = sysAutoCodePart.getDateFormat();
        return DateTimeFormatter.ofPattern(formatDate).format(LocalDateTime.now());
    }
}
