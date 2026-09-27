// Friendly aliases over the generated OpenAPI schema types. No field is invented here — every type
// points at a `components["schemas"]` entry from the three generated descriptors.
import type { components as App } from './generated/application'
import type { components as Acct } from './generated/account'
import type { components as Asst } from './generated/assistant'

type A = App['schemas']
type C = Acct['schemas']
type S = Asst['schemas']

// --- application-service ---
export type Me = A['MeResponse']
export type Overview = A['Overview']
export type Attention = A['Attention']
export type ListItem = A['ListItem']
export type ListResponse = A['ListResponse']
export type LiveVersion = A['LiveVersion']
export type Psi = A['Psi']
export type TrendPoint = A['TrendPoint']
export type Proposed = A['Proposed']

export type Detail = A['Detail']
export type Base = A['Base']
export type Current = A['Current']
export type Breakdown = A['Breakdown']
export type Bureau = A['Bureau']
export type TrailItem = A['TrailItem']
export type Override = A['Override']
export type Reason = A['Reason']
export type Fraud = A['Fraud']
export type PolicyCheck = A['PolicyCheck']
export type ScorePart = A['ScorePart']
export type ReproduceView = A['ReproduceView']

export type QueueItem = A['QueueItem']
export type OverrideStats = A['OverrideStats']
export type OverrideBand = A['Band']
export type ReviewRequest = A['ReviewRequest']
export type RecordResult = A['RecordResult']

export type SystemStatus = A['Status']
export type Service = A['Service']
export type RedecisionItem = A['RedecisionItem']
export type IdempotencyKeyView = A['IdempotencyKeyView']
export type BureauPullView = A['BureauPullView']
export type BureauFaultRequest = A['BureauFaultRequest']
export type BureauFaultResult = A['BureauFaultResult']

export type VersionsResponse = A['VersionsResponse']
export type VersionView = A['VersionView']
export type RuleConfig = A['RuleConfig']
export type BandLimit = A['BandLimit']
export type LiveView = A['LiveView']
export type CreateVersionRequest = A['CreateVersionRequest']
export type JobView = A['JobView']
export type ReplayRequest = A['ReplayRequest']
export type FlipPage = A['FlipPage']
export type FlipItem = A['FlipItem']
export type FlipDetail = A['FlipDetail']
export type SideOutcome = A['SideOutcome']
export type SideDecision = A['SideDecision']
export type DecisionOutcome = A['Decision']
export type CompareView = A['CompareView']
export type Difference = A['Difference']
export type ShadowResults = A['ShadowResults']
export type ShadowItem = A['Item']
export type Side = A['Side']

export type VerifyResult = A['VerifyResult']
export type LedgerStats = A['Stats']
export type DemoUpdate = A['DemoUpdate']
export type TamperSimulation = A['TamperSimulation']

export type DriftReport = A['Report']
export type DriftBin = A['Bin']
export type ApplicationRequest = A['ApplicationRequest']

// The engine's pipeline[] (SPEC §3 step 8) is not part of the OpenAPI response object model
// (the 201 body is written directly), so its shape comes from the SPEC, not a schema.
export interface PipelineStep {
  step: string
  label: string
  status: 'OK' | 'WARN' | 'SKIPPED'
  ms: number
  detail?: string
}
export interface SubmitResult {
  applicationId: string
  status: string
  outcome?: string
  score?: number | null
  creditLimit?: number
  reasonCodes?: { code: string; description: string }[]
  ruleVersion?: string
  ledgerSeq?: number
  decidedAt?: string
  bureau?: { pullId: string; reused: boolean } | null
  pipeline?: PipelineStep[]
}

// Ledger list rows (GET /api/v1/ledger). Not modelled as a named schema in the descriptor, so the
// shape follows SPEC §15 exactly.
export interface LedgerRow {
  seq: number
  kind: string
  source: string
  applicationId?: string | null
  displayName?: string | null
  note?: string | null
  outcome?: string | null
  ruleVersion?: string | null
  createdAt: string
  prevHash: string
  hash: string
}
export interface LedgerPage {
  items: LedgerRow[]
  page: number
  size: number
  total: number
}

// Shadow block on the decision detail (Detail.shadow is `unknown` because it is optional jsonb).
export interface ShadowBlock {
  version: string
  outcome: string
  score?: number
  creditLimit?: number
  agrees: boolean
}

// The replay report stored in replay_job.report (JobView.report is `unknown` jsonb). Shape from
// SPEC §10's documented JSON schema.
export interface ReplaySide {
  version: string
  approvals: number
  approvalRate: number
  exposure: number
  expectedLossObserved: number
  expectedLossSimulated?: number
}
export interface ReplaySegment {
  band: string
  n: number
  baselineApprovals: number
  candidateApprovals: number
  baselineLoss: number
  candidateLoss: number
}
export interface ReplayReport {
  n: number
  baseline: ReplaySide
  candidate: ReplaySide
  matrix: number[][]
  flips: number
  flipsCapped: boolean
  limitChanges: number
  outcomeUnknown: { count: number; exposure: number }
  immature: { count: number }
  segments: ReplaySegment[]
  assumptions: { ccf: number; lgd: number; pdSource: string }
  labels?: { simulatedIsSimulation: boolean }
}

// --- account-service ---
export type AccountSummary = C['AccountSummary']
export type AccountPage = C['AccountPage']
export type AccountDetail = C['AccountDetail']
export type StatementView = C['StatementView']
export type TransactionView = C['TransactionView']
export type CliRequestView = C['CliRequestView']
export type CliRequestBody = C['CliRequestBody']
export type CliRequestResult = C['CliRequestResult']
export type CollectionItem = C['CollectionItem']
export type CollectionsSummary = C['CollectionsSummary']
export type BucketSummary = C['BucketSummary']
export type CollectionActionRequest = C['CollectionActionRequest']
export type SimulateMonthRequest = C['SimulateMonthRequest']

// --- assistant-service ---
export type AssistantStatus = S['Status']
export type ToolView = S['ToolView']
export type ChatRequest = S['ChatRequest']
export type ChatResponse = S['ChatResponse']
export type ToolCall = S['ToolCall']

export type Role = 'STRATEGIST' | 'APPROVER' | 'UNDERWRITER' | 'AUDITOR' | 'CLIENT' | 'ASSISTANT'
