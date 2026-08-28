# 5. A separate public Cloud Run service for the Live voice WebSocket

Date: 2026-08-27

## Status

Accepted. Applied to Terraform configuration only; no `terraform apply` has been
run against the hackathon project as part of this change.

## Context

Ask Vextis voice works like this: the browser calls `createLiveSession` on the
public Enterprise Core GraphQL API, receives an ephemeral session token plus a
`wss://` URL, opens a WebSocket to Agent Runtime and presents that token in the
first frame. Agent Runtime validates the token against Enterprise Core before
bridging audio to the Gemini Live model.

The transport half of that never worked in GCP. `vextis-agent-runtime` has no
`allUsers` invoker binding — the Terraform comment on `VEXTIS_LIVE_ENABLED`
said so explicitly — so Cloud Run rejects the browser handshake with 403 before
any application code runs. Only IAM-authenticated callers could reach the route.

Simply adding `allUsers` to `vextis-agent-runtime` would publish far more than
the voice socket. That service also mounts the Pub/Sub push endpoint (workflow
events) and `/v1/chat/complete` (Ask Vextis text), and it runs with an identity
holding the chat callback token and Cloud Storage access. Those are internal
service-to-service surfaces with their own credential model, and none of them
should become internet-reachable to enable a microphone button.

The problem is also not solvable with Cloud Run IAM alone: a browser WebSocket
cannot attach a Google-signed identity token to the handshake. Whatever we
deploy, invocation has to be open at the IAM layer and the ephemeral session
token has to be the real authorization boundary.

## Decision

Deploy a second Cloud Run service, `vextis-agent-runtime-live`, from the same
Agent Runtime image, and give the `allUsers` invoker binding to that service
only.

- **One route.** The Live service runs with `VEXTIS_LIVE_ENABLED=true` and
  `VEXTIS_PUBSUB_PUSH_ENABLED`, `VEXTIS_CHAT_ENABLED` and
  `VEXTIS_MEMORY_BANK_ENABLED` all false. `create_app` mounts routers behind
  those flags, so the public surface is `/health` plus `/v1/live/{session_id}`
  and nothing else.
- **Its own identity and its own credential.** `vextis-agent-live-hackathon`
  holds `roles/aiplatform.user`, invoker on the private Enterprise Core, and
  accessor on `vextis-live-gateway-token` — not on the agent-tools token, and
  not on the chat callback token or the storage grants the private runtime
  identity holds. Enterprise Core resolves that credential to the service
  identity `live-gateway-agent`, and V20 binds four read-only registry entries
  to it: a voice session can look up a customer, check stock, check credit
  standing and search the knowledge base, and nothing else. Reserving stock,
  issuing an invoice, recording a plan and writing to the knowledge base stay
  with `coordinator-agent`, whose credential exists only on the private runtime.
  Separating the credential without narrowing what it can do would have been a
  different string with identical authority.
- **The private service stays private.** `vextis-agent-runtime` keeps Pub/Sub
  push and the internal chat endpoint, now with `VEXTIS_LIVE_ENABLED=false` and
  its request timeout back to 300s.
- **The token never enters a URL.** The first WebSocket frame carries it, as
  before. Query-string tokens would land in Cloud Run request logs.
- **Enterprise Core remains the authority.** Tenant, session validity, expiry
  and the tool allowlist are all decided in Core; the gateway holds no policy.

This mirrors the split the repository already makes between
`vextis-enterprise-core` and `vextis-enterprise-core-public`, so it introduces a
deployment shape the project already reasons about rather than a new one.

### Abuse controls on the open surface

`max_instance_count` is 1 on the private runtime and 2 here, so an
unauthenticated flood could otherwise occupy every instance. The runtime now
bounds each connection:

| Control | Default | Purpose |
| --- | --- | --- |
| `VEXTIS_LIVE_AUTH_TIMEOUT_SECONDS` | 5 | Drops a socket that never authenticates |
| `VEXTIS_LIVE_MAX_TEXT_FRAME_BYTES` | 4096 | Caps the auth frame and control frames |
| `VEXTIS_LIVE_MAX_AUDIO_FRAME_BYTES` | 65536 | Caps a single PCM chunk |
| `VEXTIS_LIVE_MAX_SESSION_SECONDS` | 900 | Ceiling over the expiry Core issues |
| `max_instance_request_concurrency` | 4 | Caps concurrent sessions per instance |
| Cloud Run `timeout` | `live_max_session_seconds + 60` | Backstop under the application deadline |

The session deadline is derived from the `expiresAt` Enterprise Core returns
from validation — currently a 5 minute TTL — and enforced against the live
connection, not only at handshake time. A validation without a usable expiry is
refused rather than treated as unbounded.

## Alternatives considered

1. **Add `allUsers` to the existing `vextis-agent-runtime`.** One line, no new
   resources. Rejected: it publishes the Pub/Sub push endpoint and the internal
   chat endpoint alongside the voice socket, and leaves the public surface
   running under an identity with the chat callback token and bucket access.
2. **API Gateway or a Cloud Load Balancer in front of Agent Runtime.** A real
   gateway could terminate the public edge, apply Cloud Armor rate limiting and
   keep Cloud Run private. Rejected for now on cost and complexity: an external
   HTTPS load balancer carries a standing hourly charge against a hackathon
   budget, and API Gateway does not proxy WebSockets. Worth revisiting if the
   deployment ever needs WAF-grade protection; the Live service can be moved
   behind an internal-ingress boundary without touching application code.
3. **Tunnel the audio through the already-public Enterprise Core.** Keeps a
   single public surface. Rejected: it makes the Java transactional service
   proxy long-lived binary audio streams, holding a Core instance and a database
   pool slot per voice session, and puts an AI transport concern inside the
   transactional authority.
4. **Firebase Hosting rewrite to Cloud Run.** Rejected: Hosting rewrites do not
   support WebSocket upgrade.
5. **Token in the query string with IAM left closed.** Rejected outright: it
   does not solve the 403, and it would write a live credential into request
   logs.

## Consequences

- Ask Vextis voice becomes reachable from the browser in GCP for the first time.
- One more Cloud Run service exists. It scales to zero, so the standing cost is
  storage of an image tag that is already built; the running cost appears only
  while someone holds a voice session, and it is bounded by
  `max_instance_count = 2`, four sessions per instance, and the session ceiling.
  `cpu_idle = false` means a held connection is billed for allocated CPU, which
  is what makes the session ceiling a cost control and not just a hygiene one.
- Vertex AI Gemini Live minutes are now reachable by anyone who can obtain a
  session token, so the Firebase-authenticated `createLiveSession` mutation on
  Enterprise Core is the effective rate limiter for model spend. It currently
  applies no per-user quota; that is the next control to add if voice is ever
  exposed beyond a demo audience.
- The delivery workflow deploys both `vextis-agent-runtime` and
  `vextis-agent-runtime-live` from the same image tag, so they cannot drift.
- Rolling the exposure back is a single Terraform change: the service carries no
  `prevent_destroy` or `deletion_protection`, deliberately.
