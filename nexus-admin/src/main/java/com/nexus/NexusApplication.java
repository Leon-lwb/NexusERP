package com.nexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 启动程序
 * 
 * @author nexus
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class NexusApplication
{
    public static void main(String[] args)
    {
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(NexusApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  NexusERP启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
        " ooooo      ooo oooooooooooo ooooooo  ooooo ooooo     ooo  .oooooo..o oooooooooooo   \n" +
        " `888b.     `8' `888'     `8  `8888    d8'  `888'     `8' d8P'    `Y8 `888'     `8   \n" +
         " 8 `88b.    8   888            Y888..8P     888       8  Y88bo.       888           \n" +
         " 8   `88b.  8   888oooo8        `8888'      888       8   `Y8888o.   888oooo8       \n" +
         " 8     `88b.8   888            .8PY888.     888       8       `.Y88b  888           \n" +
         " 8       `888   888       o   d8'  `888b    `88.    .8'  oo     .d8P  888       o   \n" +
        " o8o        `8  o888ooooood8 o888o  o88888o    `YbodP'    8”“88888P'  o888ooooood8   \n" );
    }
}
