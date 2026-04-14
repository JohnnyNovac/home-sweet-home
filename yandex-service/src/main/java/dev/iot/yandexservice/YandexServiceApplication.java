package dev.iot.yandexservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YandexServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(YandexServiceApplication.class, args);
    }

}
