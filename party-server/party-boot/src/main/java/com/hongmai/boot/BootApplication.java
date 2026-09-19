package com.hongmai.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 红脉云筑 · 智慧党建小程序服务端启动类。
 *
 * @EnableScheduling 供排行榜每日快照任务使用（当前为单机 Spring Task，
 * 多实例部署前需换为分布式调度，见 plan §10 技术债）。
 */
@SpringBootApplication(scanBasePackages = "com.hongmai")
@EnableScheduling
@EnableTransactionManagement
public class BootApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootApplication.class, args);
    }
}
