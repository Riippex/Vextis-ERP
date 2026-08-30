package com.vextis.livesession.api.graphql;

import com.vextis.livesession.application.LiveSessionQuotaExceededException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Surfaces a quota refusal as a client error the browser can act on, rather than
 * the INTERNAL_ERROR an unhandled exception would become. The message carries
 * the limit and no session, tenant or actor detail.
 */
@Component
class LiveSessionErrorResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        if (exception instanceof LiveSessionQuotaExceededException quota) {
            return GraphqlErrorBuilder.newError(environment)
                    .errorType(ErrorType.FORBIDDEN)
                    .message(quota.getMessage())
                    .build();
        }
        return null;
    }
}
