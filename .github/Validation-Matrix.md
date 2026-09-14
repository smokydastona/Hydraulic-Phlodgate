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
- The full Gradle compile must be rerun after dependency resolution completes; the latest attempt timed out during configuration.
- Pack-validation findings still require per-pack root-cause classification before release claims.
