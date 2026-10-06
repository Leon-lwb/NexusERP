package com.nexus.erp.fin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.GlVoucher;
import com.nexus.erp.fin.gl.domain.vo.GlVoucherVo;
import com.nexus.erp.fin.gl.mapper.*;
import com.nexus.erp.fin.gl.service.*;
import com.nexus.erp.fin.gl.service.impl.GlVoucherServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 来源状态回归：不访问真实数据库，不以返回任意既有凭证冒充成功。 */
@Tag("dev")
class GlVoucherSourceReuseSafetyTest {
    private GlVoucherMapper mapper;
    private GlVoucherLineMapper lines;
    private IGlAccountBalanceService balances;
    private IGlPeriodService periods;
    private GlVoucherServiceImpl service;
    private VoucherBuildRequest request;

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GlVoucher.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(GlVoucherMapper.class);
        lines = mock(GlVoucherLineMapper.class);
        balances = mock(IGlAccountBalanceService.class);
        periods = mock(IGlPeriodService.class);
        service = new GlVoucherServiceImpl(mapper, lines, mock(GlAccountMapper.class),
            mock(GlPeriodMapper.class), periods, balances);
        request = new VoucherBuildRequest();
        request.setVoucherSource(GlConstants.SOURCE_COST);
        request.setSourceId(901L);
    }

    private void existing(String status, boolean duplicate) {
        GlVoucher voucher = new GlVoucher();
        voucher.setVoucherId(7L);
        voucher.setStatus(status);
        GlVoucherVo vo = new GlVoucherVo();
        vo.setVoucherId(7L);
        vo.setStatus(status);
        // 两种读法提供相同场景，让 RED 暴露旧 limit 1/未验状态行为。
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(voucher);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(duplicate ? List.of(voucher, voucher) : List.of(voucher));
        when(mapper.selectVoById(7L)).thenReturn(vo);
        when(lines.selectList(any(Wrapper.class))).thenReturn(List.of());
    }

    @Test
    void uniquePostedSourceIsReusedWithLockedCurrentRead() {
        existing(GlConstants.VOUCHER_STATUS_POSTED, false);
        assertEquals(7L, service.createAndPost(request).getVoucherId(), "唯一已过账来源才可认领");
        var captor = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().toUpperCase(Locale.ROOT);
        assertTrue(sql.contains("LIMIT 2 FOR UPDATE"), "来源当前读必须锁定且保留第二条以识别重复");
        assertTrue(sql.contains("VOUCHER_SOURCE") && sql.contains("SOURCE_ID"), "必须使用完整既有来源键");
        verify(mapper, never()).insert(any(GlVoucher.class));
        verifyNoInteractions(periods, balances);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"DRAFT", "CANCELLED", "UNKNOWN"})
    void nonPostedSourceCannotBeClaimed(String status) {
        existing(status, false);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndPost(request),
            "未真正过账的既有来源必须拒绝，不能无声认领");
        assertTrue(ex.getMessage().contains("已过账"), "错误必须指出来源未真正过账");
        verify(mapper, never()).insert(any(GlVoucher.class));
        verifyNoInteractions(periods, balances);
    }

    @Test
    void duplicatedSourceCannotBeHiddenByLimitOne() {
        existing(GlConstants.VOUCHER_STATUS_POSTED, true);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.createAndPost(request),
            "重复来源必须中止，不能任取一张凭证");
        assertTrue(ex.getMessage().contains("多张"), "错误必须明确来源重复");
        verify(mapper, never()).selectVoById(any());
        verifyNoInteractions(periods, balances);
    }

    @ParameterizedTest
    @ValueSource(strings = {"COST", "SO_INVOICE", "PU_INVOICE", "RECEIPT", "DEPRECIATION", "FA_DISPOSAL", "ADJUSTMENT"})
    void allExistingBusinessCallersRejectDraftSource(String source) {
        request.setVoucherSource(source);
        existing(GlConstants.VOUCHER_STATUS_DRAFT, false);
        assertThrows(ServiceException.class, () -> service.createAndPost(request),
            "业务 caller 约定返回已过账凭证，不能返回草稿：" + source);
        verifyNoInteractions(periods, balances);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"DRAFT", "CANCELLED"})
    void missingOrStalePostedViewCannotBeReused(String status) {
        existing(GlConstants.VOUCHER_STATUS_POSTED, false);
        GlVoucherVo view = null;
        if (status != null) {
            view = new GlVoucherVo();
            view.setVoucherId(7L);
            view.setStatus(status);
        }
        when(mapper.selectVoById(7L)).thenReturn(view);
        assertThrows(ServiceException.class, () -> service.createAndPost(request),
            "锁定来源与返回视图不一致时必须拒绝，不能让其他 caller 误认已过账");
        verifyNoInteractions(periods, balances);
    }

    @Test
    void manualAndMissingSourceIdStillBypassSourceReuse() {
        request.setVoucherSource(GlConstants.SOURCE_MANUAL);
        assertThrows(ServiceException.class, () -> service.createAndPost(request), "空分录应仍由原校验拒绝");
        request.setVoucherSource(GlConstants.SOURCE_COST);
        request.setSourceId(null);
        assertThrows(ServiceException.class, () -> service.createAndPost(request), "无来源 ID 空分录仍按原校验处理");
        verify(mapper, never()).selectList(any(Wrapper.class));
        verify(mapper, never()).selectOne(any(Wrapper.class));
    }
}
