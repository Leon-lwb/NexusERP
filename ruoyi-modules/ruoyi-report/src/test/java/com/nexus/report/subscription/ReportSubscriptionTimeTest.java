package com.nexus.report.subscription;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.report.domain.ReportFault;

import org.junit.jupiter.api.Test;

import java.time.Instant;

/** cron必须明确时区，夏令时缺失跳过，重复时间只使用较早偏移。 */
class ReportSubscriptionTimeTest {
    /** 执行shanghaiAndDaylightSavingRulesAreDeterministic对应的受控处理。 */
    @Test
    void shanghaiAndDaylightSavingRulesAreDeterministic() {
        var time = new SubscriptionTimePolicy();
        assertEquals(
                Instant.parse("2026-10-15T01:00:00Z"),
                time.next(
                        "0 0 9 * * *",
                        "Asia/Shanghai",
                        "EARLIER_OFFSET",
                        Instant.parse("2026-10-14T23:00:00Z")));
        assertEquals(
                Instant.parse("2026-03-30T00:30:00Z"),
                time.next(
                        "0 30 2 * * *",
                        "Europe/Berlin",
                        "EARLIER_OFFSET",
                        Instant.parse("2026-03-28T01:30:00Z")));
        var first =
                time.next(
                        "0 30 2 * * *",
                        "Europe/Berlin",
                        "EARLIER_OFFSET",
                        Instant.parse("2026-10-24T00:30:00Z"));
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), first);
        assertEquals(
                Instant.parse("2026-10-26T01:30:00Z"),
                time.next("0 30 2 * * *", "Europe/Berlin", "EARLIER_OFFSET", first));
    }

    /** 执行unsupportedTimezoneAndSubFiveMinuteCadenceFail对应的受控处理。 */
    @Test
    void unsupportedTimezoneAndSubFiveMinuteCadenceFail() {
        var time = new SubscriptionTimePolicy();
        assertThrows(
                ReportFault.class,
                () -> time.validate("0 * * * * *", "Asia/Shanghai", "EARLIER_OFFSET"));
        assertThrows(
                ReportFault.class,
                () -> time.validate("0 0 9 * * *", "Unknown/Zone", "EARLIER_OFFSET"));
        assertThrows(
                ReportFault.class, () -> time.validate("0 0 9 * * *", "Asia/Shanghai", "BOTH"));
    }
}
