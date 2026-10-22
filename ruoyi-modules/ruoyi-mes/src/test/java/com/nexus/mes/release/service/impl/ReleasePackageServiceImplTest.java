package com.nexus.mes.release.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.flash.domain.Firmware;
import com.nexus.mes.flash.mapper.FirmwareMapper;
import com.nexus.mes.release.domain.ReleasePackage;
import com.nexus.mes.release.domain.bo.ReleasePackageBo;
import com.nexus.mes.release.domain.bo.ReleasePkgBomItemBo;
import com.nexus.mes.release.domain.bo.ReleasePkgFirmwareBo;
import com.nexus.mes.release.domain.vo.ReleasePackageVo;
import com.nexus.mes.release.mapper.ReleasePackageMapper;
import com.nexus.mes.release.mapper.ReleasePkgBomItemMapper;
import com.nexus.mes.release.mapper.ReleasePkgConfigMapper;
import com.nexus.mes.release.mapper.ReleasePkgFirmwareMapper;
import com.nexus.mes.testsupport.SpringStaticBootstrap;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

/**
 * ReleasePackageServiceImpl 单元测试: 匹配规则 / 版本唯一 / 子表校验 / 默认包
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class ReleasePackageServiceImplTest {

    @BeforeAll
    static void initStatics() {
        // MapstructUtils/JsonUtils 静态初始化依赖 SpringUtils, 先激活静态桩
        SpringStaticBootstrap.ensureActive();
        // lambdaUpdate.set(...) 立即解析实体列缓存, 单测环境须手动初始化表元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReleasePackage.class);
    }

    @Mock
    private ReleasePackageMapper baseMapper;
    @Mock
    private ReleasePkgFirmwareMapper firmwareMapperPkg;
    @Mock
    private ReleasePkgBomItemMapper bomItemMapper;
    @Mock
    private ReleasePkgConfigMapper configMapper;
    @Mock
    private FirmwareMapper firmwareMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @Mock
    private IWfProcessService processService;
    @Mock
    private WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    @Mock
    private FlowableProcInsIdHelper procInsIdHelper;
    @InjectMocks
    private ReleasePackageServiceImpl service;

    private ReleasePackage releasedPackage(Long id, String code, String customerProductCode) {
        ReleasePackage pkg = new ReleasePackage();
        pkg.setPackageId(id);
        pkg.setPackageCode(code);
        pkg.setVersion("2.1");
        pkg.setStatus("RELEASED");
        pkg.setClientId(5L);
        pkg.setMaterialId(100L);
        pkg.setCustomerProductCode(customerProductCode);
        return pkg;
    }

    // ==================== matchPackage 匹配规则 ====================

    @Test
    void matchPackage_exactCustomerProductCodePreferred() {
        ReleasePackage exact = releasedPackage(1L, "RP-001", "X-2026");
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(exact);

        ReleasePackageVo vo = service.matchPackage(5L, 100L, "X-2026");

        assertNotNull(vo);
        assertEquals("RP-001", vo.getPackageCode());
        // 命中精确匹配后不再查默认包(仅一次 selectOne)
        verify(baseMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void matchPackage_fallsBackToDefaultWhenNoExactMatch() {
        ReleasePackage defaultPkg = releasedPackage(2L, "RP-002", null);
        defaultPkg.setIsDefault("1");
        // 第一次(精确匹配)返回 null, 第二次(默认包)返回 defaultPkg
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null, defaultPkg);

        ReleasePackageVo vo = service.matchPackage(5L, 100L, "X-2026");

        assertNotNull(vo);
        assertEquals("RP-002", vo.getPackageCode());
        verify(baseMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void matchPackage_returnsNullWhenNoMatch() {
        when(baseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertNull(service.matchPackage(5L, 100L, "X-2026"));
    }

    @Test
    void matchPackage_returnsNullWhenClientOrMaterialNull() {
        assertNull(service.matchPackage(null, 100L, "X"));
        assertNull(service.matchPackage(5L, null, "X"));
        verifyNoInteractions(baseMapper);
    }

    // ==================== 版本唯一校验 ====================

    @Test
    void insertByBo_rejectsDuplicateVersionForSameClientAndMaterial() {
        when(autoCodeUtil.genSerialCode(anyString(), any())).thenReturn("RP-TEST-001");
        // 第一次 selectCount: 编码查重(0); 第二次: 同客户+物料+版本查重(1=已存在)
        when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 1L);

        ReleasePackageBo bo = new ReleasePackageBo();
        bo.setClientId(5L);
        bo.setMaterialId(100L);
        bo.setVersion("2.1");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        assertTrue(ex.getMessage().contains("已存在版本"));
    }

    // ==================== 子表校验 ====================

    @Test
    void insertByBo_rejectsNonReleasedFirmwareMapping() {
        when(autoCodeUtil.genSerialCode(anyString(), any())).thenReturn("RP-TEST-001");
        when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        Firmware draft = new Firmware();
        draft.setFirmwareId(777L);
        draft.setFirmwareCode("FW-777");
        draft.setMaterialId(100L);
        draft.setStatus("DRAFT");
        when(firmwareMapper.selectById(777L)).thenReturn(draft);

        ReleasePackageBo bo = new ReleasePackageBo();
        bo.setClientId(5L);
        bo.setMaterialId(100L);
        bo.setVersion("2.1");
        ReleasePkgFirmwareBo mapping = new ReleasePkgFirmwareBo();
        mapping.setProcessId(3L);
        mapping.setFirmwareId(777L);
        bo.setFirmwareMappings(List.of(mapping));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        assertTrue(ex.getMessage().contains("仅已发布"));
    }

    @Test
    void insertByBo_rejectsFirmwareOfDifferentMaterial() {
        when(autoCodeUtil.genSerialCode(anyString(), any())).thenReturn("RP-TEST-001");
        when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        Firmware otherMaterialFw = new Firmware();
        otherMaterialFw.setFirmwareId(777L);
        otherMaterialFw.setFirmwareCode("FW-777");
        otherMaterialFw.setMaterialId(999L);
        otherMaterialFw.setStatus("RELEASED");
        when(firmwareMapper.selectById(777L)).thenReturn(otherMaterialFw);

        ReleasePackageBo bo = new ReleasePackageBo();
        bo.setClientId(5L);
        bo.setMaterialId(100L);
        bo.setVersion("2.1");
        ReleasePkgFirmwareBo mapping = new ReleasePkgFirmwareBo();
        mapping.setProcessId(3L);
        mapping.setFirmwareId(777L);
        bo.setFirmwareMappings(List.of(mapping));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        assertTrue(ex.getMessage().contains("适用物料与发布包物料不一致"));
    }

    @Test
    void insertByBo_rejectsDuplicateLocateKey() {
        when(autoCodeUtil.genSerialCode(anyString(), any())).thenReturn("RP-TEST-001");
        when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        Firmware released = new Firmware();
        released.setFirmwareId(777L);
        released.setFirmwareCode("FW-777");
        released.setMaterialId(100L);
        released.setStatus("RELEASED");
        when(firmwareMapper.selectById(777L)).thenReturn(released);

        ReleasePackageBo bo = new ReleasePackageBo();
        bo.setClientId(5L);
        bo.setMaterialId(100L);
        bo.setVersion("2.1");
        ReleasePkgFirmwareBo m1 = new ReleasePkgFirmwareBo();
        m1.setProcessId(3L);
        m1.setFirmwareId(777L);
        m1.setFirmwareCode("FW-777");
        ReleasePkgFirmwareBo m2 = new ReleasePkgFirmwareBo();
        m2.setProcessId(3L);
        m2.setFirmwareId(777L);
        bo.setFirmwareMappings(List.of(m1, m2));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        assertTrue(ex.getMessage().contains("定位键重复"));
    }

    @Test
    void insertByBo_rejectsBomItemMissingChildMaterial() {
        when(autoCodeUtil.genSerialCode(anyString(), any())).thenReturn("RP-TEST-001");
        when(baseMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        ReleasePackageBo bo = new ReleasePackageBo();
        bo.setClientId(5L);
        bo.setMaterialId(100L);
        bo.setVersion("2.1");
        ReleasePkgBomItemBo item = new ReleasePkgBomItemBo();
        item.setActionType("REPLACE");
        item.setTargetMaterialId(301L);
        // 未填 childMaterialId(定位键)
        bo.setBomItems(List.of(item));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        assertTrue(ex.getMessage().contains("须指定原物料"));
    }

    // ==================== setDefault ====================

    @Test
    void setDefault_clearsOtherDefaultsThenSets() {
        ReleasePackage pkg = releasedPackage(1L, "RP-001", null);
        pkg.setStatus("RELEASED");
        when(baseMapper.selectById(1L)).thenReturn(pkg);
        // 第一次 update: 清同客户+物料其他默认; 第二次: CAS 设默认(返回1)
        when(baseMapper.update(any(), any())).thenReturn(1);

        assertTrue(service.setDefault(1L));
        verify(baseMapper, times(2)).update(any(), any());
    }

    @Test
    void setDefault_rejectsNonReleasedPackage() {
        ReleasePackage pkg = releasedPackage(1L, "RP-001", null);
        pkg.setStatus("DRAFT");
        when(baseMapper.selectById(1L)).thenReturn(pkg);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.setDefault(1L));
        assertTrue(ex.getMessage().contains("仅已发布"));
    }

    // ==================== approve -> doPublish 影响行数校验(MES-REL-01) ====================

    @Tag("local")
    @Tag("dev")
    @Test
    void approveThrowsWhenPublishCasAffectsZeroRows() {
        ReleasePackage pkg = releasedPackage(1L, "RP-001", null);
        pkg.setStatus("IN_REVIEW");
        when(baseMapper.selectById(1L)).thenReturn(pkg);
        // CAS 影响 0 行(状态已被并发变更: 双审批 / 与归档竞态)
        when(baseMapper.update(any(), any())).thenReturn(0);

        try (MockedStatic<LoginHelper> login = Mockito.mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);

            ServiceException ex = assertThrows(ServiceException.class, () -> service.approve(1L, "同意"));
            assertTrue(ex.getMessage().contains("状态已变更"), ex.getMessage());
        }
    }

    @Tag("local")
    @Tag("dev")
    @Test
    void approvePublishesWhenPublishCasAffectsOneRow() {
        ReleasePackage pkg = releasedPackage(1L, "RP-001", null);
        pkg.setStatus("IN_REVIEW");
        when(baseMapper.selectById(1L)).thenReturn(pkg);
        when(baseMapper.update(any(), any())).thenReturn(1);

        try (MockedStatic<LoginHelper> login = Mockito.mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(9L);

            assertTrue(service.approve(1L, "同意"));
            verify(baseMapper, times(1)).update(any(), any());
        }
    }
}
