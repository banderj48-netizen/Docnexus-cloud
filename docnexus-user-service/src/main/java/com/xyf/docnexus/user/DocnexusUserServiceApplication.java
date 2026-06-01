package com.xyf.docnexus.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocnexusUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocnexusUserServiceApplication.class, args);
    }

}
