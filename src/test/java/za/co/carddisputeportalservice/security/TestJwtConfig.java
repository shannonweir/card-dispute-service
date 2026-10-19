package za.co.carddisputeportalservice.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * Ephemeral RSA keys; standard Nimbus validation plus production claim checks, without live Auth0.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtConfig {
    private final KeyPair keys;

    public TestJwtConfig() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    @Bean
    JwtDecoder testJwtDecoder(
            final @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            final @Value("${spring.security.oauth2.resourceserver.jwt.audiences}") String audience,
            final OAuth2TokenValidator<Jwt> requiredAccessTokenClaims) {
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        new JwtClaimValidator<List<String>>(
                                "aud",
                                audiences -> audiences != null && audiences.contains(audience)),
                        requiredAccessTokenClaims));
        return decoder;
    }

    @Bean
    JwtEncoder testJwtEncoder() {
        var key =
                new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                        .privateKey((RSAPrivateKey) keys.getPrivate())
                        .keyID("test-only-signing-key")
                        .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }
}
