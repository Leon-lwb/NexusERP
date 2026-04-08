package com.nexus.framework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class JimuReportResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/jmreport/**")
                .addResourceLocations("classpath:/static/jmreport/");
        registry.addResourceHandler("/drag/**")
                .addResourceLocations("classpath:/static/drag/");
        registry.addResourceHandler("/jimubi/**")
                .addResourceLocations("classpath:/static/jimubi/");
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
