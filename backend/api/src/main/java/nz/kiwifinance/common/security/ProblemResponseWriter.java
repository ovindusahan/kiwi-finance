package nz.kiwifinance.common.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import nz.kiwifinance.common.error.ErrorCode;
import nz.kiwifinance.common.error.ProblemDetails;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes problem details from security filters, which run before Spring MVC's exception handling.
 */
@Component
@RequiredArgsConstructor
class ProblemResponseWriter {

    private final JsonMapper jsonMapper;

    void write(HttpServletResponse response, ErrorCode errorCode, String detail) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ProblemDetails.of(errorCode, detail));
    }
}
