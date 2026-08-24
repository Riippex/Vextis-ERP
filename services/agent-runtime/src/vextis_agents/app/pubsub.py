import logging

from fastapi import APIRouter, Request, Response, status

from vextis_agents.tools.core_api.planning import (
    CoreToolRejectedError,
    CoreToolUnavailableError,
)
from vextis_agents.workflows.order_to_cash.events import (
    InvalidEventError,
    decode_purchase_order_event,
)
from vextis_agents.workflows.order_to_cash.handler import PurchaseOrderReceivedHandler
from vextis_agents.workflows.order_to_cash.planning import PlanGenerationUnavailableError

logger = logging.getLogger(__name__)


def create_pubsub_router(handler: PurchaseOrderReceivedHandler) -> APIRouter:
    router = APIRouter()

    @router.post("/events/pubsub", status_code=status.HTTP_204_NO_CONTENT)
    async def receive_pubsub_push(request: Request) -> Response:
        try:
            event = decode_purchase_order_event(await request.body())
        except InvalidEventError:
            logger.warning("Rejected malformed Pub/Sub event without invoking agent tools")
            return Response(status_code=status.HTTP_204_NO_CONTENT)

        try:
            await handler.handle(event)
        except CoreToolRejectedError:
            logger.warning(
                "Enterprise Core rejected event %s for execution %s",
                event.event_id,
                event.payload.execution_id,
            )
            return Response(status_code=status.HTTP_204_NO_CONTENT)
        except (CoreToolUnavailableError, PlanGenerationUnavailableError):
            logger.warning("Transient planning failure for event %s", event.event_id)
            return Response(status_code=status.HTTP_503_SERVICE_UNAVAILABLE)

        return Response(status_code=status.HTTP_204_NO_CONTENT)

    return router
