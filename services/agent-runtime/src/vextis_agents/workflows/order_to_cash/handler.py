from vextis_agents.tools.core_api.planning import PlanningResult, PlanningTool
from vextis_agents.workflows.order_to_cash.events import PurchaseOrderReceivedV2
from vextis_agents.workflows.order_to_cash.planning import PlanGenerator


class PurchaseOrderReceivedHandler:
    def __init__(self, planning_tool: PlanningTool, plan_generator: PlanGenerator) -> None:
        self._planning_tool = planning_tool
        self._plan_generator = plan_generator

    async def handle(self, event: PurchaseOrderReceivedV2) -> PlanningResult:
        context = await self._planning_tool.start_planning(event)
        if context.approval_status is not None:
            return PlanningResult(
                id=context.id,
                state=context.state,
                correlationId=context.correlation_id,
                updatedAt=context.updated_at,
            )
        if not context.readiness_evaluated and context.state == "PLANNING":
            plan = await self._plan_generator.generate(context)
            await self._planning_tool.record_plan(
                event, context, plan, self._plan_generator.model_id
            )
        if not context.readiness_evaluated:
            await self._planning_tool.evaluate_readiness(event, context)
        recommendation = (
            f"Proceed with purchase order {context.purchase_order_number} only after "
            "a human reviews the CRM, inventory, and finance evidence."
        )
        return await self._planning_tool.request_approval(event, context, recommendation)
