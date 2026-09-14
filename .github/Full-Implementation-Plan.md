# Full Implementation Plan

## Authority

This ledger is the post-audit execution baseline. It does not promote a capability because a type, report field, or unit test exists. A capability is complete only after authoritative Java mutation, persistence, synchronization, and physical Bedrock evidence are recorded.

Status vocabulary: `OPEN`, `IN_PROGRESS`, `SERVER_VERIFIED`, `TRANSPORT_VERIFIED`, `CLIENT_VERIFIED`, `BLOCKED`.

## Workstreams

| Workstream | Current status | Blocking evidence | Exit condition |
| --- | --- | --- | --- |
| Clean runtime world and scoreboard lifecycle | SERVER_VERIFIED | Fresh-world startup and clean shutdown passed; reload/reconnect still require a client session | Fresh world starts, reloads, reconnects, and restarts with exactly one `phlodgate_bridge` objective |
| Universal resource index | OPEN | Config, corpus, recipe, packaging, and cache discovery paths still need classification and consolidation | Every discovery scan is indexed or explicitly runtime-owned |
| Recipe-manager normalization | OPEN | Opaque runtime recipes are observed but not executable | Normalized `RecipeIR` or explicit `UNKNOWN` evidence for every observed entry |
| Normalized action pipeline | IN_PROGRESS | Production action routing is narrow | Typed action decoding, validation, Java-thread execution, transaction result, and sync trace for each supported action |
| Fluid actions | OPEN | Fluid transfer substrate exists, Bedrock action contract does not | Fill/drain simulation and commit with persistence and sync evidence |
| Energy actions | OPEN | Energy transfer substrate exists, Bedrock action contract does not | Receive/extract simulation and commit with persistence and sync evidence |
| Menu actions | OPEN | Menu fallback exists; button/property/mode actions do not | Live Java menu action contracts and synchronized results |
| Entity actions | OPEN | Prompt mapping exists; authoritative use/attack/mount actions do not | Live entity resolution, mutation, and sync evidence |
| Machine lifecycle and persistence | IN_PROGRESS | Generic processing exists; restart/chunk-unload proof is pending | Mid-cycle save/restart preserves all inputs, resources, recipe, progress, and state |
| Failure and rollback | OPEN | Transaction unit tests exist; live lifecycle failure matrix is incomplete | No loss, duplication, or half-commit across all listed failures |
| Universal automation | IN_PROGRESS | Request-oriented transfer exists; network lifecycle proof is pending | Source-to-machine-to-output route persists and synchronizes |
| Pack validation classification | OPEN | Existing invalid-pack set needs root-cause categories | Every finding is classified as generated-invalid, unsupported, or validator defect |
| Physical Bedrock validation | BLOCKED | No accessible official Bedrock client/device evidence in this environment | E1-E10 artifacts recorded by a human-operated client |
| Real-mod validation | BLOCKED | Universal contracts and physical evidence are prerequisites | Adapter-free arbitrary-mod test plus evidence-backed matrix |
| Final zero-trust audit | OPEN | Must run after implementation and validation workstreams | No unsupported completion claims and all classifications have evidence |

## Rules

- Do not add supported-mod claims while a required universal contract is `OPEN`.
- `UNKNOWN` is the only valid result for an observed recipe or action whose semantics cannot be normalized safely.
- Transport handoff is not client observation.
- Existing user worlds are never deleted or repaired destructively by validation tooling; clean validation uses a separate world path.
- Every implementation change updates this ledger, the runtime matrix, the validation matrix, and affected user documentation in the same change set.

## 2026-09-14 Scoreboard Recovery Evidence

- The previous `fabric/run/world` was preserved as `fabric/run/world-scoreboard-audit-backup-20260914`; no user world data was deleted.
- Minecraft failed while loading duplicate serialized `phlodgate_bridge` objectives, before Hydraulic companion installation runs.
- `ScoreboardObjectiveMixin` now reuses an existing objective only for Hydraulic's canonical signal, preserving Minecraft's normal duplicate protection for all other objective names.
- Java 25 `:shared:compileJava` passed. A clean generated world reached `Done` and `Started Geyser` with no duplicate-objective or Hydraulic mixin error, then shut down cleanly.
- A same-world restart remains pending because the Windows Architectury transformer retained the generated shared development jar after shutdown, causing the subsequent `:shared:jar` task to fail before a server process launched. This packaging/process issue is not evidence of a scoreboard regression.
