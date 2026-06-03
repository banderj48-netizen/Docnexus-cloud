package com.xyf.docnexus.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.xyf.docnexus.file.mapper")
@EnableScheduling
public class DocnexusFileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocnexusFileServiceApplication.class, args);
    }

}
