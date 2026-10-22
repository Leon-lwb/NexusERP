package com.nexus.decisioncontrol;

import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

/**
 * 仅存在于外部测试类目录，不提供控制器、生产环境配置或业务状态变更。
 */
@Configuration(proxyBeanMethods = false)
@Profile("decision-control-test")
public class DecisionControlTestConfiguration {
    @Bean
    static BeanPostProcessor processor(Environment env) {
        if (!Arrays.asList(env.getActiveProfiles()).contains("decision-control-test")
                || Arrays.stream(env.getActiveProfiles())
                        .anyMatch(p -> !Set.of("dev", "decision-control-test").contains(p))
                || !"true".equals(env.getProperty("decision.control.enabled")))
            throw new IllegalStateException("CONTROL requires explicit test profile and opt-in");
        String schemaDate = null;
        for (String ds : new String[]{"master", "erp", "mes", "iot"}) {
            String suffix = ds.equals("master") ? "sys" : ds;
            String url = env.getProperty("spring.datasource.dynamic.datasource." + ds + ".url", "");
            if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:13307/nexus_ai_decision_control_[0-9]{8}_"
                + suffix + "(?:\\?.*)?"))
                throw new IllegalStateException("CONTROL refuses non-isolated datasource: " + ds);
            String date = url.substring(url.indexOf("control_") + 8, url.indexOf("control_") + 16);
            if (schemaDate != null && !schemaDate.equals(date))
                throw new IllegalStateException("CONTROL refuses mixed schema sets");
            schemaDate = date;
        }
        if (!"127.0.0.1".equals(env.getProperty("spring.data.redis.host"))
            || !"6386".equals(env.getProperty("spring.data.redis.port"))
            || !"127.0.0.1".equals(env.getProperty("spring.rabbitmq.host"))
            || !"5685".equals(env.getProperty("spring.rabbitmq.port"))
            || !("decision-control-" + schemaDate).equals(env.getProperty("spring.rabbitmq.virtual-host")))
            throw new IllegalStateException("CONTROL requires isolated Redis and Rabbit vhost");
        Path directory = Path.of(env.getRequiredProperty("decision.control.marker-directory")).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory) || Files.isSymbolicLink(directory))
            throw new IllegalStateException("CONTROL marker directory must already exist and be local");
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String name) {
                if (!(bean instanceof IndustrialDecisionDataPort)) return bean;
                return Proxy.newProxyInstance(IndustrialDecisionDataPort.class.getClassLoader(),
                    new Class<?>[]{IndustrialDecisionDataPort.class}, (proxy, method, args) -> {
                        Long id = args != null && args.length > 0 && args[0] instanceof DecisionActionProposal action
                            ? action.actionId() : null;
                        String phase = id == null ? "" : phase(directory, id);
                        if ("executeBusinessAction".equals(method.getName()) && "EXECUTE_FAIL".equals(phase))
                            throw new IllegalStateException("CONTROL injected pre-write failure");
                        Object result;
                        try { result = method.invoke(bean, args); }
                        catch (InvocationTargetException ex) { throw ex.getCause(); }
                        if ("verifyBusinessAction".equals(method.getName()) && phase.startsWith("VERIFY_")
                            && result instanceof DecisionActionExecutionResult verified && verified.success()) {
                            Files.writeString(directory.resolve(id + ".observed"), String.valueOf(verified.resultBizId()));
                            if ("VERIFY_PAUSE_FAIL".equals(phase)) {
                                long deadline = System.nanoTime() + 30_000_000_000L;
                                while (!Files.exists(directory.resolve(id + ".release")) && System.nanoTime() < deadline)
                                    Thread.sleep(100);
                            }
                            throw new IllegalStateException("CONTROL injected after real verification");
                        }
                        return result;
                    });
            }
        };
    }

    private static String phase(Path directory, Long actionId) throws IOException {
        Path file = directory.resolve("fault.txt");
        if (!Files.exists(file)) return "";
        if (Files.isSymbolicLink(file) || Files.size(file) > 80)
            throw new IllegalStateException("CONTROL invalid fault marker");
        String value = Files.readString(file).trim();
        if (!value.matches("[1-9][0-9]{0,18}:(?:EXECUTE_FAIL|VERIFY_FAIL|VERIFY_PAUSE_FAIL)"))
            throw new IllegalStateException("CONTROL invalid fault marker");
        String[] parts = value.split(":");
        return parts[0].equals(String.valueOf(actionId)) ? parts[1] : "";
    }
}
