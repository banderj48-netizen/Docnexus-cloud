package com.xyf.docnexuslogservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@MapperScan("com.xyf.docnexuslogservice.mapper")
@SpringBootApplication
public class DocnexusLogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocnexusLogServiceApplication.class, args);
    }

}
