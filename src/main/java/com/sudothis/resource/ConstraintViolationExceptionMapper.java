package com.sudothis.resource;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps Bean Validation ConstraintViolationException to 422 Unprocessable Entity.
 * Catches violations thrown by Hibernate Validator during persist/merge, in addition
 * to the explicit validator.validate() calls in AdminDataResource.
 */
@Provider
public class ConstraintViolationExceptionMapper
        implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
            errors.put(v.getPropertyPath().toString(), v.getMessage())
        );
        return Response.status(422)
            .entity(ApiResponse.validationError(errors))
            .build();
    }
}
