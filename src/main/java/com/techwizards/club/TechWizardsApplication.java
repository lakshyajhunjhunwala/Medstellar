package com.techwizards.club;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TechWizardsApplication {
    public static void main(String[] args) {
        SpringApplication.run(TechWizardsApplication.class, args);
    }
}
