package ru.multitool.eight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа витрины из восьми backend-проектов.
 */
@SpringBootApplication
@EnableScheduling
public class EightProjectsApplication {

    public static void main(String[] args) {
        SpringApplication.run(EightProjectsApplication.class, args);
    }
}
