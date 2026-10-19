package za.co.carddisputeportalservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import za.co.capitecbank.CardDisputePortalServiceApplication;

import java.util.Map;
import java.util.Set;

/**
 * Contract-driven security regression: adding an endpoint to OpenAPI must not accidentally make it
 * accessible without a valid bearer access token.
 */
@SpringBootTest(classes = CardDisputePortalServiceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class ApiAuthenticationCoverageTest {
    private static final String ID = "ba752bc3-c7da-4a0f-99a9-ce4d6aeca304";

    @Autowired MockMvc mvc;

    @Test
    @SuppressWarnings("unchecked")
    void everyBusinessOperationRejectsMissingAndMalformedBearerTokens() throws Exception {
        Map<String, Object> contract;
        try (var input = getClass().getResourceAsStream("/openapi/openapi.yaml")) {
            assertThat(input).isNotNull();
            contract = new Yaml().load(input);
        }

        var paths = (Map<String, Map<String, Object>>) contract.get("paths");
        int checked = 0;
        for (var entry : paths.entrySet()) {
            for (String method : entry.getValue().keySet()) {
                if (!Set.of("get", "post", "put", "patch", "delete").contains(method)) continue;
                String url = entry.getKey().replaceAll("\\{[^/}]+}", ID);
                assertThat(url).startsWith("/api/v1/");
                var httpMethod = HttpMethod.valueOf(method.toUpperCase(java.util.Locale.ROOT));

                mvc.perform(request(httpMethod, url))
                        .andExpect(status().isUnauthorized())
                        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                        .andExpect(jsonPath("$.status").value(401))
                        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .content()
                                        .contentTypeCompatibleWith(
                                                org.springframework.http.MediaType
                                                        .APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.type").value("about:blank"))
                        .andExpect(
                                jsonPath("$.detail")
                                        .value("Authentication required or token invalid"))
                        .andExpect(jsonPath("$.instance").value(url));
                mvc.perform(
                                request(httpMethod, url)
                                        .header("Authorization", "Bearer invalid-token"))
                        .andExpect(status().isUnauthorized())
                        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                        .andExpect(jsonPath("$.status").value(401))
                        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                        .andExpect(
                                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                                        .content()
                                        .contentTypeCompatibleWith(
                                                org.springframework.http.MediaType
                                                        .APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.type").value("about:blank"))
                        .andExpect(
                                jsonPath("$.detail")
                                        .value("Authentication required or token invalid"))
                        .andExpect(jsonPath("$.instance").value(url));
                checked++;
            }
        }
        assertThat(checked).isEqualTo(11);
    }

    @Test
    void securityFilterAccessDenialsUseProblemDetails() throws Exception {
        mvc.perform(
                        request(HttpMethod.GET, "/internal")
                                .with(
                                        org.springframework.security.test.web.servlet.request
                                                .SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(status().isForbidden())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                                .contentTypeCompatibleWith(
                                        org.springframework.http.MediaType
                                                .APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Customer permission required"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.instance").value("/internal"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void mvcSecurityFailuresUseTheSameResponsesAsFilterFailures() throws Exception {
        mvc.perform(get("/api/v1/customer").with(jwt()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.detail").value("Authentication required or token invalid"));

        mvc.perform(
                        get("/api/v1/customer")
                                .with(
                                        jwt().jwt(
                                                        token ->
                                                                token.issuer(
                                                                                "https://issuer.example.test/")
                                                                        .subject(
                                                                                "auth0|unmapped-test"))))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value("Customer permission required"));
    }

    @Test
    void rejectedBrowserRequestsStillReceiveProblemDetails() throws Exception {
        mvc.perform(
                        get("/api/v1/customer")
                                .accept(MediaType.TEXT_HTML)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.instance").value("/api/v1/customer"));
    }

    @Test
    void unknownApiRoutesAreNotPublic() throws Exception {
        mvc.perform(request(HttpMethod.GET, "/api/v1/internal"))
                .andExpect(status().isUnauthorized());
    }
}
