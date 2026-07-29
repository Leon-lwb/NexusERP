package com.nexus.mes.dc.task;

import com.nexus.common.iot.service.IotDeviceDataQueryService;
import com.nexus.mes.dc.domain.DcCollectionPoint;
import com.nexus.mes.dc.mapper.DcCollectionPointMapper;
import com.nexus.mes.dc.mapper.DcDataRecordMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IotDataArchiveTask 数据补偿单元测试 (F8-6)。
 * <p>直接测包级 archiveOne: 游标优先/回退滑窗/分块/截断/空结果推进游标。
 * archiveOne 不用 lambda 查询, 无需 LambdaUtils mock。archiveAutoPoints 多租户遍历由 E2E 覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class IotDataArchiveTaskTest {

    @Mock private DcCollectionPointMapper collectionPointMapper;
    @Mock private DcDataRecordMapper dataRecordMapper;
    @Mock private IotDeviceDataQueryService iotDeviceDataQueryService;
    @InjectMocks private IotDataArchiveTask task;

    private static final long MIN = 60_000L;

    private DcCollectionPoint point(Long id, Long iotId, Date lastArchive) {
        DcCollectionPoint p = new DcCollectionPoint();
        p.setPointId(id);
        p.setIotDeviceId(iotId);
        p.setLastArchiveTime(lastArchive);
        p.setPointCode("P" + id);
        p.setDataType("NUMERIC");
        return p;
    }

    @Test
    void archiveOne_cursorNull_fallsBackToWindow() {
        Date endTime = new Date(10_000_000L);
        Date windowStart = new Date(endTime.getTime() - 6 * MIN);
        DcCollectionPoint p = point(1L, 10L, null); // 无游标 -> 回退滑窗
        when(iotDeviceDataQueryService.getHistoryData(eq(10L), any(), any())).thenReturn(Collections.emptyList());

        int n = task.archiveOne(p, windowStart, endTime);

        assertEquals(0, n);
        // 回退滑窗: getHistoryData 用 windowStart
        verify(iotDeviceDataQueryService).getHistoryData(eq(10L), eq(windowStart), eq(endTime));
        verify(collectionPointMapper).updateLastArchiveTime(eq(1L), eq(endTime));
    }

    @Test
    void archiveOne_cursorAdvances_afterArchive() {
        Date endTime = new Date(10_000_000L);
        Date cursor = new Date(endTime.getTime() - 3 * MIN);
        DcCollectionPoint p = point(1L, 10L, cursor);
        when(iotDeviceDataQueryService.getHistoryData(eq(10L), any(), any())).thenReturn(Collections.emptyList());

        task.archiveOne(p, cursor, endTime);

        verify(collectionPointMapper).updateLastArchiveTime(eq(1L), eq(endTime));
    }

    @Test
    void archiveOne_largeGap_chunked() {
        // gap 3h -> 2 段(2h + 1h)
        Date endTime = new Date(10_000_000L);
        Date cursor = new Date(endTime.getTime() - 180 * MIN);
        DcCollectionPoint p = point(1L, 10L, cursor);
        when(iotDeviceDataQueryService.getHistoryData(eq(10L), any(), any())).thenReturn(Collections.emptyList());

        task.archiveOne(p, cursor, endTime);

        // 3h / 2h(120min) = 2 段
        verify(iotDeviceDataQueryService, times(2)).getHistoryData(eq(10L), any(), any());
        verify(collectionPointMapper).updateLastArchiveTime(eq(1L), eq(endTime));
    }

    @Test
    void archiveOne_gapOver24h_truncated() {
        // gap 25h -> 截断到 24h, 第一段 start = endTime-24h(非 cursor=25h)
        Date endTime = new Date(10_000_000L);
        Date cursor = new Date(endTime.getTime() - 25 * 60 * MIN); // 25h
        DcCollectionPoint p = point(1L, 10L, cursor);
        when(iotDeviceDataQueryService.getHistoryData(eq(10L), any(), any())).thenReturn(Collections.emptyList());

        task.archiveOne(p, cursor, endTime);

        Date expectedStart = new Date(endTime.getTime() - 1440 * MIN); // 截断到 24h
        verify(iotDeviceDataQueryService).getHistoryData(eq(10L), eq(expectedStart), any());
        // 24h / 2h = 12 段
        verify(iotDeviceDataQueryService, times(12)).getHistoryData(eq(10L), any(), any());
    }

    @Test
    void archiveOne_emptyResult_stillAdvancesCursor() {
        Date endTime = new Date(10_000_000L);
        Date cursor = new Date(endTime.getTime() - 3 * MIN);
        DcCollectionPoint p = point(1L, 10L, cursor);
        when(iotDeviceDataQueryService.getHistoryData(eq(10L), any(), any())).thenReturn(Collections.emptyList());

        task.archiveOne(p, cursor, endTime);

        // 空结果也推进游标(已扫描无数据 ≠ 应重扫)
        verify(collectionPointMapper).updateLastArchiveTime(eq(1L), eq(endTime));
    }
}
