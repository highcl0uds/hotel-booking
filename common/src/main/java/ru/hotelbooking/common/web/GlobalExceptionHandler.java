package ru.hotelbooking.common.web;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.ClassUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.hotelbooking.common.error.ApiException;
import ru.hotelbooking.common.error.ErrorCodes;
import ru.hotelbooking.common.error.ErrorDto;
import ru.hotelbooking.common.error.FieldViolation;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorDto> handleApiException(ApiException ex, WebRequest request) {
        log.info(
                "Request failed: status={}, code={}, message={}",
                ex.getStatus().value(),
                ex.getCode(),
                ex.getMessage());
        ErrorDto body = error(ex.getStatus(), ex.getCode(), ex.getMessage(), request);
        return ResponseEntity.status(ex.getStatus()).body(body.withContext(ex.getContext()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validationError(violations, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> handleAccessDenied(WebRequest request) {
        return respond(HttpStatus.FORBIDDEN, ErrorCodes.ACCESS_DENIED, "Access denied", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorDto> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ErrorCodes.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorDto> handleLockingFailure(RuntimeException ex, WebRequest request) {
        log.warn("Concurrent modification detected: {}", ex.getMessage());
        return respond(
                HttpStatus.CONFLICT,
                ErrorCodes.CONCURRENT_MODIFICATION,
                "The resource was modified concurrently, please retry",
                request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorDto> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(
                HttpStatus.CONFLICT,
                ErrorCodes.DATA_INTEGRITY_VIOLATION,
                "The request conflicts with the current state of the data",
                request);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorDto> handlePropertyReference(PropertyReferenceException ex, WebRequest request) {
        return respond(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.INVALID_SORT_PROPERTY,
                "Unknown sort property: " + ex.getPropertyName(),
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR, "Internal server error", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(e -> new FieldViolation(e.getField(), messageOf(e))),
                        ex.getBindingResult().getGlobalErrors().stream()
                                .map(e -> new FieldViolation(e.getObjectName(), e.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(validationBody(violations, request));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                fieldName(result.getMethodParameter().getParameterName(), error),
                                error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(validationBody(violations, request));
    }

    /** Значение поля в JSON не того типа — ошибка этого поля; нечитаемое тело — ошибка всего запроса. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.getCause() instanceof MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()) {
            FieldViolation violation = new FieldViolation(jsonPath(mismatch), expectation(mismatch.getTargetType()));
            return ResponseEntity.badRequest().body(validationBody(List.of(violation), request));
        }
        return ResponseEntity.badRequest()
                .body(error(
                        HttpStatus.BAD_REQUEST,
                        ErrorCodes.MALFORMED_REQUEST,
                        "Request body is missing or is not valid JSON",
                        request));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name =
                ex instanceof MethodArgumentTypeMismatchException argument ? argument.getName() : ex.getPropertyName();
        FieldViolation violation = new FieldViolation(name, expectation(ex.getRequiredType()));
        return ResponseEntity.badRequest().body(validationBody(List.of(violation), request));
    }

    /** Текст берётся из описания ошибки Spring, а не из сообщения исключения: в нём бывают имена классов. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        String message;
        if (body instanceof ProblemDetail problem && problem.getDetail() != null) {
            message = problem.getDetail();
        } else if (ex instanceof ErrorResponse errorResponse
                && errorResponse.getBody().getDetail() != null) {
            message = errorResponse.getBody().getDetail();
        } else {
            HttpStatus resolved = HttpStatus.resolve(statusCode.value());
            message = resolved != null ? resolved.getReasonPhrase() : "Request failed";
        }
        ErrorDto error = error(statusCode, ErrorCodes.forStatus(statusCode), message, request);
        return ResponseEntity.status(statusCode).headers(headers).body(error);
    }

    /** Значение не удалось преобразовать к типу поля: сообщаем, какое ожидалось, без имён Java-классов. */
    private static String messageOf(FieldError error) {
        if (!error.isBindingFailure()) {
            return error.getDefaultMessage();
        }
        return error.contains(TypeMismatchException.class)
                ? expectation(error.unwrap(TypeMismatchException.class).getRequiredType())
                : expectation(null);
    }

    private static String expectation(@Nullable Class<?> type) {
        if (type == null) {
            return "has an invalid value";
        }
        Class<?> boxed = ClassUtils.resolvePrimitiveIfNecessary(type);
        if (boxed.isEnum()) {
            return "must be one of " + Arrays.toString(boxed.getEnumConstants());
        }
        if (boxed == UUID.class) {
            return "must be a UUID";
        }
        if (boxed == LocalDate.class) {
            return "must be a date in ISO format (yyyy-MM-dd)";
        }
        if (boxed == Boolean.class) {
            return "must be true or false";
        }
        if (Number.class.isAssignableFrom(boxed)) {
            return "must be a number";
        }
        return "has an invalid value";
    }

    private static String jsonPath(MismatchedInputException ex) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference reference : ex.getPath()) {
            if (reference.getFieldName() == null) {
                path.append('[').append(reference.getIndex()).append(']');
            } else {
                path.append(path.isEmpty() ? "" : ".").append(reference.getFieldName());
            }
        }
        return path.toString();
    }

    private ResponseEntity<ErrorDto> validationError(List<FieldViolation> violations, WebRequest request) {
        return ResponseEntity.badRequest().body(validationBody(violations, request));
    }

    private ErrorDto validationBody(List<FieldViolation> violations, WebRequest request) {
        return error(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed", request)
                .withViolations(violations);
    }

    private ResponseEntity<ErrorDto> respond(HttpStatus status, String code, String message, WebRequest request) {
        return ResponseEntity.status(status).body(error(status, code, message, request));
    }

    private static ErrorDto error(HttpStatusCode status, String code, String message, WebRequest request) {
        return ErrorDto.of(status, code, message, path(request), ErrorResponseWriter.currentTraceId());
    }

    private static String fieldName(@Nullable String parameterName, MessageSourceResolvable error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField();
        }
        return parameterName != null ? parameterName : "request";
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : request.getDescription(false);
    }
}
