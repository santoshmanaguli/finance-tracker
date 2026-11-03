package in.santosh.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. {@code @SpringBootApplication} = configuration + auto-configuration
 * + component scanning of this package and everything below it. That is why this
 * class sits at the root package: feature packages (ingest, transaction, ...) are
 * sub-packages and get scanned automatically.
 */
@SpringBootApplication
public class FinanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceApplication.class, args);
    }
}
