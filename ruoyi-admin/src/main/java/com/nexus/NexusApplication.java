package com.nexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * 启动程序
 *
 * @author wanghuan
 */

@SpringBootApplication
public class NexusApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(NexusApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  Nexus启动成功   ლ(´ڡ`ლ)ﾞ");
    }

}
