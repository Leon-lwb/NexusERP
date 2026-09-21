package com.nexus.mes.testsupport;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.linpeilie.Converter;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.redisson.api.RedissonClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * 单测静态工具引导
 * <p>
 * MapstructUtils / JsonUtils / RedisUtils 的静态初始化依赖 SpringUtils.getBean(无 Spring 上下文会抛错,
 * 且失败的 <clinit> 在本 JVM 内永久不可恢复), 纯 Mockito 单测须在首次触碰这些工具类之前
 * 激活 SpringUtils 静态桩:
 * ① ObjectMapper 用真实 Jackson 实例(JsonUtils 的序列化/反序列化走真实逻辑);
 * ② Converter 用通用桩——按同名属性拷贝(hutool BeanUtil), 与 MapStruct 生成器对测试实体的行为一致;
 * ③ RedissonClient 用桩实例, 使 RedisUtils 能安全完成静态初始化(否则测试无法
 * {@code mockStatic(RedisUtils.class)}, 报 "Cannot instrument class ... could not be initialized")。
 * </p>
 * <p>注意: getBean(Class) 声明在 hutool 的 SpringUtil(项目 SpringUtils 的父类), 静态桩必须
 * 注册在声明类上才能拦截(mockStatic 仅拦截被注册类自身声明的方法)。</p>
 * <p><b>为什么预热后必须立刻关闭静态桩</b>: Mockito 不允许同一线程对同一个类重复注册静态桩
 * ("static mocking is already registered in the current thread")。若此处常驻不关, 那些
 * 需要在方法内自建 {@code mockStatic(SpringUtil.class)} 的测试(如
 * {@code TgMaintenanceDueTaskTest}/{@code EqSchedulerFallbackTest}) 就会直接报错。
 * 预热完成后各工具类的静态字段已持有可用实例, 不再需要 SpringUtil 桩, 因此关闭是安全的。</p>
 * <p>JVM 级单例: 只预热一次。</p>
 *
 * @author Nexus
 */
public final class SpringStaticBootstrap {

    /** 依赖 Spring 容器、需要提前安全完成静态初始化的工具类(不在类路径上则静默跳过) */
    private static final String[] SPRING_DEPENDENT_UTILS = {
        "com.nexus.common.core.utils.MapstructUtils",
        "com.nexus.common.json.utils.JsonUtils",
        "com.nexus.common.redis.utils.RedisUtils",
    };

    private static volatile boolean active = false;

    private SpringStaticBootstrap() {
    }

    public static synchronized void ensureActive() {
        if (active) {
            return;
        }
        try (MockedStatic<SpringUtil> springMock = mockStatic(SpringUtil.class)) {
            Converter converter = mock(Converter.class);
            springMock.when(() -> SpringUtil.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());
            springMock.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(converter);
            springMock.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(mock(RedissonClient.class));
            Mockito.doAnswer(inv -> BeanUtil.toBean(inv.getArgument(0), (Class<?>) inv.getArgument(1)))
                .when(converter).convert(any(Object.class), any(Class.class));
            for (String className : SPRING_DEPENDENT_UTILS) {
                initializeQuietly(className);
            }
        }
        active = true;
    }

    /**
     * 触发指定类的静态初始化; 类不在测试类路径上时静默跳过。
     */
    private static void initializeQuietly(String className) {
        try {
            Class.forName(className, true, SpringStaticBootstrap.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            // 该工具类不在本模块测试类路径上, 无需预热
        } catch (Throwable t) {
            // 该工具类的 <clinit> 已在本 JVM 内失败过(NoClassDefFoundError / ExceptionInInitializerError),
            // 此处不能再向外抛: 否则一个坏掉的工具类会连锁打断本类之后的所有测试类。
            // 真正需要该工具类的用例会在首次使用时以更精确的断言失败。
            System.err.println("[SpringStaticBootstrap] 跳过无法初始化的工具类: " + className + " -> " + t);
        }
    }
}
