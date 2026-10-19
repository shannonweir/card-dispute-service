package za.co.capitecbank.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.util.UUID;

/** Shared Problem Details fields for MVC and security-filter responses. */
public final class CardDisputeProblem {

    private CardDisputeProblem() {}

    public static ProblemDetail create(final int status, final String code, final String detail, final HttpServletRequest request) {
        return prepare(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail), code, request);
    }

    public static ProblemDetail prepare(final ProblemDetail problem, final String code, final HttpServletRequest request) {

        if (problem.getType() == null)
            problem.setType(URI.create("about:blank"));

        if (problem.getDetail() == null)
            problem.setDetail(problem.getTitle());

        problem.setProperty("code", code);
        problem.setInstance(URI.create(request.getRequestURI()));
        if (problem.getStatus() >= 500) {
            problem.setDetail("An unexpected error occurred");
            problem.setProperty("errorId", UUID.randomUUID().toString());
        }
        return problem;
    }
}
