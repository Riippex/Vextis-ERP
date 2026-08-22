from vextis_agents.tools.core_api.planning import PlanningResult, PlanningTool
from vextis_agents.workflows.order_to_cash.events import PurchaseOrderReceivedV2


class PurchaseOrderReceivedHandler:
    def __init__(self, planning_tool: PlanningTool) -> None:
        self._planning_tool = planning_tool

    async def handle(self, event: PurchaseOrderReceivedV2) -> PlanningResult:
        return await self._planning_tool.start_planning(event)
