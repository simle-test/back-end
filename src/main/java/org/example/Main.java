package org.example;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("org.example.mapper")
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
        System.out.println(">>> 后端服务启动成功！");
    }
}