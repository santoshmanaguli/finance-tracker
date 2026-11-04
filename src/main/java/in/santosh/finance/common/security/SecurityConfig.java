package in.santosh.finance.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import in.santosh.finance.common.FinanceProperties;

@Configuration
@EnableConfigurationProperties(FinanceProperties.class)
class SecurityConfig {

    /**
     * Registers the filter for the ingest API only. Actuator health stays open so Docker,
     * a load balancer, or an uptime checker can probe it without credentials.
     */
    @Bean
    FilterRegistrationBean<ApiTokenFilter> apiTokenFilter(FinanceProperties properties) {
        var registration = new FilterRegistrationBean<>(new ApiTokenFilter(properties));
        registration.addUrlPatterns("/api/ingest/*");
        registration.setName("apiTokenFilter");
        return registration;
    }
}
