package in.santosh.finance;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One real Postgres 16 container for the whole test run.
 *
 * Why not H2? H2 does not support partial indexes, TIMESTAMPTZ semantics, or
 * Postgres regex CHECKs the way Postgres does. Tests on a different database
 * prove nothing about production. Same image tag as docker-compose.yml.
 *
 * {@code @ServiceConnection} tells Spring Boot to read the container's JDBC URL,
 * username and password and override spring.datasource.* with them, so no
 * {@code @DynamicPropertySource} boilerplate is needed.
 *
 * Because the bean is a singleton in a cached Spring context, every test class
 * that imports this config shares one container: startup cost is paid once.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    }
}
