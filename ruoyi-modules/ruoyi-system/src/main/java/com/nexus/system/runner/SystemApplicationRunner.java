package com.nexus.system.runner;

import com.nexus.system.service.ISysOssConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 初始化 system 模块对应业务数据
 * <p>在服务启动时执行，负责初始化 OSS 对象存储配置，确保系统运行所需的存储配置就绪。属于系统管理（System）模块。</p>
 *
 * @author Lion Li
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SystemApplicationRunner implements ApplicationRunner {

    /** OSS 配置业务服务，提供存储配置初始化能力。 */
    private final ISysOssConfigService ossConfigService;

    /**
     * 应用启动完成后执行的初始化逻辑。
     *
     * @param args 应用启动参数
     * @throws Exception 初始化异常时抛出
     */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        ossConfigService.init();
        log.info("初始化OSS配置成功");
    }

}
