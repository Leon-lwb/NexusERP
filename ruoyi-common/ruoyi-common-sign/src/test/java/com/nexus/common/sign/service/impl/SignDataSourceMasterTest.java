package com.nexus.common.sign.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.processor.DsProcessor;
import com.baomidou.dynamic.datasource.support.DataSourceClassResolver;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.common.sign.service.IESSealGrantService;
import com.nexus.common.sign.service.IESSealService;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 签章模块「数据库访问固定到 master 数据源」守卫。
 *
 * <p><b>为什么需要它：</b>es_seal / es_seal_grant / es_sign_document / es_signature 四张表
 * <b>只存在于 ry-vue 库</b>（主数据源 {@code master}，见 {@code application-dev.yml} 的
 * {@code dynamic.datasource.primary: master}），而 {@code @DS} 是<b>线程级上下文栈</b>：
 * 本模块若不自行固定，就会继承调用方的数据源。线上实测
 * （{@code POST /mes/sop/view-log}）：MES 的 {@code SopViewLogServiceImpl} 类级
 * {@code @DS("mes")} 把上下文压成 mes，签章服务随后打到 ry-mes 库，报
 * {@code Table 'ry-mes.es_signature' doesn't exist}；又因为 SOP 查阅是
 * 「降级不阻断」，接口照常 200、{@code sop_view_log} 照常落库，只有
 * {@code signature_id} 静默留空 —— 整条功能链全损却无人发现。</p>
 *
 * <p><b>为什么普通单测抓不到、本测试却能抓：</b>没有数据源连接，无法断言「SQL 打到哪个库」。
 * 但「用哪个数据源」在 dynamic-datasource 里完全由<b>注解解析</b>决定
 * （{@code DynamicDataSourceAnnotationInterceptor} → {@code DataSourceClassResolver}），
 * 而解析器本身是纯反射、无 IO 的。故本测试直接调用 dynamic-datasource <b>自己的</b>解析器
 * 与拦截器（不是另写一套断言逻辑），断言的是框架真实会用到的那个值。</p>
 *
 * <p><b>本测试锁定的设计决策：</b>三个 impl 用<b>类级</b> {@code @DS("master")}
 * 而非逐方法标注 —— 类级注解对每个经代理调用的公开方法都生效，后续新增方法不会漏挂
 * （见 {@link #classLevelDsIsMaster()} 与 {@link #everyPublicEntryPointResolvesToMaster()}）。
 * 同时锁定「Spring Boot 默认 CGLIB 代理」这一前提：类级注解的可见性依赖
 * {@code ClassUtils.getUserClass()} 能把代理类还原成 impl 类，JDK 动态代理做不到
 * （见 {@link #resolvesToMasterThroughCglibProxy()}）。</p>
 *
 * <p><b>边界声明：</b>本测试证明「注解声明正确 + 框架会解析出 master」，<b>不</b>证明
 * 「跨源事务内切换一定成功」。{@code @Transactional} 一旦在异库上绑定连接，
 * 事务内任何 {@code @DS} 都切不动（先例：{@code MessageRecordService} 需
 * {@code @DS("master") + REQUIRES_NEW}）。{@link ESignatureService#sign} 的 REQUIRED
 * 语义正是该边界，其约束与处置已写入 {@code ESignatureServiceImpl} 类注释。</p>
 *
 * @author nexus
 */
class SignDataSourceMasterTest {

    /** 签章库所在数据源名：ry-vue，即 {@code dynamic.datasource.primary: master} */
    private static final String MASTER = "master";

    /** 「调用方已把上下文压成 mes」——线上故障现场（MES 模块类级 @DS("mes")） */
    private static final String CALLER_DS = "mes";

    /** 三个 impl 及其对外接口（接口上的方法即「外部经代理调用的公开入口」） */
    private static final List<Entry> ENTRIES = List.of(
        new Entry(ESignatureServiceImpl.class, ESignatureService.class),
        new Entry(EsSealServiceImpl.class, IESSealService.class),
        new Entry(EsSealGrantServiceImpl.class, IESSealGrantService.class)
    );

    private record Entry(Class<?> implClass, Class<?> apiInterface) {
    }

    @AfterEach
    void tearDown() {
        // 上下文栈是 ThreadLocal：测试用例也必须自己清干净，否则污染后续用例
        DynamicDataSourceContextHolder.clear();
    }

    @Test
    @DisplayName("反射守卫: 三个签章 Service impl 必须带类级 @DS(\"master\")（删掉即回归「继承调用方数据源」）")
    void classLevelDsIsMaster() {
        List<String> missing = new ArrayList<>();
        List<String> wrongValue = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            DS ds = AnnotatedElementUtils.findMergedAnnotation(entry.implClass(), DS.class);
            if (ds == null) {
                missing.add(entry.implClass().getSimpleName());
            } else if (!MASTER.equals(ds.value())) {
                wrongValue.add(entry.implClass().getSimpleName() + " -> " + ds.value());
            }
        }
        assertTrue(missing.isEmpty(),
            "以下 impl 缺少类级 @DS：数据源会继承调用方上下文，"
                + "在 MES/ERP 上下文下打到业务库并报「表不存在」，而 SOP 查阅等场景会静默降级成 signature_id=NULL。缺失: "
                + missing);
        assertTrue(wrongValue.isEmpty(),
            "签章四表只存在于 ry-vue 库，@DS 值必须是 \"" + MASTER + "\"，实际: " + wrongValue);
    }

    @Test
    @DisplayName("有效数据源守卫: 接口上的每个公开入口经 dynamic-datasource 自己的解析器都必须解析为 master")
    void everyPublicEntryPointResolvesToMaster() {
        DataSourceClassResolver resolver = new DataSourceClassResolver(true);
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (Entry entry : ENTRIES) {
            Object target = newImpl(entry.implClass());
            Method[] apiMethods = entry.apiInterface().getDeclaredMethods();
            assertTrue(apiMethods.length > 0,
                entry.apiInterface().getSimpleName() + " 未声明任何方法，本断言形同虚设");
            for (Method apiMethod : apiMethods) {
                checked++;
                String key = resolver.findKey(apiMethod, target, DS.class);
                if (!MASTER.equals(key)) {
                    failures.add(entry.implClass().getSimpleName() + "#" + apiMethod.getName()
                        + " 解析为 '" + key + "'");
                }
            }
        }
        assertTrue(checked >= 17, "应覆盖三个接口的全部公开入口，实际只检查了 " + checked + " 个");
        assertTrue(failures.isEmpty(),
            "以下公开入口未解析到 " + MASTER + " 数据源（空串 = 回落到调用方上下文，"
                + "正是本次线上故障的成因）: " + failures);
    }

    @Test
    @DisplayName("代理前提守卫: Spring Boot 默认 CGLIB 代理(proxyTargetClass=true)下同样解析为 master")
    void resolvesToMasterThroughCglibProxy() {
        DataSourceClassResolver resolver = new DataSourceClassResolver(true);
        for (Entry entry : ENTRIES) {
            Object proxy = cglibProxy(newImpl(entry.implClass()));
            assertTrue(AopUtils.isCglibProxy(proxy),
                "本仓库 Spring Boot 默认 proxyTargetClass=true，签章服务应被 CGLIB 代理；"
                    + "类级 @DS 的可见性正依赖 CGLIB 子类能被还原成 impl 类，JDK 动态代理下会失效");
            for (Method apiMethod : entry.apiInterface().getDeclaredMethods()) {
                assertEquals(MASTER, resolver.findKey(apiMethod, proxy, DS.class),
                    "CGLIB 代理下 " + entry.implClass().getSimpleName() + "#" + apiMethod.getName()
                        + " 必须仍解析为 " + MASTER);
            }
        }
    }

    @Test
    @DisplayName("上下文覆盖守卫: 调用方已压入 mes 时，签章入口仍把 master 压在其上（复现线上故障现场）")
    void interceptorOverridesCallerContextWithMaster() {
        DynamicDataSourceAnnotationInterceptor interceptor = new DynamicDataSourceAnnotationInterceptor(true, noopProcessor());
        AtomicReference<String> dsInsideSign = new AtomicReference<>();

        for (Entry entry : ENTRIES) {
            Object proxy = cglibProxy(newImpl(entry.implClass()));
            Method apiMethod = entry.apiInterface().getDeclaredMethods()[0];
            MethodInvocation invocation = invocationOf(apiMethod, proxy,
                () -> dsInsideSign.set(DynamicDataSourceContextHolder.peek()));

            // 调用方上下文：MES 模块类级 @DS("mes") 已经压栈（线上故障现场）
            DynamicDataSourceContextHolder.push(CALLER_DS);
            try {
                assertEquals(CALLER_DS, DynamicDataSourceContextHolder.peek(), "前置条件: 调用方上下文为 mes");
                interceptor.invoke(invocation);
                assertEquals(MASTER, dsInsideSign.get(),
                    entry.implClass().getSimpleName() + "#" + apiMethod.getName()
                        + " 执行期间的生效数据源必须是 " + MASTER + "（而非调用方继承来的 " + CALLER_DS + "），"
                        + "否则签章 SQL 会打到 ry-mes 库报「表不存在」");
                assertEquals(CALLER_DS, DynamicDataSourceContextHolder.peek(),
                    "退出签章服务后必须弹出 master、把上下文还给调用方（poll 语义），不得污染调用方后续 SQL");
            } catch (Throwable t) {
                throw new AssertionError("拦截器调用失败: " + entry.implClass().getSimpleName(), t);
            } finally {
                DynamicDataSourceContextHolder.poll();
            }
        }
    }

    // ------------------------------------------------------------------
    // 测试替身与工具
    // ------------------------------------------------------------------

    /**
     * 反射实例化 impl，依赖一律注入 null。
     *
     * <p>本测试只关心注解解析，不触发任何方法体，故不需要真实 Mapper / 校验器；
     * 用「参数最多的构造器 + 全 null」可以免去逐个维护依赖列表
     * （将来 impl 新增依赖时本测试不会因此编译失败）。</p>
     */
    private static Object newImpl(Class<?> implClass) {
        Constructor<?> ctor = Arrays.stream(implClass.getDeclaredConstructors())
            .max(Comparator.comparingInt(Constructor::getParameterCount))
            .orElseThrow(() -> new IllegalStateException(implClass + " 没有构造器"));
        try {
            return ctor.newInstance(new Object[ctor.getParameterCount()]);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法实例化 " + implClass, e);
        }
    }

    /** 与 Spring Boot 默认一致的 CGLIB 代理（{@code proxyTargetClass=true}）。 */
    private static Object cglibProxy(Object target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        return factory.getProxy(SignDataSourceMasterTest.class.getClassLoader());
    }

    /** 解析器不会为 "master" 这类字面量调用处理器，故给一个永不匹配的空实现即可。 */
    private static DsProcessor noopProcessor() {
        return new DsProcessor() {
            @Override
            public boolean matches(String key) {
                return false;
            }

            @Override
            public String doDetermineDatasource(MethodInvocation invocation, String key) {
                return key;
            }
        };
    }

    /**
     * 造一个最小 {@link MethodInvocation}：{@code getThis()} 返回代理对象
     * （与真实调用的 {@code invocation.getThis()} 一致，注解解析正是靠它回溯到 impl 类），
     * {@code proceed()} 则记录「此刻生效的数据源」。
     */
    private static MethodInvocation invocationOf(Method method, Object proxy, Runnable onProceed) {
        return new MethodInvocation() {
            @Override
            public Method getMethod() {
                return method;
            }

            @Override
            public Object[] getArguments() {
                return new Object[0];
            }

            @Override
            public Object proceed() {
                onProceed.run();
                return null;
            }

            @Override
            public Object getThis() {
                return proxy;
            }

            @Override
            public AccessibleObject getStaticPart() {
                return method;
            }
        };
    }
}
