package com.nexus.erp.bd;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdBomDetail;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.bo.BdBomDetailBo;
import com.nexus.erp.bd.mapper.BdBomDetailMapper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.service.impl.BdBomDetailServiceImpl;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.testsupport.SpringStaticBootstrap;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BOM 明细 Service 单元测试。
 * <ul>
 *   <li>ERP-BD-03：{@code selectById} 查不到时必须返回 null 而不是抛 NPE（List.of(null) 同型残留）</li>
 *   <li>ERP-BD-08：明细「全删重插」必须与整表删除一样有未完成工单守卫</li>
 *   <li>ERP-BD-10：停用物料不得加入 BOM</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class BdBomDetailServiceImplTest {

    @Mock
    private BdBomDetailMapper baseMapper;
    @Mock
    private BdMaterialMapper materialMapper;
    @Mock
    private PrdWorkOrderMapper workOrderMapper;

    @InjectMocks
    private BdBomDetailServiceImpl bomDetailService;

    @BeforeAll
    static void initTableInfo() {
        // saveBatch 会走 MapstructUtils.convert，必须先让其在 SpringUtil 静态桩窗口内完成静态初始化，
        // 否则本类会成为 surefire fork 的毒化源（ExceptionInInitializerError 在 JVM 内粘性）
        SpringStaticBootstrap.ensureActive();
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BdBomDetail.class);
        TableInfoHelper.initTableInfo(assistant, BdMaterial.class);
        TableInfoHelper.initTableInfo(assistant, PrdWorkOrder.class);
    }

    /** ERP-BD-03：不存在 id 返回 null，不抛 NPE */
    @Test
    void selectById_notFound_returnsNullWithoutNpe() {
        when(baseMapper.selectVoById(404L)).thenReturn(null);
        assertNull(bomDetailService.selectById(404L));
    }

    /** ERP-BD-08：BOM 被未完成工单引用时，saveBatch（全删重插）必须被拒绝 */
    @Test
    void saveBatch_openWorkOrderReference_rejected() {
        lenient().when(workOrderMapper.selectCount(any())).thenReturn(3L);

        BdBomDetailBo bo = detailBo(7L, 100L);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> bomDetailService.saveBatch(List.of(bo)));
        assertTrue(ex.getMessage().contains("工单"), ex.getMessage());
        verify(baseMapper, never()).delete(any());
        verify(baseMapper, never()).insert(any(BdBomDetail.class));
    }

    /** ERP-BD-08：无工单引用时正常保存 */
    @Test
    void saveBatch_noWorkOrderReference_succeeds() {
        when(workOrderMapper.selectCount(any())).thenReturn(0L);
        when(materialMapper.selectList(any())).thenReturn(List.of(enabledMaterial(100L)));
        when(baseMapper.insert(any(BdBomDetail.class))).thenReturn(1);

        BdBomDetailBo bo = detailBo(7L, 100L);
        assertTrue(bomDetailService.saveBatch(List.of(bo)) == 1);
        verify(baseMapper).delete(any());
        verify(baseMapper).insert(any(BdBomDetail.class));
    }

    /** ERP-BD-10：停用/不存在的子项物料不得加入 BOM */
    @Test
    void insertByBo_disabledMaterial_rejected() {
        when(materialMapper.selectList(any())).thenReturn(List.of());

        ServiceException ex = assertThrows(ServiceException.class,
            () -> bomDetailService.insertByBo(detailBo(7L, 999L)));
        assertTrue(ex.getMessage().contains("停用"), ex.getMessage());
        verify(baseMapper, never()).insert(any(BdBomDetail.class));
    }

    private BdBomDetailBo detailBo(Long bomId, Long childMaterialId) {
        BdBomDetailBo bo = new BdBomDetailBo();
        bo.setBomId(bomId);
        bo.setChildMaterialId(childMaterialId);
        bo.setQuantityPerParent(new BigDecimal("2"));
        bo.setOperationStep(10);
        return bo;
    }

    private BdMaterial enabledMaterial(Long materialId) {
        BdMaterial material = new BdMaterial();
        material.setMaterialId(materialId);
        material.setStatus(1L);
        return material;
    }
}
