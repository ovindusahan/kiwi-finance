package nz.kiwifinance.common.error;

import java.util.List;
import org.springframework.http.ProblemDetail;

public final class ProblemDetails {

    public static final String CODE = "code";
    public static final String ERRORS = "errors";

    private ProblemDetails() {}

    public static ProblemDetail of(ErrorCode errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problem.setTitle(errorCode.title());
        problem.setProperty(CODE, errorCode.code());
        return problem;
    }

    public static ProblemDetail validation(List<FieldError> errors) {
        ProblemDetail problem = of(ErrorCode.VALIDATION_FAILED, "Some fields need attention.");
        problem.setProperty(ERRORS, errors);
        return problem;
    }

    public record FieldError(String field, String message) {}
}
