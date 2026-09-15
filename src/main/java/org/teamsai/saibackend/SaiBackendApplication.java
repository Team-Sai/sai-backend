package org.teamsai.saibackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;

@SpringBootApplication(
        exclude = {
                DataSourceInitializationAutoConfiguration.class
        }
)
@EnableScheduling
public class SaiBackendApplication {

    public static void main(String[] args) {

        SpringApplication application =
                new SpringApplication(SaiBackendApplication.class);

        application.setDefaultProperties(
                Map.ofEntries(
                        Map.entry(
                                "sai.batch.enabled",
                                "false"
                        ),

                        // Batch Job 자동 실행 방지
                        Map.entry(
                                "spring.batch.job.enabled",
                                "false"
                        ),

                        // Batch 테이블 자동 생성 방지
                        Map.entry(
                                "spring.batch.jdbc.initialize-schema",
                                "never"
                        ),

                        // DB 연결 실패 때문에 Hikari가 부팅 자체를 죽이지 않음
                        Map.entry(
                                "spring.datasource.hikari.initialization-fail-timeout",
                                "-1"
                        ),

                        // Bean 생성을 실제 필요 시점까지 지연
                        Map.entry(
                                "spring.main.lazy-initialization",
                                "true"
                        ),

                        // DB 연결 없이도 Hibernate가 어떤 DB인지 알 수 있게 함
                        Map.entry(
                                "spring.jpa.database-platform",
                                "org.hibernate.dialect.MariaDBDialect"
                        ),

                        // 부팅 시 Hibernate가 테이블 생성/수정하지 않음
                        Map.entry(
                                "spring.jpa.hibernate.ddl-auto",
                                "none"
                        ),

                        // 부팅 시 JDBC Metadata 조회를 위해 DB 접속하지 않음
                        Map.entry(
                                "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access",
                                "false"
                        ),

                        Map.entry(
                                "spring.datasource.url",
                                "jdbc:mariadb://127.0.0.1:3306/sai_backend"
                        ),

                        Map.entry(
                                "spring.datasource.driver-class-name",
                                "org.mariadb.jdbc.Driver"
                        )
                )
        );

        application.run(args);
    }
}