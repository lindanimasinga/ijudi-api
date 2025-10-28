package io.curiousoft.izinga.ordermanagement.errorhandling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.stream.Collectors;

@ControllerAdvice
public class IjudiErrorHandler {

    // request mapping method omitted
    private static final Logger LOGGER = LoggerFactory.getLogger(IjudiErrorHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception e) {

        ErrorResponse errorResponse = new ErrorResponse();
        if( e instanceof MethodArgumentNotValidException) {
            errorResponse.setStatus(HttpStatus.BAD_REQUEST);
            errorResponse.setMessage(((MethodArgumentNotValidException) e)
                    .getBindingResult().
                    getFieldErrors()
                    .stream()
                    .map(FieldError::getDefaultMessage).collect(Collectors.toList()));
        } else {
            errorResponse.setMessage(e.getMessage());
            errorResponse.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        LOGGER.error(e.getMessage(), e);
        return new ResponseEntity<>(errorResponse, errorResponse.getStatus());
    }

}