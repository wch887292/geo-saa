package com.geosaa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class GeoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeoApplication.class, args);
    }
}