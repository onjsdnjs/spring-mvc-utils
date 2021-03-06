package uk.co.caeldev.spring.mvc;

import com.google.common.base.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

import static uk.co.caeldev.spring.mvc.ETagBuilder.eTagBuilder;
import static com.google.common.net.HttpHeaders.*;
import static org.joda.time.LocalTime.now;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;
import static org.springframework.http.HttpStatus.PRECONDITION_REQUIRED;
import static org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;


public abstract class ResourceInterceptor extends HandlerInterceptorAdapter {

    private final static Logger LOGGER = LoggerFactory.getLogger(ResourceInterceptor.class);

    protected abstract String entityHashCodeByUUID(String uuid);

    protected abstract String getUUIDVariableName();

    protected Optional<String> resolveHashCode(final HttpServletRequest request) {
        final String uuid = getUUIDFromURI(request, getUUIDVariableName());
        LOGGER.info("Resolved UUID from URL: {}", uuid);

        try {
            return Optional.of(entityHashCodeByUUID(uuid));
        } catch(final IllegalArgumentException e) {
            LOGGER.info("Entity HashCode not Found");
            return Optional.absent();
        }
    }

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler) throws Exception {
        LOGGER.info("preHandle start");
        final Optional<String> hashCode = resolveHashCode(request);

        if (!hashCode.isPresent()) {
            LOGGER.debug("HashCode not present");
            return super.preHandle(request, response, handler);
        }

        final String token = eTagBuilder().value(hashCode.get()).build();
        LOGGER.debug("Token generated: {}", token);

        final HttpMethod method = HttpMethod.valueOf(request.getMethod());
        LOGGER.debug("HTTP METHOD {}", method);
        switch (method) {
            case GET:
                response.setHeader(ETAG, token);
                response.setHeader(LAST_MODIFIED, now().toString());
                break;
            case POST:
                final Optional<String> previousToken = Optional.fromNullable(request.getHeader(IF_MATCH));

                if (!previousToken.isPresent()) {
                    response.sendError(PRECONDITION_REQUIRED.value());
                    return false;
                }
                if (!previousToken.get().equals(token)) {
                    LOGGER.debug("Previous token: {}", previousToken.orNull());
                    response.sendError(PRECONDITION_FAILED.value());
                    return false;
                }
                break;
        }

        return super.preHandle(request, response, handler);
    }

    protected String getUUIDFromURI(final HttpServletRequest request, final String variableNameUUID) {
        LOGGER.info("Get UUID from URI");
        final UrlPathHelper urlPathHelper = new UrlPathHelper();
        final Map<String, String> pathVariables = (Map<String, String>)request.getAttribute(URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        final Map<String, String> decodedPathVariables = urlPathHelper.decodePathVariables(request, pathVariables);
        return decodedPathVariables.get(variableNameUUID);
    }
}