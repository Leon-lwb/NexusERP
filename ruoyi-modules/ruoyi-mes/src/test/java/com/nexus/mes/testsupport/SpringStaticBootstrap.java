package com.nexus.mes.testsupport;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.linpeilie.Converter;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * 单测静态工具引导
 * <p>
 * MapstructUtils / JsonUtils 的静态初始化依赖 SpringUtils.getBean(无 Spring 上下文会抛错,
 * 且失败的 <clinit> 在本 JVM 内永久不可恢复), 纯 Mockito 单测须在首次触碰这两个工具类之前
 * 激活 SpringUtils 静态桩:
 * ① ObjectMapper 用真实 Jackson 实例(JsonUtils 的序列化/反序列化走真实逻辑);
 * ② Converter 用通用桩——按同名属性拷贝(hutool BeanUtil), 与 MapStruct 生成器对测试实体的行为一致。
 * </p>
 * <p>注意: getBean(Class) 声明在 hutool 的 SpringUtil(项目 SpringUtils 的父类), 静态桩必须
 * 注册在声明类上才能拦截(mockStatic 仅拦截被注册类自身声明的方法)。</p>
 * <p>JVM 级单例: 激活后不关闭(测试 JVM 退出即释放), 保证多个测试类共享同一份静态初始化。</p>
 *
 * @author Nexus
 */
public final class SpringStaticBootstrap {

    private static volatile boolean active = false;

    private SpringStaticBootstrap() {
    }

    public static synchronized void ensureActive() {
        if (active) {
            return;
        }
        MockedStatic<SpringUtil> springMock = mockStatic(SpringUtil.class);
        Converter converter = mock(Converter.class);
        springMock.when(() -> SpringUtil.getBean(ObjectMapper.class)).thenReturn(new ObjectMapper());
        springMock.when(() -> SpringUtil.getBean(Converter.class)).thenReturn(converter);
        Mockito.doAnswer(inv -> BeanUtil.toBean(inv.getArgument(0), (Class<?>) inv.getArgument(1)))
            .when(converter).convert(any(Object.class), any(Class.class));
        // 故意不关闭: 保持激活使后续测试类首次触碰 MapstructUtils/JsonUtils 时静态初始化同样安全
        active = true;
    }
}

