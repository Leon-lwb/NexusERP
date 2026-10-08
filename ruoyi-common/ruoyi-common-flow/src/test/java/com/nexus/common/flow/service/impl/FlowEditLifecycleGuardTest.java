package com.nexus.common.flow.service.impl;

import cn.hutool.core.exceptions.UtilException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.flow.domain.bo.FlowBo;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.mapper.FlowInstanceMapper;
import com.nexus.common.flow.mapper.FlowMapper;
import com.nexus.common.flow.mapper.FlowVersionMapper;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 编辑生命周期边界：真实 MapStruct 映射，模拟 Mapper，不连接数据库或启动应用。 */
class FlowEditLifecycleGuardTest {
    private static final long ID = 2104385976564420610L;
    private static ConfigurableListableBeanFactory previousBeanFactory;

    private final FlowMapper flows = mock(FlowMapper.class);
    private final FlowInstanceMapper instances = mock(FlowInstanceMapper.class);
    private final FlowVersionMapper versions = mock(FlowVersionMapper.class);
    private final FlowServiceImpl service = new FlowServiceImpl(flows, instances, versions);
    private Flow stored;
    private Runnable beforeWrite = () -> { };

    @BeforeAll
    static void initializeMappingAndTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Flow.class);
        try {
            previousBeanFactory = SpringUtils.getConfigurableBeanFactory();
        } catch (UtilException ignored) {
            // 无应用上下文时，仅为 MapstructUtils 注册真实 Converter。全程不启动业务服务。
        }
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("converter", new Converter());
        new SpringUtils().postProcessBeanFactory(beans);
    }

    @AfterAll
    static void restoreBeanFactory() {
        new SpringUtils().postProcessBeanFactory(previousBeanFactory);
    }

    @BeforeEach
    void initializePersistenceBoundary() {
        stored = new Flow();
        stored.setId(ID);
        stored.setTenantId("000000");
        stored.setStatus("DRAFT");
        stored.setVersion(7);
        stored.setFlowName("旧流程");
        stored.setConfigJson("旧图");
        when(flows.selectById(ID)).thenAnswer(invocation -> lockedSnapshot());
        when(flows.selectOne(any())).thenAnswer(invocation -> lockedSnapshot());
        when(flows.updateById(any(Flow.class))).thenAnswer(invocation -> {
            beforeWrite.run();
            applyEdit(invocation.getArgument(0));
            return 1;
        });
        when(flows.update(any(Flow.class), any())).thenAnswer(invocation -> {
            beforeWrite.run();
            Wrapper<Flow> guard = invocation.getArgument(1);
            // 按真实 Wrapper 的 WHERE 参数模拟单行 CAS，不把“返回 0”预先写死。
            if (!Objects.equals(conditionValue(guard, "id"), stored.getId())
                || !Objects.equals(conditionValue(guard, "status"), stored.getStatus())
                || !Objects.equals(conditionValue(guard, "version"), stored.getVersion())) {
                return 0;
            }
            applyEdit(invocation.getArgument(0));
            return 1;
        });
    }

    @AfterEach
    void editNeverTouchesRunsOrVersionSnapshots() {
        verifyNoInteractions(instances, versions);
    }

    static Stream<Arguments> forbiddenStatuses() {
        return Stream.of("DRAFT", "OFFLINE").flatMap(existing ->
            Stream.of("PUBLISHED", "OFFLINE", "INVALID", "published", "", " ")
                .map(requested -> Arguments.of(existing, requested)));
    }

    @ParameterizedTest(name = "{0} 编辑不可提交状态 [{1}]")
    @MethodSource("forbiddenStatuses")
    void rejectsClientLifecycleTransitionsBeforeMapperWrite(String existing, String requested) {
        stored.setStatus(existing);
        FlowBo bo = edit();
        bo.setStatus(requested);

        assertThrows(ServiceException.class, () -> service.updateByBo(bo));

        assertNoWrite();
        assertEquals(existing, stored.getStatus());
        assertEquals(7, stored.getVersion());
        assertEquals("旧图", stored.getConfigJson());
    }

    @ParameterizedTest
    @CsvSource({"DRAFT, 0", "DRAFT, 999", "OFFLINE, 6", "OFFLINE, 8"})
    void rejectsForgedOrStaleClientVersionBeforeMapperWrite(String existing, int requested) {
        stored.setStatus(existing);
        FlowBo bo = edit();
        bo.setVersion(requested);

        assertThrows(ServiceException.class, () -> service.updateByBo(bo));

        assertNoWrite();
        assertEquals(7, stored.getVersion());
        assertEquals("旧图", stored.getConfigJson());
    }

    static Stream<Arguments> editableRequests() {
        return Stream.of("DRAFT", "OFFLINE").flatMap(existing ->
            Stream.of(null, "DRAFT").flatMap(status ->
                Stream.of(false, true).map(includeVersion -> Arguments.of(existing, status, includeVersion))));
    }

    @ParameterizedTest(name = "编辑 {0}，请求状态 {1}，回传当前版本 {2}")
    @MethodSource("editableRequests")
    void savesDraftWithServerVersionAndBoundedCas(String existing, String requested, boolean includeVersion) {
        stored.setStatus(existing);
        FlowBo bo = edit();
        bo.setStatus(requested);
        bo.setVersion(includeVersion ? 7 : null);

        assertEquals(1, service.updateByBo(bo));

        assertEquals("DRAFT", stored.getStatus());
        assertEquals(7, stored.getVersion());
        assertEquals("新流程", stored.getFlowName());
        assertEquals("新草稿图", stored.getConfigJson());
        assertEquals(requested, bo.getStatus(), "服务端归一化不能改写客户端对象");
        assertEquals(includeVersion ? 7 : null, bo.getVersion());
        assertLockedReadAndSingleCas(existing, 7);
    }

    @Test
    void preservesLegacyNullVersionWithIsNullCas() {
        stored.setStatus("OFFLINE");
        stored.setVersion(null);

        assertEquals(1, service.updateByBo(edit()));

        assertEquals("DRAFT", stored.getStatus());
        assertNull(stored.getVersion(), "编辑不能创建或提升发布版本");
        assertLockedReadAndSingleCas("OFFLINE", null);
    }

    @Test
    void rejectsClientInventedVersionWhenStoredVersionIsNull() {
        stored.setVersion(null);
        FlowBo bo = edit();
        bo.setVersion(1);

        assertThrows(ServiceException.class, () -> service.updateByBo(bo));

        assertNoWrite();
        assertNull(stored.getVersion());
    }

    @Test
    void stillRejectsPublishedFlowBeforeMapperWrite() {
        stored.setStatus("PUBLISHED");

        assertThrows(ServiceException.class, () -> service.updateByBo(edit()));

        assertNoWrite();
        assertEquals("PUBLISHED", stored.getStatus());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"INVALID", ""})
    void rejectsUnknownPersistedLifecycleState(String status) {
        stored.setStatus(status);

        assertThrows(ServiceException.class, () -> service.updateByBo(edit()));

        assertNoWrite();
    }

    @Test
    void stillRejectsMissingFlow() {
        stored = null;

        assertThrows(ServiceException.class, () -> service.updateByBo(edit()));

        assertNoWrite();
    }

    @ParameterizedTest
    @CsvSource({"DRAFT, PUBLISHED, 7", "DRAFT, PUBLISHED, 8", "DRAFT, OFFLINE, 7", "OFFLINE, DRAFT, 7"})
    void rejectsStatusChangeBetweenLockedReadAndWrite(String original, String concurrent, int version) {
        stored.setStatus(original);
        beforeWrite = () -> {
            stored.setStatus(concurrent);
            stored.setVersion(version);
        };

        assertThrows(ServiceException.class, () -> service.updateByBo(edit()));

        assertEquals(concurrent, stored.getStatus(), "编辑不得覆盖刚发布/下线的状态");
        assertEquals(version, stored.getVersion());
        assertEquals("旧流程", stored.getFlowName());
        assertEquals("旧图", stored.getConfigJson());
        assertLockedReadAndSingleCas(original, 7);
    }

    @ParameterizedTest
    @CsvSource(value = {"DRAFT, 7, 8", "OFFLINE, 7, 8", "DRAFT, NULL, 0", "DRAFT, 0, NULL"}, nullValues = "NULL")
    void rejectsVersionChangeBetweenLockedReadAndWrite(String status, Integer original, Integer concurrent) {
        stored.setStatus(status);
        stored.setVersion(original);
        beforeWrite = () -> stored.setVersion(concurrent);

        assertThrows(ServiceException.class, () -> service.updateByBo(edit()));

        assertEquals(status, stored.getStatus());
        assertEquals(concurrent, stored.getVersion(), "包括 null 与 0 的变化，均不得覆盖");
        assertEquals("旧图", stored.getConfigJson());
        assertLockedReadAndSingleCas(status, original);
    }

    private FlowBo edit() {
        FlowBo bo = new FlowBo();
        bo.setId(ID);
        bo.setFlowName("新流程");
        bo.setConfigJson("新草稿图");
        bo.setStatus("DRAFT");
        return bo;
    }

    private Flow lockedSnapshot() {
        if (stored == null) {
            return null;
        }
        Flow snapshot = new Flow();
        snapshot.setId(stored.getId());
        snapshot.setTenantId(stored.getTenantId());
        snapshot.setStatus(stored.getStatus());
        snapshot.setVersion(stored.getVersion());
        return snapshot;
    }

    private void applyEdit(Flow entity) {
        if (entity.getStatus() != null) {
            stored.setStatus(entity.getStatus());
        }
        if (entity.getVersion() != null) {
            stored.setVersion(entity.getVersion());
        }
        stored.setFlowName(entity.getFlowName());
        stored.setConfigJson(entity.getConfigJson());
    }

    private void assertNoWrite() {
        verify(flows, never()).updateById(any(Flow.class));
        verify(flows, never()).update(any(Flow.class), any());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void assertLockedReadAndSingleCas(String originalStatus, Integer originalVersion) {
        ArgumentCaptor<Wrapper<Flow>> read = ArgumentCaptor.forClass(Wrapper.class);
        verify(flows, times(1)).selectOne(read.capture());
        assertTrue(read.getValue().getSqlSegment().contains("FOR UPDATE"), "生命周期校验必须在锁定读后执行");
        assertEquals(ID, conditionValue(read.getValue(), "id"));
        verify(flows, never()).selectById(anyLong());
        ArgumentCaptor<Flow> entity = ArgumentCaptor.forClass(Flow.class);
        ArgumentCaptor<Wrapper<Flow>> cas = ArgumentCaptor.forClass(Wrapper.class);
        verify(flows, times(1)).update(entity.capture(), cas.capture());
        assertEquals(ID, conditionValue(cas.getValue(), "id"));
        assertEquals(originalStatus, conditionValue(cas.getValue(), "status"));
        assertEquals(originalVersion, conditionValue(cas.getValue(), "version"));
        assertEquals("DRAFT", entity.getValue().getStatus());
        assertEquals(originalVersion, entity.getValue().getVersion());
        verify(flows, never()).updateById(any(Flow.class));
        verifyNoMoreInteractions(flows);
    }

    private Object conditionValue(Wrapper<Flow> wrapper, String column) {
        String sql = wrapper.getSqlSegment();
        if (sql.contains(column + " IS NULL")) {
            return null;
        }
        var parameter = Pattern.compile("\\b" + column + " = #\\{ew\\.paramNameValuePairs\\.(\\w+)\\}").matcher(sql);
        assertTrue(parameter.find(), "CAS 缺少字段条件 " + column + ": " + sql);
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs().get(parameter.group(1));
    }
}
