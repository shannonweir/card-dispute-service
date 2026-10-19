package za.co.carddisputeportalservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import za.co.capitecbank.CardDisputePortalServiceApplication;

@SpringBootTest(
        classes = CardDisputePortalServiceApplication.class,
        properties =
                "spring.datasource.url=jdbc:h2:mem:localSecurity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class SecurityConfigurationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void usesTheServletSecurityChainAndStillRequiresAuthentication() throws Exception {
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/transactions")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/transactions")
                                .with(
                                        jwt().jwt(
                                                        token ->
                                                                token.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(
                                                                                "auth0|shannon-test"))))
                .andExpect(status().isOk());
    }
}
