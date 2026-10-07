package com.nexus.ai.service.token;

import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.mapper.AiModelMapper;
import com.nexus.common.ai.mapper.AiTokenUsageMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 用量统计属于实际请求租户，缺失身份不能伪落到全局0。 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AiTokenUsageTenantTest {
    private final AiTokenUsageMapper mapper = mock(AiTokenUsageMapper.class);
    private final AiTokenUsageServiceImpl service = new AiTokenUsageServiceImpl(
        mapper, mock(AiModelMapper.class), mock(AiMessageMapper.class));

    @ParameterizedTest(name = "统计原样使用租户 {0}")
    @ValueSource(strings = {"000000", "000021", "TENANT-A"})
    void statisticsPreserveExactTenant(String tenant) {
        service.incrementDailyUsage(tenant, 1L, "业务管理员", 2L, "业务解读模型", 10, 4, BigDecimal.ZERO);
        verify(mapper).upsertDailyUsage(anyLong(), eq(tenant), eq(1L), anyString(), eq(2L),
            anyString(), any(), eq(10L), eq(4L), eq(14L), eq(BigDecimal.ZERO));
    }

    @ParameterizedTest(name = "缺失或不安全租户不产生用量记录：{0}")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "000000'", "a\\b"})
    void missingOrUnsafeTenantCannotCreateGlobalStatistics(String tenant) {
        assertThrows(IllegalArgumentException.class, () -> service.incrementDailyUsage(
            tenant, 1L, "业务管理员", 2L, "业务解读模型", 10, 4, BigDecimal.ZERO));
        verifyNoInteractions(mapper);
    }
}
