package za.co.carddisputeportalservice.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import za.co.capitecbank.security.SecurityErrorHandler;

class SecurityErrorHandlerTest {
    @Test
    void failsRatherThanReturningSuccessWhenNoResolverHandlesTheFailure() {
        var handler = new SecurityErrorHandler(mock(HandlerExceptionResolver.class));
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var authentication = new BadCredentialsException("Invalid token");
        var forbidden = new AccessDeniedException("Access denied");

        assertThatThrownBy(() -> handler.commence(request, response, authentication))
                .isInstanceOf(ServletException.class)
                .hasCause(authentication);
        assertThatThrownBy(() -> handler.handle(request, response, forbidden))
                .isInstanceOf(ServletException.class)
                .hasCause(forbidden);
    }
}
