package com.vextis.workflow.api.graphql;

import com.vextis.workflow.application.WorkflowConflictException;
import com.vextis.workflow.application.WorkflowNotFoundException;
import com.vextis.workflow.domain.DuplicatePurchaseOrderException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/** Converts expected workflow refusals into safe, actionable public GraphQL errors. */
@Component
class WorkflowGraphQlErrorResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        if (exception instanceof WorkflowNotFoundException) {
            return error(environment, ErrorType.NOT_FOUND, exception.getMessage());
        }
        if (exception instanceof DuplicatePurchaseOrderException
                || exception instanceof WorkflowConflictException) {
            return error(environment, ErrorType.BAD_REQUEST, exception.getMessage());
        }
        return null;
    }

    private GraphQLError error(DataFetchingEnvironment environment, ErrorType type, String message) {
        return GraphqlErrorBuilder.newError(environment)
                .errorType(type)
                .message(message)
                .build();
    }
}
