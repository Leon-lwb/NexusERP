package com.nexus.im.integration;

import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.web.core.BigNumberSerializer;
import com.nexus.im.push.ImEnvelopeHelper;
import com.nexus.im.support.ImTestFixture;
import com.nexus.common.web.config.JacksonConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3-9: P0-3 雪花 ID 序列化集成测试（可运行样板）。
 * <p>
 * P0-3 修复目标：{@code ImEnvelopeHelper} 使用 Spring 容器的 {@link ObjectMapper}（带
 * {@code BigNumberSerializer}）替代 fastjson2，将超出 JS 安全整数范围（2^53-1）的 Long
 * 序列化为字符串，避免前端 {@code JSON.parse} 精度丢失。
 * <p>
 * <b>为何放在集成测试而非单元测试</b>：{@link JsonUtils#OBJECT_MAPPER} 是 {@code static final}，
 * 类加载时通过 {@code SpringUtils.getBean(ObjectMapper.class)} 初始化。单元测试无 Spring 容器，
 * {@code SpringUtils.getBean} 返回 null 导致 {@code ExceptionInInitializerError}，
 * {@code mockStatic(JsonUtils.class)} 也无法 instrument {@code static final} 字段初始化。
 * 必须通过 {@link SpringBootTest} 启动完整容器，让 {@link SpringUtils} 注入 {@code ApplicationContext}
 * 后，{@link JsonUtils} 类初始化才能拿到带 {@code BigNumberSerializer} 的 {@link ObjectMapper}。
 * <p>
 * <b>配置策略</b>：使用 {@code @SpringBootTest(classes = TestConfig.class)} 限定只加载
 * {@link JacksonAutoConfiguration} + {@link JacksonConfig} + {@link SpringUtils}，
 * 不启动数据库/Redis/WebSocket 等重配置，启动 < 3s。
 * <p>
 * <b>默认不执行</b>：{@code @Tag("integration")} + ruoyi-im/pom.xml 的
 * {@code <excludedGroups>integration</excludedGroups>} 确保默认 {@code mvn test} 跳过。
 * 手动执行：{@code mvn -pl ruoyi-modules/ruoyi-im test -Dgroups=integration -DexcludedGroups=}。
 *
 * @author nexus
 */
@Tag("integration")
@SpringBootTest(classes = ImEnvelopeHelperIntegrationTest.TestConfig.class)
@DirtiesContext // 每个测试类启动独立上下文，避免 ObjectMapper 静态缓存污染
@DisplayName("P3-9: P0-3 雪花 ID 序列化集成测试")
class ImEnvelopeHelperIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 最小化 Spring 配置：只加载 Jackson 自动配置 + BigNumberSerializer 注册 + SpringUtils。
     * <p>排除数据库、Redis、MyBatis-Plus、Sa-Token、WebSocket 等重配置，启动 < 3s。
     * <p>注：必须用 @Configuration 而非 @TestConfiguration，因为 @SpringBootTest(classes=...)
     * 要求主配置类是 @Configuration / @SpringBootConfiguration，@TestConfiguration 不被识别为主配置。
     */
    @Configuration
    @ImportAutoConfiguration(JacksonAutoConfiguration.class)
    @Import({JacksonConfig.class, SpringUtils.class})
    static class TestConfig {
    }

    // ==================== P0-3 雪花 ID 序列化守护 ====================

    /**
     * P0-3: 雪花 ID（19 位长整型，超出 JS Number.MAX_SAFE_INTEGER）必须序列化为字符串。
     * <p>守护点：
     * <ul>
     *   <li>JSON 输出中 {@code "messageId"} 的值是字符串字面量 {@code "1900000000000000123"}</li>
     *   <li>不是数值 {@code 1900000000000000123}（会导致前端精度丢失为 1900000000000000000）</li>
     * </ul>
     * 若有人移除 {@code BigNumberSerializer} 或改回 fastjson2，此用例立即失败。
     */
    @Test
    @DisplayName("雪花 ID 序列化为字符串，避免 JS 精度丢失")
    void snowflakeId_serializedAsString_viaImEnvelopeHelper() {
        Long snowflakeId = ImTestFixture.snowflakeId(123L); // 1900000000000000123L

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", snowflakeId);

        // 完整路径：ImEnvelopeHelper.imMessage → JsonUtils.toJsonString → OBJECT_MAPPER
        String json = ImEnvelopeHelper.imMessage(payload);

        // 守护：序列化为字符串字面量
        assertThat(json).contains("\"messageId\":\"1900000000000000123\"");
        // 守护：不是数值（数值会被前端 JSON.parse 截断末尾）
        assertThat(json).doesNotContain("\"messageId\":1900000000000000123");
        // 守护：不是科学计数法
        assertThat(json).doesNotContain("\"messageId\":1.9");
        assertThat(json).doesNotContain("\"messageId\":1.90000000000000");
    }

    /**
     * P0-3: 多个 Long 字段同时序列化，所有雪花 ID 均转为字符串。
     * <p>守护点：{@code messageId}、{@code fromUserId}、{@code toUserId}、{@code groupId}
     * 都是 19 位雪花 ID 时，全部序列化为字符串。
     */
    @Test
    @DisplayName("多个雪花 ID 字段全部序列化为字符串")
    void multipleSnowflakeFields_allSerializedAsString() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", ImTestFixture.snowflakeId(0L));
        payload.put("fromUserId", ImTestFixture.snowflakeId(1L));
        payload.put("toUserId", ImTestFixture.snowflakeId(2L));
        payload.put("groupId", ImTestFixture.snowflakeId(3L));

        String json = ImEnvelopeHelper.imMessage(payload);

        // 守护：所有雪花 ID 都是字符串
        assertThat(json).contains("\"messageId\":\"" + ImTestFixture.snowflakeId(0L) + "\"");
        assertThat(json).contains("\"fromUserId\":\"" + ImTestFixture.snowflakeId(1L) + "\"");
        assertThat(json).contains("\"toUserId\":\"" + ImTestFixture.snowflakeId(2L) + "\"");
        assertThat(json).contains("\"groupId\":\"" + ImTestFixture.snowflakeId(3L) + "\"");
    }

    /**
     * P0-3: 范围内的 Long（≤ JS Number.MAX_SAFE_INTEGER）保持数值类型，不转字符串。
     * <p>守护点：普通 Long（如 123）序列化为数值 {@code 123}，不是字符串 {@code "123"}。
     * <p>这是为了兼容前端 {@code typeof total === 'number'} 的场景（如分页 total）。
     * 若有人误用 {@code ToStringSerializer} 替代 {@code BigNumberSerializer}，此用例立即失败。
     */
    @Test
    @DisplayName("范围内的 Long 保持数值类型（兼容前端 typeof === 'number'）")
    void smallLong_remainsAsNumber_notString() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", 123L); // 范围内
        payload.put("total", 100L); // 分页 total 兼容

        String json = ImEnvelopeHelper.imMessage(payload);

        // 守护：范围内 Long 保持数值
        assertThat(json).contains("\"messageId\":123");
        assertThat(json).doesNotContain("\"messageId\":\"123\"");
        assertThat(json).contains("\"total\":100");
        assertThat(json).doesNotContain("\"total\":\"100\"");
    }

    /**
     * P0-3: 直接验证 ObjectMapper Bean 注册了 BigNumberSerializer。
     * <p>守护点：{@link ObjectMapper#getRegisteredModuleIds()} 包含 bigNumberModule。
     * <p>这是 P0-3 修复的基础设施守护：如果 JacksonConfig 未加载，BigNumberSerializer 未注册，
     * 上面的序列化测试都会失败。此用例直接定位问题根因。
     */
    @Test
    @DisplayName("ObjectMapper Bean 注册了 BigNumberSerializer（bigNumberModule）")
    void objectMapper_hasBigNumberSerializerRegistered() throws Exception {
        // 守护：Long.class 的 serializer 是 BigNumberSerializer
        // 注：bigNumberModule 在 getRegisteredModuleIds() 中显示为 "SimpleModule-N"（默认 typeId），
        // 无法通过模块名精确匹配，改为直接检查 Long.class 的 serializer 类型。
        JsonSerializer<?> longSerializer = objectMapper.getSerializerProviderInstance()
            .findValueSerializer(Long.class);
        assertThat(longSerializer).isInstanceOf(BigNumberSerializer.class);
    }

    /**
     * P0-3: JsonUtils 静态字段 OBJECT_MAPPER 与容器中的 ObjectMapper 是同一实例。
     * <p>守护点：{@code JsonUtils.getObjectMapper() == @Autowired ObjectMapper}。
     * <p>若有人误改 JsonUtils 为 {@code new ObjectMapper()}（绕过 Spring 容器），
     * BigNumberSerializer 不会生效，此用例立即失败。
     */
    @Test
    @DisplayName("JsonUtils.OBJECT_MAPPER 使用容器管理的 ObjectMapper（非 new 出来的）")
    void jsonUtils_usesContainerManagedObjectMapper() {
        ObjectMapper staticMapper = JsonUtils.getObjectMapper();
        assertThat(staticMapper).isSameAs(objectMapper);
    }
}
