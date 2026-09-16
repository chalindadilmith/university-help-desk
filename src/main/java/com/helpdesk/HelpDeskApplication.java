package com.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the University Help Desk System.
 *
 * Run with: mvn spring-boot:run
 * or package with: mvn clean package  then  java -jar target/university-help-desk-0.1.0.jar
 */
@SpringBootApplication
public class HelpDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelpDeskApplication.class, args);
    }
}
