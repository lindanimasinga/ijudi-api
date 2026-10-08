package io.curiousoft.izinga.ordermanagement.errorhandling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class IjudiErrorHandler {

    // request mapping method omitted
    private static final Logger LOGGER = LoggerFactory.getLogger(IjudiErrorHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e) {

        ErrorResponse errorResponse = new ErrorResponse();
        if (e instanceof MethodArgumentNotValidException) {
            errorResponse.setStatus(HttpStatus.BAD_REQUEST);
            errorResponse.setMessage(((MethodArgumentNotValidException) e)
                    .getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(FieldError::getDefaultMessage).collect(Collectors.toList()));
        } else if (e instanceof ResponseStatusException rse) {
            // Propagate the HTTP status from ResponseStatusException so that 403/400/422 responses
            // from service-layer guards (T-06, T-07, T-08, T-09) are returned with the correct code.
            errorResponse.setStatus((HttpStatus) rse.getStatusCode());
            String reason = rse.getReason();
            errorResponse.setMessage(reason != null ? Map.of("error", reason) : rse.getMessage());
            if (rse.getStatusCode().is5xxServerError()) {
                LOGGER.error(e.getMessage(), e);
            } else {
                LOGGER.warn(e.getMessage());
            }
        } else if (e instanceof NoResourceFoundException) {
            errorResponse.setMessage(e.getMessage());
            errorResponse.setStatus(HttpStatus.NOT_FOUND);
            LOGGER.warn(e.getMessage());
        } else {
            errorResponse.setMessage(e.getMessage());
            errorResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            LOGGER.error(e.getMessage(), e);
        }
        return new ResponseEntity<>(errorResponse, errorResponse.getStatus());
    }

}