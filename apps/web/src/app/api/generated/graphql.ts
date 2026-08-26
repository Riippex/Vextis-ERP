/** Internal type. DO NOT USE DIRECTLY. */
type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
/** Internal type. DO NOT USE DIRECTLY. */
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
import { gql } from 'apollo-angular';
import { Injectable } from '@angular/core';
import * as Apollo from 'apollo-angular';
export type AgentRegistryStatus =
  | 'ACTIVE'
  | 'DRAFT'
  | 'RETIRED';

export type ApprovalDecision =
  | 'APPROVE'
  | 'REJECT';

export type ApprovalStatus =
  | 'APPROVED'
  | 'PENDING'
  | 'REJECTED';

export type AskVextisMessageInput = {
  conversationId?: string | number | null | undefined;
  message: string;
};

export type AskVextisMessageKind =
  | 'TEXT'
  | 'VOICE_TRANSCRIPT';

export type AskVextisMessageSender =
  | 'ASSISTANT'
  | 'USER';

export type CreditStanding =
  | 'BLOCKED'
  | 'GOOD'
  | 'REVIEW';

export type DecideApprovalInput = {
  approvalId: string | number;
  decision: ApprovalDecision;
  executionId: string | number;
  idempotencyKey: string;
  reason?: string | null | undefined;
};

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

export type PreparePurchaseOrderUploadInput = {
  contentType: string;
  fileName: string;
  sizeBytes: number;
};

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

export type SetStockAvailabilityInput = {
  availableQuantity: number;
  sku: string;
};

export type StockReservationStatus =
  | 'FULFILLED'
  | 'RELEASED'
  | 'RESERVED';

export type TimelineEntryType =
  | 'APPROVAL_DECIDED'
  | 'APPROVAL_REQUESTED'
  | 'COMPLETED'
  | 'FAILED'
  | 'RECEIVED'
  | 'STATUS_CHANGED';

export type UpsertCreditProfileInput = {
  customerId: string | number;
  maxPaymentTermsDays: number;
  standing: CreditStanding;
};

export type UpsertCustomerInput = {
  active: boolean;
  id?: string | number | null | undefined;
  legalName: string;
};

export type HealthQueryVariables = Exact<{ [key: string]: never; }>;


export type HealthQuery = { health: { status: ServiceStatus } };

export type UpsertCustomerMutationVariables = Exact<{
  input: UpsertCustomerInput;
}>;


export type UpsertCustomerMutation = { upsertCustomer: { id: string, legalName: string, active: boolean } };

export type UpsertCreditProfileMutationVariables = Exact<{
  input: UpsertCreditProfileInput;
}>;


export type UpsertCreditProfileMutation = { upsertCreditProfile: { customerId: string, customerName: string, standing: CreditStanding, maxPaymentTermsDays: number } };

export type SetStockAvailabilityMutationVariables = Exact<{
  input: SetStockAvailabilityInput;
}>;


export type SetStockAvailabilityMutation = { setStockAvailability: { sku: string, availableQuantity: number } };

export type MissionControlQueryVariables = Exact<{ [key: string]: never; }>;


export type MissionControlQuery = { missionControl: { agents: Array<{ agentId: string, version: string, displayName: string, department: string, purpose: string, framework: string, modelId: string, promptVersion: string, serviceIdentity: string, status: AgentRegistryStatus, capabilities: Array<string>, allowedTools: Array<string> }>, executions: Array<{ id: string, purchaseOrderNumber: string, customerName: string, state: ExecutionState, correlationId: string, updatedAt: string }>, customers: Array<{ id: string, legalName: string, active: boolean }>, stockItems: Array<{ sku: string, availableQuantity: number }>, stockReservations: Array<{ id: string, orderId: string, sku: string, quantity: number, status: StockReservationStatus, createdAt: string }>, creditProfiles: Array<{ customerId: string, customerName: string, standing: CreditStanding, maxPaymentTermsDays: number }>, executionVolumeByDepartment: Array<{ department: PlanningDepartment, count: number }> } };

export type DecideApprovalMutationVariables = Exact<{
  input: DecideApprovalInput;
}>;


export type DecideApprovalMutation = { decideApproval: { id: string, goal: string, state: ExecutionState, correlationId: string, createdAt: string, updatedAt: string, timeline: Array<{ sequence: number, type: TimelineEntryType, title: string, detail: string, occurredAt: string }>, plan: { summary: string, modelId: string, generatedAt: string, requestedPaymentTermsDays: number, orderLines: Array<{ sku: string, quantity: number }>, steps: Array<{ sequence: number, department: PlanningDepartment, objective: string, requiresApproval: boolean }> } | null, readiness: { evaluatedAt: string, checks: Array<{ department: PlanningDepartment, status: ReadinessStatus, detail: string }> } | null, approval: { id: string, recommendation: string, status: ApprovalStatus, requestedBy: string, requestedAt: string, expiresAt: string, decidedBy: string | null, decidedAt: string | null, reason: string | null } | null } };

export type FindExecutionQueryVariables = Exact<{
  id: string | number;
}>;


export type FindExecutionQuery = { execution: { id: string, goal: string, state: ExecutionState, correlationId: string, createdAt: string, updatedAt: string, timeline: Array<{ sequence: number, type: TimelineEntryType, title: string, detail: string, occurredAt: string }>, plan: { summary: string, modelId: string, generatedAt: string, requestedPaymentTermsDays: number, orderLines: Array<{ sku: string, quantity: number }>, steps: Array<{ sequence: number, department: PlanningDepartment, objective: string, requiresApproval: boolean }> } | null, readiness: { evaluatedAt: string, checks: Array<{ department: PlanningDepartment, status: ReadinessStatus, detail: string }> } | null, approval: { id: string, recommendation: string, status: ApprovalStatus, requestedBy: string, requestedAt: string, expiresAt: string, decidedBy: string | null, decidedAt: string | null, reason: string | null } | null } | null };

export type PreparePurchaseOrderUploadMutationVariables = Exact<{
  input: PreparePurchaseOrderUploadInput;
}>;


export type PreparePurchaseOrderUploadMutation = { preparePurchaseOrderUpload: { uploadUrl: string, documentUri: string, expiresAt: string, formFields: Array<{ name: string, value: string }> } };

export type ReceivePurchaseOrderMutationVariables = Exact<{
  input: ReceivePurchaseOrderInput;
}>;


export type ReceivePurchaseOrderMutation = { receivePurchaseOrder: { purchaseOrder: { id: string, purchaseOrderNumber: string, customerName: string, documentUri: string, receivedAt: string }, execution: { id: string, goal: string, state: ExecutionState, correlationId: string, createdAt: string, updatedAt: string, timeline: Array<{ sequence: number, type: TimelineEntryType, title: string, detail: string, occurredAt: string }>, plan: { summary: string, modelId: string, generatedAt: string, requestedPaymentTermsDays: number, orderLines: Array<{ sku: string, quantity: number }>, steps: Array<{ sequence: number, department: PlanningDepartment, objective: string, requiresApproval: boolean }> } | null, readiness: { evaluatedAt: string, checks: Array<{ department: PlanningDepartment, status: ReadinessStatus, detail: string }> } | null, approval: { id: string, recommendation: string, status: ApprovalStatus, requestedBy: string, requestedAt: string, expiresAt: string, decidedBy: string | null, decidedAt: string | null, reason: string | null } | null } } };

export type AskVextisMutationVariables = Exact<{
  input: AskVextisMessageInput;
}>;


export type AskVextisMutation = { askVextis: { conversationId: string, messageId: string, reply: string, createdAt: string } };

export type AskVextisConversationQueryVariables = Exact<{
  id: string | number;
}>;


export type AskVextisConversationQuery = { askVextisConversation: { id: string, messages: Array<{ id: string, sender: AskVextisMessageSender, content: string, kind: AskVextisMessageKind, createdAt: string }> } | null };

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
export const UpsertCustomerDocument = gql`
    mutation UpsertCustomer($input: UpsertCustomerInput!) {
  upsertCustomer(input: $input) {
    id
    legalName
    active
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class UpsertCustomerGQL extends Apollo.Mutation<UpsertCustomerMutation, UpsertCustomerMutationVariables> {
    document = UpsertCustomerDocument;

    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const UpsertCreditProfileDocument = gql`
    mutation UpsertCreditProfile($input: UpsertCreditProfileInput!) {
  upsertCreditProfile(input: $input) {
    customerId
    customerName
    standing
    maxPaymentTermsDays
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class UpsertCreditProfileGQL extends Apollo.Mutation<UpsertCreditProfileMutation, UpsertCreditProfileMutationVariables> {
    document = UpsertCreditProfileDocument;

    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const SetStockAvailabilityDocument = gql`
    mutation SetStockAvailability($input: SetStockAvailabilityInput!) {
  setStockAvailability(input: $input) {
    sku
    availableQuantity
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class SetStockAvailabilityGQL extends Apollo.Mutation<SetStockAvailabilityMutation, SetStockAvailabilityMutationVariables> {
    document = SetStockAvailabilityDocument;

    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const MissionControlDocument = gql`
    query MissionControl {
  missionControl {
    agents {
      agentId
      version
      displayName
      department
      purpose
      framework
      modelId
      promptVersion
      serviceIdentity
      status
      capabilities
      allowedTools
    }
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
    stockReservations {
      id
      orderId
      sku
      quantity
      status
      createdAt
    }
    creditProfiles {
      customerId
      customerName
      standing
      maxPaymentTermsDays
    }
    executionVolumeByDepartment {
      department
      count
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
export const DecideApprovalDocument = gql`
    mutation DecideApproval($input: DecideApprovalInput!) {
  decideApproval(input: $input) {
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
    approval {
      id
      recommendation
      status
      requestedBy
      requestedAt
      expiresAt
      decidedBy
      decidedAt
      reason
    }
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class DecideApprovalGQL extends Apollo.Mutation<DecideApprovalMutation, DecideApprovalMutationVariables> {
    document = DecideApprovalDocument;

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
    approval {
      id
      recommendation
      status
      requestedBy
      requestedAt
      expiresAt
      decidedBy
      decidedAt
      reason
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
export const PreparePurchaseOrderUploadDocument = gql`
    mutation PreparePurchaseOrderUpload($input: PreparePurchaseOrderUploadInput!) {
  preparePurchaseOrderUpload(input: $input) {
    uploadUrl
    documentUri
    expiresAt
    formFields {
      name
      value
    }
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class PreparePurchaseOrderUploadGQL extends Apollo.Mutation<PreparePurchaseOrderUploadMutation, PreparePurchaseOrderUploadMutationVariables> {
    document = PreparePurchaseOrderUploadDocument;

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
      approval {
        id
        recommendation
        status
        requestedBy
        requestedAt
        expiresAt
        decidedBy
        decidedAt
        reason
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
export const AskVextisDocument = gql`
    mutation AskVextis($input: AskVextisMessageInput!) {
  askVextis(input: $input) {
    conversationId
    messageId
    reply
    createdAt
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class AskVextisGQL extends Apollo.Mutation<AskVextisMutation, AskVextisMutationVariables> {
    document = AskVextisDocument;

    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }
export const AskVextisConversationDocument = gql`
    query AskVextisConversation($id: ID!) {
  askVextisConversation(id: $id) {
    id
    messages {
      id
      sender
      content
      kind
      createdAt
    }
  }
}
    `;

  @Injectable({
    providedIn: 'root'
  })
  export class AskVextisConversationGQL extends Apollo.Query<AskVextisConversationQuery, AskVextisConversationQueryVariables> {
    document = AskVextisConversationDocument;

    constructor(apollo: Apollo.Apollo) {
      super(apollo);
    }
  }