package com.nexus.mes.dhr.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.domain.vo.VerifyResultVo;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.common.sign.util.Sm3Utils;
import com.nexus.mes.dhr.domain.DhrRecord;
import com.nexus.mes.dhr.domain.DhrSection;
import com.nexus.mes.dhr.domain.DhrSectionCode;
import com.nexus.mes.dhr.domain.DhrStatus;
import com.nexus.mes.dhr.domain.bo.DhrBuildBo;
import com.nexus.mes.dhr.domain.bo.DhrSignBo;
import com.nexus.mes.dhr.domain.vo.DhrRecordVo;
import com.nexus.mes.dhr.domain.vo.DhrVerifyVo;
import com.nexus.mes.dhr.mapper.DhrRecordMapper;
import com.nexus.mes.dhr.mapper.DhrSectionMapper;
import com.nexus.mes.dhr.service.IDhrAggregateService;
import com.nexus.mes.dhr.support.DhrSignatureAccessor;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import com.nexus.mes.testsupport.SpringStaticBootstrap;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * DHR 批记录 Service 单元测试。
 *
 * <p>覆盖（修复前必失败 —— 审计 §4 第 7 项：DHR 实体不存在）：</p>
 * <ol>
 *   <li>状态机：仅 DRAFT 可提交/重建/删除；仅 SUBMITTED 可放行/驳回；仅 RELEASED 可归档；</li>
 *   <li>合规动作必须签名：LOGIN_ONLY 被拒、驳回必须带原因、签名失败即整单失败（不降级）、
 *       签名 ID 回填与状态 CAS 同语句；</li>
 *   <li>签名参数：bizType 用 DHR_SUBMIT/DHR_RELEASE/DHR_REJECT，docHash 绑定 snapshot_hash，
 *       bizId=dhrId、bizNo=dhrNo、docOssId=null（逻辑签名）；</li>
 *   <li>快照哈希：SM3 复算 + 篡改检出；</li>
 *   <li>分册完整性：无数据分册也落库（共 10 条）、缺分册禁止提交。</li>
 * </ol>
 *
 * <p>LoginHelper 是 Sa-Token 静态工具，纯单测必须 mockStatic；
 * JsonUtils 的静态初始化依赖 Spring 上下文，故先跑 SpringStaticBootstrap 预热。</p>
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DhrRecordServiceImplTest {

    @Mock private DhrRecordMapper baseMapper;
    @Mock private DhrSectionMapper sectionMapper;
    @Mock private IDhrAggregateService aggregateService;
    @Mock private DhrSignatureAccessor signatureAccessor;
    @Mock private ProWorkOrderMapper workOrderMapper;
    @Mock private ESignatureService eSignatureService;
    @InjectMocks private DhrRecordServiceImpl service;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeAll
    static void initTableInfo() {
        // 真实列缓存：LambdaQueryWrapper/LambdaUpdateWrapper 生成 SQL 片段需要 TableInfo
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DhrRecord.class);
        TableInfoHelper.initTableInfo(assistant, DhrSection.class);
        SpringStaticBootstrap.ensureActive();
    }

    @BeforeEach
    void setUp() {
        loginHelperMock = mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(9L);
        loginHelperMock.when(LoginHelper::getTenantId).thenReturn("000000");
        loginHelperMock.when(LoginHelper::getUsername).thenReturn("tester");
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (loginHelperMock != null) {
            loginHelperMock.close();
        }
    }

    // ==================== 辅助 ====================

    private DhrRecord record(String status) {
        DhrRecord record = new DhrRecord();
        record.setDhrId(1L);
        record.setDhrNo("DHR-WO001-NA");
        record.setWorkOrderId(100L);
        record.setWorkOrderNo("WO001");
        record.setMaterialId(200L);
        record.setMaterialCode("M001");
        record.setStatus(status);
        record.setSnapshotHash("SNAPSHOT_HASH");
        record.setVersion(1);
        return record;
    }

    private DhrSignBo signBo() {
        DhrSignBo bo = new DhrSignBo();
        bo.setAuthMethod(SignAuthMethod.PASSWORD);
        bo.setCredential("pwd");
        return bo;
    }

    private List<DhrSection> fullSections() {
        List<DhrSection> sections = new ArrayList<>();
        int order = 1;
        for (String code : DhrSectionCode.ordered()) {
            DhrSection section = new DhrSection();
            section.setDhrId(1L);
            section.setSectionCode(code);
            section.setSectionName(DhrSectionCode.nameOf(code));
            section.setContentJson("[]");
            section.setContentHash(Sm3Utils.hashHex("[]"));
            section.setItemCount(0);
            section.setSortOrder(order++);
            sections.add(section);
        }
        return sections;
    }

    private VerifyResultVo passedChain() {
        VerifyResultVo vo = new VerifyResultVo();
        vo.setPassed(true);
        vo.setChainPassed(true);
        vo.setCheckedCount(1);
        return vo;
    }

    private ProWorkOrder workOrder() {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(100L);
        wo.setWoNo("WO001");
        wo.setMaterialId(200L);
        wo.setMaterialCode("M001");
        wo.setMaterialName("产品A");
        wo.setPlannedQty(new BigDecimal("100"));
        wo.setCompletedQty(new BigDecimal("98"));
        wo.setScrapQty(new BigDecimal("2"));
        wo.setCreateTime(new Date());
        return wo;
    }

    // ==================== 提交（DRAFT -> SUBMITTED） ====================

    @Test
    void submitRejectsLoginOnly() {
        DhrSignBo bo = new DhrSignBo();
        bo.setAuthMethod(SignAuthMethod.LOGIN_ONLY);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.submit(1L, bo));
        assertTrue(ex.getMessage().contains("二级认证"));
        // 认证不通过时业务库一行未动、签名也未发生
        verifyNoInteractions(eSignatureService);
        verify(baseMapper, never()).update(nullable(DhrRecord.class), any());
    }

    @Test
    void submitSignsThenCasAndBindsSignature() {
        when(baseMapper.selectById(1L)).thenReturn(record(DhrStatus.DRAFT));
        when(sectionMapper.selectList(any())).thenReturn(fullSections());
        when(eSignatureService.signInNewTransaction(any())).thenReturn(99L);
        when(baseMapper.update(nullable(DhrRecord.class), any())).thenReturn(1);

        assertTrue(service.submit(1L, signBo()));

        ArgumentCaptor<SignCommand> captor = ArgumentCaptor.forClass(SignCommand.class);
        verify(eSignatureService).signInNewTransaction(captor.capture());
        SignCommand cmd = captor.getValue();
        assertEquals(SignBizType.DHR_SUBMIT, cmd.bizType());
        assertEquals(1L, cmd.bizId());
        assertEquals("DHR-WO001-NA", cmd.bizNo());
        assertEquals("提交批记录复核", cmd.signatureMeaning());
        // 逻辑签名：docHash 绑定快照哈希，docOssId 为 null
        assertEquals("SNAPSHOT_HASH", cmd.docHash());
        assertNull(cmd.docOssId());
        assertNull(cmd.sealId());
        assertEquals(SignAuthMethod.PASSWORD, cmd.authMethod());
        verify(baseMapper).update(nullable(DhrRecord.class), any());
    }

    @Test
    void submitFailsWholeOrderWhenSignatureFails() {
        when(baseMapper.selectById(1L)).thenReturn(record(DhrStatus.DRAFT));
        when(sectionMapper.selectList(any())).thenReturn(fullSections());
        when(eSignatureService.signInNewTransaction(any()))
            .thenThrow(new ServiceException("签名通道繁忙，请稍后重试"));

        assertThrows(ServiceException.class, () -> service.submit(1L, signBo()));
        // 不降级：状态流转必须未执行
        verify(baseMapper, never()).update(nullable(DhrRecord.class), any());
    }

    @Test
    void submitRejectsNonDraftStatus() {
        when(baseMapper.selectById(1L)).thenReturn(record(DhrStatus.SUBMITTED));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.submit(1L, signBo()));
        assertTrue(ex.getMessage().contains("仅 DRAFT"));
        verifyNoInteractions(eSignatureService);
    }

    @Test
    void submitRejectsIncompleteSections() {
        when(baseMapper.selectById(1L)).thenReturn(record(DhrStatus.DRAFT));
        List<DhrSection> missing = fullSections();
        missing.remove(0);
        when(sectionMapper.selectList(any())).thenReturn(missing);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.submit(1L, signBo()));
        assertTrue(ex.getMessage().contains("分册不完整"));
        assertTrue(ex.getMessage().contains(DhrSectionCode.WO_INFO));
        verifyNoInteractions(eSignatureService);
    }

    // ==================== 放行 / 驳回 / 归档 ====================

    @Test
    void releaseSignsWithReleaseBizType() {
        when(baseMapper.selectById(1L)).thenReturn(record(DhrStatus.SUBMITTED));
        when(eSignatureService.signInNewTransaction(any())).thenReturn(77L);
        when(baseMapper.update(nullable(DhrRecord.class), any())).thenReturn(1);

        assertTrue(service.release(1L, signBo()));

        ArgumentCaptor<SignCommand> captor = ArgumentCaptor.forClass(SignCommand.class);
        verify(eSignatureService).signInNewTransaction(captor.capture());
        assertEquals(SignBizType.DHR_RELEASE, captor.getValue().bizType());
        assertEquals("批记录放行", captor.getValue().signatureMeaning());
    }

    @Test
    void releaseRejectsDraftStatus() {
        when(baseMapper.selectById(1L)).thenReturn(record(DhrStatus.DRAFT));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.release(1L, signBo()));
        assertTrue(ex.getMessage().contains("仅 SUBMITTED"));
        verifyNoInteractions(eSignatureService);
    }

    @Test
    void rejectRequiresReason() {
        DhrSignBo bo = signBo();
        when(baseMapper.selectById(1L)).thenReturn(record(DhrStatus.SUBMITTED));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.reject(1L, bo));
        assertTrue(ex.getMessage().contains("驳回原因"));
        verifyNoInteractions(eSignatureService);
    }

    @Test
    void rejectSignsWithRejectBizType() {
        DhrSignBo bo = signBo();
        bo.setReason("过程记录缺失");
        when(baseMapper.selectById(1L)).thenReturn(record(DhrStatus.SUBMITTED));
        when(eSignatureService.signInNewTransaction(any())).thenReturn(55L);
        when(baseMapper.update(nullable(DhrRecord.class), any())).thenReturn(1);

        assertTrue(service.reject(1L, bo));

        ArgumentCaptor<SignCommand> captor = ArgumentCaptor.forClass(SignCommand.class);
        verify(eSignatureService).signInNewTransaction(captor.capture());
        assertEquals(SignBizType.DHR_REJECT, captor.getValue().bizType());
        assertEquals("批记录驳回", captor.getValue().signatureMeaning());
    }

    @Test
    void archiveOnlyFromReleased() {
        when(baseMapper.selectById(1L)).thenReturn(record(DhrStatus.SUBMITTED));
        assertThrows(ServiceException.class, () -> service.archive(1L));

        when(baseMapper.selectById(1L)).thenReturn(record(DhrStatus.RELEASED));
        when(baseMapper.update(nullable(DhrRecord.class), any())).thenReturn(1);
        assertTrue(service.archive(1L));
    }

    @Test
    void deleteOnlyDraftAndRemovesSections() {
        when(baseMapper.selectById(1L)).thenReturn(record(DhrStatus.SUBMITTED));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.deleteById(1L));
        assertTrue(ex.getMessage().contains("仅 DRAFT"));

        when(baseMapper.selectById(1L)).thenReturn(record(DhrStatus.DRAFT));
        when(baseMapper.delete(any())).thenReturn(1);
        when(sectionMapper.delete(any())).thenReturn(10);
        assertTrue(service.deleteById(1L));
        verify(sectionMapper).delete(any());
    }

    // ==================== 校验 ====================

    @Test
    void verifyPassesWhenHashesConsistent() {
        List<DhrSection> sections = fullSections();
        StringBuilder builder = new StringBuilder();
        for (DhrSection section : sections) {
            builder.append(section.getSectionCode()).append('\n')
                .append(section.getContentHash()).append('\n');
        }
        DhrRecord record = record(DhrStatus.SUBMITTED);
        record.setSnapshotHash(Sm3Utils.hashHex(builder.toString()));

        when(baseMapper.selectById(1L)).thenReturn(record);
        when(sectionMapper.selectList(any())).thenReturn(sections);
        when(signatureAccessor.verifyChain(anyString(), any())).thenReturn(passedChain());

        DhrVerifyVo vo = service.verify(1L);
        assertEquals("PASS", vo.getVerdict());
        assertTrue(vo.getHashMatched());
        assertEquals(record.getSnapshotHash(), vo.getRecomputedHash());
        assertNotNull(vo.getChainVerify());
    }

    @Test
    void verifyDetectsTamperedContent() {
        List<DhrSection> sections = fullSections();
        // 篡改分册正文：content_json 改了，content_hash 未同步
        sections.get(0).setContentJson("[{\"tampered\":true}]");

        DhrRecord record = record(DhrStatus.SUBMITTED);
        record.setSnapshotHash("ORIGINAL_HASH");
        when(baseMapper.selectById(1L)).thenReturn(record);
        when(sectionMapper.selectList(any())).thenReturn(sections);
        when(signatureAccessor.verifyChain(anyString(), any())).thenReturn(passedChain());

        DhrVerifyVo vo = service.verify(1L);
        assertEquals("FAIL", vo.getVerdict());
        assertFalse(vo.getHashMatched());
        assertFalse(vo.getRecomputedHash().equals("ORIGINAL_HASH"));
    }

    @Test
    void verifyFailsWhenSignatureChainBroken() {
        List<DhrSection> sections = fullSections();
        StringBuilder builder = new StringBuilder();
        for (DhrSection section : sections) {
            builder.append(section.getSectionCode()).append('\n')
                .append(section.getContentHash()).append('\n');
        }
        DhrRecord record = record(DhrStatus.SUBMITTED);
        record.setSnapshotHash(Sm3Utils.hashHex(builder.toString()));

        VerifyResultVo broken = new VerifyResultVo();
        broken.setPassed(false);
        broken.setChainPassed(false);
        broken.setBrokenAt(42L);

        when(baseMapper.selectById(1L)).thenReturn(record);
        when(sectionMapper.selectList(any())).thenReturn(sections);
        when(signatureAccessor.verifyChain(anyString(), any())).thenReturn(broken);

        DhrVerifyVo vo = service.verify(1L);
        assertEquals("FAIL", vo.getVerdict());
        assertTrue(vo.getHashMatched());
        assertEquals(42L, vo.getChainVerify().getBrokenAt());
    }

    // ==================== 构建 ====================

    @Test
    void buildWritesAllTenSectionsIncludingEmpty() {
        Map<String, List<Map<String, Object>>> aggregated = new LinkedHashMap<>();
        List<Map<String, Object>> woItems = new ArrayList<>();
        Map<String, Object> woRow = new LinkedHashMap<>();
        woRow.put("wo_no", "WO001");
        woRow.put("entry_type", "WORK_ORDER");
        woItems.add(woRow);
        aggregated.put(DhrSectionCode.WO_INFO, woItems);

        when(workOrderMapper.selectById(100L)).thenReturn(workOrder());
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(aggregateService.aggregate(any(), any(), any())).thenReturn(aggregated);
        when(baseMapper.insert(any(DhrRecord.class))).thenReturn(1);
        when(sectionMapper.insert(any(DhrSection.class))).thenReturn(1);

        DhrBuildBo bo = new DhrBuildBo();
        bo.setWorkOrderId(100L);
        DhrRecordVo vo = service.build(bo);

        assertEquals(DhrStatus.DRAFT, vo.getStatus());
        assertNotNull(vo.getSnapshotHash());
        assertEquals(10, vo.getSections().size());
        verify(sectionMapper, times(10)).insert(any(DhrSection.class));
        // 无数据分册 item_count=0，但仍落库（分册集合完整可校验）
        DhrSection empty = null;
        for (DhrSection section : capturedSections()) {
            if (DhrSectionCode.NCR.equals(section.getSectionCode())) {
                empty = section;
            }
        }
        assertNotNull(empty);
        assertEquals(0, empty.getItemCount());
        assertEquals(Sm3Utils.hashHex("[]"), empty.getContentHash());
    }

    private List<DhrSection> capturedSections() {
        ArgumentCaptor<DhrSection> captor = ArgumentCaptor.forClass(DhrSection.class);
        verify(sectionMapper, times(10)).insert(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void buildRejectsDuplicateUnarchivedRecord() {
        when(workOrderMapper.selectById(100L)).thenReturn(workOrder());
        when(baseMapper.selectCount(any())).thenReturn(1L);

        DhrBuildBo bo = new DhrBuildBo();
        bo.setWorkOrderId(100L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.build(bo));
        assertTrue(ex.getMessage().contains("未归档"));
    }

    @Test
    void buildRejectsMissingWorkOrder() {
        when(workOrderMapper.selectById(100L)).thenReturn(null);
        DhrBuildBo bo = new DhrBuildBo();
        bo.setWorkOrderId(100L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.build(bo));
        assertTrue(ex.getMessage().contains("工单不存在"));
    }
}
