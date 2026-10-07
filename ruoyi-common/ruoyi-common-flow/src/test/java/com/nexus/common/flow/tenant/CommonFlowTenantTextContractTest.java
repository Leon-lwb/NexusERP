package com.nexus.common.flow.tenant;

import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.domain.entity.FlowInstance;
import com.nexus.common.flow.domain.entity.FlowInstanceToFlowInstanceVoMapperImpl;
import com.nexus.common.flow.domain.entity.FlowNodeRun;
import com.nexus.common.flow.domain.entity.FlowNodeRunToFlowNodeRunVoMapperImpl;
import com.nexus.common.flow.domain.entity.FlowToFlowVoMapperImpl;
import com.nexus.common.flow.domain.entity.FlowVersion;
import com.nexus.common.flow.domain.entity.FlowVersionToFlowVersionVoMapperImpl;
import com.nexus.common.flow.domain.vo.FlowInstanceVo;
import com.nexus.common.flow.domain.vo.FlowInstanceVoToFlowInstanceMapperImpl;
import com.nexus.common.flow.domain.vo.FlowNodeRunVo;
import com.nexus.common.flow.domain.vo.FlowNodeRunVoToFlowNodeRunMapperImpl;
import com.nexus.common.flow.domain.vo.FlowVersionVo;
import com.nexus.common.flow.domain.vo.FlowVersionVoToFlowVersionMapperImpl;
import com.nexus.common.flow.domain.vo.FlowVo;
import com.nexus.common.flow.domain.vo.FlowVoToFlowMapperImpl;
import com.nexus.common.tenant.core.TenantEntity;
import io.github.linpeilie.BaseMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** 四类流程VO真实MapStruct转换契约：不连接数据库、不迁移旧数据、不替换生成的映射逻辑。 */
class CommonFlowTenantTextContractTest {
    private static final long SNOWFLAKE_ID = 2104385976564420610L;

    private record Mapping(Class<? extends TenantEntity> entityType, Class<?> voType,
                           BaseMapper<?, ?> toVo, BaseMapper<?, ?> toEntity, List<String> businessIds) {
        @Override
        public String toString() {
            return voType.getSimpleName();
        }
    }

    static Stream<Mapping> mappings() {
        // 直接实例化本次编译生成的mapper实现，测试不自行编写String/Long转换。
        return Stream.of(
            new Mapping(Flow.class, FlowVo.class, new FlowToFlowVoMapperImpl(), new FlowVoToFlowMapperImpl(),
                List.of("id", "createBy")),
            new Mapping(FlowInstance.class, FlowInstanceVo.class, new FlowInstanceToFlowInstanceVoMapperImpl(),
                new FlowInstanceVoToFlowInstanceMapperImpl(), List.of("id", "flowId", "triggerBy")),
            new Mapping(FlowNodeRun.class, FlowNodeRunVo.class, new FlowNodeRunToFlowNodeRunVoMapperImpl(),
                new FlowNodeRunVoToFlowNodeRunMapperImpl(), List.of("id", "instanceId", "flowId")),
            new Mapping(FlowVersion.class, FlowVersionVo.class, new FlowVersionToFlowVersionVoMapperImpl(),
                new FlowVersionVoToFlowVersionMapperImpl(), List.of("id", "flowId", "createBy"))
        );
    }

    static Stream<Arguments> exactTenantCases() {
        return mappings().flatMap(mapping -> Stream.of("000000", "001234", "factory_A", "0")
            .map(tenant -> Arguments.of(mapping, tenant)));
    }

    @ParameterizedTest(name = "{0} 真实映射租户 [{1}] 不取整、不升级历史0")
    @MethodSource("exactTenantCases")
    void generatedMapperPreservesTenantTextAndSnowflakeIds(Mapping mapping, String tenant) throws Exception {
        TenantEntity entity = mapping.entityType().getDeclaredConstructor().newInstance();
        entity.setTenantId(tenant);
        for (String field : mapping.businessIds()) {
            mapping.entityType().getMethod(setter(field), Long.class).invoke(entity, SNOWFLAKE_ID);
        }

        Object vo = assertDoesNotThrow(() -> convert(mapping.toVo(), entity),
            "文本租户必须通过真实生成mapper，不能抛NumberFormatException");
        assertNotNull(vo);
        assertBusinessLongIds(mapping, vo);
        Object returnedTenant = mapping.voType().getMethod("getTenantId").invoke(vo);
        assertEquals(tenant, returnedTenant, "000000、前导零、文本租户和旧0都必须原样返回String");

        TenantEntity restored = (TenantEntity) assertDoesNotThrow(() -> convert(mapping.toEntity(), vo),
            "反向生成mapper也必须保持精确文本身份");
        assertEquals(tenant, restored.getTenantId());
        assertEquals(tenant, entity.getTenantId(), "转换不能改写原实体历史归属");
        for (String field : mapping.businessIds()) {
            assertEquals(SNOWFLAKE_ID, mapping.entityType().getMethod(getter(field)).invoke(restored));
        }
    }

    @ParameterizedTest(name = "{0} 只有tenantId改为String，业务雪花ID仍为Long")
    @MethodSource("mappings")
    void tenantIsTextWhileBusinessIdTypesStayLong(Mapping mapping) throws Exception {
        assertEquals(String.class, mapping.entityType().getMethod("getTenantId").getReturnType());
        assertEquals(String.class, mapping.voType().getDeclaredField("tenantId").getType());
        for (String field : mapping.businessIds()) {
            assertEquals(Long.class, mapping.voType().getDeclaredField(field).getType());
            assertEquals(Long.class, mapping.entityType().getMethod(getter(field)).getReturnType());
        }
    }

    private void assertBusinessLongIds(Mapping mapping, Object vo) throws Exception {
        for (String field : mapping.businessIds()) {
            assertEquals(Long.class, mapping.voType().getDeclaredField(field).getType());
            assertEquals(SNOWFLAKE_ID, mapping.voType().getMethod(getter(field)).invoke(vo),
                "大雪花ID不得为解决租户问题改成其他类型或截断");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object convert(BaseMapper mapper, Object source) {
        return mapper.convert(source);
    }

    private static String getter(String field) {
        return "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }

    private static String setter(String field) {
        return "set" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }
}
