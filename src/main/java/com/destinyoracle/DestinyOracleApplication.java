package com.destinyoracle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class DestinyOracleApplication {
    public static void main(String[] args) {
        SpringApplication.run(DestinyOracleApplication.class, args);
    }
}
