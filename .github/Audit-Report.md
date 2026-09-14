# Phlodgate Zero-Trust Audit Report

Date: 2026-09-14
Repository: `Hydraulic-Phlodgate`
Branch: `master`
Commit audited: `8d6e4d0`

## Actual Product Mission

Allow Bedrock players to participate in modded Java-server content through generated Bedrock resources, Java-authoritative compatibility bridges, Geyser transport, and honest capability reporting.

Resource conversion alone is not the product. Full support requires authoritative mutation, persistence, synchronization, transport, and real Bedrock observation.

## Executive Verdict

Phlodgate is a substantial compatibility-engineering framework with several executable Java-side capability slices. It is not a universal mod-compatibility runtime and is not release-ready.

The strongest verified boundary is:

`resource/index discovery -> compatibility analysis -> compiled dispatch -> selected generic Java bridge -> unit/injected transport evidence`

The unverified or incomplete boundary is:

`arbitrary mod behavior -> complete runtime mutation -> persistence -> real Bedrock action -> client observation -> reconnect correctness`

## Evidence Levels

- E0: documentation only
- E1: static code
- E2: unit test
- E3: integration test
- E4: live Java/server runtime
- E5: Geyser/transport handoff
- E6: real Bedrock client observation

No E6 evidence was available in this audit.

## Audit Boundary And Coverage

Active repository root: `D:/Downloads/Hydraulic-mod/Hydraulic-mod`.

Excluded from deep semantic analysis: build output, Gradle caches, Fabric runtime data, temporary files, generated logs, nested `org` dependency material, and sibling worktrees (`_runtime_validation`, `_push_worktree`, `ship-recovery`). These remain classified as generated/runtime/dependency material and are not treated as production source.

Inventory evidence:

- Files detected in workspace tree: 28,608
- Excluded generated/runtime/dependency files: 15,400
- Active scoped files: 13,208
- Git-tracked files: 554
- Active production Java files: 254
- Active test Java files: 112
- Production filesystem traversal matches: 15

This report is a focused zero-trust execution audit, not a claim that every generated binary, archive, cache entry, or third-party runtime file received semantic review. Full file-by-file reconciliation of all 13,208 scoped files remains an explicit audit limitation.

## Pass 1 Findings

### Confirmed strengths

- Generic item, fluid, energy, mixed-resource, automation, machine-processing, runtime-target, tracing, and Geyser handoff classes exist and are exercised by shared tests. Evidence: E2/E3.
- Dynamic block-entity discovery is now reachable through the active `BlockEntity.setLevel` lifecycle mixin. Evidence: E1 and successful Fabric transformation/runtime bootstrap, E4.
- Concrete menu-type discovery is wired through `ServerPlayer.openMenu`. Evidence: E1/E4 bootstrap path.
- Machine synchronization is attached to Minecraft's concrete `LevelChunk$BoundTickingBlockEntity.tick` seam and filters active Geyser sessions by level/range. Evidence: E1/E4 bootstrap path; no E6.
- Analyzer-created compatibility objects carry explicit `implementationMaturity`; legacy construction defaults to `UNKNOWN`. Evidence: E1/E2.
- Malformed persisted handoff entries are warning-only and regression-tested. Evidence: E2.
- Fabric/Java 25 compilation and the full shared test suite pass in the current validation shell. Evidence: E2/E3.

### Confirmed gaps

1. The production action router implements block-use item insertion/extraction only. There are no equivalent compiled Bedrock-originated contracts for tank, energy, menu button, property, mode, or general entity actions. Evidence: E1.
2. `RuntimeTargetDiscovery` can execute fluid and energy transfers when called, but no Bedrock action contract reaches those operations. Evidence: E1/E2.
3. Analyzer output can report unsupported block-entity, fluid, menu, and entity behavior while still producing presentation or fallback artifacts. This is correctly conservative in those analyzers, but the repository contains documentation and representative tables that can be read more broadly than runtime evidence supports. Evidence: E1.
4. Universal indexing is incomplete. Active production code still traverses roots or directories in `AutomaticRecipeDiscovery`, `MetadataLoader`, corpus loaders/importers, companion packaging/fingerprinting, `PackManager`, and pack packaging. Some are intentionally separate config/corpus/cache domains, but the one-pass claim is not globally true. Evidence: E1.
5. Live recipe-manager extraction inspects and counts opaque entries but does not normalize opaque runtime recipes into executable machine plans. Evidence: E1/E4.
6. Pack validation remains open: the latest artifact reports 69 valid and 56 invalid packs, including 50 missing outputs and 15 missing required textures. Evidence: E4/generated artifact.
7. The dev world cannot complete startup because an existing saved scoreboard contains duplicate `phlodgate_bridge`; this is a runtime-data failure before normal world operation, not proof of successful clean-world startup. Evidence: E4.
8. No real official Bedrock client evidence exists for action, synchronization, persistence, reconnect, or gameplay semantics. Evidence: absence of E6 artifacts; existing tests stop at injected transport.
9. Test fixtures prove generic contracts for specially constructed objects, not arbitrary third-party mod semantics. Evidence: E2/static fixture inspection.
10. Mod-specific adapters are not justified as complete compatibility adapters until generic capability maturity and client validation gates pass. Evidence: E1/E2.

### Post-Audit Remediation Status (2026-09-14)

- Finding 5 is partially remediated: resource JSON and live `RecipeManager` entries now converge on
	typed `RecipeIR`; Minecraft's registry-aware recipe codec normalizes allowlisted serializers, and
	unsupported entries produce `RECIPE_RUNTIME_UNKNOWN`. The final runtime inspected 8,934 entries,
	normalized 3,126, and rejected 5,808 as unknown. Automatic machine-to-recipe association remains open.
- Finding 7 is remediated for clean startup: scoped duplicate-objective recovery reached a clean
	Fabric/Geyser startup. Same-world restart and physical-client reconnect evidence remain separate gates.
- Finding 9 is partially remediated: an adapter-unknown mutable inventory now proves automatic live
	binding and Java mutation through production dispatch. This remains fixture evidence, not arbitrary-mod
	or physical Bedrock proof.

## Pass 2 Adversarial Challenge

- Pass 1 claim: generic transfer bridges are executable. Attack: verify factory selection, operation direction, simulation, mutation, and tests. Result: CONFIRMED for constructed runtime bridge shapes at E2; NOT universal arbitrary-mod proof.
- Pass 1 claim: lifecycle discovery is live. Attack: check active mixin registration and runtime bootstrap. Result: CONFIRMED at E4 for the lifecycle seam; dynamic plan breadth remains limited by available runtime object contracts.
- Pass 1 claim: synchronization is integrated. Attack: follow ticker -> dirty state -> session selection -> encoder -> Geyser transport. Result: PARTIALLY CONFIRMED at E1/E4; no physical client observation, no proof for every machine state category.
- Pass 1 claim: compatibility reports indicate support. Attack: compare analyzer levels, runtime requirements, and action reachability. Result: CONFIRMED that reports are structured; REJECTED if interpreted as universal gameplay support.
- Pass 1 claim: full runtime startup was verified. Attack: inspect the latest server log. Result: REVISED: conversion and Geyser bootstrap reached E4, but saved-world initialization failed on duplicate scoreboard state.
- Pass 1 claim: pack conversion is release-ready. Attack: inspect current validation artifact. Result: REJECTED: 56 invalid packs remain.

## Capability Matrix

| Capability | Discovery | IR/compile | Dispatch | Java mutation | Persistence | Sync/transport | Client | Overall |
|---|---|---|---|---|---|---|---|---|
| Presentation | Yes | Yes | Yes | N/A | N/A | Pack handoff | E6 missing | SERVER-VERIFIED / client unverified |
| Item transfer | Yes for supported shapes | Yes | Yes | Yes | Fixture/runtime dependent | E5 injected handoff | E6 missing | EXECUTABLE BUT UNVERIFIED |
| Fluid transfer | Yes for supported shapes | Yes | Yes | Yes in bridge substrate | Runtime dependent | E5 substrate | E6 missing | PARTIALLY EXECUTABLE |
| Energy transfer | Yes for supported shapes | Yes | Yes | Yes in bridge substrate | Runtime dependent | E5 substrate | E6 missing | PARTIALLY EXECUTABLE |
| Machine processing | Metadata/fixture contracts | Yes | Yes for compiled plans | Yes for generic item/mixed paths | Fixture/runtime dependent | Partial | E6 missing | PARTIALLY EXECUTABLE |
| Automation | Request-oriented substrate | Yes | Yes | Yes for supported targets | Runtime dependent | Partial | E6 missing | PARTIALLY EXECUTABLE |
| Block entities | Lifecycle discovery and patch facts | Partial | Partial | Patch/data paths only | Not generic | Partial | E6 missing | PARTIAL |
| Menus | Menu-type evidence and fallback metadata | Partial | Fallback dispatch | Generic behavior incomplete | Not generic | Partial | E6 missing | PARTIAL |
| Entity interaction | Metadata prompt path | Partial | Prompt path | General mutation incomplete | Not proven | Partial | E6 missing | PARTIAL |
| Entity behavior | Limited analysis | Limited | No universal executor | No | No | No | E6 missing | UNSUPPORTED |
| Networking | Limited facts | Partial | Transport substrate | Action subset only | No generic protocol | E5 subset | E6 missing | PARTIAL |
| Recipes | JSON and specialized serializers | Yes for supported schemas | Machine consumers subset | Yes for normalized recipes | Runtime dependent | Partial | E6 missing | PARTIAL |
| World fluids | Limited transfer/presentation | Partial | Partial | Java authority exists | Runtime dependent | Partial | E6 missing | UNSUPPORTED/APPROXIMATED |

## Looks Complete But Is Not

- A compatibility score is not full gameplay support.
- A compiled runtime plan is not proof that every required operation is executable.
- A generic transfer factory is not automatic support for every mod capability shape.
- Geyser packet handoff is not Bedrock client observation.
- A resource pack is not a working machine, menu, fluid system, or entity.
- The companion scoreboard signal is not behavior-pack execution.
- A passing fixture test is not arbitrary third-party mod compatibility.
- Recipe-manager enumeration is not recipe normalization.
- A fallback menu or bucket texture is not complete behavior.
- Server startup through conversion is not clean-world or reconnect validation.

## Top Risks

1. CRITICAL: No E6 Level 4–7 Bedrock validation.
2. HIGH: 56 current pack-validation failures.
3. HIGH: Explicit action routing covers only block-use item insertion/extraction.
4. HIGH: Generic menu, block-entity behavior, entity behavior, and network semantics remain incomplete.
5. HIGH: Opaque recipe managers cannot be normalized automatically.
6. HIGH: Universal one-pass indexing is not yet true across all resource/config domains.
7. MEDIUM: Saved scoreboard state can prevent clean runtime startup.
8. MEDIUM: Fixture-backed bridges may overstate generality if evidence maturity is ignored.
9. MEDIUM: Existing TODO/unsupported notes show deliberate incomplete conversion semantics.
10. MEDIUM: Persistence/reconnect behavior is not proven for generic bridges.

## Scores

These are evidence-weighted assessments, not mathematical product metrics:

- Engineering health: 70/100. Build and shared tests are strong; generated validation and runtime-world stability are not.
- Architectural completeness: 65/100. The intended layers exist, but several IR and index boundaries remain partial.
- Implementation completeness: 52/100. Generic substrate slices execute; major capability families remain incomplete.
- Runtime completeness: 35/100. Server-side execution and injected transport exist for subsets only.
- End-to-end verification: 15/100. No physical Bedrock-client evidence.
- Mission capability: 30/100. The product can convert and bridge selected capability slices, not generally operate arbitrary modded content.
- Release readiness: NOT READY.

## Remediation Plan

### Phase 1: Evidence and false-positive control

- Promote maturity only from explicit validation records; never from scores or analyzer presence.
- Add report counts by maturity and capability domain.
- Mark every representative mod table with evidence level and maturity.
- Exit: no report path can imply client support from presentation or transport alone.

### Phase 2: Normalized action contracts

- Define typed `BedrockAction` and compiled action plans for fluid, energy, menu button, property, mode, and entity operations.
- Route all actions through Java server-thread validation, transaction, dirty-state, and transport paths.
- Add malformed, stale, wrong-dimension, wrong-target, permission, partial-commit, and disconnect tests.
- Exit: each advertised action has a concrete mutation and rollback/failure contract.

### Phase 3: Universal machine and persistence proof

- Complete normalized machine profiles across inventory, recipes, fluid, energy, automation, progress, and state.
- Bind save/load and chunk reload state for mixed-resource fixtures.
- Exit: restart/reload tests preserve authoritative state and synchronize deltas.

### Phase 4: Pack validation correction

- Separate intentionally empty non-content packs from conversion failures without marking them valid content packs.
- Resolve texture dependency omissions for itemcollectors/trashcans and add regression fixtures.
- Exit: every registered pack is either a valid non-empty artifact or an explicit skipped/unsupported result, with no false output-missing failures.

### Phase 5: Bedrock client proof

- Use a clean compatibility test world.
- Run Level 4 client observation, Level 5 action-to-Java mutation, Level 6 synchronization, and Level 7 save/reconnect tests on an official Bedrock client.
- Record trace ID, Java mutation, packet handoff, client observation, and reconnect result.
- Exit: only manually observed capabilities receive `CLIENT_VERIFIED`.

### Phase 6: Second audit and release gate

- Re-run the audit against active source only.
- Reconcile tracked-file inventory, generated outputs, runtime logs, test evidence, and documentation.
- Require zero unresolved critical issues and explicit accepted limitations.

## Final Status

The repository is ahead of upstream Hydraulic in architecture and generic runtime infrastructure, but it is not fully functional under the requested definition. The strongest honest classification is:

`PARTIALLY EXECUTABLE / SERVER-VERIFIED FOR SELECTED SLICES / TRANSPORT-VERIFIED FOR SELECTED SLICES / CLIENT-OBSERVED: NOT AVAILABLE / RELEASE: NOT READY`.
