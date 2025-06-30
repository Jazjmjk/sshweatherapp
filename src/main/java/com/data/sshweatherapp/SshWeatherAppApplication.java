package com.data.sshweatherapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SshWeatherAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(SshWeatherAppApplication.class, args);
    }
}