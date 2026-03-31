package com.example.agentruntime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 应用启动入口。
 * 这里仅负责引导 Spring Boot 和配置属性绑定。
 */
@SpringBootApplication
@EnableConfigurationProperties(AgentRuntimeProperties.class)
public class AgentRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentRuntimeApplication.class, args);
    }
}
