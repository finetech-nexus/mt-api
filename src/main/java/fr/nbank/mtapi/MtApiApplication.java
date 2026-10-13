package fr.nbank.mtapi;

import fr.nbank.mtapi.config.MtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(MtProperties.class)
public class MtApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MtApiApplication.class, args);
    }
}
