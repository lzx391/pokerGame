package com.example.mgdemoplus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.mgdemoplus.mapper")
public class MgDemoPlusApplication {

    public static void main(String[] args) {
        SpringApplication.run(MgDemoPlusApplication.class, args);
        System.out.println("启动成功awa");
    }

}
