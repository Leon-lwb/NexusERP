package com.nexus.common.sign.service.impl;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.constants.SealType;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.bo.SealBo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 印章编辑校验测试（图片字段分组后的服务层兜底）。
 *
 * <p>背景：{@code SealBo} 的图片三字段 {@code @NotNull} 已收敛到 {@code AddGroup}
 * （修改不再强制重传图片），故「提供了图片 ⇒ 必须同时给出宽高」这条配对规则
 * 改由 {@code validateSealBo} 兜底。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EsSealServiceImplTest {

    private static final Long SEAL_ID = 88L;

    @Mock
    private EsSealMapper baseMapper;
    @Mock
    private EsSealGrantMapper sealGrantMapper;

    private EsSealServiceImpl service;

    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        // ServiceImpl 的 baseMapper 在祖父类 CrudRepository 中声明，@InjectMocks 注入不到，
        // 这里用子类在构造时显式塞入（protected 字段，子类可赋值）
        service = new TestableEsSealServiceImpl(sealGrantMapper, baseMapper);

        // 名称唯一性校验会走 lambda 查询，预热 MyBatis-Plus 列缓存（与 ESignatureServiceImplTest 同法）
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> cols = new HashMap<>();
        for (String c : new String[]{"SEALID", "SEALNAME", "SEALTYPE", "OWNERUSERID", "SEALOSSID"}) {
            cols.put(c, new ColumnCache(c.toLowerCase(), c.toLowerCase()));
        }
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EsSeal.class)).thenReturn(cols);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) {
            lambdaUtilsMock.close();
        }
    }

    /** 测试用子类：把 mock 的 baseMapper 注入到 ServiceImpl 继承来的 protected 字段。 */
    private static class TestableEsSealServiceImpl extends EsSealServiceImpl {

        TestableEsSealServiceImpl(EsSealGrantMapper sealGrantMapper, EsSealMapper baseMapper) {
            super(sealGrantMapper);
            this.baseMapper = baseMapper;
        }
    }

    /** 已存在的启用态公司章。 */
    private static EsSeal existingCompanySeal() {
        EsSeal seal = new EsSeal();
        seal.setSealId(SEAL_ID);
        seal.setSealType(SealType.COMPANY);
        seal.setSealName("公司公章");
        seal.setSealOssId(1001L);
        seal.setImgWidth(200);
        seal.setImgHeight(200);
        seal.setStatus(SealStatus.ENABLED);
        seal.setSealVersion(1);
        return seal;
    }

    /** 编辑入参：不重传图片（前端 SealVo 不回传 sealOssId）。 */
    private static SealBo editBo() {
        SealBo bo = new SealBo();
        bo.setSealId(SEAL_ID);
        bo.setSealType(SealType.COMPANY);
        bo.setSealName("公司公章");
        return bo;
    }

    @Test
    @DisplayName("编辑：换了图片却没给宽高 -> ServiceException，且不落库")
    void updateByBo_replacingImageWithoutSize_rejected() {
        when(baseMapper.selectById(SEAL_ID)).thenReturn(existingCompanySeal());

        SealBo bo = editBo();
        bo.setSealOssId(2002L);
        bo.setImgHeight(300);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        assertTrue(ex.getMessage().contains("图片宽度"), "提示语应指明缺少图片宽高：" + ex.getMessage());
        verify(baseMapper, never()).updateById(any(EsSeal.class));
    }

    @Test
    @DisplayName("编辑：换了图片只给宽不给高 -> ServiceException")
    void updateByBo_replacingImageWithoutHeight_rejected() {
        when(baseMapper.selectById(SEAL_ID)).thenReturn(existingCompanySeal());

        SealBo bo = editBo();
        bo.setSealOssId(2002L);
        bo.setImgWidth(200);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateByBo(bo));
        assertTrue(ex.getMessage().contains("图片宽度"), "提示语应指明缺少图片宽高：" + ex.getMessage());
        verify(baseMapper, never()).updateById(any(EsSeal.class));
    }

    @Test
    @DisplayName("编辑：不重传印章图片不会被尺寸配对校验拦下（继续走到名称唯一性校验）")
    void updateByBo_omittedImage_passesSizePairingGuard() {
        when(baseMapper.selectById(SEAL_ID)).thenReturn(existingCompanySeal());
        // 名称重复：用它的报错证明「图片配对校验已通过、流程继续往下走」
        when(baseMapper.selectCount(any())).thenReturn(1L);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.updateByBo(editBo()));
        assertTrue(ex.getMessage().contains("已存在"),
            "留空图片应通过尺寸配对校验，继续到名称唯一性校验：" + ex.getMessage());
    }
}
