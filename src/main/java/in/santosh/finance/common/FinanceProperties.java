package in.santosh.finance.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding of the {@code finance.*} block in application.yml.
 *
 * Why a record instead of {@code @Value("${...}")} sprinkled around? One place
 * documents every custom setting, values are immutable, and tests can construct
 * it directly with {@code new FinanceProperties(...)}.
 *
 * @param ingestToken shared secret the iOS Shortcut sends in {@code X-Api-Token}.
 *                    Blank means "not configured": the ingest API then rejects everything.
 * @param dedupWindowMinutes how far apart (either side) an SMS and an email for the
 *                    same transaction may be timestamped and still be matched.
 */
@ConfigurationProperties(prefix = "finance")
public record FinanceProperties(String ingestToken, int dedupWindowMinutes) {

    public FinanceProperties {
        if (dedupWindowMinutes <= 0) {
            dedupWindowMinutes = 15;
        }
    }

    public boolean ingestTokenConfigured() {
        return ingestToken != null && !ingestToken.isBlank();
    }
}
