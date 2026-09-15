# Full Implementation Plan

## Authority

This ledger is the post-audit execution baseline. It does not promote a capability because a type, report field, or unit test exists. A capability is complete only after authoritative Java mutation, persistence, synchronization, and physical Bedrock evidence are recorded.

Status vocabulary: `OPEN`, `IN_PROGRESS`, `SERVER_VERIFIED`, `TRANSPORT_VERIFIED`, `CLIENT_VERIFIED`, `BLOCKED`.

## Workstreams

| Workstream | Current status | Blocking evidence | Exit condition |
| --- | --- | --- | --- |
| Clean runtime world and scoreboard lifecycle | SERVER_VERIFIED | Fresh-world startup and clean shutdown passed; reload/reconnect still require a client session | Fresh world starts, reloads, reconnects, and restarts with exactly one `phlodgate_bridge` objective |
| Automatic third-party live binding | IN_PROGRESS | Adapter-unknown runtime inventory is discovered, verified, bound, executed, unbound, and rebound in production dispatch tests; generic serialized block-entity state observation now runs through lifecycle binding; real third-party block-entity and client round trips remain open | Real mod block entity completes discover-to-client round trip across unload, reload, and restart |
| Universal resource index | OPEN | Config, corpus, recipe, packaging, and cache discovery paths still need classification and consolidation | Every discovery scan is indexed or explicitly runtime-owned |
| Recipe-manager normalization | IN_PROGRESS | Resource and codec-backed runtime entries produce `RecipeIR`; unknown/custom semantics produce `RECIPE_RUNTIME_UNKNOWN`; automatic machine-to-recipe association remains open | Every observed entry has typed evidence and each machine binds only compatible executable RecipeIR records |
| Normalized action pipeline | IN_PROGRESS | Production action routing is narrow | Typed action decoding, validation, Java-thread execution, transaction result, and sync trace for each supported action |
| Fluid actions | IN_PROGRESS | Metadata-declared exact bucket exchange reaches an executable live tank and emits a numeric container-property update; persistence and physical Bedrock evidence remain open | Fill/drain simulation and commit with persistence and sync evidence |
| Energy actions | IN_PROGRESS | Receive/extract block-use contract now executes through the shared energy bridge and records traceable property state; persistence and physical client evidence remain open | Receive/extract simulation and commit with persistence and sync evidence |
| Menu actions | SERVER_VERIFIED | Server-thread packet routing, explicit button/toggle contracts, transaction evidence, authoritative resync, tests, compilation, and live startup are verified; physical Bedrock execution is unverified | Live Java menu action contracts and synchronized results observed from Bedrock |
| Entity actions | OPEN | Prompt mapping exists; authoritative use/attack/mount actions do not | Live entity resolution, mutation, and sync evidence |
| Machine lifecycle and persistence | IN_PROGRESS | Generic processing and ephemeral serialized block-entity state observation exist; restart/chunk-unload and full mutable-state proof are pending | Mid-cycle save/restart preserves all inputs, resources, recipe, progress, and state |
| Failure and rollback | IN_PROGRESS | Transfer and machine transaction tests pass; live lifecycle failure matrix is incomplete | No loss, duplication, or half-commit across all listed failures |
| Universal automation | IN_PROGRESS | Request-oriented transfer exists; network lifecycle proof is pending | Source-to-machine-to-output route persists and synchronizes |
| Pack validation classification | IN_PROGRESS | Typed `FailureClassification` is emitted for validator findings and malformed JSON precedence is regression-tested; full third-party corpus remediation remains open | Every finding is classified as generated-invalid, unsupported, or validator defect |
| Physical Bedrock validation | BLOCKED | No accessible official Bedrock client/device evidence in this environment | E1-E10 artifacts recorded by a human-operated client |
| Real-mod validation | IN_PROGRESS | A 231-mod Java 25 corpus run now provides startup, recipe, and pack-artifact evidence; object behavior, persistence, and physical Bedrock evidence remain open | Adapter-free arbitrary-mod test plus evidence-backed matrix |
| Final zero-trust audit | OPEN | Must run after implementation and validation workstreams | No unsupported completion claims and all classifications have evidence |

## Capability Round-Trip Matrix

Status values: `PASS`, `PARTIAL`, `OPEN`, and `BLOCKED`. `PASS` applies only to the named stage.

| Capability | Discovery | Classification | Runtime contract | Live binding | Java execution | State persistence | Java to Bedrock sync | Bedrock to Java action | Physical Bedrock validation |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Item inventory/transfer | PASS | PASS | PASS | PASS | PASS | PARTIAL | PARTIAL | PARTIAL | BLOCKED |
| Fluid transfer | PASS | PASS | PASS | PASS | PASS | OPEN | TRANSPORT | PARTIAL | BLOCKED |
| Energy transfer | PASS | PASS | PASS | PASS | PASS | OPEN | OPEN | OPEN | BLOCKED |
| Machine processing | PASS | PASS | PASS | PARTIAL | PASS | PARTIAL | PARTIAL | PARTIAL | BLOCKED |
| Menu/container | PASS | PASS | PASS | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL | BLOCKED |
| Entity interaction | PARTIAL | PARTIAL | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | BLOCKED |
| Automation | PASS | PASS | PASS | PARTIAL | PARTIAL | OPEN | PARTIAL | OPEN | BLOCKED |

No row is a support claim unless every required stage for that object is `PASS`.

## Prioritized Completion Plan

| Priority | System | Required closure |
| --- | --- | --- |
| P0.1 | Fluid actions | Bedrock fill/drain/transfer intent reaches a discovered live tank, commits atomically on the Java thread, persists, and synchronizes authoritative state |
| P0.2 | Energy actions/state | Bounded receive/extract operations mutate Java storage and expose a concrete synchronized client state |
| P0.3 | Entity actions | Use, attack, mount, and dismount resolve live entities and execute through authoritative Java handlers |
| P0.4 | Persistence | Save, shutdown, restart, rebind, and compare state for every promoted mutable contract |
| P0.5 | Automation lifecycle | Prove chunk unload/reload, break/replacement, dimension changes, disconnect, and restart without stale state, loss, or duplication |
| P0.6 | Pack remediation | Classify and resolve every release-blocking pack result as a generic defect, adapter requirement, or explicit unsupported case |
| P1 | Physical Bedrock | Record manual E1-E10 client evidence for every promoted gameplay round trip |
| P1 | Real third-party mods | Validate Create first, then widen the evidence-backed mod matrix |

Each P0 implementation must use the same compiled generic contract for the fixture and an arbitrary
discovered runtime object. Identifier-specific fixture branching does not satisfy the exit condition.

## 2026-09-14 Fluid Action Evidence

- `interaction.fluid.*` metadata now compiles into a strict `FluidBlockUseActionPlan` for
	`DRAIN_HELD_CONTAINER` and `FILL_HELD_CONTAINER` actions. The contract requires explicit input and
	output item identifiers, fluid identity, tank, and exact amount; malformed identifiers, negative
	values, and same-item exchanges are rejected.
- `BedrockRuntimeActionRouter` resolves the live tank through `RuntimeTargetDiscovery`, simulates the
	full transfer, commits only an exact result, exchanges the held Java item only after the tank commit,
	and compensates the tank mutation if that exchange fails. The transaction records traceable tank state
	and, when declared, `container.property.<id>` state for existing `ContainerSetDataPacket` delivery.
- The bundled fluid-machine fixture declares a water-bucket drain contract and property `0` projection.
	Java 25 focused router, plan, and bridge tests plus `:fabric:compileJava` passed. This is transport-path
	evidence only; live client observation and restart persistence remain open.

## 2026-09-14 Block-Entity State Evidence

- `BlockEntityStateSynchronizer` serializes live block-entity custom state through Minecraft's
	supported `saveWithoutMetadata(level.registryAccess())` API and keeps only an ephemeral previous
	snapshot for comparison.
- `RuntimeLifecycleCoordinator` observes every discovered block entity, emits a `block_entity.state`
	delta through `SessionAutoFlushCoordinator` when the serialized state changes, and clears the
	snapshot on lifecycle unbind or coordinator reinstall.
- `BlockEntityStateSynchronizerTest`, `:shared:compileJava`, and the focused shared test pass. This
	proves authoritative state observation and delta production, not arbitrary mutation, persistence
	across a real restart, generic Bedrock packet encoding, or client observation.

## 2026-09-14 Energy Action Evidence

- `EnergyBlockUseActionPlan` accepts only bounded `RECEIVE`/`EXTRACT` contracts with positive
	amounts, optional side, and optional property ID; malformed facts fail closed.
- `BedrockRuntimeActionRouter.executeEnergyAction` uses the existing target discovery and energy
	transaction path, requires an exact moved amount, carries the action trace into dirty state,
	and optionally emits a `container.property.<id>` projection for Geyser transport.
- Focused Java 25 tests pass for parser rejection, exact action execution, transfer state changes,
	fluid regressions, and pack validation. Persistence, live server fixture execution, and physical
	Bedrock observation are not promoted by this evidence.

## 2026-09-14 Pack Classification Evidence

- `PackValidationReport.FailureClassification` now checks explicit generated-output defect codes
	before generic path heuristics. A malformed JSON entry is therefore classified as
	`GENERIC_GENERATOR_DEFECT`, even when its archive path contains the word `path`.
- `PackValidatorTest` asserts this behavior. Pack validation still records a failed pack and
	continues the pipeline; classification does not claim that every third-party asset is repaired.

## 2026-09-14 Third-Party Corpus Evidence

- A Java 25 `:fabric:runServer` run against the attached corpus loaded 231 Fabric mods and reached
	Minecraft/Geyser readiness on UDP `19132` before clean shutdown.
- Hydraulic compiled 3,128 datapack recipes, normalized 3,126 runtime recipe entries, and marked
	5,808 entries `RECIPE_RUNTIME_UNKNOWN` under the existing fail-closed serializer policy.
- The persisted pack report contained 125 records: 69 valid and 56 invalid. The invalid set
	included 50 missing-output records, which the corrected classifier maps to
	`NO_CONVERTIBLE_OUTPUT`, and 15 missing selected-texture records; 316 long-path warnings were
	recorded across generated packs.
- This promotes real-mod startup and artifact evidence to `IN_PROGRESS`; it does not promote any
	Create, Mekanism, Thermal, AE2, or other ecosystem object to runtime or client compatibility.

## Release Readiness Gates

- Every critical capability has discovery, classification, compiled-contract, binding, Java mutation,
	persistence, synchronization, transport, and physical-client evidence, or is explicitly unsupported.
- Java 25 compilation and all active-module tests pass with no new warnings from touched subsystems.
- Malformed and adversarial inputs fail closed without server-thread crashes, unauthorized mutation,
	duplication, item/fluid/energy loss, stale sessions, or cross-player state delivery.
- Generated-pack failures are classified and all release-blocking generator defects are resolved.
- The real-mod matrix records object-level evidence; no ecosystem-level claim is inferred from a generic
	substrate or successful pack conversion alone.
- Final zero-trust review finds no placeholder path, inflated maturity, or transport-as-client claim.

## Milestone: Universal Live Binding

Current evidence:

- `PASS`: adapter-unknown runtime object discovery, capability evidence, executable bridge verification,
	generic adapter selection, live binding, simulated operation, authoritative Java mutation, explicit
	unbind/rebind, static-plan augmentation, and stale dynamic-plan replacement.
- `PARTIAL`: Minecraft block-entity lifecycle wiring through `setLevel`, `setRemoved`, `clearRemoved`,
	and ticking. Fabric transformation compiles, but a real third-party block entity has not completed
	the full runtime/client ladder.
- `OPEN`: persisted machine recovery, dirty-state-to-client proof for the arbitrary object, Geyser
	reconnect evidence, and physical Bedrock observation.

The milestone remains `IN_PROGRESS` until all open stages pass.

## 2026-09-14 Recipe Normalization Evidence

- `RecipeIR` is the authoritative portable record for resource JSON and codec-backed live
	`RecipeManager` entries. Existing machine consumers receive only its executable projection.
- Live recipes are serialized through Minecraft 26.2's registry-aware `Recipe.CODEC`. Only
	built-in Minecraft serializers and explicitly registered specialized serializers are eligible
	for execution; all others become `RECIPE_RUNTIME_UNKNOWN` evidence.
- Catalysts are preserved separately and matched without consumption. Unresolved tags,
	component predicates, alternative singular ingredients, environmental/kinetic requirements,
	conditions, and chance outputs cannot be projected to an executable machine recipe.
- Reload ingestion publishes immutable complete snapshots, preventing removed recipes from
	surviving a successful reload and preventing readers from observing partial replacement.
- Automatic association between a live machine and the correct normalized recipe set remains open.
- Live Java 25 evidence: 8,934 `RecipeManager` entries were inspected; 3,126 were normalized
	through allowed codec/serializer contracts and 5,808 were classified `RECIPE_RUNTIME_UNKNOWN`.
	Minecraft and Geyser reached `Done`, and shutdown saved all worlds cleanly. Waystones custom-block
	registration still fails independently on negative mining destructibility without aborting startup.

## 2026-09-14 Live Session Lifecycle Evidence

- `RuntimeLifecycleCoordinator` now reconciles the `SessionAutoFlushCoordinator` against
	Geyser's active connection snapshot on each machine synchronization tick.
- New sessions receive a pipeline, while disconnected sessions are removed with their
	dirty-state and transport references. Position filtering still prevents delivery to
	unrelated players.
- The focused `SessionAutoFlushCoordinatorTest` suite passes. This is server-side lifecycle
	evidence only; it does not promote transport handoff or physical Bedrock observation.

## 2026-09-14 Menu Round-Trip Evidence

- Geyser-originated Java container click and button packets are validated after Minecraft's
	server-thread scheduling barrier, then delegated to vanilla `ServerGamePacketListenerImpl` for
	authoritative mutation. Stale sessions/state IDs, invalid slots, invalid button encodings, and
	unsupported clone actions fail closed and trigger a full Java menu resync.
- Successful actions are measured from before/after Java menu snapshots with exact item/component
	hashes and a traceable `MenuTransaction`. Completion and rejection both synchronize through
	`AbstractContainerMenu.broadcastFullState()` rather than speculative Geyser cache mutation.
- Explicit `menu.button.<id>=button|toggle` metadata compiles into `MenuActionPlan`. The bundled
	`menu_machine` fixture declares button `0` as a persistent enabled-state toggle that controls its
	Java progress tick.
- Focused router, analyzer, metadata-bootstrap, and auto-flush tests pass under Java 25. A live
	Fabric run reached Minecraft `Done` and started Geyser on UDP `19132` with no menu-mixin apply or
	injection failure. No official Bedrock client has yet invoked and observed the fixture, so Java
	execution, persistence, synchronization, and Bedrock-to-Java stages remain `PARTIAL` rather than
	complete.

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
