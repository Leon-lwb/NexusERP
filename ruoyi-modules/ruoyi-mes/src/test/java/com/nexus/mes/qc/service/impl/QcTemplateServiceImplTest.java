package com.nexus.mes.qc.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.qc.domain.QcInspectItem;
import com.nexus.mes.qc.domain.QcInspectTemplate;
import com.nexus.mes.qc.domain.bo.QcInspectItemBo;
import com.nexus.mes.qc.domain.bo.QcInspectTemplateBo;
import com.nexus.mes.qc.mapper.QcInspectDetailMapper;
import com.nexus.mes.qc.mapper.QcInspectItemMapper;
import com.nexus.mes.qc.mapper.QcInspectTemplateMapper;
import com.nexus.mes.testsupport.SpringStaticBootstrap;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * QcTemplateServiceImpl 单元测试
 * <p>覆盖 MES-QC-09(入参 status 被忽略 + 启用条件更新 CAS + 至少一个启用项目) 与
 * MES-QC-06(NOMINAL 负公差被拒)。</p>
 * <p>MapstructUtils 的静态初始化依赖 SpringUtils, 必须先经 {@link SpringStaticBootstrap} 预热
 * (否则失败的 clinit 在本 JVM 内不可恢复); 预热后转换走"同名属性拷贝"的真实逻辑,
 * 因此 "入参 status=ACTIVE 被覆盖为 DRAFT" 是对修复本身的真实断言。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class QcTemplateServiceImplTest {

    @Mock private QcInspectTemplateMapper baseMapper;
    @Mock private QcInspectItemMapper itemMapper;
    @Mock private QcInspectDetailMapper detailMapper;
    @InjectMocks private QcTemplateServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        SpringStaticBootstrap.ensureActive();
        // 真实 TableInfo/列缓存: changeStatus 会构造 QcInspectItem 的 lambda 条件
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), QcInspectTemplate.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), QcInspectItem.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(QcInspectTemplate.class);
        TableInfoHelper.remove(QcInspectItem.class);
    }

    // ==================== MES-QC-09: 状态由服务端强制草稿 ====================

    @Test
    void insertByBoForcesDraftStatusIgnoringInput() {
        when(baseMapper.insert(any(QcInspectTemplate.class))).thenReturn(1);

        QcInspectTemplateBo bo = new QcInspectTemplateBo();
        bo.setTemplateCode("T-001");
        bo.setTemplateName("来料检验模板");
        bo.setTemplateType("IQC");
        bo.setStatus("ACTIVE");
        bo.setItems(List.of(quantitativeItem("DIM", "LSL_USL")));

        service.insertByBo(bo);

        ArgumentCaptor<QcInspectTemplate> captor = ArgumentCaptor.forClass(QcInspectTemplate.class);
        verify(baseMapper).insert(captor.capture());
        assertEquals("DRAFT", captor.getValue().getStatus(), "MES-QC-09: 新建模板必须强制 DRAFT, 忽略入参 status");
    }

    @Test
    void updateByBoForcesDraftStatusIgnoringInput() {
        QcInspectTemplate exist = new QcInspectTemplate();
        exist.setTemplateId(7L);
        exist.setStatus("ACTIVE");
        when(baseMapper.selectById(7L)).thenReturn(exist);
        when(baseMapper.updateById(any(QcInspectTemplate.class))).thenReturn(1);

        QcInspectTemplateBo bo = new QcInspectTemplateBo();
        bo.setTemplateId(7L);
        bo.setTemplateCode("T-001");
        bo.setStatus("ACTIVE");
        bo.setItems(List.of(quantitativeItem("DIM", "LSL_USL")));

        service.updateByBo(bo);

        ArgumentCaptor<QcInspectTemplate> captor = ArgumentCaptor.forClass(QcInspectTemplate.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals("DRAFT", captor.getValue().getStatus(), "MES-QC-09: 编辑模板必须强制 DRAFT, 忽略入参 status");
    }

    // ==================== MES-QC-09: 启用为条件更新(CAS) ====================

    @Test
    void changeStatusActiveCasZeroRowsThrows() {
        QcInspectTemplate t = new QcInspectTemplate();
        t.setTemplateId(1L);
        t.setStatus("ACTIVE");
        when(baseMapper.selectById(1L)).thenReturn(t);
        when(itemMapper.selectCount(any())).thenReturn(1L);
        when(baseMapper.update(isNull(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.changeStatus(1L, true));

        assertTrue(ex.getMessage().contains("状态已变更"), ex.getMessage());
        // 绝不允许退回整实体 updateById(会把并发状态覆盖回去)
        verify(baseMapper, never()).updateById(any(QcInspectTemplate.class));
    }

    @Test
    void changeStatusActiveCasSuccess() {
        QcInspectTemplate t = new QcInspectTemplate();
        t.setTemplateId(1L);
        t.setStatus("DRAFT");
        when(baseMapper.selectById(1L)).thenReturn(t);
        when(itemMapper.selectCount(any())).thenReturn(1L);
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        assertDoesNotThrow(() -> service.changeStatus(1L, true));

        verify(baseMapper).update(isNull(), any());
        verify(baseMapper, never()).updateById(any(QcInspectTemplate.class));
    }

    @Test
    void changeStatusActiveRejectsTemplateWithoutEnabledItems() {
        QcInspectTemplate t = new QcInspectTemplate();
        t.setTemplateId(1L);
        t.setStatus("DRAFT");
        when(baseMapper.selectById(1L)).thenReturn(t);
        when(itemMapper.selectCount(any())).thenReturn(0L);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.changeStatus(1L, true));

        assertTrue(ex.getMessage().contains("启用的检验项目"), ex.getMessage());
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void changeStatusThrowsWhenTemplateMissing() {
        when(baseMapper.selectById(99L)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.changeStatus(99L, true));

        assertTrue(ex.getMessage().contains("不存在"), ex.getMessage());
    }

    // ==================== MES-QC-06: NOMINAL 负公差被拒 ====================

    @Test
    void insertByBoRejectsNegativeUpperTolerance() {
        QcInspectItemBo item = quantitativeItem("DIM", "NOMINAL");
        item.setNominalValue(new BigDecimal("10"));
        item.setTolUpper(new BigDecimal("-0.1"));
        item.setTolLower(new BigDecimal("0.1"));

        QcInspectTemplateBo bo = new QcInspectTemplateBo();
        bo.setItems(List.of(item));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo));

        assertTrue(ex.getMessage().contains("正值"), ex.getMessage());
        verify(baseMapper, never()).insert(any(QcInspectTemplate.class));
    }

    @Test
    void insertByBoRejectsNegativeLowerTolerance() {
        QcInspectItemBo item = quantitativeItem("DIM", "NOMINAL");
        item.setNominalValue(new BigDecimal("10"));
        item.setTolUpper(new BigDecimal("0.1"));
        item.setTolLower(new BigDecimal("-0.1"));

        QcInspectTemplateBo bo = new QcInspectTemplateBo();
        bo.setItems(List.of(item));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo));

        assertTrue(ex.getMessage().contains("正值"), ex.getMessage());
        verify(baseMapper, never()).insert(any(QcInspectTemplate.class));
    }

    // ==================== MES-QC-09: 至少一个启用项目 ====================

    @Test
    void insertByBoRejectsWhenNoEnabledItem() {
        QcInspectItemBo item = quantitativeItem("DIM", "LSL_USL");
        item.setEnabled("0");

        QcInspectTemplateBo bo = new QcInspectTemplateBo();
        bo.setItems(List.of(item));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.insertByBo(bo));

        assertTrue(ex.getMessage().contains("至少需要一个启用"), ex.getMessage());
        verify(baseMapper, never()).insert(any(QcInspectTemplate.class));
    }

    private QcInspectItemBo quantitativeItem(String code, String standardType) {
        QcInspectItemBo item = new QcInspectItemBo();
        item.setItemCode(code);
        item.setItemName("尺寸" + code);
        item.setDataType("QUANTITATIVE");
        item.setStandardType(standardType);
        if ("LSL_USL".equals(standardType)) {
            item.setLslValue(new BigDecimal("9.9"));
            item.setUslValue(new BigDecimal("10.1"));
        }
        return item;
    }
}
