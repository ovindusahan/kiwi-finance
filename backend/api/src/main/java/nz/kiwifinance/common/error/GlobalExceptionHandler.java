package nz.kiwifinance.common.error;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Log4j2
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
        ErrorCode code = exception.errorCode();
        return ResponseEntity.status(code.status()).body(ProblemDetails.of(code, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        var error = new ProblemDetails.FieldError(exception.getName(), "has an invalid value");
        return ResponseEntity.badRequest().body(ProblemDetails.validation(List.of(error)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.internalServerError()
                .body(ProblemDetails.of(ErrorCode.INTERNAL_ERROR, "Something went wrong. Please try again."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ProblemDetails.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ProblemDetails.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ProblemDetails.validation(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ProblemDetails.FieldError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ProblemDetails.FieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(ProblemDetails.validation(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.debug("Unreadable request body", exception);
        return ResponseEntity.badRequest()
                .body(ProblemDetails.of(ErrorCode.MALFORMED_REQUEST, "The request body could not be read."));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status())
                .body(ProblemDetails.of(ErrorCode.NOT_FOUND, "No endpoint at this path."));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.status())
                .body(ProblemDetails.of(ErrorCode.METHOD_NOT_ALLOWED, exception.getMessage()));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(ErrorCode.IMPORT_TOO_LARGE.status())
                .body(ProblemDetails.of(ErrorCode.IMPORT_TOO_LARGE, "Files must be 5 MB or smaller."));
    }
}
