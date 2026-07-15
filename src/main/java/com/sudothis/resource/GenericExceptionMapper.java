package com.sudothis.resource;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * Last-resort handler for unhandled exceptions on admin endpoints.
 * Returns 500 with a sanitized message — never leaks stack traces to the client.
 * WebApplicationExceptions (which carry their own status code) are re-thrown so
 * JAX-RS can handle them normally.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(Exception ex) {
        if (ex instanceof WebApplicationException wae) {
            return wae.getResponse();
        }
        LOG.errorf(ex, "Unhandled exception on admin endpoint");
        return Response.serverError()
            .entity(ApiResponse.error("An internal error occurred"))
            .build();
    }
}
