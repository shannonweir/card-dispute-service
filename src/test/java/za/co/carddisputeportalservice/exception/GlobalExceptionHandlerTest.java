package za.co.carddisputeportalservice.exception;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import za.co.capitecbank.exception.CardDisputeException;
import za.co.capitecbank.exception.ErrorCode;
import za.co.capitecbank.exception.GlobalExceptionHandler;

import java.util.Map;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc =
                MockMvcBuilders.standaloneSetup(new ErrorCases())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void preservesAllowHeaderAndContractBodyForUnsupportedMethods() throws Exception {
        mvc.perform(post("/errors/query"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Method Not Allowed"))
                .andExpect(jsonPath("$.instance").value("/errors/query"));
    }

    @Test
    void requestValidationIs400ButReturnValueValidationIs500() throws Exception {
        mvc.perform(get("/errors/query").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(
                        post("/errors/body")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/errors/return-value"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void malformedRequestsRetainTheir400StatusAndContractCode() throws Exception {
        mvc.perform(get("/errors/query").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/errors/query"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/errors/body").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unsupportedResponseFormatsStillReceiveProblemDetails() throws Exception {
        mvc.perform(get("/errors/query").param("page", "1").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"))
                .andExpect(jsonPath("$.instance").value("/errors/query"));
    }

    @Test
    void explicitInputFailuresAre400WhileProgrammingAndDatabaseFailuresAreSafe500s()
            throws Exception {
        mvc.perform(get("/errors/input"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("from must not be after to"));
        for (String path : new String[] {"/errors/programming", "/errors/database"}) {
            mvc.perform(get(path))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                    .andExpect(jsonPath("$.errorId").isNotEmpty())
                    .andExpect(jsonPath("$.type").value("about:blank"))
                    .andExpect(jsonPath("$.instance").value(path))
                    .andExpect(jsonPath("$.message").doesNotExist());
        }
    }

    @Test
    void preservesBusinessAndControllerSecurityErrors() throws Exception {
        mvc.perform(get("/errors/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(get("/errors/ineligible"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSACTION_INELIGIBLE"));
        mvc.perform(get("/errors/authentication"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/errors/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/errors/document"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @ParameterizedTest
    @CsvSource({
        "EMPTY_FILE,400,Choose a non-empty file",
        "PAYLOAD_TOO_LARGE,413,Files must be no larger than 5 MB",
        "INVALID_FILE_NAME,400,Invalid file name"
    })
    void usesCentralErrorDefinitionsWhenNoSpecificDetailIsSupplied(
            final String code, final int expectedStatus, final String expectedDetail)
            throws Exception {
        mvc.perform(get("/errors/default").param("code", code))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").value(expectedDetail))
                .andExpect(jsonPath("$.instance").value("/errors/default"))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @RestController
    static class ErrorCases {
        @GetMapping("/errors/default")
        void defaultDetail(final @RequestParam("code") ErrorCode code) {
            throw new CardDisputeException(code);
        }

        @GetMapping("/errors/query")
        Map<String, Integer> query(final @RequestParam("page") @Min(1) int page) {
            return Map.of("page", page);
        }

        @PostMapping("/errors/body")
        Payload body(final @RequestBody @Valid Payload payload) {
            return payload;
        }

        @GetMapping("/errors/return-value")
        @NotNull
        String returnValue() {
            return null;
        }

        @GetMapping("/errors/input")
        void input() {
            throw new CardDisputeException(ErrorCode.VALIDATION_ERROR, "from must not be after to");
        }

        @GetMapping("/errors/programming")
        void programming() {
            throw new IllegalArgumentException("Internal programming detail");
        }

        @GetMapping("/errors/database")
        void database() {
            throw new DataIntegrityViolationException("Internal table and SQL details");
        }

        @GetMapping("/errors/missing")
        void missing() {
            throw new CardDisputeException(ErrorCode.NOT_FOUND, "Transaction was not found");
        }

        @GetMapping("/errors/conflict")
        void conflict() {
            throw new CardDisputeException(ErrorCode.CONFLICT, "A dispute already exists");
        }

        @GetMapping("/errors/ineligible")
        void ineligible() {
            throw new CardDisputeException(
                    ErrorCode.TRANSACTION_INELIGIBLE, "Only posted transactions can be disputed");
        }

        @GetMapping("/errors/authentication")
        void authentication() {
            throw new AuthenticationCredentialsNotFoundException("Internal auth details");
        }

        @GetMapping("/errors/forbidden")
        void forbidden() {
            throw new AccessDeniedException("Internal permission details");
        }

        @GetMapping("/errors/document")
        void document() {
            throw new CardDisputeException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Choose a PDF, PNG or JPEG");
        }
    }

    record Payload(@NotBlank String text) {}
}
