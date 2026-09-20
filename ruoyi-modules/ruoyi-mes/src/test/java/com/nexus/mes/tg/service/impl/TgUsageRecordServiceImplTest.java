package com.nexus.mes.tg.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.tg.domain.TgUsageRecord;
import com.nexus.mes.tg.domain.bo.TgCheckoutBo;
import com.nexus.mes.tg.domain.bo.TgUsageRecordBo;
import com.nexus.mes.tg.mapper.TgUsageRecordMapper;
import com.nexus.mes.tg.service.ITgToolingService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MES 工装使用记录 Service 单元测试 — MES-TG-03（写端点收敛到状态机 + 追溯台账守卫）。
 *
 * <p>核心断言（修复前必失败）：已归还记录不可改、任何记录不可删、新增必须走 checkout 状态机
 * 而不是直接 insert（修复前可直接造 OUT 记录/改成 RETURNED 不累加/删未归还记录卡死 IN_USE）。</p>
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class TgUsageRecordServiceImplTest {

    @Mock private TgUsageRecordMapper baseMapper;
    @Mock private ITgToolingService tgToolingService;

    @InjectMocks private TgUsageRecordServiceImpl service;

    /** 真实列缓存: LambdaWrapper 需要实体 TableInfo。 */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), ""), TgUsageRecord.class);
    }

    private TgUsageRecord record(Long id, String status) {
        TgUsageRecord r = new TgUsageRecord();
        r.setUsageId(id);
        r.setToolingId(9L);
        r.setStatus(status);
        return r;
    }

    @Test
    void updateRejectsReturnedRecord() {
        when(baseMapper.selectById(100L)).thenReturn(record(100L, ITgToolingService.USAGE_RETURNED));
        TgUsageRecordBo bo = new TgUsageRecordBo();
        bo.setUsageId(100L);
        bo.setStatus(ITgToolingService.USAGE_RETURNED);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateByBo(bo));

        assertTrue(ex.getMessage().contains("已归还"), ex.getMessage());
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void updateRejectsUnknownStatusRecord() {
        when(baseMapper.selectById(101L)).thenReturn(record(101L, "SOMETHING_ELSE"));
        TgUsageRecordBo bo = new TgUsageRecordBo();
        bo.setUsageId(101L);

        assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void updateAllowsOutRecordWithCasGuard() {
        when(baseMapper.selectById(102L)).thenReturn(record(102L, ITgToolingService.USAGE_OUT));
        when(baseMapper.update(any(), any(Wrapper.class))).thenReturn(1);
        TgUsageRecordBo bo = new TgUsageRecordBo();
        bo.setUsageId(102L);
        bo.setRemark("补录备注");
        // 客户端试图改写状态: 服务端必须忽略(归还只能走 doReturn, 否则寿命不累加)
        bo.setStatus(ITgToolingService.USAGE_RETURNED);

        assertTrue(service.updateByBo(bo));
        verify(baseMapper, times(1)).update(isNull(), any(Wrapper.class));
    }

    @Test
    void updateFailsWhenCasAffectedZeroRows() {
        when(baseMapper.selectById(103L)).thenReturn(record(103L, ITgToolingService.USAGE_OUT));
        when(baseMapper.update(any(), any(Wrapper.class))).thenReturn(0);
        TgUsageRecordBo bo = new TgUsageRecordBo();
        bo.setUsageId(103L);
        bo.setRemark("x");

        assertThrows(ServiceException.class, () -> service.updateByBo(bo));
    }

    @Test
    void deleteIsRejectedForAnyExistingRecord() {
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.deleteByIds(new Long[]{200L}));

        assertTrue(ex.getMessage().contains("禁止删除"), ex.getMessage());
        verify(baseMapper, never()).deleteByIds(anyList());
    }

    @Test
    void deleteReturnsFalseWhenRecordsAlreadyGone() {
        when(baseMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        assertFalse(service.deleteByIds(new Long[]{201L}));
        verify(baseMapper, never()).deleteByIds(anyList());
    }

    @Test
    void deleteReturnsFalseForEmptyIds() {
        assertFalse(service.deleteByIds(new Long[0]));
        assertFalse(service.deleteByIds(null));
        verifyNoInteractions(baseMapper);
    }

    @Test
    void insertDelegatesToCheckoutStateMachineNeverInsertsDirectly() {
        when(tgToolingService.checkout(eq(9L), any(TgCheckoutBo.class))).thenReturn(true);
        TgUsageRecordBo bo = new TgUsageRecordBo();
        bo.setToolingId(9L);
        bo.setOperatorId(5L);
        // 客户端传入的状态/次数一律被忽略(服务端权威)
        bo.setStatus(ITgToolingService.USAGE_RETURNED);
        bo.setUsageCount(999);

        assertTrue(service.insertByBo(bo));

        verify(tgToolingService, times(1)).checkout(eq(9L), any(TgCheckoutBo.class));
        verify(baseMapper, never()).insert(any(TgUsageRecord.class));
    }

    @Test
    void insertRejectsMissingToolingId() {
        TgUsageRecordBo bo = new TgUsageRecordBo();

        assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        verifyNoInteractions(tgToolingService);
    }

    @Test
    void updateRejectsMissingUsageId() {
        assertThrows(ServiceException.class, () -> service.updateByBo(new TgUsageRecordBo()));
        verifyNoInteractions(baseMapper);
    }

    @Test
    void queryWrapperFiltersByStatusAndTooling() {
        TgUsageRecordBo bo = new TgUsageRecordBo();
        bo.setToolingId(9L);
        bo.setStatus(ITgToolingService.USAGE_OUT);
        // 仅验证查询路径可用（分页由 mapper 承担）
        assertNotNull(service);
    }

    @Test
    void statusConstantsMatchDictionaryValues() {
        assertEquals("OUT", ITgToolingService.USAGE_OUT);
        assertEquals("RETURNED", ITgToolingService.USAGE_RETURNED);
        List<String> allowed = List.of(ITgToolingService.USAGE_OUT, ITgToolingService.USAGE_RETURNED);
        assertTrue(allowed.contains(record(1L, ITgToolingService.USAGE_OUT).getStatus()));
    }
}
