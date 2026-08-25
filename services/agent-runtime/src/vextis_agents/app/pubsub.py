import logging

from fastapi import APIRouter, Request, Response, status

from vextis_agents.tools.core_api.planning import (
    CoreToolRejectedError,
    CoreToolUnavailableError,
)
from vextis_agents.workflows.order_to_cash.events import (
    InvalidEventError,
    PurchaseOrderReceivedV2,
    decode_domain_event,
)
from vextis_agents.workflows.order_to_cash.handler import (
    PurchaseOrderReceivedHandler,
    WorkflowApprovalDecidedHandler,
)
from vextis_agents.workflows.order_to_cash.planning import PlanGenerationUnavailableError

logger = logging.getLogger(__name__)


def create_pubsub_router(
    purchase_order_handler: PurchaseOrderReceivedHandler,
    approval_handler: WorkflowApprovalDecidedHandler,
) -> APIRouter:
    router = APIRouter()

    @router.post("/events/pubsub", status_code=status.HTTP_204_NO_CONTENT)
    async def receive_pubsub_push(request: Request) -> Response:
        try:
            event = decode_domain_event(await request.body())
        except InvalidEventError:
            logger.warning("Rejected malformed Pub/Sub event without invoking agent tools")
            return Response(status_code=status.HTTP_204_NO_CONTENT)

        try:
            if isinstance(event, PurchaseOrderReceivedV2):
                await purchase_order_handler.handle(event)
            else:
                await approval_handler.handle(event)
        except CoreToolRejectedError:
            logger.warning(
                "Enterprise Core rejected event %s for execution %s",
                event.event_id,
                event.payload.execution_id,
            )
            return Response(status_code=status.HTTP_204_NO_CONTENT)
        except CoreToolUnavailableError as exception:
            logger.warning(
                "Transient planning failure for event %s dependency=enterprise_core reason=%s",
                event.event_id,
                exception,
            )
            return Response(status_code=status.HTTP_503_SERVICE_UNAVAILABLE)
        except PlanGenerationUnavailableError as exception:
            logger.warning(
                "Transient planning failure for event %s dependency=gemini reason=%s",
                event.event_id,
                exception,
            )
            return Response(status_code=status.HTTP_503_SERVICE_UNAVAILABLE)

        return Response(status_code=status.HTTP_204_NO_CONTENT)

    return router
