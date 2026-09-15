[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Discord](https://img.shields.io/discord/613163671870242838.svg?color=%237289da&label=discord)](https://discord.gg/geysermc)

# Hydraulic-Phlodgate

<p align="center">
  <img src="https://raw.githubusercontent.com/smokydastona/Hydraulic-Phlodgate/master/.github/Phlodgate_LOGO.png" alt="Phlodgate Banner">
</p>
> **Hydraulic-Phlodgate is a fork of [GeyserMC Hydraulic](https://github.com/GeyserMC/Hydraulic).**
>
> Phlodgate's goal is to push Hydraulic further toward automatic compatibility with modded Java servers, so Bedrock players can interact with as much of a modded server as possible without requiring the server owner to manually create a Bedrock compatibility layer for every mod.

## Repository Authority

The canonical source tree is the nested `Hydraulic-mod/` repository that contains this README. Keep active code, Gradle builds, and commits there. `_runtime_validation/` is a detached linked worktree for validation-only runs, and `_push_worktree/` is an independent export/push checkout; neither is an additional development source tree.

Runtime validation output stays under the canonical tree's ignored `fabric/run/` directory, especially `fabric/run/config/hydraulic/reports/` and `fabric/run/config/hydraulic/cache/`. Run `scripts/validate-repository-topology.ps1` before a release or after changing worktree setup to verify these boundaries without modifying any checkout.

Generated runtime logs, including compressed rotations such as `*.log.gz`, are local diagnostics and
new rotations are ignored by Git. Evidence intended for version control must be distilled into the
tracked validation matrices or an explicitly reviewed artifact rather than committing raw server logs.

## What is Hydraulic?

[Hydraulic](https://github.com/GeyserMC/Hydraulic) is a companion mod for [Geyser](https://github.com/GeyserMC/Geyser) that allows Bedrock players to connect to modded Minecraft: Java Edition servers.

Hydraulic handles the difficult part of taking Java mod content and preparing a Bedrock-compatible representation of that content.

Phlodgate starts with that system rather than replacing it.

The basic idea is:

**Hydraulic provides the foundation. Phlodgate expands the compatibility layer around it.**

## Current Release Gate

Phlodgate is currently **not release-ready**. The zero-trust audit found real server and Geyser
transport slices, but no physical Bedrock-client evidence and no basis for universal arbitrary-mod
compatibility claims yet. The active implementation ledger is in
[`.github/Full-Implementation-Plan.md`](.github/Full-Implementation-Plan.md), with the runtime
contract status in [`.github/Runtime-Contract-Matrix.md`](.github/Runtime-Contract-Matrix.md) and
validation evidence in [`.github/Validation-Matrix.md`](.github/Validation-Matrix.md).

The current priority is closing gameplay round trips in this order: fluid actions, energy actions
and state, entity actions, persistence, automation lifecycle, and pack remediation. Physical
Bedrock evidence and real third-party mod validation follow those server-authoritative contracts.
Resource conversion or a transport handoff does not imply gameplay support or client observation.

The first fluid action contract is now available for metadata-declared exact bucket exchanges. It
requires explicit input/output item IDs, fluid ID, tank, amount, and optional container-property ID;
Hydraulic simulates the full move, commits only an exact result, compensates a failed item exchange,
and hands the declared numeric tank level to Geyser through `ContainerSetDataPacket`. This is tested
server/transport-path behavior, not physical Bedrock-client or restart-persistence verification.

Metadata can also declare bounded energy block-use actions with
`interaction.energy.action=receive|extract`, `interaction.energy.amount`, and optional
`interaction.energy.side` and `interaction.energy.property`. The action is simulated and committed
through the compiled energy bridge only when the exact amount succeeds; partial or malformed
operations fail closed, and an optional property projection uses the existing Geyser sync path.
Focused Java 25 tests cover this server-side contract. Persistence and physical Bedrock observation
remain release gates.

Machine synchronization session pipelines are reconciled against Geyser's live connection snapshot
on each machine tick, so disconnected sessions do not retain dirty-state or transport references.
This is server-side lifecycle handling, not evidence that a Bedrock client received or displayed an
update.

Live capability binding now owns ephemeral links between runtime block entities and verified compiled
plans. The binder records object identity, runtime type, discovered capabilities, selected adapters,
verified bridge kinds, contract version, and confidence; Minecraft `setRemoved`/`clearRemoved` lifecycle
events remove and recreate those bindings. An adapter-unknown inventory fixture is automatically bound
and mutated through production dispatch, including augmentation of an existing presentation plan. This
is server-side evidence, not arbitrary real-mod or physical Bedrock completion.

---

# What is Phlodgate?

Phlodgate is an experimental compatibility-focused fork of Hydraulic.

The main difference is that Phlodgate does more than convert resources and generate Bedrock packs. It also tries to determine **what a Java mod actually requires in order to be usable from Bedrock**.

That means looking at things such as:

* blocks
* block states
* items
* recipes
* entities
* fluids
* block entities
* menus
* models
* textures
* metadata
* interactions
* behavior requirements
* dependencies between mods
* Bedrock protocol limitations

Instead of treating every unsupported object as simply "converted" or "not converted", Phlodgate builds compatibility information around the discovered content.

The long-term goal is to make compatibility increasingly automatic.

Ideally, a server owner should be able to install a modpack, start the server, and have Phlodgate handle as much of the Bedrock compatibility work as possible.

---

# Phlodgate vs Upstream Hydraulic

Phlodgate is **not a replacement for Hydraulic**.

It is a fork that builds additional systems on top of the Hydraulic conversion and Geyser integration.

| Area                              | Upstream Hydraulic   | Phlodgate                          |
| --------------------------------- | -------------------- | ---------------------------------- |
| Geyser integration                | Yes                  | Yes                                |
| Mod resource discovery            | Yes                  | Expanded                           |
| Bedrock pack generation           | Yes                  | Yes                                |
| Registry/resource conversion      | Yes                  | Yes                                |
| Compatibility analysis            | Limited              | Expanded                           |
| Compatibility metadata            | Limited              | Extensive                          |
| Block compatibility rules         | Basic conversion     | Metadata + state-aware rules       |
| Item compatibility                | Conversion           | Analysis + behavior classification |
| Entity compatibility              | Conversion           | Analysis + runtime registration    |
| Fluid analysis                    | Limited              | Dedicated fluid analysis           |
| Menu analysis                     | Limited              | Dedicated menu analysis            |
| Block entity analysis             | Limited              | Dedicated block-entity analysis    |
| Compatibility reports             | Basic                | Detailed structured reports        |
| Runtime compatibility plan        | No equivalent system | Yes                                |
| Runtime bridge categories         | No equivalent system | Yes                                |
| Metadata patches                  | No equivalent system | Metadata V2                        |
| Lazy model loading                | Limited              | Indexed/lazy model provider        |
| Texture dependency tracking       | Limited              | Indexed dependency graph           |
| Conversion caching                | Yes                  | Expanded and dependency-aware      |
| Pack validation                   | Limited              | Dedicated validation report        |
| Automatic compatibility decisions | Limited              | Expanded                           |
| Mod-specific adapters             | Foundation           | Expanded adapter system            |
| Machine/automation behavior       | Incomplete           | Generic Java-side execution substrate; incomplete client validation |
| Fluid runtime behavior            | Incomplete           | Generic Java-side transfer substrate; incomplete world-fluid translation |
| Kinetic presentation              | No equivalent system | Create-style RPM, axis, gear-ratio, and overstress animation transforms |
| Processing particles               | No equivalent system | Tick-aligned smoke, sparks, laser, flame, and splash emitters |
| Corpus change detection            | No equivalent system | Curated schema diffing with startup re-index reports and score refresh |
| Large-topology invalidation        | No equivalent system | Cycle-safe synthetic benchmark coverage for 500+ mod graphs |

Dynamic machine discovery is conservative and executable-state driven. When a live unmapped block
entity is registered through the dynamic lifecycle manager, Hydraulic inspects its public runtime
contract, validates item, fluid, and energy operations against the reflective transfer factories,
and registers only the bridge kinds that can actually execute. Method-name evidence without a
validated operation remains `VISUAL_ONLY`; this path does not claim automatic understanding of
arbitrary mod-specific recipes or ticking behavior.

The live lifecycle boundary now also observes server-side block-entity loading, concrete menu
creation, and Minecraft's bound block-entity ticker. Machine changes are coalesced and delivered
only to active Geyser sessions whose Java player is in the same level and tracking range. Typed
`RecipeIR` is the authoritative normalized form for resource JSON and supported codec-backed live
`RecipeManager` entries; opaque runtime entries become `RECIPE_RUNTIME_UNKNOWN` and are not promoted
to executable machine plans.

Compatibility reports now carry an additive `implementationMaturity` field with the values
`UNKNOWN`, `ARCHITECTURE_IMPLEMENTED`, `CAPABILITY_IMPLEMENTED`, `INTEGRATED`, `VERIFIED`, and
`CLIENT_VERIFIED`. Analyzer output starts at `ARCHITECTURE_IMPLEMENTED`; older cached reports
load as `UNKNOWN`. This prevents support level or compatibility score from being mistaken for
runtime integration or real Bedrock-client observation.

The current zero-trust audit is recorded in [.github/Audit-Report.md](.github/Audit-Report.md).
It is the evidence boundary for release claims: selected slices are server/transport verified,
while physical Bedrock observation and full arbitrary-mod behavior remain open.

The important distinction is that **Phlodgate is trying to add the compatibility and runtime layers that sit between Hydraulic's conversion pipeline and the actual behavior of a mod.**

## External Research Boundary

The external Fabric and Bedrock repositories used during architecture research are not bundled as runtime dependencies. The awesome lists are discovery catalogs; Mojang Creator Tools is an optional external validator; ScriptAPI is an offline evidence source; bridge. is authoring software; BedrockBridge targets Bedrock Dedicated Server rather than a Java/Geyser session; and MCXboxBroadcast/Broadcaster is an Xbox Live presence broadcaster rather than a compatibility engine. Their source, assets, licenses, versions, and runtime assumptions are tracked in [.github/external-research-report.md](.github/external-research-report.md).

Hydraulic does not copy third-party code or assets from those projects. Runtime behavior remains Java-server authoritative, with local corpus snapshots and compiled compatibility plans as the only supported integration surfaces. A downloaded or executed Bedrock behavior pack is never treated as proof of Geyser-session execution.

## NetherNet Protocol Boundary

Research covered the original `df-mc/nethernet-spec`, the MIT
`PrismarineJS/node-nethernet` implementation, the MIT
`LucienHH/bedrock-portal-nethernet` implementation, and the current MIT
`bedrock-v/nethernet` implementation. Hydraulic includes a dependency-free
wire codec under `org.geysermc.hydraulic.compat.nethernet` for the verified
protocol substrate:

* authenticated LAN discovery on UDP port `7551` using the AES-ECB/HMAC-SHA256
       format and little-endian `0xdeadbeef` application key;
* typed request, response, and message packets with hex-encoded responses;
* current server-data version 7 fields and varint/string validation;
* strict `CONNECTREQUEST`, `CONNECTRESPONSE`, `CANDIDATEADD`, and
       `CONNECTERROR` signaling parsing with uint64 connection IDs;
* raw Bedrock message segmentation on reliable channels up to 262,143 bytes
       per segment and 256 segments, with explicit rejection of segmented
       unreliable messages.

The codec is bounded and fail-closed: malformed authentication, lengths,
segment order, signaling types, and oversized inputs are rejected. It is not a
live WebRTC client, identity/JWS verifier, Xbox Live/PlayFab signaling client,
UDP listener, or Geyser transport replacement. The reference implementations
show that current connections depend on WebRTC data channels, DTLS, identity
assertions, and out-of-band signaling. No production path claims NetherNet
connectivity until those dependencies and real Bedrock-client observation are
independently verified.

---

# Asymmetric Version Lifecycle

A fundamental reality of bridging Java Edition and Bedrock Edition is **version asymmetry**:

1. **Bedrock Forced Updates**: Bedrock players on mobile, console, and Windows are forced by app stores to update to the latest Minecraft release. Hydraulic treats the current Bedrock client version and its JSON/protocol specifications as a **mandatory target constraint**.
2. **Cross-Version Java Mod Consumption**: Java server mods frequently lag behind Minecraft updates or run on older versions (e.g. 1.20.1, 1.21.x) via compatibility shims. Hydraulic is designed to **consume mod assets, recipes, and capabilities across any Minecraft version era** and normalize them into a unified intermediate representation (IR).
3. **Decoupled Regeneration**: Cache keys (`ConversionKey`) separate source mod asset fingerprints from target Bedrock schema versions. When Bedrock updates, Hydraulic regenerates client packs without requiring changes to source Java mod files.

---

# What Phlodgate Adds

## Compatibility Analysis

Phlodgate analyzes discovered mod content and records compatibility information instead of only converting the resource files.

Compatibility is currently divided into several areas:

* **Content**
* **Presentation**
* **State / Data**
* **Interaction**
* **Behavior**

Each discovered object can receive structured compatibility information including:

* support level
* confidence
* provenance
* findings
* required capabilities
* adapter bindings
* runtime requirements
* mod fingerprints

Current support levels include:

* `NATIVE`
* `AUTOMATIC`
* `ADAPTED`
* `APPROXIMATED`
* `VISUAL_ONLY`
* `UNSUPPORTED`

This makes it possible to distinguish between something that is fully usable, something that only looks correct, and something that still needs an actual runtime bridge.

---

# Metadata

Phlodgate adds a metadata system on top of Hydraulic's normal conversion process.

Metadata can describe how specific Java content should be represented on Bedrock.

For example:

```json
{
  "blocks": [
    {
      "java_id": "example:machine",
      "rules": [
        {
          "bedrock_identifier": "example:machine",
          "geometry": "minecraft:geometry.full_block",
          "material": "example:block/machine"
        }
      ]
    }
  ]
}
```

Metadata can currently describe things such as:

* Bedrock identifiers
* Java state conditions
* Bedrock state values
* geometry
* materials
* interaction prompts
* bucket textures
* behavior requirements
* behavior categories
* item mappings
* recipe mappings
* entity mappings
* menu mappings

Metadata V2 also supports patch-style changes for supported runtime data.

Machine processing can be compiled from patch facts when the runtime object exposes executable item transfer operations:

```json
{
       "patch": {
              "machine.processing.enabled": "true",
              "machine.inventory.enabled": "true",
              "machine.inventory.input_slot": "0",
              "machine.inventory.output_slot": "1",
              "machine.processing.recipe.0.input": "minecraft:stone",
              "machine.processing.recipe.0.input_count": "1",
              "machine.processing.recipe.0.output": "minecraft:iron_ingot",
              "machine.processing.recipe.0.output_count": "1",
              "machine.processing.recipe.0.duration": "20"
       }
}
```

Malformed recipe facts fail closed and do not create a machine processing bridge.

Metadata is loaded from:

```text
config/hydraulic/metadata
```

with separate areas for:

```text
builtin/
mods/
server/
user/
```

Higher-priority metadata can override lower-priority metadata.

---

# Runtime Compatibility

One of the biggest differences between Phlodgate and the original Hydraulic approach is the addition of a compiled compatibility plan.

Phlodgate takes the results of compatibility analysis and compiles them into a runtime dispatch structure.

This means runtime systems do not need to repeatedly walk the entire compatibility report looking for matching objects.

The current runtime plan covers areas including:

* block registration
* block creative exposure
* block placement
* item registration
* item creative exposure
* armor presentation
* bow presentation
* entity registration
* menu fallback handling
* block entity patch handling
* fluid compatibility lookup
* state-aware block definitions
* runtime compatibility requirements

Runtime requirements are also classified into typed bridge categories such as:

```text
MENU_CONTAINER
BLOCK_ENTITY_DATA
FLUID_RUNTIME
ITEM_BEHAVIOR
ITEM_TRANSFER
FLUID_TRANSFER
ENERGY_TRANSFER
MACHINE_INVENTORY
AUTOMATION_ACCESS
```

This is intended to give future runtime bridges a consistent place to plug into the system.

## Automatic Semantic Discovery & Dynamic Machine Lifecycle

The discovery layer provides a metadata-independent runtime contract path:
* `SemanticDiscoveryEngine.discoverRuntimeObject` inspects public method shapes for item, fluid, energy, processing, and menu contracts.
* `DynamicMachineLifecycleManager` compiles an unmapped object into `CompiledCompatibilityPlan` only after the existing reflective transfer factory constructs an executable bridge. Method-name evidence alone remains visual-only, and processing behavior additionally requires concrete recipe facts before `MACHINE_BEHAVIOR` is advertised.

## Automatic Recipe Discovery

`AutomaticRecipeDiscovery` scans local mod/data roots under `data/<namespace>/recipes`, derives stable
recipe identifiers, delegates JSON interpretation to the existing datapack and specialized serializers,
and returns a diagnostic report for compiled, malformed, unsupported, and I/O-failed recipes. The
normalized result is retained as typed `RecipeIR` before projection to `UniversalRecipe`. Live
`RecipeManager` entries use Minecraft's registry-aware recipe codec and become either normalized IR or
explicit `RECIPE_RUNTIME_UNKNOWN` evidence. Catalysts remain non-consumed requirements; unresolved tags,
component predicates, alternative singular ingredients, conditions, chance outputs, environmental or
kinetic requirements, and unregistered custom serializers fail closed. Successful reloads atomically
replace the normalized recipe snapshot, while failed scans retain the last complete snapshot. Automatic
association of arbitrary machine instances with the correct recipe set remains incomplete.

## Multi-Resource Session Auto-Flush

`SessionAutoFlushCoordinator` coordinates tick-driven synchronization between `MachineSynchronizationCoordinator` / `MultiResourceTransaction` and active Bedrock `GeyserSession` viewers, immediately dispatching coalesced `InventorySlotPacket` and `ContainerSetDataPacket` state updates.

## Universal Menu IR Pagination & Search

`PaginatedMenuForm` translates massive virtual inventory grids (AE2, Refined Storage, Storage Drawers) into Bedrock SimpleForm JSON payloads with client-side item search filtering, page chunking, and item action dispatching.

---

# Current Runtime Bridges

Some of these systems are already live rather than being report-only features.

Current work includes:

### Entity Runtime

Metadata-backed custom entities can be registered through Geyser when the compatibility result allows the entity to be represented safely.

### Menu Fallback

A metadata-defined Bedrock `ContainerType` can be used when a Java menu cannot be translated through the normal Geyser path.

This is intentionally a fallback mechanism.

It does **not** magically make an arbitrary Java machine interface work on Bedrock.

### Authoritative Menu Actions

For a Bedrock-backed player, Hydraulic now validates translated Java container clicks and buttons
on the Minecraft server thread, lets vanilla menu handling own the mutation, records exact
before/after slot and carried-stack state, and requests a canonical Java full-state broadcast after
completion or rejection. Metadata can classify custom button IDs as `button` or `toggle` through
`menu.button.<id>`, but it cannot invent a third-party menu's semantics.

The bundled menu-machine fixture declares button `0` as a persistent enabled-state toggle. Focused
Java tests pass, and a live Fabric run reaches Minecraft and Geyser readiness without a menu-mixin
failure. Official Bedrock-client execution, visual observation, and restart persistence are still
required before this is a completed client round trip.

### Block Entity Data

Metadata can describe Bedrock block-entity data and copy selected values from incoming Java NBT.

For example:

```text
$java.CustomName
$java.front_text.page
$java.Items.0.Count
```

Numeric list paths are supported on both the Java source and Bedrock destination side.

### Fluid Presentation

Phlodgate currently has a fluid-adjacent bridge for bucket presentation.

A fluid can provide a configured bucket texture and the corresponding bucket item can inherit that presentation when the required Java bucket item can be resolved.

This is intentionally separate from actual fluid simulation.

### Executable Capability Bridges

The shared runtime layer now exposes fail-closed executable bridges for:

* item, fluid, and energy transfer
* machine inventory slot reads
* sided automation insertion and extraction
* generic machine processing from compiled recipe facts
* normalized container-to-tank transfers with fluid identity checks
* Create-style kinetic rotation transforms for Bedrock entity bones
* tick-aligned processing particle emitters for active machines

These bridges operate against compatible Java runtime objects through the compiled dispatch table. Unsupported object shapes, missing directions, malformed recipes, and mismatched fluids do not become silent no-op behavior.

Kinetic presentation is intentionally separate from kinetic behavior. The rendering bridge compiles
network RPM, axis, direction, gear ratio, and overstress state into Bedrock bone transforms and
animation-controller states; it does not claim to recreate Create's server-side kinetic simulation.
Particle emitters likewise produce Bedrock particle-effect packets only while a registered machine
emitter is active and its tick cadence is due.

---

# Machines, Automation and Fluids

This is one of the most important parts of the project.

A machine that looks correct on Bedrock is not necessarily a machine that **works** on Bedrock.

A real modded machine may involve:

* item insertion
* item extraction
* fluid insertion
* fluid extraction
* energy transfer
* inventories
* sided inventories
* pipes
* conveyors
* machines
* processing recipes
* GUI state
* block entity state
* automation
* redstone interaction
* world interaction

Generating a Bedrock block or menu for these systems is only the presentation layer.

Phlodgate's long-term goal is to bridge the underlying behavior as well.

### Current runtime boundary

Phlodgate has generic Java-server-side item, fluid, energy, transaction, machine-processing,
automation, dirty-state, synchronization-planning, and Geyser transport seams. They execute
only when a compiled runtime plan has concrete capability facts and a real target bridge; malformed
or incomplete plans fail closed. A machine is not advertised as executable merely because its
model, menu, or one transfer operation is available.

Stateful processing bridges are retained by the live machine block entity, so recipe progress
persists across server ticks instead of resetting during bridge reconstruction. Tick-driven
delivery remains deliberately limited to an explicitly identified compatible Bedrock container;
Phlodgate does not broadcast machine inventory packets to arbitrary sessions.

The remaining gaps are still material: automatic semantic and recipe discovery for arbitrary
mods, world-fluid translation, richer menu and block-entity behavior, automatic binding of discovered
runtime objects into every live mod block entity, and live Bedrock-client observation of delivered
state. The machine synchronization coordinator now provides a tick-to-dirty-state-to-transport
flush boundary for callers that bind it to a live machine tick; it does not claim that every third-
party block entity is automatically wired to that coordinator.

State observation is also anchored in Minecraft's authoritative block-entity serialization
boundary. Phlodgate snapshots `saveWithoutMetadata(level.registryAccess())` for every discovered live
block entity, retains only an ephemeral previous snapshot, and emits a `block_entity.state` delta
when custom serialized state changes. Lifecycle unbind and coordinator reinstall clear these
snapshots. This is state observation and delta production; arbitrary block-entity mutation, restart
proof, and generic Bedrock packet encoding remain separate gates.

Generated-pack findings now include typed root-cause classifications. Explicit malformed-output
codes take precedence over broad path heuristics, so invalid JSON is reported as a generator defect
even when its archive entry name looks path-related. Classification improves triage; it does not
turn third-party conversion-only evidence into a runtime compatibility claim.

The attached third-party corpus has also been exercised in a Java 25 Fabric/Geyser startup: 231
mods loaded, Geyser reached UDP `19132`, and Hydraulic produced 125 pack-validation records. The
run recorded 69 valid packs, 50 missing-output records, 15 missing-texture errors, and 316 long-path
warnings; focused post-change tests classify missing output as `NO_CONVERTIBLE_OUTPUT`. This proves
startup and artifact coverage only; it is not proof that Create,
Mekanism, AE2, or another mod's gameplay works from Bedrock.

---

# Bedrock Companion Packages

Phlodgate can automatically deliver a custom Bedrock "companion" (a resource pack, optionally
paired with a behavior pack) to every Bedrock player connecting through Geyser. This is a
separate, first-class subsystem (`org.geysermc.hydraulic.companion`) from the Bedrock addon
corpus below: corpus entries are advisory research evidence, while a companion package is an
approved, locally installed artifact that Phlodgate is allowed to deploy.

### The real Geyser boundary this respects

Geyser supports pushing Bedrock **resource packs** to a connecting session. It does not install
or execute Bedrock **behavior-pack** scripts on the client — there is no server-side Bedrock
add-on execution model to hook into when the backing server is a Java server. Because of that:

* The companion's **resource pack** is built, content-addressed, cached, and registered with
  Geyser through the same `GeyserDefineResourcePacksEvent` mechanism used for mod-converted
  packs, so it is delivered automatically to every Bedrock/Geyser session.
       The bundled Geyser `2.11.2-SNAPSHOT` API exposes this global registration event but does not
       expose `SessionLoadResourcePacksEvent`; companion-pack selection is therefore global in this
       build, not dynamically varied per session.
* The companion's **behavior pack** is never sent over the network or executed by Geyser. If a
  companion's behavior pack contains a Script API module meant to run everywhere, the player must
  enable it themselves as a Bedrock "Global Resource" on their own client. Phlodgate only parses
  and documents its declared capabilities for `companion-report.json`; it never pretends this
  makes the behavior pack execute through Geyser.
* The one genuine Java-to-Bedrock "behavior" signal Phlodgate implements is a real scoreboard
  objective, `phlodgate_bridge`, created via the vanilla scoreboard API and translated to Bedrock
       by Geyser like any other objective. The bundled add-on directly checks this canonical objective
       during its companion-mode poll; it can also report modded namespaces already visible through
       Geyser's own custom-content registration.

### Companion component contract

The bundled companion is a first-class Phlodgate component, but it is deliberately mod-agnostic.
It must consume stable identifiers and typed capability contracts rather than branch on Java mod
names. Phlodgate remains authoritative for Java-world mutation, compatibility decisions, and
transport. An explicitly installed and enabled Bedrock behavior pack may provide client-local
generic primitives such as forms, inspection, HUD presentation, and local settings; it cannot
turn an unimplemented Java-side machine, fluid, automation, or synchronization bridge into a
working server behavior.

The intended flow is:

```text
Java content -> compiled compatibility/runtime plan -> Hydraulic Java bridge -> Geyser transport
                                           -> companion resource presentation / manually enabled client behavior
```

No companion feature may claim arbitrary Java-mod execution based only on a generated resource
pack, an addon manifest, or a client-side Script API implementation.

### Package layout

```text
config/hydraulic/companions/<companion-id>/
  companion.json
  resource_pack/
    manifest.json
    ...
  behavior_pack/        (optional, documentation/capability-reporting only)
    manifest.json
    ...
```

`companion.json` fields:

* `id` (required): stable companion identifier.
* `name`, `version`: display metadata.
* `resourcePack` / `behaviorPack`: directory names (defaults: `resource_pack`, none).
* `executionMode`: `client_global_behavior_pack` (default) or `geyser_resource_pack_only`.
* `capabilities`: array of `{ "id", "description", "requiresServerBridge" }` entries.

### What Phlodgate actually does with a companion package

1. Discover every subdirectory of `config/hydraulic/companions`.
2. Validate `companion.json` plus the Bedrock `manifest.json` of the resource (and behavior, if
   present) pack. One invalid companion is rejected without affecting the others.
3. Compute a deterministic SHA-256 fingerprint of the resource pack directory and build a
   content-addressed `.mcpack` under `config/hydraulic/cache/companions`, reusing the cached
   archive on unchanged content.
4. Register the built `.mcpack` with Geyser so it reaches every Bedrock session.
5. Classify every declared capability: capabilities that don't need server involvement are
   `CLIENT_LOCAL_SUPPORTED`; the scoreboard-signal capability is `SERVER_SIGNAL_SUPPORTED` once
   the real bridge installs; anything else that claims `requiresServerBridge: true` without a real
   implemented bridge is honestly reported `UNSUPPORTED_NO_BRIDGE` — it is never faked as working.
6. Write `config/hydraulic/reports/companion-report.json` with every companion's build artifact,
   validation issues, and per-capability support status.

### Installing the bundled Phlodgate Add-On companion

`scripts/deploy-companion-pack.ps1` builds the `Plodgate_Add-on` project (`npm run package`) and
installs its `RP/`/`BP/` output plus a generated `companion.json` into
`fabric/run/config/hydraulic/companions/phlodgate`. Run it once after cloning, and again whenever
the add-on's version changes:

```powershell
scripts\deploy-companion-pack.ps1
```

---

# Bedrock Addon Corpus

Phlodgate can load normalized, locally reviewed Bedrock addon records as offline compatibility evidence. It does not crawl GitHub, CurseForge, or other remote sources during startup.

Place normalized corpus entries under:

```text
config/hydraulic/corpus/
       sources/
       generated/
       curated/
       java/
         sources/
         generated/
         curated/
```

Entries use the typed `AddonCorpusEntry` schema already used by the compatibility subsystem. When the same corpus ID exists in more than one directory, precedence is `curated` over `generated` over `sources`. Invalid entries are rejected with warnings; valid inadmissible entries remain reportable but are excluded from compatibility evidence.

On startup, Hydraulic seeds its reviewed Bedrock addon snapshots into `curated/builtin/` and reviewed Java capability-semantics snapshots into `java/curated/builtin/`. The bundled Bedrock corpus currently contains 15 records, of which 12 are admissible; the Java corpus contains two admissible references for Forge Capabilities and the Fabric Transfer API. These built-in directories are Hydraulic-owned and overwritten when the bundled snapshot changes. Server owners should place their own curated records directly under `curated/` or `java/curated/`, outside `builtin/`.

The shared `CorpusSnapshotImporter` can ingest a local Bedrock addon source directory or ZIP archive into `generated/`. It deterministically extracts manifest versions/dependencies, behavior and resource-pack structure, textures, models, recipes, functions, scripts, UI files, custom-component evidence, GameTest usage, and capability-pattern hints. It is bounded to 50,000 files and 64 MiB, rejects symbolic links and unsafe ZIP paths, and never executes scripts or copies source assets into Hydraulic.

Import requests must provide explicit source and license facts. Local files use `LOCAL_FILE` provenance; GitHub and other remote identities may be recorded only when the caller supplies the inspected source URL and permissions. Download availability alone never makes an entry admissible.

Hydraulic persists the resulting versioned index and manifest as `corpus-index.json` and `corpus-manifest.json`, then writes `corpus-summary.json`, `corpus-admissibility-report.json`, and `java-corpus-summary.json` under `config/hydraulic/reports`.

The corpus is advisory. It can enrich compatibility analysis and adapter ranking, but raw corpus records cannot directly advertise executable runtime bridges.

`CorpusDiffReindexer` can scan curated schema snapshots against a persisted index and classify entries
as `ADDED`, `MODIFIED`, `DELETED`, or `UNCHANGED`. Changed entries receive refreshed capability scores
and the utility returns an updated typed index with deleted entries removed. This remains an offline
local scan: Hydraulic does not crawl remote repositories at runtime.

---

# Performance

Compatibility scanning can become expensive on large modpacks, so Phlodgate tries to avoid repeatedly doing the same work.

The current implementation includes:

* indexed mod resources
* persistent resource indexes
* compatibility caching
* conversion caching
* validation caching
* dependency-aware invalidation
* lazy model loading
* bounded model caches
* negative model caching
* texture-output caching
* indexed texture discovery
* texture dependency tracking
* demand-driven block material generation
* indexed blockstate loading
* indexed item asset loading
* compiled runtime lookup tables
* startup corpus diff re-indexing for curated schema changes
* bounded runtime-contract discovery evidence
* automatic local recipe discovery and schema diagnostics
* machine progress/active-state synchronization at the coordinator boundary

The invalidation benchmark also includes a 500-mod synthetic topology with injected cycles. It
verifies that cycle-breaking topological ordering preserves every node and that repeated transitive
invalidation remains within the benchmark's latency budget.

The cache is stored under:

```text
config/hydraulic/cache
```

The intention is simple:

**Do the expensive work when something actually changed, not every time the server starts.**

---

# Reports

Phlodgate produces several reports under:

```text
config/hydraulic/reports
```

Including:

```text
content-inventory.json
compatibility-report.json
compatibility-summary.json
compatibility-contracts.json
adapter-opportunity-report.json
performance-report.json
pack-validation-report.json
```

These are useful both for debugging and for identifying where compatibility work is still missing.

Pack validation also reports Bedrock compatibility warnings for archive paths at or above 80 characters. These warnings do not invalidate the pack, but the generated path should be shortened before distributing it because some Bedrock platforms have path-length limits.

The compatibility report can show:

* discovered content
* support level
* compatibility domains
* adapter bindings
* runtime requirements
* metadata findings
* confidence
* provenance
* validation results
* unsupported behavior

Each analyzed object also has a typed compatibility contract at the analysis boundary. The contract separates content, presentation, state, interaction, behavior, and network outcomes, records required runtime bridges, and marks whether the object is executable or must degrade to visual-only or omitted behavior.

The performance report records measured work performed during indexing, compatibility analysis, conversion and runtime dispatch.

---

# Automatic Compatibility

The larger goal of Phlodgate is not to maintain a giant list of manually written compatibility rules forever.

The project is designed around the idea that a mod should be inspected first.

If the system can determine that something can be represented automatically, it should.

If it needs a known adapter, it should use one.

If metadata can solve the problem, metadata should be able to describe the solution.

If the Java behavior cannot currently be translated, the compatibility system should identify exactly what is missing instead of pretending the object is fully supported.

That gives the project a path toward gradually expanding compatibility without hardcoding every mod directly into the core.

---

# Mod-Specific Adapters

Some mods will always require special handling.

Phlodgate therefore supports adapter-driven compatibility rather than forcing every mod-specific implementation into the main conversion code.

The intended model is:

```text
Java Mod
   ↓
Resource / Content Discovery
   ↓
Compatibility Analysis
   ↓
Known Adapter or Generic Compatibility
   ↓
Compiled Compatibility Plan
   ↓
Runtime Bridge
   ↓
Geyser
   ↓
Bedrock
```

The generic path handles what can be handled generically.

Adapters handle the things that cannot.

---

# Connecting & Testing with a Real Bedrock Client

Because Phlodgate targets standard Geyser translation, you do **not** want a custom or headless terminal client here—the server *is* your terminal. You need a Bedrock client that can accept the auto-flushed resource packs Phlodgate generates.
The exact client you use depends on how your development environment is isolated:

### Option 1: Standard Minecraft for Windows (Same Machine Testing)
If your Fabric server is running on the same PC you play on, use the official **Minecraft for Windows** client.

* **The Catch:** Windows AppX packages (UWP apps) are isolated in a network sandbox by default. They are strictly blocked from connecting to `localhost` or `127.0.0.1`.
* **The Fix:** Open a separate PowerShell window as **Administrator** and run this loopback exemption command to allow Minecraft to see your local Fabric/Geyser server:
```powershell
CheckNetIsolation LoopbackExempt -a -n="Microsoft.MinecraftUWP_8wekyb3d8bbwe"
```
* Once executed, boot Minecraft, navigate to **Play > Friends > Add Server**, and enter Server Address `127.0.0.1` with your Geyser port (default `19132`).

### Option 2: Minecraft Preview (For Future Schema Validation)
Because Phlodgate treats the latest Bedrock schemas as a **mandatory target constraint**, testing on the **Minecraft Preview** client ensures custom addon blocks or V2 metadata patches do not break when Mojang pushes a forced retail update.

* **The Fix:** If testing Preview on the same machine, run the loopback exemption command tailored for the Preview package:
```powershell
CheckNetIsolation LoopbackExempt -a -n="Microsoft.MinecraftWindowsBeta_8wekyb3d8bbwe"
```

### Option 3: Android / iOS Mobile Clients (For Cross-Device Verification)
To ensure generated UI layout files or `PaginatedMenuForm` arrays scale properly without screen overflow, testing on a mobile device is ideal:

* Ensure your mobile device is connected to the **same Wi-Fi network** as your development PC.
* In the mobile Bedrock client, navigate to servers and enter your **PC's local LAN IPv4 address** (e.g. `192.168.1.XX`) on port `19132`. No loopback exemption commands are needed for external devices.

---

# Verifying the Pack Handoff in VS Code & Server Console

Once your Bedrock client connects to your Fabric server:

1. **Console Connection Capture**: Look at your server terminal console. You should see Geyser capture the session connection.
2. **Pack Compilation & Storage**: Phlodgate compiles custom asset configurations into compressed `.mcpack` files located under `fabric/run/config/hydraulic/storage/<mod-id>/` and cached under `config/hydraulic/cache/`.
3. **Session Auto-Flush & Pack Transfer**: When joining, Geyser triggers the resource pack transfer. If the client fails to download or render custom blocks:
   * Inspect the server console for Geyser `ResourcePackSendPacket` logs or pack validation errors in `config/hydraulic/reports/pack-validation-report.json`.
   * Check `config/hydraulic/reports/compatibility-report.json` to verify whether the block/item compiled as `NATIVE`, `AUTOMATIC`, `ADAPTED`, `APPROXIMATED`, or `VISUAL_ONLY`.

---

# Compatibility With Hydraulic

Phlodgate intentionally remains closely tied to upstream Hydraulic.

Changes to Hydraulic are not automatically treated as obsolete simply because Phlodgate has additional systems.

The goal is to keep the fork recognizable and compatible with the direction of the upstream project while experimenting with features that are currently outside Hydraulic's scope.

When upstream Hydraulic improves its conversion, resource-pack, Geyser, or protocol handling, those changes are important to Phlodgate as well.

Likewise, Phlodgate's compatibility work is intended to eventually provide ideas, implementations, or proven solutions that could be useful to the wider Hydraulic ecosystem.

Upstream project:

**[GeyserMC/Hydraulic](https://github.com/GeyserMC/Hydraulic)**

This fork:

**[smokydastona/Hydraulic-Phlodgate](https://github.com/smokydastona/Hydraulic-Phlodgate)**

---

# Project Status

Phlodgate is experimental.

It should **not** currently be considered a universal compatibility solution for arbitrary modpacks.

The project already has real compatibility-analysis, metadata, caching, pack-generation, validation, and runtime-bridge infrastructure, but there are still major areas to solve.

The current implementation report is:

| Area | Verified state |
| --- | --- |
| Runtime semantic discovery | Public-contract inference for inventory, fluid, energy, processing, ticking, and menu shapes; focused tests pass |
| Recipe discovery | Local recipe-root scan with existing specialized serializers and malformed/unsupported diagnostics; focused tests pass |
| Machine synchronization | Coordinator records progress/active deltas, coalesces, encodes, and delivers through the existing transport abstraction; focused tests pass |
| Generic machine execution | Existing item/fluid/energy transaction and processing bridges remain fail-closed and full-suite verified |
| Menu actions | Server-thread click/button validation, vanilla Java mutation, transaction evidence, and canonical menu resync are implemented; physical Bedrock execution remains unverified |
| Fluid actions | Generic fill/drain transactions and live tank binding exist; no complete Bedrock-originated container action and client-observed state round trip is verified |
| Energy actions/state | Generic receive/extract transactions and live storage binding exist; no complete client action/state presentation round trip is verified |
| Entity actions | Metadata-backed prompts exist; authoritative use, attack, mount, and dismount round trips remain open |
| Persistence/lifecycle | Selected fixture state is serialized and lifecycle hooks exist; shutdown/restart/rebind and chunk replacement matrices remain incomplete |
| Pack remediation | Structured validation reports exist; remaining failures still require generic fixes, explicit adapters, or unsupported classification |
| Real Bedrock observation | Not verified in this environment; transport handoff is not client observation |
| Arbitrary third-party automatic binding | In progress: `LiveCapabilityBinder` owns verified runtime-object bindings and block-entity removal/reload hooks; adapter-unknown inventory execution passes, while real-mod and physical-client round trips remain open |

The object-level compatibility report remains the machine-readable authority. The current ecosystem
summary in [.github/fork-architecture-plan.md](.github/fork-architecture-plan.md) distinguishes generated
artifact evidence, generic runtime substrate, and physical Bedrock evidence so a converted pack or fixture
cannot be mistaken for verified support for Create or another third-party mod.

## Prioritized Completion Order

1. **P0.1 Fluid actions:** Bedrock fill, drain, and transfer requests must mutate a discovered live
       Java tank and synchronize the resulting identity and amount.
2. **P0.2 Energy actions/state:** authoritative receive/extract operations must produce bounded,
       client-visible storage state without inventing a universal Bedrock energy mechanic.
3. **P0.3 Entity actions:** use, attack, mount, and dismount must resolve a live entity and execute
       through Java's authoritative handlers.
4. **P0.4 Persistence:** save, shutdown, restart, rebind, and state recovery must be demonstrated for
       every mutable fixture and promoted generic contract.
5. **P0.5 Automation lifecycle:** verify chunk unload/reload, block break/replacement, dimension
       changes, disconnects, and restart without stale bindings, loss, duplication, or cross-session sync.
6. **P0.6 Pack remediation:** classify every remaining runtime pack failure as a generic generator
       defect, an adapter requirement, or an explicit unsupported result.
7. **P1 Physical Bedrock:** collect the manual E1-E10 action and observation evidence.
8. **P1 Real third-party mods:** validate Create first, then widen the evidence-backed mod matrix.

The acceptance question for each P0 slice is whether the same compiled generic contract operates a
newly discovered runtime object without any fixture identifier check. Fixture-only success is test
coverage, not architecture completion.

The largest remaining problems are behavior-heavy and client-validation systems such as:

* machines
* automation
* complex menus
* complex block entities
* mod-specific interactions
* automatic semantic and recipe discovery for arbitrary mods
* live Bedrock-client observation of synchronized state

Generic item, fluid, energy, transaction, automation, machine-processing, and synchronization
handoff paths now exist and are covered by Java-side tests. They remain fail-closed and do not imply
that every mod's semantics have been discovered or that a real Bedrock client has observed the
result.

Those systems require actual runtime bridges rather than additional reporting alone.

## Release Readiness Summary

A release candidate requires all critical capability stages to pass: discovery, classification,
compiled contract, live binding, authoritative Java execution, persistence, synchronization,
transport handoff, and physical Bedrock observation. It also requires the Java 25 build and tests,
failure and rollback coverage, lifecycle leak checks, dependency/security review, classified pack
validation output, and an evidence-backed real-mod matrix. The current repository does not satisfy
those gates and must not be described as production-ready or universally compatible.

---

# The Goal

The end goal is straightforward:

**Make modded Java Minecraft as accessible as possible to Bedrock players.**

Ideally:

```text
Install Java mods
       ↓
Start server
       ↓
Phlodgate discovers the mods
       ↓
Phlodgate analyzes what they contain
       ↓
Known compatibility is applied
       ↓
Generic compatibility is applied where possible
       ↓
Bedrock resources are generated
       ↓
Runtime bridges handle supported behavior
       ↓
Bedrock players join
```

The closer Phlodgate can get to that workflow without requiring a server owner to manually configure every mod, the better.

---

# Development

Phlodgate is being developed as an experimental extension of Hydraulic.

The project is intentionally focused on solving the difficult parts of modded Java → Bedrock compatibility rather than simply making unsupported content look correct.

If something cannot actually work yet, the compatibility system should say so.

If something can be adapted, there should be a defined path for doing it.

And when a new runtime bridge is added, it should become part of the same compatibility pipeline instead of another isolated special case.

## Production Bedrock Pack Deployment

After pack generation, validated archives are stored under:

```text
fabric/run/config/hydraulic/storage/<mod-id>/<mod-id>.mcpack
```

Deploy them to the resource-pack directory used by the production Geyser/Bedrock distribution with an explicit destination:

```powershell
.\scripts\deploy-generated-packs.ps1 `
       -DestinationRoot 'C:\path\to\production\bedrock\resource_packs' `
       -DryRun

.\scripts\deploy-generated-packs.ps1 `
       -DestinationRoot 'C:\path\to\production\bedrock\resource_packs' `
       -Force
```

The dry run validates each archive as a `.mcpack` ZIP, checks `manifest.json`, and reports SHA-256 hashes without changing files. A real deployment stages and verifies each archive before promotion and writes `.hydraulic-deployment.json`; it does not delete unrelated files. `-Force` is required to replace an existing archive whose hash differs. Ensure the server account can write the destination and restart or reload Geyser according to its deployment procedure.

Deployment success only proves that the files reached the destination. It does not prove that Geyser sent every runtime update or that a real Bedrock client rendered the result. Those remain separate transport and `CLIENT_OBSERVED` validation steps.

---

## Credits

Phlodgate is based on **Hydraulic**, developed by the **GeyserMC** project and contributors.

Hydraulic exists to allow Bedrock players to join modded Java Edition servers through Geyser.

Phlodgate builds on that foundation and focuses specifically on expanding automatic compatibility, compatibility analysis, metadata-driven adaptation, and runtime bridging.

* [GeyserMC](https://github.com/GeyserMC)
* [Hydraulic](https://github.com/GeyserMC/Hydraulic)


### Project Setup
1. Clone the repo to your computer.
2. Navigate to the Hydraulic root directory and run `git submodule update --init --recursive`. This command downloads all the needed submodules for Hydraulic and is a crucial step in this process.
3. If your default JVM/JDK is not Java 25, please set your IDE to use a valid Java 25 JVM. Otherwise, you will run into an error while building Hydraulic. 
4. The project should import into your IDE after the loom setup is complete. For more detailed information, see the [Fabric setup](https://docs.fabricmc.net/develop/getting-started/setting-up).
5. Use `./gradlew build` to compile jars and refresh the generated test assets used by this fork's Fabric test module.
6. Use `./gradlew :fabric:runServer` to run a server with Hydraulic installed. This runtime path reuses the last generated test assets instead of rerunning datagen on every launch. Make sure you have Geyser in your `mods` folder along with Hydraulic.
7. If you want to refresh only the generated test assets without a full build, run `./gradlew :test:prepareGeneratedResources`.
8. If you want to test metadata overrides in the Fabric dev environment, place your JSON files in `fabric/run/config/hydraulic/metadata` before starting the server.

## Links:
- Website: https://geysermc.org
- Docs: https://geysermc.org/wiki/other/hydraulic
- Download: https://geysermc.org/download?project=other-projects&hydraulic=expanded
- Discord: https://discord.gg/geysermc
- Donate: https://opencollective.com/geysermc
