package org.teamsai.saibackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SaiBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SaiBackendApplication.class, args);
    }

}
