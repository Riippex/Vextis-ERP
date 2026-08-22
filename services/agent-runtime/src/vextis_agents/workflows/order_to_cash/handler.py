from vextis_agents.tools.core_api.planning import PlanningResult, PlanningTool
from vextis_agents.workflows.order_to_cash.events import PurchaseOrderReceivedV2
from vextis_agents.workflows.order_to_cash.planning import PlanGenerator


class PurchaseOrderReceivedHandler:
    def __init__(self, planning_tool: PlanningTool, plan_generator: PlanGenerator) -> None:
        self._planning_tool = planning_tool
        self._plan_generator = plan_generator

    async def handle(self, event: PurchaseOrderReceivedV2) -> PlanningResult:
        context = await self._planning_tool.start_planning(event)
        if context.state != "PLANNING":
            return PlanningResult(
                id=context.id,
                state=context.state,
                correlationId=context.correlation_id,
                updatedAt=context.updated_at,
            )
        plan = await self._plan_generator.generate(context)
        return await self._planning_tool.record_plan(
            event,
            context,
            plan,
            self._plan_generator.model_id,
        )
