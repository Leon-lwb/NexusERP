package com.nexus.common.sign.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.constants.SealType;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSealGrant;
import com.nexus.common.sign.domain.bo.SealGrantBo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
 * 用章授权修改的回归测试。
 *
 * <p>背景：{@code EsSealGrant} 的 {@code granteeUserId} / {@code granteeDeptId} 二选一，
 * 而 {@code updateById} 会跳过 null 字段。若把「按部门」的授权改成「按用户」，
 * 行里旧的 {@code grantee_dept_id} 会残留，得到一条「既匹配某用户、又匹配整个部门」的授权，
 * 实际可用章的范围被无声放大；反向同理。故 {@code updateByBo} 改由 wrapper 显式把缺席的一列置 NULL。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("local")
@Tag("dev")
class EsSealGrantServiceImplTest {

    private static final Long GRANT_ID = 66L;
    private static final Long SEAL_ID = 88L;
    private static final Long USER_ID = 1001L;
    private static final Long DEPT_ID = 2002L;

    @Mock
    private EsSealGrantMapper baseMapper;
    @Mock
    private EsSealMapper sealMapper;
    @Captor
    private ArgumentCaptor<EsSealGrant> entityCaptor;
    @Captor
    private ArgumentCaptor<LambdaUpdateWrapper<EsSealGrant>> wrapperCaptor;

    private EsSealGrantServiceImpl service;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    /** 静态 mock：MapstructUtils.CONVERTER 依赖 SpringUtils.getBean(Converter)，纯 Mockito 下先注入真实转换器子类 */
    private static MockedStatic<SpringUtil> springUtilMock;

    @BeforeAll
    static void initMapstruct() {
        springUtilMock = mockStatic(SpringUtil.class);
        // Converter 为实体类：只覆写单对象转换（BeanUtil 属性拷贝），其余重载不触发
        Converter converter = new Converter() {
            @Override
            public <S, T> T convert(S source, Class<T> targetType) {
                return BeanUtil.copyProperties(source, targetType);
            }
        };
        lenient().when(SpringUtil.getBean(Converter.class)).thenReturn(converter);
    }

    @AfterAll
    static void closeMapstruct() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    @BeforeEach
    void setUp() {
        service = new EsSealGrantServiceImpl(baseMapper, sealMapper);

        // 授权对象二选一的清空逻辑走 lambda wrapper，预热 MyBatis-Plus 列缓存（与 EsSealServiceImplTest 同法）
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> cols = new HashMap<>();
        cols.put("GRANTID", new ColumnCache("grant_id", "grant_id"));
        cols.put("SEALID", new ColumnCache("seal_id", "seal_id"));
        cols.put("GRANTEEUSERID", new ColumnCache("grantee_user_id", "grantee_user_id"));
        cols.put("GRANTEEDEPTID", new ColumnCache("grantee_dept_id", "grantee_dept_id"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EsSealGrant.class)).thenReturn(cols);

        when(baseMapper.update(any(EsSealGrant.class), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) {
            lambdaUtilsMock.close();
        }
    }

    /** 已存在的启用态公司章（授权只针对公司章） */
    private static EsSeal companySeal() {
        EsSeal seal = new EsSeal();
        seal.setSealId(SEAL_ID);
        seal.setSealType(SealType.COMPANY);
        seal.setSealName("公司公章");
        seal.setStatus(SealStatus.ENABLED);
        return seal;
    }

    /** 库中既有的授权行 */
    private static EsSealGrant existingGrant(Long granteeUserId, Long granteeDeptId) {
        EsSealGrant grant = new EsSealGrant();
        grant.setGrantId(GRANT_ID);
        grant.setSealId(SEAL_ID);
        grant.setGranteeUserId(granteeUserId);
        grant.setGranteeDeptId(granteeDeptId);
        grant.setStatus("1");
        return grant;
    }

    /** 提交「按用户」授权（granteeDeptId 缺席，模拟前端二选一后不提交另一侧） */
    private static SealGrantBo userBo() {
        SealGrantBo bo = new SealGrantBo();
        bo.setGrantId(GRANT_ID);
        bo.setSealId(SEAL_ID);
        bo.setGranteeUserId(USER_ID);
        bo.setStatus("1");
        return bo;
    }

    /** 提交「按部门」授权（granteeUserId 缺席） */
    private static SealGrantBo deptBo() {
        SealGrantBo bo = new SealGrantBo();
        bo.setGrantId(GRANT_ID);
        bo.setSealId(SEAL_ID);
        bo.setGranteeDeptId(DEPT_ID);
        bo.setStatus("1");
        return bo;
    }

    /** 取 wrapper 中显式 SET 的片段（entity 的非空字段不在此列） */
    private static String wrapperSetSql(LambdaUpdateWrapper<EsSealGrant> luw) {
        return luw.getSqlSet() == null ? "" : luw.getSqlSet();
    }

    @Test
    @DisplayName("updateByBo: 按部门 -> 按用户，显式清空 grantee_dept_id")
    void updateByBo_deptToUser_clearsGranteeDeptId() {
        when(baseMapper.selectById(GRANT_ID)).thenReturn(existingGrant(null, DEPT_ID));
        when(sealMapper.selectById(SEAL_ID)).thenReturn(companySeal());

        assertEquals(1, service.updateByBo(userBo()));

        verify(baseMapper).update(entityCaptor.capture(), wrapperCaptor.capture());
        assertEquals(USER_ID, entityCaptor.getValue().getGranteeUserId());
        assertNull(entityCaptor.getValue().getGranteeDeptId());

        LambdaUpdateWrapper<EsSealGrant> luw = wrapperCaptor.getValue();
        String sqlSet = wrapperSetSql(luw);
        assertTrue(sqlSet.contains("grantee_dept_id="),
            "应通过 wrapper 显式 SET grantee_dept_id=NULL，实际：" + sqlSet);
        assertFalse(sqlSet.contains("grantee_user_id="),
            "非空的一列由 entity 更新，不应重复出现在 wrapper：" + sqlSet);
        assertTrue(luw.getParamNameValuePairs().containsValue(null),
            "置 NULL 的列必须以 null 参数下发：" + luw.getParamNameValuePairs());
        assertTrue(luw.getSqlSegment().contains("grant_id"),
            "wrapper 应以 grant_id 定位待更新行：" + luw.getSqlSegment());
    }

    @Test
    @DisplayName("updateByBo: 按用户 -> 按部门，显式清空 grantee_user_id")
    void updateByBo_userToDept_clearsGranteeUserId() {
        when(baseMapper.selectById(GRANT_ID)).thenReturn(existingGrant(USER_ID, null));
        when(sealMapper.selectById(SEAL_ID)).thenReturn(companySeal());

        service.updateByBo(deptBo());

        verify(baseMapper).update(entityCaptor.capture(), wrapperCaptor.capture());
        assertEquals(DEPT_ID, entityCaptor.getValue().getGranteeDeptId());
        assertNull(entityCaptor.getValue().getGranteeUserId());

        LambdaUpdateWrapper<EsSealGrant> luw = wrapperCaptor.getValue();
        String sqlSet = wrapperSetSql(luw);
        assertTrue(sqlSet.contains("grantee_user_id="),
            "应通过 wrapper 显式 SET grantee_user_id=NULL，实际：" + sqlSet);
        assertFalse(sqlSet.contains("grantee_dept_id="),
            "非空的一列由 entity 更新，不应重复出现在 wrapper：" + sqlSet);
        assertTrue(luw.getParamNameValuePairs().containsValue(null),
            "置 NULL 的列必须以 null 参数下发：" + luw.getParamNameValuePairs());
    }

    @Test
    @DisplayName("updateByBo: 保持按用户的普通修改（另一侧缺席）同样清空另一列")
    void updateByBo_singleGranteeUnchangedMode_clearsOther() {
        // 方式不变，仅改备注：仍然只提交一侧，另一列必须被显式置 NULL，
        // 以顺带清理历史脏数据（早期版本可能留下过同时匹配用户与部门的行）
        when(baseMapper.selectById(GRANT_ID)).thenReturn(existingGrant(USER_ID, DEPT_ID));
        when(sealMapper.selectById(SEAL_ID)).thenReturn(companySeal());

        SealGrantBo bo = userBo();
        bo.setRemark("改备注");
        service.updateByBo(bo);

        verify(baseMapper).update(entityCaptor.capture(), wrapperCaptor.capture());
        assertEquals("改备注", entityCaptor.getValue().getRemark());
        String sqlSet = wrapperSetSql(wrapperCaptor.getValue());
        assertTrue(sqlSet.contains("grantee_dept_id="),
            "普通修改也必须清空另一侧，实际：" + sqlSet);
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(null),
            "清空应作为 null 参数下发：" + wrapperCaptor.getValue().getParamNameValuePairs());
    }

    @Test
    @DisplayName("updateByBo: 同时提交用户与部门（非二选一）时不额外清空")
    void updateByBo_bothGranteesSupplied_noExplicitNull() {
        when(baseMapper.selectById(GRANT_ID)).thenReturn(existingGrant(USER_ID, DEPT_ID));
        when(sealMapper.selectById(SEAL_ID)).thenReturn(companySeal());

        SealGrantBo bo = userBo();
        bo.setGranteeDeptId(DEPT_ID);
        service.updateByBo(bo);

        verify(baseMapper).update(entityCaptor.capture(), wrapperCaptor.capture());
        assertEquals(USER_ID, entityCaptor.getValue().getGranteeUserId());
        assertEquals(DEPT_ID, entityCaptor.getValue().getGranteeDeptId());
        assertFalse(wrapperSetSql(wrapperCaptor.getValue()).contains("grantee_"),
            "两侧都提交时不应有额外的 NULL 清空：" + wrapperSetSql(wrapperCaptor.getValue()));
    }

    @Test
    @DisplayName("updateByBo: 授权记录不存在 -> ServiceException 且不落库")
    void updateByBo_missingRow_throws() {
        when(baseMapper.selectById(GRANT_ID)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateByBo(userBo()));
        assertTrue(ex.getMessage().contains("授权记录不存在"), "提示语：" + ex.getMessage());
        verify(baseMapper, never()).update(any(EsSealGrant.class), any());
    }

    @Test
    @DisplayName("updateByBo: 校验不通过（未填授权对象 / 非公司章）-> ServiceException 且不落库")
    void updateByBo_validationFailures_throw() {
        when(baseMapper.selectById(GRANT_ID)).thenReturn(existingGrant(USER_ID, null));

        SealGrantBo neither = userBo();
        neither.setGranteeUserId(null);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateByBo(neither));
        assertTrue(ex.getMessage().contains("至少填写一项"), "提示语：" + ex.getMessage());

        EsSeal personalSeal = companySeal();
        personalSeal.setSealType(SealType.PERSONAL);
        when(sealMapper.selectById(SEAL_ID)).thenReturn(personalSeal);
        ServiceException ex2 = assertThrows(ServiceException.class, () -> service.updateByBo(userBo()));
        assertTrue(ex2.getMessage().contains("仅公司章需要授权"), "提示语：" + ex2.getMessage());

        verify(baseMapper, never()).update(any(EsSealGrant.class), any());
    }
}
