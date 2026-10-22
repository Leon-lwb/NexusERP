package com.nexus.snailjob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// 同名类型说明：本文件同时使用 com.nexus.snailjob.SnailJobServerApplication 和
// com.aizuda.snailjob.server.SnailJobServerApplication，后者保留全限定名以区分。

/**
 * SnailJob Server 启动程序
 *
 * @author opensnail
 * @date 2024-05-17
 */
@SpringBootApplication
public class SnailJobServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(com.aizuda.snailjob.server.SnailJobServerApplication.class, args);
    }

}
