/** Internal type. DO NOT USE DIRECTLY. */
type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
/** Internal type. DO NOT USE DIRECTLY. */
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
import { gql } from 'apollo-angular';
import { Injectable } from '@angular/core';
import * as Apollo from 'apollo-angular';
export type Maybe<T> = T | null;
export type InputMaybe<T> = Maybe<T>;
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
  ID: { input: string; output: string; }
  String: { input: string; output: string; }
  Boolean: { input: boolean; output: boolean; }
  Int: { input: number; output: number; }
  Float: { input: number; output: number; }
};

export type CreateLiveSessionInput = {
  conversationId: Scalars['ID']['input'];
};

export type Execution = {
  __typename?: 'Execution';
  correlationId: Scalars['String']['output'];
  createdAt: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  state: ExecutionState;
  updatedAt: Scalars['String']['output'];
};

export enum ExecutionState {
  Completed = 'COMPLETED',
  Failed = 'FAILED',
  Planning = 'PLANNING',
  Received = 'RECEIVED',
  Running = 'RUNNING',
  WaitingApproval = 'WAITING_APPROVAL'
}

export type Health = {
  __typename?: 'Health';
  status: ServiceStatus;
};

export type LiveSession = {
  __typename?: 'LiveSession';
  expiresAt: Scalars['String']['output'];
  id: Scalars['ID']['output'];
  sessionToken: Scalars['String']['output'];
  websocketUrl: Scalars['String']['output'];
};

export type Mutation = {
  __typename?: 'Mutation';
  closeLiveSession: Scalars['Boolean']['output'];
  createLiveSession: LiveSession;
};


export type MutationCloseLiveSessionArgs = {
  id: Scalars['ID']['input'];
};


export type MutationCreateLiveSessionArgs = {
  input: CreateLiveSessionInput;
};

export type Query = {
  __typename?: 'Query';
  execution?: Maybe<Execution>;
  health: Health;
};


export type QueryExecutionArgs = {
  id: Scalars['ID']['input'];
};

export enum ServiceStatus {
  Up = 'UP'
}

export type ServiceStatus =
  | 'UP';

export type HealthQueryVariables = Exact<{ [key: string]: never; }>;


export type HealthQuery = { health: { status: ServiceStatus } };

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