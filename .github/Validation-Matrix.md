# Validation Matrix

## Evidence levels

| Level | Meaning | Current state |
| --- | --- | --- |
| E1 | Unit and contract tests | PARTIAL: selected runtime slices covered |
| E2 | Java/Fabric integration | SERVER-VERIFIED for selected fixtures |
| E3 | Packet and runtime handoff | TRANSPORT-VERIFIED for inventory/property packet slices |
| E4 | Official Bedrock client connection and pack observation | BLOCKED: no client evidence |
| E5 | Real-mod regression corpus | IN PROGRESS: 231-mod startup and pack/recipe artifacts verified; behavior claims remain open |
| E6 | Persistence/reconnect/restart on a physical client | BLOCKED |
| E7 | Final zero-trust release audit | OPEN |

## Required physical evidence record

Each completed client test must record timestamp, Java/Minecraft/Bedrock/Geyser/Hydraulic versions, commit, mod and object, action, expected result, observed result, screenshots or packet trace where available, and a human-attested result. Server-side encoding, transport handoff, or a unit-test bot cannot populate the client-observed field.

## Current blockers

- The previous development world contained scoreboard residue and was preserved as `fabric/run/world-scoreboard-audit-backup-20260914`. A fresh generated world reached Geyser-ready startup and clean shutdown after the scoped scoreboard-load recovery fix; same-world restart is pending a Windows Architectury dev-jar lock workaround.
- The current environment has no accessible official Bedrock client/device for E4-E6.
- Java 25 focused tests and Fabric compilation pass for the live-binding slice. A live server/client
	round trip is still required before promotion beyond server evidence.
- Pack-validation findings now carry typed root-cause classifications, including generator defects,
  invalid manifests, invalid paths, missing assets, and unsupported content. Full third-party pack
  remediation and corpus-wide review remain release gates.

## Fluid Action Evidence

- `FluidBlockUseActionPlanTest`, `FluidContainerBridgeTest`, and
	`BedrockRuntimeActionRouterTest` cover strict contract parsing, identity checks, exact transfer,
	partial-transfer rejection, trace-bearing property projection, and rollback when the held-item
	exchange fails.
- Java 25 focused shared tests and `:fabric:compileJava` passed. The action path uses the existing
	server-authoritative block-use route and Geyser `ContainerSetDataPacket` handoff.
- Not proven: physical Bedrock client receipt or rendering, block-entity persistence across restart,
	and arbitrary third-party fluid-container item semantics.

## Energy Action Evidence

- `EnergyBlockUseActionPlanTest` covers positive bounded receive contracts and malformed, unbounded,
	and negative-property rejection.
- `BedrockRuntimeActionRouterTest` covers exact energy receive execution, trace propagation, and
	`container.property.<id>` dirty-state projection through the shared runtime action path.
- `EnergyTransferTransactionTest` covers simulation, exact commit, and rollback behavior for the
	underlying energy bridge.
- Not proven: a live third-party energy block, restart persistence, official Bedrock observation,
	or a physical client rendering of the projected property.

## Block-Entity State Evidence

- `BlockEntityStateSynchronizer` uses Minecraft's authoritative serialized custom state and retains
	only an ephemeral previous snapshot. A changed snapshot produces a `block_entity.state` delta and
	enters the existing session auto-flush boundary.
- `RuntimeLifecycleCoordinator` clears snapshots on install and block-entity unbind, so runtime
	objects and their state are not persisted in Hydraulic's binding map.
- `BlockEntityStateSynchronizerTest` and Java 25 shared compilation pass. Not proven: arbitrary
	block-entity mutation, restart/rebind comparison, concrete Geyser encoding for generic state, or
	physical Bedrock observation.

## Pack Classification Evidence

- `PackValidatorTest` verifies malformed generated JSON is classified as
	`GENERIC_GENERATOR_DEFECT`, not `INVALID_PATH` because of the archive entry name.
- Validator errors remain pack-local and do not abort conversion of other mods. The current evidence
	does not establish that all third-party pack inputs have been remediated.

## Third-Party Corpus Run

- Java 25 `:fabric:runServer` loaded 231 Fabric mods and reached Minecraft/Geyser readiness on UDP
	`19132`, followed by clean world shutdown.
- Recipe ingestion inspected 8,934 manager entries, normalized 3,126, and classified 5,808 as
	`RECIPE_RUNTIME_UNKNOWN`; 3,128 datapack recipes were compiled.
- The observed pack report recorded 125 packs: 69 valid, 56 invalid, 50 missing-output records,
	15 missing selected textures, and 316 long-path warnings. Focused post-change tests classify the
	missing-output records as `NO_CONVERTIBLE_OUTPUT`.
- This is Level 2/startup and artifact evidence. No third-party object has a completed authoritative
	behavior round trip, persistence proof, or physical Bedrock observation.

## Live-Binding Evidence

- Adapter-unknown runtime inventory: discovery, classification, executable contract verification,
	binding, simulation, Java mutation, unbind, rebind, and stale-plan replacement pass focused tests.
- Minecraft 26.2 lifecycle: `setLevel`, `setRemoved`, and `clearRemoved` hooks compile through the
	Fabric transformation path. A live `:fabric:runServer` reached `Done` and started Geyser with no
	Hydraulic lifecycle-mixin or binding failure.
- Not proven: real third-party block-entity operation, persistence after restart, Geyser delivery for
	this arbitrary binding, or official Bedrock-client observation.

## Recipe Evidence

- Focused tests cover resource JSON normalization, runtime-codec normalization, opaque/custom
	serializer rejection, catalyst preservation, and fail-closed tag/condition/chance handling.
- Machine and recipe test suites pass under Java 25, and Fabric compilation accepts the typed
	Minecraft 26.2 codec path.
- Runtime counts are recorded from the current server log when available; automatic machine
	association and physical client behavior remain unverified.
- Final Java 25 runtime: 8,934 live manager entries inspected, 3,126 normalized, and 5,808
	classified `RECIPE_RUNTIME_UNKNOWN`; Minecraft and Geyser reached `Done`. Waystones custom-block
	registration still fails independently on negative mining destructibility without aborting startup.
- Final allowlisted runtime run: 8,934 entries inspected, 3,126 normalized, 5,808
	`RECIPE_RUNTIME_UNKNOWN`; Minecraft reached `Done` and shut down cleanly.
- Independent pack blockers observed in the same run include malformed McW pack format ranges,
	negative Waystones mining destructibility, Lootr model deserialization failures, and invalid
	empty-output packs. They remain in the pack-remediation workstream.

## Menu Action Evidence

- Focused tests cover normalized click actions, malformed button rejection, exact component-aware
	Java state deltas, transaction trace identity, and explicit toggle-plan compilation.
- `menu.button.0=toggle` is installed for the bundled menu-machine fixture. Its Java menu delegates
	button `0` to a persisted block-entity enabled flag and requests a canonical full-state broadcast.
- Container click, button, and close hooks run after Minecraft's server-thread transfer and compile
	through the Fabric transformation path. Java remains the mutation authority; rejected and completed
	actions resynchronize through `AbstractContainerMenu.broadcastFullState()`.
- E1 contract and compilation evidence passes. A live Fabric startup reached Minecraft `Done` and
	started Geyser on UDP `19132` without a menu-mixin apply or injection failure. E4 client
	action/observation and E6 reopen/restart persistence remain blocked without an official Bedrock
	client.
