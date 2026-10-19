package za.co.capitecbank.config;

import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.util.Assert;

import java.time.Instant;

/** Boot adds these checks to its signature, issuer, audience and timestamp validation. */
@Configuration(proxyBeanMethods = false)
public class JwtValidationConfig {

    @Bean
    OAuth2TokenValidator<Jwt> requiredAccessTokenClaims(final OAuth2ResourceServerProperties properties) {

        Assert.hasText(properties.getJwt().getIssuerUri(), "AUTH0_ISSUER_URI must be configured");
        var audiences = properties.getJwt().getAudiences();
        Assert.isTrue(
                audiences != null
                        && !audiences.isEmpty()
                        && audiences.stream().allMatch(a -> a != null && !a.isBlank()),
                "AUTH0_AUDIENCE must be configured");
        return new DelegatingOAuth2TokenValidator<>(
                new JwtClaimValidator<Instant>("exp", expiration -> expiration != null),
                new JwtClaimValidator<String>(
                        "sub", subject -> subject != null && !subject.isBlank()));
    }
}
