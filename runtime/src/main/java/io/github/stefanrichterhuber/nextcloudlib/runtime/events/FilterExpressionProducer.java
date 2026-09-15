package io.github.stefanrichterhuber.nextcloudlib.runtime.events;

import org.jboss.logging.Logger;

import io.github.stefanrichterhuber.nextcloudlib.runtime.events.PHPMongoQueryParser.CompiledFilter;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;

public class FilterExpressionProducer {

    @Inject
    Logger logger;

    @Produces
    @FilterExpression("")
    CompiledFilter getFilter(InjectionPoint ip) {
        final FilterExpression filterExpression = ip.getQualifiers().stream().filter(a -> a instanceof FilterExpression)
                .map(a -> (FilterExpression) a).findFirst()
                .orElse(null);

        if (filterExpression != null) {
            final String filter = filterExpression.value();
            final String processedFilter = FilterExpressionResolver.resolve(filter);
            final CompiledFilter f = PHPMongoQueryParser.compile(processedFilter);
            return f;
        }
        throw new IllegalStateException("CompiledFilter injection without @FilterExpression qualifier");
    }
}
