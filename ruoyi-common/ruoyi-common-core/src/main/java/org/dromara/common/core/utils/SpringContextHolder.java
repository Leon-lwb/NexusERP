package org.dromara.common.core.utils;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring上下文工具类
 *
 * @author ruoyi
 */
@Component
public final class SpringContextHolder implements ApplicationContextAware, BeanFactoryPostProcessor {

    private static ConfigurableListableBeanFactory beanFactory;

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringContextHolder.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        SpringContextHolder.beanFactory = beanFactory;
    }

    /**
     * 获取ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 通过name获取 Bean.
     *
     * @param <T>  Bean类型
     * @param name 名称
     * @return Object 一个以所给名字注册的bean的实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        return (T) applicationContext.getBean(name);
    }

    /**
     * 通过name获取 Bean.
     *
     * @param <T>       Bean类型
     * @param name      名称
     * @param required 是否必须
     * @return Object 一个以所给名字注册的bean的实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name, boolean required) {
        try {
            return (T) applicationContext.getBean(name);
        } catch (NoSuchBeanDefinitionException e) {
            if (required) {
                throw new RuntimeException("Required bean '" + name + "' is not available", e);
            }
            return null;
        }
    }

    /**
     * 通过class获取Bean.
     *
     * @param <T>   Bean类型
     * @param clazz 类
     * @return Object 一个以所给类型注册的bean的实例
     */
    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }

    /**
     * 通过name,以及Clazz返回指定的Bean
     *
     * @param <T>   Bean类型
     * @param name  名称
     * @param clazz 类型
     * @return Object 一个以所给对象注册的bean的实例
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return applicationContext.getBean(name, clazz);
    }

    /**
     * 获取指定类型的所有Bean实现
     *
     * @param <T>  Bean类型
     * @param clazz 类型
     * @return Map<String, T> 返回包含所有匹配对象的Map
     */
    public static <T> java.util.Map<String, T> getBeansOfType(Class<T> clazz) {
        return applicationContext.getBeansOfType(clazz);
    }

}
