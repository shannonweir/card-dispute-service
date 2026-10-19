package za.co.capitecbank.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Bridges security-filter failures to the application's global MVC exception handling. */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public SecurityErrorHandler(@Qualifier("handlerExceptionResolver") final HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(final HttpServletRequest request, final HttpServletResponse response, final AuthenticationException exception)
            throws ServletException {
        resolve(request, response, exception);
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response, final AccessDeniedException exception)
            throws ServletException {
        resolve(request, response, exception);
    }

    private void resolve(final HttpServletRequest request, final HttpServletResponse response, final Exception exception)
            throws ServletException {
        if (resolver.resolveException(request, response, null, exception) == null) {
            throw new ServletException("No handler resolved the security failure", exception);
        }
    }
}
