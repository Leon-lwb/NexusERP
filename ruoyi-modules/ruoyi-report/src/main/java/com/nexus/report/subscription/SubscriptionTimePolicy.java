package com.nexus.report.subscription;

import com.nexus.report.domain.ReportFault;

import org.springframework.scheduling.support.CronExpression;

import java.time.*;
import java.util.*;

/** 使用显式时区计算cron，缺失本地时间跳过，重复本地时间只执行较早偏移。 */
public final class SubscriptionTimePolicy {
    /**
     * 校验安装的DST语义及最小五分钟执行间隔。
     *
     * @param cron 受控cron表达式
     * @param timezone 计划日期计算使用的明确时区
     * @param policy 当前契约的policy配置或状态
     */
    public void validate(String cron, String timezone, String policy) {
        Instant cursor = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 20; i++) {
            Instant next = next(cron, timezone, policy, cursor);
            if (i > 0 && Duration.between(cursor, next).getSeconds() < 300)
                throw ReportFault.invalid("报表订阅执行间隔至少五分钟");
            cursor = next;
        }
    }

    /**
     * 本地cron候选转为真实Instant，不使用JVM默认时区。
     *
     * @param cron 受控cron表达式
     * @param timezone 计划日期计算使用的明确时区
     * @param policy 当前契约的policy配置或状态
     * @param after 当前契约的after配置或状态
     * @return 当前受控处理的固定结果
     */
    public Instant next(String cron, String timezone, String policy, Instant after) {
        if (cron == null
                || cron.length() > 120
                || !"EARLIER_OFFSET".equals(policy)
                || after == null) throw ReportFault.invalid("订阅时间规则无效");
        try {
            var expression = CronExpression.parse(cron);
            var zone = ZoneId.of(timezone);
            LocalDateTime cursor = after.atZone(zone).toLocalDateTime();
            for (int i = 0; i < 64; i++) {
                cursor = expression.next(cursor);
                if (cursor == null) throw ReportFault.invalid("cron没有未来执行时刻");
                var offsets = zone.getRules().getValidOffsets(cursor);
                if (offsets.isEmpty()) continue;
                Instant candidate = cursor.toInstant(offsets.get(0));
                if (candidate.isAfter(after)) return candidate;
            }
            throw ReportFault.invalid("cron在时区内没有受支持执行时刻");
        } catch (ReportFault fault) {
            throw fault;
        } catch (Exception invalid) {
            throw ReportFault.invalid("cron、时区或夏令时规则无效");
        }
    }
}
