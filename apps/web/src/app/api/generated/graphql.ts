/** Internal type. DO NOT USE DIRECTLY. */
type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
/** Internal type. DO NOT USE DIRECTLY. */
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
import { gql } from 'apollo-angular';
import { Injectable } from '@angular/core';
import * as Apollo from 'apollo-angular';
export type CreditStanding =
  | 'BLOCKED'
  | 'GOOD'
  | 'REVIEW';

export type ExecutionState =
  | 'COMPLETED'
  | 'FAILED'
  | 'PLANNING'
  | 'RECEIVED'
  | 'RUNNING'
  | 'WAITING_APPROVAL';

export type PlanningDepartment =
  | 'CRM_SALES'
  | 'FINANCE_BILLING'
  | 'INVENTORY_OPERATIONS';

export type ReadinessStatus =
  | 'READY'
  | 'REVIEW_REQUIRED';

export type ReceivePurchaseOrderInput = {
  customerName: string;
  documentUri: string;
  idempotencyKey: string;
  purchaseOrderNumber: string;
};

export type ServiceStatus =
  | 'UP';

export type TimelineEntryType =
  | 'APPROVAL_DECIDED'
  | 'APPROVAL_REQUESTED'
  | 'COMPLETED'
  | 'FAILED'
  | 'RECEIVED'
  | 'STATUS_CHANGED';

export type HealthQueryVariables = Exact<{ [key: string]: never; }>;


export type HealthQuery = { health: { status: ServiceStatus } };

export type MissionControlQueryVariables = Exact<{ [key: string]: never; }>;


export type MissionControlQuery = { missionControl: { executions: Array<{ id: string, purchaseOrderNumber: string, customerName: string, state: ExecutionState, correlationId: string, updatedAt: string }>, customers: Array<{ id: string, legalName: string, active: boolean }>, stockItems: Array<{ sku: string, availableQuantity: number }>, creditProfiles: Array<{ customerId: string, customerName: string, standing: CreditStanding, maxPaymentTermsDays: number }> } };

export type FindExecutionQueryVariables = Exact<{
  id: string | number;
}>;


export type FindExecutionQuery = { execution: { id: string, goal: string, state: ExecutionState, correlationId: string, createdAt: string, updatedAt: string, timeline: Array<{ sequence: number, type: TimelineEntryType, title: string, detail: string, occurredAt: string }>, plan: { summary: string, modelId: string, generatedAt: string, requestedPaymentTermsDays: number, orderLines: Array<{ sku: string, quantity: number }>, steps: Array<{ sequence: number, department: PlanningDepartment, objective: string, requiresApproval: boolean }> } | null, readiness: { evaluatedAt: string, checks: Array<{ department: PlanningDepartment, status: ReadinessStatus, detail: string }> } | null } | null };

export type ReceivePurchaseOrderMutationVariables = Exact<{
  input: ReceivePurchaseOrderInput;
}>;


export type ReceivePurchaseOrderMutation = { receivePurchaseOrder: { purchaseOrder: { id: string, purchaseOrderNumber: string, customerName: string, documentUri: string, receivedAt: string }, execution: { id: string, goal: string, state: ExecutionState, correlationId: string, createdAt: string, updatedAt: string, timeline: Array<{ sequence: number, type: TimelineEntryType, title: string, detail: string, occurredAt: string }>, plan: { summary: string, modelId: string, generatedAt: string, requestedPaymentTermsDays: number, orderLines: Array<{ sku: string, quantity: number }>, steps: Array<{ sequence: number, department: PlanningDepartment, objective: string, requiresApproval: boolean }> } | null, readiness: { evaluatedAt: string, checks: Array<{ department: PlanningDepartment, status: ReadinessStatus, detail: string }> } | null } } };

export const HealthDocument = gql`
    query Health {
  health {
    status
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class HealthGQL extends Apollo.Query<HealthQuery, HealthQueryVariables> {
    document = HealthDocument;

    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const MissionControlDocument = gql`
    query MissionControl {
  missionControl {
    executions {
      id
      purchaseOrderNumber
      customerName
      state
      correlationId
      updatedAt
    }
    customers {
      id
      legalName
      active
    }
    stockItems {
      sku
      availableQuantity
    }
    creditProfiles {
      customerId
      customerName
      standing
      maxPaymentTermsDays
    }
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class MissionControlGQL extends Apollo.Query<MissionControlQuery, MissionControlQueryVariables> {
    document = MissionControlDocument;

    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const FindExecutionDocument = gql`
    query FindExecution($id: ID!) {
  execution(id: $id) {
    id
    goal
    state
    correlationId
    createdAt
    updatedAt
    timeline {
      sequence
      type
      title
      detail
      occurredAt
    }
    plan {
      summary
      modelId
      generatedAt
      requestedPaymentTermsDays
      orderLines {
        sku
        quantity
      }
      steps {
        sequence
        department
        objective
        requiresApproval
      }
    }
    readiness {
      evaluatedAt
      checks {
        department
        status
        detail
      }
    }
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class FindExecutionGQL extends Apollo.Query<FindExecutionQuery, FindExecutionQueryVariables> {
    document = FindExecutionDocument;

    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const ReceivePurchaseOrderDocument = gql`
    mutation ReceivePurchaseOrder($input: ReceivePurchaseOrderInput!) {
  receivePurchaseOrder(input: $input) {
    purchaseOrder {
      id
      purchaseOrderNumber
      customerName
      documentUri
      receivedAt
    }
    execution {
      id
      goal
      state
      correlationId
      createdAt
      updatedAt
      timeline {
        sequence
        type
        title
        detail
        occurredAt
      }
      plan {
        summary
        modelId
        generatedAt
        requestedPaymentTermsDays
        orderLines {
          sku
          quantity
        }
        steps {
          sequence
          department
          objective
          requiresApproval
        }
      }
      readiness {
        evaluatedAt
        checks {
          department
          status
          detail
        }
      }
    }
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class ReceivePurchaseOrderGQL extends Apollo.Mutation<ReceivePurchaseOrderMutation, ReceivePurchaseOrderMutationVariables> {
    document = ReceivePurchaseOrderDocument;

    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }