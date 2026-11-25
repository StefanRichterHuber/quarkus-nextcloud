package io.github.stefanrichterhuber.nextcloudlib.nativetests;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.jboss.logging.Logger;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Returns the stack trace of a failed scenario to the test, so errors raised
 * inside the native executable (e.g. missing reflection registrations) are
 * visible in the test report.
 */
@Provider
public class ScenarioFailureMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOG = Logger.getLogger(ScenarioFailureMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        LOG.error("Scenario failed", exception);
        final StringWriter sw = new StringWriter();
        if (exception instanceof WebApplicationException wae) {
            // The response of a WebApplicationException thrown by a REST client must not
            // be returned from a server endpoint (rejected since Quarkus 4), so only its
            // status is passed on. The "status code" prefix lets the test retry 503s.
            final int status = wae.getResponse().getStatus();
            sw.append("status code: ").append(String.valueOf(status)).append('\n');
            exception.printStackTrace(new PrintWriter(sw));
            return Response.status(status).type(MediaType.TEXT_PLAIN).entity(sw.toString()).build();
        }
        exception.printStackTrace(new PrintWriter(sw));
        return Response.serverError().type(MediaType.TEXT_PLAIN).entity(sw.toString()).build();
    }
}
