package za.co.capitecbank.exception;

import jakarta.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CardDisputeException.class)
    ProblemDetail cardDisputeException(final CardDisputeException exception, final HttpServletRequest request) {
        var error = exception.getErrorCode();
        return CardDisputeProblem.create(
                error.getStatus().value(), error.getCode(), exception.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> authenticationException(final HttpServletRequest request) {
        ErrorCode error = ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(error.getStatus())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(CardDisputeProblem.create(error.getStatus().value(), error.getCode(), error.getDefaultDetail(), request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDeniedException(final HttpServletRequest request) {
        ErrorCode error = ErrorCode.FORBIDDEN;
        return CardDisputeProblem.create(error.getStatus().value(), error.getCode(), error.getDefaultDetail(), request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(final Exception exception, final Object body,
                                                             final HttpHeaders headers, final HttpStatusCode status,
                                                             final WebRequest request) {
        var response = super.handleExceptionInternal(exception, body, headers, status, request);

        if (response == null)
            return null; // The response may already have been committed.

        var problem = response.getBody() instanceof ProblemDetail existing ? existing
                : ProblemDetail.forStatusAndDetail(status, "The request could not be processed");

        String code = switch (status.value()) {
            case 400 -> exception instanceof MethodArgumentNotValidException
                    || exception instanceof HandlerMethodValidationException
                    ? "VALIDATION_ERROR"
                    : "INVALID_REQUEST";
            case 413 -> "PAYLOAD_TOO_LARGE";
            default -> status.is5xxServerError()
                    ? "INTERNAL_ERROR"
                    : HttpStatus.valueOf(status.value()).name();
        };
        CardDisputeProblem.prepare(problem, code, ((ServletWebRequest) request).getRequest());

        if (status.is5xxServerError())
            logFailure(exception, problem);

        return ResponseEntity.status(status)
                .headers(response.getHeaders())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(final Exception failure, final HttpServletRequest request) {
        ErrorCode error = ErrorCode.INTERNAL_ERROR;
        ProblemDetail problem = CardDisputeProblem.create(error.getStatus().value(), error.getCode(), error.getDefaultDetail(), request);
        logFailure(failure, problem);
        return problem;
    }

    private void logFailure(final Exception failure, final ProblemDetail problem) {
        log.error("Request failed; errorId={}", problem.getProperties().get("errorId"), failure);
    }
}
