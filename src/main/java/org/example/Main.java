package org.example; // <--- 必须加上这一行！

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Main {

    // 1. 注入你的 Service
    @Autowired
    private QuestionService questionService;

    public static void main(String[] args) {
        // 2. 启动 Spring Boot
        SpringApplication.run(Main.class, args);
    }

    // 3. 添加这个 Bean，Spring Boot 启动成功后会自动运行这里的代码
    @Bean
    public CommandLineRunner testQuestionService() {
        return args -> {
            System.out.println(">>> 开始测试 QuestionService...");
            // 如果 QuestionService 的 init() 方法是 public，可以直接调
            // 如果是 @PostConstruct，这里其实不需要手动调，启动就会自动执行
        };
    }
}