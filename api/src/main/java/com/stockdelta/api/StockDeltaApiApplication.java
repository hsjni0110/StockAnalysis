package com.stockdelta.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.stockdelta.api", "com.stockdelta.common"})
@EntityScan(basePackages = "com.stockdelta.common.entity")
@EnableJpaRepositories(basePackages = "com.stockdelta.common.repository")
public class StockDeltaApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockDeltaApiApplication.class, args);
    }
}