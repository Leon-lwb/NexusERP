package com.nexus.erp.prd;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 仅测试使用的 3.5.16 全局状态快照；字段已按本地 jar 核实，版本漂移直接失败，不能静默跳过。
 * 初始化替换缓存条目而不修改原条目，故保存原对象引用；使用者必须由 @Isolated 串行隔离。
 */
final class PrdProductionPlanMapperGlobalState implements AutoCloseable {
    private record Cache(String name, Map<Object, Object> live, Map<Object, Object> saved) { }

    private final List<Cache> caches;
    private final IdentifierGenerator generator;
    private boolean closed;

    private PrdProductionPlanMapperGlobalState() {
        caches = List.of(
            cache(TableInfoHelper.class, "TABLE_INFO_CACHE"),
            cache(TableInfoHelper.class, "TABLE_NAME_INFO_CACHE"),
            cache(LambdaUtils.class, "COLUMN_CACHE_MAP"),
            cache(GlobalConfigUtils.class, "GLOBAL_CONFIG")
        );
        generator = (IdentifierGenerator) value(IdWorker.class, "IDENTIFIER_GENERATOR");
    }

    static PrdProductionPlanMapperGlobalState capture() {
        return new PrdProductionPlanMapperGlobalState();
    }

    @Override
    public void close() {
        if (closed) return;
        try {
            for (Cache cache : caches) {
                cache.live().clear();
                cache.live().putAll(cache.saved());
            }
        } finally {
            IdWorker.setIdentifierGenerator(generator);
            closed = true;
        }
    }

    void assertRestored() {
        List<Executable> checks = new ArrayList<>();
        checks.add(() -> assertSame(generator, value(IdWorker.class, "IDENTIFIER_GENERATOR"),
            "必须恢复前一工厂的原 IdentifierGenerator 对象"));
        for (Cache cache : caches) {
            checks.add(() -> assertEquals(cache.saved().keySet(), cache.live().keySet(),
                cache.name() + " 必须移除本夹具条目并保留原键集"));
            cache.saved().forEach((key, expected) -> checks.add(() -> assertSame(expected, cache.live().get(key),
                cache.name() + " 必须恢复原条目对象：" + key)));
        }
        assertAll("同 JVM 清理后全局缓存与生成器必须完整不变", checks);
    }

    @SuppressWarnings("unchecked")
    private static Cache cache(Class<?> owner, String name) {
        Map<Object, Object> live = (Map<Object, Object>) value(owner, name);
        return new Cache(owner.getSimpleName() + "." + name, live, new HashMap<>(live));
    }

    private static Object value(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("本地 3.5.16 测试隔离字段不可读：" + owner.getName() + "." + name, failure);
        }
    }
}
