---
name: vextis-hackathon-checklist
description: Audit Vextis against the current All Things Agentic Hackathon track, build, submission, demo, evidence, and bonus requirements. Use for readiness checks, gap analysis, or submission planning; do not publish, submit, share the repository, or create public content without explicit authorization.
---

# Vextis Hackathon Checklist

Turn the official competition requirements into an evidence-based readiness report. Do not accept planned work or unsupported claims as completed.

## Refresh the requirements

At review time, consult the current official Devpost Overview, Rules, Resources, FAQ, and submission form when accessible. Record the date checked and distinguish mandatory requirements, judging guidance, bonus opportunities, and informal recommendations. If official sources are unavailable, mark uncertain items for verification instead of guessing.

Use repository documentation and contracts as product context, but let current official hackathon sources decide eligibility and submission requirements. Do not expose or include files under `documents/` in public deliverables.

## Audit Vextis

Check the current official requirements and, when still applicable, verify evidence for:

- the declared **Fortified Enterprise Fleet** track and a clear explanation of how the agent fleet satisfies it;
- Gemini 3.5 or newer as a visible runtime model;
- at least one accepted Google agent framework such as Google ADK;
- at least one demonstrated Google Cloud infrastructure service;
- autonomous or asynchronous action beyond a standard chat loop;
- agent registry or lifecycle, durable execution and memory, identity and policy enforcement, and useful observability expected by the selected track;
- repository access, reproducible setup or deployment instructions, and technology disclosure;
- an architecture diagram consistent with the deployed system;
- a concise project description covering problem, value, functionality, technologies, data sources, findings, and learnings;
- a demo video within the current time guidance that shows the product working and proves the backend ran on Google Cloud;
- a hosted URL when available or a clearly documented alternative accepted by the rules;
- safe sample data and no exposed secrets, credentials, private documentation, or personal customer information;
- bonus integrations or public content only when they are complete and meet the current publication rules.

Also evaluate the judging story: operational utility and autonomy, architectural discipline, and clarity of the live demo. A technically present feature does not pass if a judge cannot see credible evidence of it.

## Evidence statuses

Assign every item one status:

- `PASS`: direct, reproducible evidence exists;
- `PARTIAL`: some evidence exists but a requirement or proof is incomplete;
- `MISSING`: required implementation or artifact is absent;
- `UNKNOWN`: the rule or evidence could not be verified.

For each item record the requirement, status, evidence path or URL, gap, and smallest next action. Validate that README claims, diagrams, contracts, deployed services, screenshots, and video narration agree with one another.

## Produce the readiness report

Lead with:

1. submission blockers;
2. high-impact judging gaps;
3. evidence that already passes;
4. bonus opportunities ranked by effort and demo value;
5. an ordered final verification sequence.

Do not publish a repository, upload a video, submit a Devpost entry, share access, or post on social media without explicit user authorization. If asked to implement missing items, keep each change within the accepted Vextis architecture and verify it before changing its status to `PASS`.
