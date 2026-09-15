# Runtime Contract Matrix

This matrix records the strongest verified state, not the intended design.

| Contract | Discovered | Normalized | Compiled | Bound | Executable | Persistent | Synchronized | Bedrock-verified | Regression-verified |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Block use | PASS | PASS | PASS | PASS | PASS | PARTIAL | TRANSPORT | OPEN | PASS |
| Item insert | PASS | PASS | PASS | PASS | PASS | PARTIAL | TRANSPORT | OPEN | PASS |
| Item extract | PASS | PASS | PASS | PASS | PASS | PARTIAL | TRANSPORT | OPEN | PASS |
| Item move | PARTIAL | PARTIAL | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN |
| Fluid fill | PASS | PASS | PASS | PASS | SERVER | OPEN | TRANSPORT | PARTIAL | PASS |
| Fluid drain | PASS | PASS | PASS | PASS | SERVER | OPEN | TRANSPORT | PARTIAL | PASS |
| Energy receive | PASS | PASS | PASS | PASS | SERVER | OPEN | OPEN | OPEN | PARTIAL |
| Energy extract | PASS | PASS | PASS | PASS | SERVER | OPEN | OPEN | OPEN | PARTIAL |
| Menu open | PASS | PASS | PASS | PARTIAL | PARTIAL | OPEN | TRANSPORT | OPEN | PARTIAL |
| Menu button | PASS | PASS | PASS | PARTIAL | SERVER | PARTIAL | PARTIAL | OPEN | PASS |
| Menu property | PARTIAL | PARTIAL | PARTIAL | PARTIAL | OPEN | OPEN | PARTIAL | OPEN | PARTIAL |
| Menu mode | PASS | PASS | PASS | PARTIAL | SERVER | PARTIAL | PARTIAL | OPEN | PASS |
| Entity use | PARTIAL | PARTIAL | PARTIAL | OPEN | OPEN | OPEN | OPEN | OPEN | PARTIAL |
| Entity attack | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN |
| Entity mount/dismount | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN | OPEN |
| Machine processing | PASS | PASS | PASS | PASS | PASS | PARTIAL | TRANSPORT | OPEN | PASS |
| Machine persistence | PASS | PASS | PASS | PASS | PARTIAL | OPEN | OPEN | OPEN | PARTIAL |
| Automation route | PASS | PASS | PASS | PARTIAL | PARTIAL | OPEN | PARTIAL | OPEN | PARTIAL |
| Adapter-unknown live inventory | PASS | PASS | PASS | PASS | PASS | OPEN | OPEN | OPEN | PASS |
| Recipe manager entry | PASS | PASS/UNKNOWN | PARTIAL | OPEN | PARTIAL | OPEN | OPEN | N/A | PASS |

`TRANSPORT` means a concrete Geyser packet handoff was tested. It never means that an official Bedrock client displayed or applied the result.

Session pipeline lifecycle is `SERVER_VERIFIED`: active sessions are reconciled on machine ticks,
and stale session pipelines are removed. This does not establish client delivery or observation.

Live object binding is `SERVER_VERIFIED` for the adapter-unknown inventory contract: an existing
presentation plan is preserved, verified runtime bridges are merged into dispatch, simulation does
not mutate state, commit does mutate Java state, and removal/reload replaces the ephemeral binding.
Real third-party block entities and physical Bedrock observation remain open.

Recipe manager normalization is server-verified at the codec boundary. `PASS/UNKNOWN` means every
processed entry either yields portable `RecipeIR` or explicit `RECIPE_RUNTIME_UNKNOWN`; it does not
mean every recipe is executable or automatically associated with a machine.

Menu button and mode contracts are `SERVER` executable for explicitly compiled actions. The
menu-machine fixture persists its toggle field, but restart recovery and a physical Bedrock action
remain unverified; therefore binding, persistence, synchronization, and Bedrock verification are
not promoted to `PASS`.

Fluid fill and drain have a strict metadata-compiled bucket exchange contract. They simulate the full
amount before mutation, require explicit input/output item and fluid identities, reject partial or
wrong-fluid actions, compensate the tank if the item exchange fails, and emit an optional numeric
container-property update through the existing Geyser transport. `TRANSPORT` reflects the tested
packet path, not physical client observation or persistence.
