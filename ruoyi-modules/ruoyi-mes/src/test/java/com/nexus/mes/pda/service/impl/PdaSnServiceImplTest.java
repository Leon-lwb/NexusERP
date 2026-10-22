package com.nexus.mes.pda.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.lt.domain.LtLot;
import com.nexus.mes.lt.domain.LtSnBinding;
import com.nexus.mes.lt.mapper.LtLotMapper;
import com.nexus.mes.lt.mapper.LtSnBindingMapper;
import com.nexus.mes.pda.domain.bo.PdaSnBindBo;
import com.nexus.mes.pda.domain.bo.PdaSnUnbindBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.trace.MesSerialReferenceService;
import com.nexus.mes.trace.MesSnCreationWriter;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * PDA SN 绑定测试(MES-PDA-01)
 * <p>
 * 核心断言: 绑定必须写入由 snCode 反查出的 snId(修复前为 null -> SQL 1364),
 * 且重复绑定/并发唯一键冲突按幂等处理。
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class PdaSnServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        // lambdaQuery/lambdaUpdate 立即解析实体列缓存, 单测环境须手动初始化表元数据
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, LtSnBinding.class);
        TableInfoHelper.initTableInfo(assistant, LtLot.class);
        TableInfoHelper.initTableInfo(assistant, ProSn.class);
    }

    @Mock
    private LtSnBindingMapper snBindingMapper;
    @Mock
    private LtLotMapper lotMapper;
    @Mock
    private ProSnMapper proSnMapper;
    @Mock
    private MesSnCreationWriter productLots;
    @Mock
    private MesSerialReferenceService serialReferences;
    @InjectMocks
    private PdaSnServiceImpl service;

    @BeforeEach
    void identityLookup() {
        Mockito.lenient()
                .when(serialReferences.resolve(anyString()))
                .thenAnswer(
                        call -> {
                            String code = call.getArgument(0);
                            ProSn row =
                                    proSnMapper.selectOne(
                                            Wrappers.<ProSn>lambdaQuery()
                                                    .eq(ProSn::getSnCode, code));
                            return row == null
                                    ? null
                                    : new MesSerialReferenceService.Resolved(
                                            "000001",
                                            row.getIdentityId(),
                                            row.getSnId(),
                                            code,
                                            row.getSnCode(),
                                            row.getMaterialId(),
                                            row);
                        });
    }

    private PdaSnBindBo bo(String snCode, String lotCode) {
        PdaSnBindBo bo = new PdaSnBindBo();
        bo.setSnCode(snCode);
        bo.setLotCode(lotCode);
        return bo;
    }

    private ProSn sn(Long snId, String snCode) {
        ProSn sn = new ProSn();
        sn.setSnId(snId);
        sn.setSnCode(snCode);
        sn.setMaterialId(555L);
        return sn;
    }

    private LtLot lot(Long lotId, String lotCode, Long materialId) {
        LtLot lot = new LtLot();
        lot.setLotId(lotId);
        lot.setLotCode(lotCode);
        lot.setMaterialId(materialId);
        return lot;
    }

    private LtSnBinding binding(Long bindingId, Long snId, Long lotId) {
        LtSnBinding binding = new LtSnBinding();
        binding.setBindingId(bindingId);
        binding.setSnId(snId);
        binding.setLotId(lotId);
        binding.setLotCode("LOT-001");
        return binding;
    }

    @Test
    void bindWritesResolvedSnId() {
        when(proSnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sn(9001L, "SN-001"));
        when(lotMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(lot(77L, "LOT-001", 555L));
        when(snBindingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(snBindingMapper.insert(any(LtSnBinding.class))).thenReturn(1);

        MesActionResultVo result;
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(100L);
            result = service.bind(bo("SN-001", "LOT-001"));
        }

        assertTrue(Boolean.TRUE.equals(result.getOk()), result.getMessage());
        ArgumentCaptor<LtSnBinding> captor = ArgumentCaptor.forClass(LtSnBinding.class);
        verify(snBindingMapper).insert(captor.capture());
        assertEquals(
                9001L, captor.getValue().getSnId(), "必须写入反查出的 snId, 否则 sn_id NOT NULL 报 SQL 1364");
        assertEquals("SN-001", captor.getValue().getSnCode());
        assertEquals(77L, captor.getValue().getLotId());
        assertEquals(555L, captor.getValue().getMaterialId());
        assertEquals(100L, captor.getValue().getBoundBy());
    }

    @Test
    void bindRejectsUnknownSnWithoutInsert() {
        when(proSnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        MesActionResultVo result = service.bind(bo("SN-NOT-EXIST", "LOT-001"));

        assertFalse(Boolean.TRUE.equals(result.getOk()));
        assertTrue(result.getMessage().contains("SN不存在"), result.getMessage());
        verify(snBindingMapper, never()).insert(any(LtSnBinding.class));
    }

    @Test
    void bindIsIdempotentWhenAlreadyBoundToSameLot() {
        when(proSnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sn(9001L, "SN-001"));
        when(lotMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(lot(77L, "LOT-001", 555L));
        when(snBindingMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(binding(1L, 9001L, 77L));

        MesActionResultVo result = service.bind(bo("SN-001", "LOT-001"));

        assertTrue(Boolean.TRUE.equals(result.getOk()), result.getMessage());
        verify(snBindingMapper, never()).insert(any(LtSnBinding.class));
    }

    @Test
    void bindRejectsWhenAlreadyBoundToAnotherLot() {
        when(proSnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sn(9001L, "SN-001"));
        when(lotMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(lot(77L, "LOT-001", 555L));
        when(snBindingMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(binding(1L, 9001L, 88L));

        MesActionResultVo result = service.bind(bo("SN-001", "LOT-001"));

        assertFalse(Boolean.TRUE.equals(result.getOk()));
        assertTrue(result.getMessage().contains("业务纠正"), result.getMessage());
    }

    @Test
    void bindTreatsConcurrentUniqueKeyConflictAsIdempotent() {
        when(proSnMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sn(9001L, "SN-001"));
        when(lotMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(lot(77L, "LOT-001", 555L));
        // 插入前查重为空, 插入冲突后再次查询命中并发写入的同一批次绑定
        when(snBindingMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null, binding(2L, 9001L, 77L));
        when(snBindingMapper.insert(any(LtSnBinding.class)))
                .thenThrow(new DuplicateKeyException("uk_sn_material"));

        MesActionResultVo result;
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(100L);
            result = service.bind(bo("SN-001", "LOT-001"));
        }

        assertTrue(Boolean.TRUE.equals(result.getOk()), result.getMessage());
    }

    @Test
    void unbindCannotDeleteTraceAudit() {
        when(proSnMapper.selectOne(any())).thenReturn(sn(9001L, "SN-001"));
        when(snBindingMapper.selectOne(any())).thenReturn(binding(1L, 9001L, 77L));
        var request = new PdaSnUnbindBo();
        request.setSnCode("SN-001");
        assertFalse(Boolean.TRUE.equals(service.unbind(request).getOk()));
        verify(snBindingMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void componentMaterialCannotBeRecordedAsAProductLotWithoutActualConsumption() {
        var product = sn(9001L, "SN-001");
        product.setMaterialId(100L);
        when(proSnMapper.selectOne(any())).thenReturn(product);
        when(lotMapper.selectOne(any())).thenReturn(lot(77L, "COMPONENT", 555L));
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(100L);
            assertFalse(Boolean.TRUE.equals(service.bind(bo("SN-001", "COMPONENT")).getOk()));
        }
        verify(snBindingMapper, never()).insert(any(LtSnBinding.class));
    }
}
