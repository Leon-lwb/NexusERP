package com.nexus.erp.testsupport;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.linpeilie.Converter;
import org.mockito.MockedStatic;
import org.redisson.api.RedissonClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * 单测静态工具引导（ERP 模块）。
 *
 * <p><b>为什么需要它</b>：{@code MapstructUtils.CONVERTER} 与 {@code JsonUtils.OBJECT_MAPPER}
 * 都是「静态 final 字段 + {@code SpringUtils.getBean(...)}」的写法，静态初始化强依赖 Spring 容器。
 * 纯 Mockito 单测没有容器，首次触碰即抛 {@code ExceptionInInitializerError}
 * （{@code cn.hutool.core.exceptions.UtilException: No ConfigurableListableBeanFactory or
 * ApplicationContext injected}）。而 JVM 对失败的 {@code <clinit>} 是<b>粘性</b>的：
 * 同一个 fork 内此后所有访问这两个工具类的测试都会变成
 * {@code NoClassDefFoundError: Could not initialize class ...}，
 * 于是「一个测试踩雷，整模块测试全崩」。</p>
 *
 * <p><b>做法</b>：在 {@code SpringUtil} 静态桩窗口内，主动触发这些工具类的静态初始化，
 * 然后<b>立即关闭</b>静态桩（与 MES 模块的
 * {@code com.nexus.mes.testsupport.SpringStaticBootstrap} 思路一致，但这里不常驻，
 * 避免改变既有测试的静态桩语义）：</p>
 * <ul>
 *   <li>{@code Converter} 用「按同名属性拷贝」（hutool {@code BeanUtil}）的通用实现，
 *       与 MapStruct 生成器对测试实体的行为一致；</li>
 *   <li>{@code ObjectMapper} 用真实 Jackson 实例，保证 JsonUtils 走真实序列化逻辑。</li>
 * </ul>
 *
 * <p>注意：{@code getBean(Class)} 声明在 hutool 的 {@code SpringUtil}（项目 {@code SpringUtils} 的父类）
 * 上，静态桩必须注册在<b>声明类</b>上才能拦截。</p>
 *
 * <p>JUnit 5 下由 {@link SpringStaticBootstrapExtension} 自动为每个测试类预热，
 * 因此不依赖测试类的执行顺序（surefire 默认按文件系统顺序，顺序变化不会再引发雪崩）。</p>
 *
 * @author Nexus
 */
public final class SpringStaticBootstrap {

    /**
     * 依赖 Spring 容器、需要提前安全完成静态初始化的工具类。
     * 不在当前模块测试类路径上的（例如未引入 ruoyi-common-json）会被静默跳过。
     */
    private static final String[] SPRING_DEPENDENT_UTILS = {
        "com.nexus.common.core.utils.MapstructUtils",
        "com.nexus.common.json.utils.JsonUtils",
        "com.nexus.common.redis.utils.RedisUtils",
    };

    private static volatile boolean warmed = false;

    private SpringStaticBootstrap() {
    }

    /**
     * 在 {@code SpringUtil} 静态桩窗口内完成 Spring 依赖工具类的静态初始化，随后立即关闭静态桩。
     * <p>幂等；必须在任何测试触碰这些工具类之前调用。</p>
     */
    public static synchronized void ensureActive() {
        if (warmed) {
            return;
        }
        try (MockedStatic<SpringUtil> springMock = mockStatic(SpringUtil.class)) {
            Converter converter = new Converter() {
                @Override
                public <S, T> T convert(S source, Class<T> targetType) {
                    return BeanUtil.copyProperties(source, targetType);
                }
            };
            springMock.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(converter);
            springMock.when(() -> SpringUtil.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());
            // RedisUtils.CLIENT = SpringUtils.getBean(RedissonClient.class)：
            // 其静态初始化同样依赖 Spring 容器，而「无法静态打桩一个初始化失败的类」
            // （Mockito: Cannot instrument class ... because it or one of its supertypes could not be initialized），
            // 因此先用桩 Client 让它安全完成初始化，之后测试才能 mockStatic(RedisUtils.class)。
            springMock.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(mock(RedissonClient.class));
            for (String className : SPRING_DEPENDENT_UTILS) {
                initializeQuietly(className);
            }
        }
        warmed = true;
    }

    /**
     * 触发指定类的静态初始化；类不在测试类路径上时静默跳过。
     */
    private static void initializeQuietly(String className) {
        try {
            Class.forName(className, true, SpringStaticBootstrap.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            // 该工具类不在本模块测试类路径上，无需预热
        }
    }
}
