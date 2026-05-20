package org.example.config; // 确保包名和你的文件夹路径一致

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration // 标记这是一个配置类
public class RestTemplateConfig {

    @Bean // 标记这个方法返回的对象要交给 Spring 管理
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}