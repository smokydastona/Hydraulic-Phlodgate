# Validation Matrix

## Evidence levels

| Level | Meaning | Current state |
| --- | --- | --- |
| E1 | Unit and contract tests | PARTIAL: selected runtime slices covered |
| E2 | Java/Fabric integration | SERVER-VERIFIED for selected fixtures |
| E3 | Packet and runtime handoff | TRANSPORT-VERIFIED for inventory/property packet slices |
| E4 | Official Bedrock client connection and pack observation | BLOCKED: no client evidence |
| E5 | Real-mod regression corpus | PARTIAL: conversion evidence only; behavior claims remain open |
| E6 | Persistence/reconnect/restart on a physical client | BLOCKED |
| E7 | Final zero-trust release audit | OPEN |

## Required physical evidence record

Each completed client test must record timestamp, Java/Minecraft/Bedrock/Geyser/Hydraulic versions, commit, mod and object, action, expected result, observed result, screenshots or packet trace where available, and a human-attested result. Server-side encoding, transport handoff, or a unit-test bot cannot populate the client-observed field.

## Current blockers

- The previous development world contained scoreboard residue and was preserved as `fabric/run/world-scoreboard-audit-backup-20260914`. A fresh generated world reached Geyser-ready startup and clean shutdown after the scoped scoreboard-load recovery fix; same-world restart is pending a Windows Architectury dev-jar lock workaround.
- The current environment has no accessible official Bedrock client/device for E4-E6.
- Java 25 focused tests and Fabric compilation pass for the live-binding slice. A live server/client
	round trip is still required before promotion beyond server evidence.
- Pack-validation findings still require per-pack root-cause classification before release claims.

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
