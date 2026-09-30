package com.nexus.erp.prd;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.bo.PrdCapacityCalendarBo;
import com.nexus.erp.prd.domain.vo.PrdCapacityCalendarVo;
import io.github.linpeilie.ConvertException;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.BeanWrapperImpl;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * 不依赖 Spring Boot、服务层或数据库，验证真实生成的产能日历映射。
 */
class PrdCapacityCalendarMappingTest {

    private static final long SHIFT_ID = 2104809497157365761L;
    private static final BigDecimal HOURS = new BigDecimal("8.00");
    private static final BigDecimal ZERO_HOURS = new BigDecimal("0.00");
    private static final BigDecimal EFFICIENCY = new BigDecimal("100.00");
    private static Class<?> realMapstructUtils;

    @BeforeAll
    static void bootstrapRealMapstructUtils() throws ClassNotFoundException {
        /**
         * ERP 自动扩展会预先给 MapstructUtils 装入基于 BeanUtil 的 Converter。
         * 仅隔离此工具类的静态最终字段（{@code static final}）；实体与生成的映射器
         * 保持原有类身份。只模拟组件查找，不模拟转换器或其映射关系。
         */
        String utilityName = MapstructUtils.class.getName();
        ClassLoader parent = MapstructUtils.class.getClassLoader();
        ClassLoader isolated = new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!utilityName.equals(name)) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        String resource = name.replace('.', '/') + ".class";
                        try (InputStream input = parent.getResourceAsStream(resource)) {
                            if (input == null) {
                                throw new ClassNotFoundException(name);
                            }
                            byte[] bytes = input.readAllBytes();
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException exception) {
                            throw new ClassNotFoundException(name, exception);
                        }
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
        };
        Converter converter = new Converter();
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(converter);
            realMapstructUtils = Class.forName(utilityName, true, isolated);
        }
    }

    @Test
    void entityToVoPreservesCalendarValuesAndSource() {
        PrdCapacityCalendar source = calendar();
        String before = source.toString();

        PrdCapacityCalendarVo vo = assertDoesNotThrow(() -> convert(source, PrdCapacityCalendarVo.class));

        assertNotNull(vo);
        assertCalendarValues(vo);
        assertEquals(before, source.toString(), "Mapping must not change the source calendar");
    }

    @Test
    void nonEmptyListToVoUsesRealGeneratedMapping() {
        PrdCapacityCalendar source = calendar();
        List<PrdCapacityCalendar> rows = List.of(source);
        String before = source.toString();

        List<PrdCapacityCalendarVo> result = assertDoesNotThrow(
            () -> convertList(rows, PrdCapacityCalendarVo.class));

        assertEquals(1, result.size());
        assertCalendarValues(result.get(0));
        assertSame(source, rows.get(0), "Reading a VO must not replace the selected entity");
        assertEquals(before, source.toString());
    }

    @Test
    void boToEntityPreservesSubmittedValuesAndSource() {
        PrdCapacityCalendarBo source = bo();
        String before = source.toString();

        PrdCapacityCalendar result = assertDoesNotThrow(() -> convert(source, PrdCapacityCalendar.class));

        assertBoValues(result);
        assertEquals(source.getRemark(), result.getRemark());
        assertEquals(before, source.toString(), "Mapping must not change the submitted BO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"000000", "tenantA01"})
    void boCopyPreservesExistingTenantAndUnsubmittedCalendarIdentity(String tenantId) {
        PrdCapacityCalendar target = calendar();
        setStringTenant(target, tenantId);
        PrdCapacityCalendarBo source = bo();
        String before = source.toString();

        PrdCapacityCalendar result = assertDoesNotThrow(() -> copy(source, target));

        assertSame(target, result);
        assertBoValues(result);
        assertEquals(source.getRemark(), result.getRemark());
        assertEquals(tenantId, result.getTenantId(), "The BO has no tenant field to overwrite the tenant");
        assertEquals("WORKSTATION16", result.getObjectCode());
        assertEquals("锻造机", result.getObjectName());
        assertEquals("PBJH2026001-1", result.getShiftCode());
        assertEquals("白班", result.getShiftName());
        assertEquals(ZERO_HOURS, result.getPlannedHours());
        assertEquals(ZERO_HOURS, result.getActualHours());
        assertEquals(HOURS, result.getEffectiveHours());
        assertEquals(HOURS, result.getRemainingHours());
        assertEquals("0", result.getDelFlag());
        assertEquals(before, source.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"000000", "tenantA01"})
    void entityAndVoRoundTripPreservesStringTenant(String tenantId) {
        PrdCapacityCalendar source = calendar();
        setStringTenant(source, tenantId);
        String before = source.toString();

        PrdCapacityCalendarVo vo = assertDoesNotThrow(() -> convert(source, PrdCapacityCalendarVo.class));

        assertEquals(String.class, new BeanWrapperImpl(vo).getPropertyType("tenantId"));
        assertEquals(tenantId, vo.getTenantId());
        assertCalendarValues(vo);
        PrdCapacityCalendar roundTrip = assertDoesNotThrow(() -> convert(vo, PrdCapacityCalendar.class));
        assertEquals(tenantId, roundTrip.getTenantId());
        assertBoValues(roundTrip);
        assertEquals(source.getObjectCode(), roundTrip.getObjectCode());
        assertEquals(source.getObjectName(), roundTrip.getObjectName());
        assertEquals(source.getShiftCode(), roundTrip.getShiftCode());
        assertEquals(source.getShiftName(), roundTrip.getShiftName());
        assertEquals(source.getPlannedHours(), roundTrip.getPlannedHours());
        assertEquals(source.getActualHours(), roundTrip.getActualHours());
        assertEquals(source.getEffectiveHours(), roundTrip.getEffectiveHours());
        assertEquals(source.getRemainingHours(), roundTrip.getRemainingHours());
        assertEquals(before, source.toString());
    }

    @Test
    void emptyListDoesNotRequireAnEntityMapping() {
        List<PrdCapacityCalendarVo> result = assertDoesNotThrow(
            () -> convertList(List.<PrdCapacityCalendar>of(), PrdCapacityCalendarVo.class));

        assertTrue(result.isEmpty(), "An empty read can hide a missing generated mapping");
    }

    @Test
    void boMappingDoesNotGenerateUnrequestedReverseConversion() {
        assertThrows(ConvertException.class, () -> convert(calendar(), PrdCapacityCalendarBo.class));
    }

    private static PrdCapacityCalendar calendar() {
        PrdCapacityCalendar row = new PrdCapacityCalendar();
        row.setCalendarId(1L);
        row.setCalendarDate(calendarDate());
        row.setObjectType("WORKSTATION");
        row.setObjectId(16L);
        row.setObjectCode("WORKSTATION16");
        row.setObjectName("锻造机");
        row.setShiftId(SHIFT_ID);
        row.setShiftCode("PBJH2026001-1");
        row.setShiftName("白班");
        row.setAvailableHours(HOURS);
        row.setPlannedHours(ZERO_HOURS);
        row.setActualHours(ZERO_HOURS);
        row.setEfficiency(EFFICIENCY);
        row.setEffectiveHours(HOURS);
        row.setRemainingHours(HOURS);
        row.setStatus("ACTIVE");
        row.setRemark("calendar mapping regression");
        row.setCreateDept(10L);
        row.setCreateBy(20L);
        row.setCreateTime(calendarDate());
        row.setUpdateBy(30L);
        row.setUpdateTime(calendarDate());
        row.setDelFlag("0");
        return row;
    }

    private static PrdCapacityCalendarBo bo() {
        PrdCapacityCalendarBo bo = new PrdCapacityCalendarBo();
        bo.setCalendarId(1L);
        bo.setCalendarDate(calendarDate());
        bo.setObjectType("WORKSTATION");
        bo.setObjectId(16L);
        bo.setShiftId(SHIFT_ID);
        bo.setAvailableHours(HOURS);
        bo.setEfficiency(EFFICIENCY);
        bo.setStatus("ACTIVE");
        bo.setRemark("calendar mapping regression");
        bo.setCreateDept(10L);
        bo.setCreateBy(20L);
        bo.setCreateTime(calendarDate());
        bo.setUpdateBy(30L);
        bo.setUpdateTime(calendarDate());
        return bo;
    }

    private static Date calendarDate() {
        return Date.from(LocalDate.of(2026, 9, 30).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
    }

    private static void setStringTenant(Object bean, String tenantId) {
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);
        /**
         * 同时兼容错误的 Long 类型模型与正确的 String 类型模型，保证测试可以编译。
         * 测试失败必须来自断言，而不能来自调用 {@code setTenantId(String)} 时的 javac 编译错误。
         */
        assertEquals(String.class, wrapper.getPropertyType("tenantId"), "Match the existing varchar tenant column");
        assertDoesNotThrow(() -> wrapper.setPropertyValue("tenantId", tenantId));
    }

    private static void assertCalendarValues(PrdCapacityCalendarVo vo) {
        assertAll(
            () -> assertEquals(1L, vo.getCalendarId()),
            () -> assertEquals(calendarDate(), vo.getCalendarDate()),
            () -> assertEquals("WORKSTATION", vo.getObjectType()),
            () -> assertEquals(16L, vo.getObjectId()),
            () -> assertEquals("WORKSTATION16", vo.getObjectCode()),
            () -> assertEquals("锻造机", vo.getObjectName()),
            () -> assertEquals(SHIFT_ID, vo.getShiftId()),
            () -> assertEquals("PBJH2026001-1", vo.getShiftCode()),
            () -> assertEquals("白班", vo.getShiftName()),
            () -> assertEquals(HOURS, vo.getAvailableHours()),
            () -> assertEquals(ZERO_HOURS, vo.getPlannedHours()),
            () -> assertEquals(ZERO_HOURS, vo.getActualHours()),
            () -> assertEquals(EFFICIENCY, vo.getEfficiency()),
            () -> assertEquals(HOURS, vo.getEffectiveHours()),
            () -> assertEquals(HOURS, vo.getRemainingHours()),
            () -> assertEquals("ACTIVE", vo.getStatus()),
            () -> assertEquals(10L, vo.getCreateDept()),
            () -> assertEquals(20L, vo.getCreateBy()),
            () -> assertEquals(calendarDate(), vo.getCreateTime()),
            () -> assertEquals(30L, vo.getUpdateBy()),
            () -> assertEquals(calendarDate(), vo.getUpdateTime()));
    }

    private static void assertBoValues(PrdCapacityCalendar row) {
        assertAll(
            () -> assertEquals(1L, row.getCalendarId()),
            () -> assertEquals(calendarDate(), row.getCalendarDate()),
            () -> assertEquals("WORKSTATION", row.getObjectType()),
            () -> assertEquals(16L, row.getObjectId()),
            () -> assertEquals(SHIFT_ID, row.getShiftId()),
            () -> assertEquals(HOURS, row.getAvailableHours()),
            () -> assertEquals(EFFICIENCY, row.getEfficiency()),
            () -> assertEquals("ACTIVE", row.getStatus()),
            () -> assertEquals(10L, row.getCreateDept()),
            () -> assertEquals(20L, row.getCreateBy()),
            () -> assertEquals(calendarDate(), row.getCreateTime()),
            () -> assertEquals(30L, row.getUpdateBy()),
            () -> assertEquals(calendarDate(), row.getUpdateTime()));
    }

    private static <T> T convert(Object source, Class<T> targetType) throws Throwable {
        return targetType.cast(invoke(new Class<?>[]{Object.class, Class.class}, source, targetType));
    }

    @SuppressWarnings("unchecked")
    private static <S, T> List<T> convertList(List<S> source, Class<T> targetType) throws Throwable {
        return (List<T>) invoke(new Class<?>[]{List.class, Class.class}, source, targetType);
    }

    @SuppressWarnings("unchecked")
    private static <S, T> T copy(S source, T target) throws Throwable {
        return (T) invoke(new Class<?>[]{Object.class, Object.class}, source, target);
    }

    private static Object invoke(Class<?>[] parameterTypes, Object source, Object target) throws Throwable {
        Method method = realMapstructUtils.getMethod("convert", parameterTypes);
        try {
            return method.invoke(null, source, target);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }
}
