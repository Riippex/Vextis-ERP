/** Internal type. DO NOT USE DIRECTLY. */
type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
/** Internal type. DO NOT USE DIRECTLY. */
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
import { gql } from 'apollo-angular';
import { Injectable } from '@angular/core';
import * as Apollo from 'apollo-angular';
export type ExecutionState =
  | 'COMPLETED'
  | 'FAILED'
  | 'PLANNING'
  | 'RECEIVED'
  | 'RUNNING'
  | 'WAITING_APPROVAL';

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

export type ReceivePurchaseOrderMutationVariables = Exact<{
  input: ReceivePurchaseOrderInput;
}>;


export type ReceivePurchaseOrderMutation = { receivePurchaseOrder: { purchaseOrder: { id: string, purchaseOrderNumber: string, customerName: string, documentUri: string, receivedAt: string }, execution: { id: string, goal: string, state: ExecutionState, correlationId: string, createdAt: string, updatedAt: string, timeline: Array<{ sequence: number, type: TimelineEntryType, title: string, detail: string, occurredAt: string }> } } };

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