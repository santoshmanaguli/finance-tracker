package in.santosh.finance;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import in.santosh.finance.ingest.parser.TestParsersConfig;

/**
 * Meta-annotation for integration tests.
 *
 * Spring caches one application context per unique configuration. If every test class
 * declared slightly different annotations, each would boot its own context AND its own
 * Postgres container. One shared annotation = one context = one container for the whole run.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "finance.ingest-token=" + IntegrationTest.TOKEN)
@Import({TestcontainersConfig.class, TestParsersConfig.class})
public @interface IntegrationTest {

    String TOKEN = "test-token-0123456789abcdefghijklmnopqrstuvwxyz";
}
