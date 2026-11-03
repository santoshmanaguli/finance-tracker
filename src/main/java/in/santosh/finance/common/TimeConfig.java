package in.santosh.finance.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Time is a dependency, not a global.
 *
 * Code that calls {@code Instant.now()} directly cannot be tested deterministically
 * ("was this SMS received within the last 15 minutes?"). Injecting a {@link Clock}
 * lets tests pass {@code Clock.fixed(...)}.
 *
 * We store instants in UTC (TIMESTAMPTZ) and convert to IST only when the business
 * meaning depends on the calendar (e.g. "November" means November in India).
 */
@Configuration
public class TimeConfig {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
