# Hydraulic Fork Architecture Plan

## Goal
Turn this fork from a narrow override layer into a general compatibility system that can support the maximum practical number of Java mods for Bedrock players with the least possible per-mod hand work.

The target is not:

- block-only JSON overrides
- a pile of per-mod special cases
- a second full resource manager that keeps the whole modpack resident forever
- a fake compatibility score that hides broken behavior
- a Bedrock behavior-pack-first design that ignores what Geyser actually supports

The target is:

- one discovery pass over mod and resource roots
- one authoritative index shared by compatibility, conversion, validation, and caching
- a versioned Bedrock addon corpus and knowledge pipeline that feeds compatibility evidence without leaking into hot runtime paths
- typed compatibility data and compiled runtime plans
- automatic resource generation for the visual layer
- Hydraulic runtime bridges for interaction and behavior where assets are insufficient
- generic capability adapters before mod-specific adapters
- explicit reporting, provenance, confidence, and degradation evidence

The long-term scaling strategy remains:

1. automatic translation
2. metadata correction and override
3. generic capability adapter
4. mod-specific adapter
5. explicit unsupported result when gaps remain

## Post-Audit Execution Contract

The zero-trust audit is the release baseline, not evidence of completion. The permanent
implementation ledger is split across:

- `.github/Full-Implementation-Plan.md` for workstream status and exit criteria
- `.github/Runtime-Contract-Matrix.md` for capability lifecycle evidence
- `.github/Validation-Matrix.md` for server, transport, and physical-client gates

The current product is **not release-ready**. Selected item/block-use, fluid/energy transfer, machine,
lifecycle, menu, and Geyser transport slices are server- or transport-verified, but fluid, energy,
and entity action contracts, universal indexing, machine-to-recipe association, persistence proof,
and physical Bedrock observation remain open. A transport handoff must never be described as client
observation. Existing development worlds are preserved; clean runtime validation uses a separate
world path.

The implementation order is now locked: fluid actions, energy actions/state, entity actions,
machine persistence and rollback, automation lifecycle, pack-validation remediation, physical
E1-E10 evidence, Create-first real-mod validation, and a final zero-trust audit. The clean runtime,
recipe normalization, live binding, and menu transaction slices remain foundations, not completion
claims.

## Ground Truth Snapshot

### Date
- 2026-09-14

### Latest verified implementation slice
- Local Bedrock client connection triage and Geyser fallback hardening: the Windows Bedrock client
  repeatedly reached the Fabric/Geyser server on UDP `19132`; with Geyser `auth-type: offline` and
  Java `online-mode=false`, `SmokyDaStona` connected through Geyser, logged into the Java server, and
  joined the game server-side. The client still did not reach a clean client-observed gameplay state;
  Geyser emitted downstream decode errors while translating the large modded state stream. The first
  root cause was a Hydraulic custom-block registration abort for unbreakable blocks: Java negative
  destroy times were passed to Geyser as `destructibleByMining`, which Geyser rejects. `BlockPackModule`
  now omits that component for negative destroy times and clamps Java block-state hardness to `0` for
  override metadata. The previous `BlockPackModule/waystones` `Destructible by mining must be
  non-negative` error disappeared on restart. A stale normal dev world containing removed/renamed mod
  blocks was backed up, and a fresh flat/no-structure/peaceful creative world was used to isolate
  connection behavior from corrupted saved chunks. Two guarded fallback mixins now prevent specific
  Geyser mapping-boundary crashes observed during physical-client testing: `BlockMappingsMixin` maps
  unmapped Java block-state IDs to Bedrock air and logs each Java ID once, and `GeyserItemStackMixin`
  maps Java item IDs outside Geyser's `JAVA_ITEMS` table to `Items.AIR` and logs each ID once. Focused
  Java 25 compilation of `:shared:compileJava :fabric:classes` passed after the changes. This is
  graceful-degradation hardening and diagnostic evidence only: it intentionally hides unsupported
  block/item visuals as air and does not prove E2-E10 client observation, generated custom-content
  correctness, modded entity metadata translation, or production release readiness.
- Bedrock client data-folder research: `D:/Downloads/Hydraulic-mod/Minecraft Bedrock` was inventoried
  as a local Minecraft for Windows data/cache root. Most content is user/cache/marketplace data and is
  not admissible Hydraulic source material. The only directly relevant installed add-on was
  `Users/Shared/games/com.mojang/{behavior_packs,resource_packs}/PhlodgateA`. Its manifests identify
  the pack as `All Rights Reserved`, so no code or assets were copied into Hydraulic. Useful evidence
  was mapped back to the maintained sibling `Plodgate_Add-on` source tree instead: the behavior pack
  polls the canonical `phlodgate_bridge` scoreboard objective, detects non-vanilla namespaces from item
  and entity IDs, registers local recipe evidence in companion mode, inspects Bedrock-exposed block
  inventory components and numeric dynamic properties, and presents diagnostics through Bedrock forms;
  the resource pack repositions the vanilla action-bar HUD through `ui/hud_screen.json`. These are
  companion/client-local and validation patterns, not proof that Geyser can install or execute a Bedrock
  behavior pack during an ordinary Java server session.
- Generic block-entity state synchronization foundation: `BlockEntityStateSynchronizer` snapshots
  authoritative `BlockEntity.saveWithoutMetadata(level.registryAccess())` output for every live
  discovered block entity, retains only an ephemeral previous tag, and emits a `block_entity.state`
  delta through `SessionAutoFlushCoordinator` when serialized state changes. Lifecycle install,
  unbind, replacement, and garbage collection clear the runtime snapshot; no live block entity is
  stored as persistent state. Focused Java 25 tests and shared compilation pass. This establishes
  safe state observation and delta production, not arbitrary reflective mutation or physical client
  observation; generic block-entity packet encoding remains explicitly unsupported where no concrete
  Geyser mapping exists.
- Metadata-declared energy block-use actions: `EnergyBlockUseActionPlan` compiles bounded
  `interaction.energy.action`, `interaction.energy.amount`, optional side, and optional property
  facts for exact `RECEIVE` or `EXTRACT` operations. `BedrockRuntimeActionRouter` executes the
  operation through `RuntimeTargetDiscovery.transferEnergy`, rejects partial commits, preserves
  the trace, and can project the moved amount through the existing container-property sync path.
  Focused Java 25 parser, router, transfer, and pack-validation tests pass. This is server-path
  and transport-ready evidence; persistence and physical client observation remain open.
- Pack failure classification: explicit generator defect codes are now classified before broad
  path heuristics, so malformed generated JSON is reported as `GENERIC_GENERATOR_DEFECT` even when
  its archive entry contains a path-like name. Focused validator and tracker tests cover the
  classification regression. Missing generated output is classified separately as
  `NO_CONVERTIBLE_OUTPUT`, which prevents resource-less Fabric modules from being mislabeled as
  generator defects. This prevents a validator finding from being mistaken for a content
  incompatibility, but does not by itself remediate every third-party pack defect.
- Third-party corpus runtime evidence: a Java 25 `:fabric:runServer` validation loaded 231 Fabric
  mods, reached Minecraft/Geyser readiness on UDP `19132`, compiled 3,128 datapack recipes,
  normalized 3,126 runtime entries, and classified 5,808 as `RECIPE_RUNTIME_UNKNOWN`. The persisted
  pack report contained 125 records: 69 valid, 56 invalid, 50 missing-output records, 15 missing
  selected textures, and 316 long-path warnings. The corrected classifier maps those missing-output
  records to `NO_CONVERTIBLE_OUTPUT`; focused tests verify that mapping. Per-mod conversion continued
  through malformed metadata such as Gilded Armor's invalid pack format range. This is real-mod
  artifact/startup evidence only; object behavior, persistence, and physical Bedrock observation
  remain open.
- Metadata-declared fluid action handoff: `FluidBlockUseActionPlan` compiles explicit fill/drain
  bucket-exchange contracts from `interaction.fluid.*` facts. `BedrockRuntimeActionRouter` resolves
  the live tank through compiled dispatch, simulates the exact amount before mutation, exchanges the
  Java held item only after commit, rolls the tank back if that exchange fails, and emits a traceable
  numeric `container.property` update when configured. The bundled fluid-machine fixture declares a
  water-bucket drain contract. Focused Java 25 tests and Fabric compilation pass. Physical client
  observation and restart persistence remain open and are not promoted by this evidence.
- Zero-trust audit baseline: `.github/Audit-Report.md` records the active-repository scope,
  evidence levels, capability matrix, false-completion risks, scores, and remediation phases.
  It explicitly classifies the current product as partially executable/server-verified for selected
  slices, with no E6 client observation and release readiness not achieved.
- Scoreboard-load recovery: the existing generated development world was preserved and replaced
  for validation. Minecraft's failure occurred while deserializing duplicate
  `phlodgate_bridge` objectives before Hydraulic initialization. The scoped
  `ScoreboardObjectiveMixin` now reuses only Hydraulic's existing signal on load; Java 25
  compilation and a clean-world Fabric/Geyser startup plus clean shutdown passed without the
  duplicate-objective failure. Same-world restart remains blocked by a Windows Architectury
  dev-jar lock after shutdown and is not yet claimed as verified.
- Session lifecycle binding: ticking machine synchronization now reconciles its session pipelines
  against Geyser's live connection snapshot, creating pipelines for active sessions and retiring
  disconnected ones. This prevents retained dirty-state and transport references while preserving
  the existing same-level/range delivery filter. Focused coordinator tests pass; physical client
  delivery remains unverified.
- Universal live capability binding: `LiveCapabilityBinder` now owns weak, ephemeral bindings from
  live runtime objects to dynamically verified compiled plans. Bindings carry object/type identity,
  capability and adapter evidence, bridge kinds, contract version, and confidence. Existing indexed
  presentation plans are augmented with verified runtime bridges without promoting their support level,
  and stale dynamic dispatch indexes are replaced. Minecraft 26.2 `setRemoved` and `clearRemoved` hooks
  remove and recreate bindings. Focused tests prove an adapter-unknown inventory can be simulated,
  mutated through production dispatch, unbound, rebound, and reverified; real-mod and Bedrock-client
  round trips remain open. A live Fabric run reached `Done` and started Geyser with the Minecraft
  26.2 lifecycle hooks applied and no Hydraulic binding/mixin failure.
- Live lifecycle binding and position-filtered synchronization: the compiled compatibility registry
  now installs a shared runtime lifecycle coordinator. A server-side `BlockEntity.setLevel` seam
  discovers unmapped block-entity contracts, `ServerPlayer.openMenu` records concrete menu-type
  evidence, and Minecraft's concrete `LevelChunk$BoundTickingBlockEntity.tick` seam drains machine
  changes through coalesced synchronization targeted to active Geyser sessions in the same level and
  tracking range. The ticker seam is verified against the remapped 26.2 class; a full server run
  reached pack conversion without a Hydraulic mixin-transform failure, but the supplied dev world
  then stopped on a pre-existing duplicate `phlodgate_bridge` scoreboard objective.
- Recipe-manager boundary correction: active resource-manager JSON recipes continue to compile into
  typed `RecipeIR`, while live `RecipeManager` entries are serialized through Minecraft 26.2's
  registry-aware recipe codec. Built-in and explicitly registered specialized serializers may produce
  executable projections; opaque or unsupported entries produce `RECIPE_RUNTIME_UNKNOWN`. Catalysts
  remain non-consumed requirements, and unresolved tags, component predicates, alternative inputs,
  environmental/kinetic requirements, conditions, and chance outputs fail closed. Reload publication
  swaps immutable complete snapshots so failed scans cannot replace the last good recipe state.
  A final Java 25 runtime inspected 8,934 manager entries, normalized 3,126, classified 5,808 as
  `RECIPE_RUNTIME_UNKNOWN`, and reached Minecraft `Done`. Automatic association between arbitrary
  live machines and the correct normalized recipes remains open.
- Authoritative menu transaction round trip: Geyser-originated Java container clicks and buttons are
  validated after Minecraft's server-thread scheduling barrier and delegated to vanilla menu handling
  for mutation. Exact before/after stack and component state produces traceable transactions, while
  completion and rejection use `AbstractContainerMenu.broadcastFullState()` for canonical resync.
  Explicit `menu.button.<id>=button|toggle` facts compile into the runtime action plan; the bundled
  menu-machine fixture supplies a persisted toggle target. Focused and full Java tests pass, Fabric
  compilation passes, and a live run reached Minecraft and Geyser readiness without menu-mixin failure.
  Physical Bedrock action and observation remain unverified.
- Handoff resilience: malformed persisted compatibility handoff files are now skipped with a warning
  and regression-tested instead of producing startup error noise or aborting queue loading.
- Machine-readable evidence maturity: `CompatibilityObject` now carries an additive
  `implementationMaturity` value distinguishing `UNKNOWN`, `ARCHITECTURE_IMPLEMENTED`,
  `CAPABILITY_IMPLEMENTED`, `INTEGRATED`, `VERIFIED`, and `CLIENT_VERIFIED`. Analyzer-created
  objects begin at `ARCHITECTURE_IMPLEMENTED`, legacy constructor/report data defaults to `UNKNOWN`,
  and maturity is independent of support level and score. This establishes the evidence model;
  validation promotion to `VERIFIED` and manual Level 4 promotion to `CLIENT_VERIFIED` remain
  explicitly gated by recorded evidence.
- Dynamic lifecycle transfer contract correction: `DynamicMachineLifecycleManager` now consumes the
  canonical fluid facts emitted by `SemanticDiscoveryEngine` (`has_fluid`, `can_insert_fluid`, and
  `can_extract_fluid`) instead of stale aliases, so an executable discovered tank can bind the
  `FLUID_TRANSFER` bridge. Duplicate discovery for one identifier is idempotent under concurrent
  registration, and focused regression coverage proves the compiled plan reaches the runtime
  dispatch table. This validates dynamic plan compilation; it does not claim that arbitrary third-
  party block entities are automatically discovered without a lifecycle caller.
- Defensive pack metadata sanitization: `MinecraftResourcePackReaderImplMixin` now normalizes malformed `pack.min_format` arrays such as `[107, 1]` before they reach Creative's deserializer, preventing third-party resource packs like Mcaw's Furniture from crashing the entire conversion pipeline while preserving valid pack metadata behavior.
- Regression coverage for malformed resource metadata: `MinecraftResourcePackReaderImplMixinTest` verifies the sanitization and the unsupported item-model schema guardrails remain in place.
- Dynamic Machine Block-Entity Lifecycle: `DynamicMachineLifecycleManager` discovers unmapped legacy and modern block entity runtime shapes on the fly via `SemanticDiscoveryEngine`, then compiles only bridge kinds that the existing reflective transfer factories can construct as executable. Method-name evidence alone remains `VISUAL_ONLY`; processing behavior additionally requires concrete recipe facts (Phase 5B).
- Session Auto-Flush for Ticking Multi-Resource Machines: `SessionAutoFlushCoordinator` auto-flushes dirty-state deltas across active `GeyserSession` connections on machine tick transitions and multi-resource transactions (Phase 8).
- Universal Menu IR Pagination and Search: `PaginatedMenuForm` implements client-side item searching and multi-page chunking for large virtual inventory networks (AE2 / Refined Storage) generating Bedrock SimpleForm JSON payloads (Phase 6).
- Multi-Platform Physical Bedrock Client Attestation Matrix: `publish-attestation-matrix.ps1` publishes structured level-4 manual observation records across Windows 11, iOS, Android, and Nintendo Switch (Phase 10).
- Multi-Era Version Mapping & Normalization: `MinecraftVersionEra`, `CrossVersionClassMapper`, `LegacyModelNormalizer`, and `BedrockSchemaValidator` normalize legacy Java mod assets and enforce strict Bedrock store format versions.
- Focused runtime and conversion tests have passed for selected slices in prior Java 25 runs;
  this is not 100% project coverage and does not replace the pending restart/persistence,
  physical-client, and real-mod evidence gates.

### Porting Lib research boundary (2026-09-13)
- The public `Fabricators-of-Create/Porting-Lib` repository was reviewed at its `1.21.1` branch. Its README and module layout confirm reusable design references for `transfer`, `fluids`, `blocks`, `items`, `gui_utils`, `resources`, `data`, `entity`, `model_data`, `model_loader`, `registry`, `tags`, and `mixin_extensions`.
- The useful architectural lesson is contract normalization: simulation-aware transfer operations, explicit sided access, typed fluid/item state, and lifecycle-safe adapters should inform Hydraulic's existing bridge contracts. Hydraulic must not copy third-party implementation code or assets; it should use the repository as an API-pattern and compatibility reference subject to its license and version boundaries.
- The supplied Copilot share was not accessible as technical source material. Its response only exposed the Copilot shell page, so no implementation or compatibility claim is based on that link.

### BOs Easy Model Entities research boundary (2026-09-15)
- `MarkusBordihn/BOs-Easy-Model-Entities` was reviewed at the `1.20.1` branch. The useful, portable concepts are its split server/render profile layout, schema/version fields, explicit model and texture references, bounded validation diagnostics, body-type agreement checks, and named animation settings.
- The upstream source is MIT, but its license explicitly excludes models, textures, sounds, animations, and other artistic assets. Hydraulic therefore does not copy upstream code, assets, or the 1.20.1 loader/rendering runtime, and does not add the project as a dependency.
- Hydraulic now consumes only the profile evidence boundary through `EntityPresentationProfileScanner`: it reuses `ModResourceIndex.fileStamps()` rather than walking mod roots again, enforces a 256 KiB JSON limit, validates required fields and indexed model/texture references, and degrades malformed or missing assets to structured profile issues.
- Per-mod evidence is written to `config/hydraulic/reports/entity-presentation/<mod>.json` during entity pack post-processing. This artifact supports presentation diagnostics and conversion prioritization only; it does not create entity behavior bridges, Bedrock behavior-pack execution, runtime rendering, or `CLIENT_OBSERVED` evidence.

### Additional Bedrock and entity research boundary (2026-09-15)
- `PowerNukkitX` is an LGPL-3.0 standalone Bedrock server. Its custom content registration, AI, container, command, and world-generation patterns are useful vocabulary references, but it is not a Java/Geyser bridge and is not a Hydraulic dependency.
- `AzureLib` is a Bedrock-model-oriented Java animation library with MIT repository metadata and additional repository licenses. Its keyframe, easing, event, and model-presentation concepts are reference material only; no renderer, model, texture, or secondary-licensed asset is copied.
- `ExtraBiomes` is an MIT paired Java/Bedrock content project and a useful cross-edition regression candidate for biome, structure, block, item, and entity breadth. Its assets and credits remain external; no parity or runtime support is inferred from the repository alone.
- `BedrockMotion` exposes a useful independent animation architecture (bone targets, keyframes, controllers, render controllers, MoLang, and pack parsing), but is GPL-3.0. `GeyserDisplayEntity` provides a useful display-entity/Geyser seam reference, but is AGPL-3.0 and credits code from a Geyser display-entity branch. Neither project is copied, linked, shaded, or added as a dependency.
- `mcpe-bedrock-script` contributes only README-level Script API vocabulary and has no inspectable source/license evidence in the supplied repository state. It remains low-confidence documentation evidence, not executable input.
- `minecraft-bedrock-edition-vanilla-pack` is explicitly rejected as a security source: its README promotes disabling Windows Defender and running a password-protected executable, while its repository listing contains encrypted binaries/configuration and credential-like files. Hydraulic must not download, execute, vendor, or reference its content.
- The production implementation consequence is explicit non-integration: these sources update the offline research/security boundary only. Hydraulic continues to use independently authored indexed resources, typed compiled plans, and Java-server-authoritative Geyser transport; no third-party Bedrock runtime is introduced.

### External Bedrock and Fabric research boundary (2026-09-13)
- The supplied `awesome-fabric`, `awesome-minecraft`, and `awesome-minecraft-bedrock` repositories are curated catalogs, not runtime libraries. They may identify candidate sources for offline corpus research, but catalog entries never become dependencies or compatibility proof by themselves.
- `Mojang/minecraft-creator-tools` is a separate MIT-licensed authoring and validation tool. Its bundled vanilla assets have Minecraft EULA terms. Hydraulic may document an optional operator/CI validation step around an installed `@minecraft/creator-tools` CLI, but Hydraulic startup and pack generation must not require Node.js, npm, network access, or vendored Mojang assets.
- The old `bridge-core/bridge.` repository is superseded by `bridge-core/editor`; both are GPL-3.0 applications. Their schema-aware editing, diagnostics, and packaging concepts may inform offline validation, but their application code must not be embedded or linked into Hydraulic's current distribution.
- `JaylyDev/ScriptAPI` is an MIT-licensed community sample repository. Stable-branch scripts and official Script API references may supply corpus evidence for Bedrock capability classification. They do not prove behavior-pack execution in a Java/Geyser session, and no script is a Hydraulic runtime dependency.
- `InnateAlpaca/BedrockBridge` is an MIT-licensed Bedrock Dedicated Server plus Discord add-on. Its BDS-only modules, permissions, experiments, and Discord token flow are outside Hydraulic's Java/Geyser runtime and must not be presented as a Hydraulic bridge.
- `Broadcaster-master.zip` is the GPL-3.0 MCXboxBroadcast project. Its Geyser extension broadcasts Bedrock listener state to Xbox Live through authenticated REST/WebSocket/NetherNet/WebRTC code; it does not convert content or execute Hydraulic runtime plans. It is reference-only and must not be copied, shaded, linked, or added as a dependency.
- Broadcaster's tokens, session dumps, screenshots, external-IP discovery, friend management, and social presence are outside Hydraulic's trust boundary. Hydraulic must not import those credential or network surfaces into pack generation, corpus ingestion, reports, or runtime bridges.
- The detailed source disposition, license notes, and security review are recorded in `.github/external-research-report.md`. No source code, asset, binary, or npm package from these projects was copied into this repository.

### External compatibility evidence synthesis (2026-09-14)
- `Furzide/MCBE-Tweaks` is a Bedrock client tuning and optimization guide, not a Java/Geyser compatibility engine. Its value here is limited to Bedrock client expectations, graphics/performance tuning, and version-compatibility awareness; it does not provide valid server-side mod behavior or a supported runtime integration contract for Hydraulic.
- `JaylyDev/ScriptAPI` is the clearest reference for Bedrock Script API conventions, pack-side events, and experimental capability patterns. It is a useful offline corpus for Bedrock capability classification and event naming, but it remains non-authoritative for Java server compatibility claims and must never be treated as a Hydraulic dependency or proof of Geyser-side execution.
- `LiteLDev/LeviOptimize` is a Bedrock Dedicated Server optimization layer for TPS and chunk/physics performance. It is relevant to optimization patterns and operational constraints, but it is not a compatibility translator and it does not solve cross-version Java mod interoperability.
- `bedrock-dot-dev` is the canonical Bedrock addon and registry reference for the pack schema, tag semantics, and documentation surface that Hydraulic must align with. It is the strongest public source for pack-generation correctness, vanilla registry facts, and Bedrock-side conventions.
- `Bedrock-OSS/regolith` is the best public reference for deterministic addon compilation flows: project-folder source of truth, filter pipelines, generated outputs, and reproducible packaging. This directly informs Hydraulic's builder architecture while reinforcing that generated pack output is a delivery artifact, not a proof of Java-mod gameplay support.
- Core rule: use these repositories as evidence, schema references, and build-pattern inspiration only. Hydraulic keeps its runtime behavior server-authoritative, with local corpus evidence, metadata decisions, and compiled runtime plans as the accepted integration surfaces; no external Bedrock repo becomes a production dependency or a shortcut past verification.

### Verified environment gate (2026-09-14)
- Active validation shell uses `D:/jdks/jdk-25.0.2` with redirected Gradle, temp, and cache directories.
- `:shared:test` and `:fabric:compileJava` pass under Java 25.
- A fresh validation world reached Minecraft `Done`, started Geyser on UDP `19132`, and applied the
  menu and lifecycle mixins without an injection failure. The previous world remains preserved as
  audit evidence; it is not reused as a release baseline.
- Result: Java/build and server-startup verification are available in this environment, but release
  readiness still requires persistence/restart evidence, pack-failure classification, generic
  non-menu action round trips, and real Bedrock-client observation.

### Validated repo baseline
- Live fork: `smokydastona/Hydraulic--Skeleton_Key`
- Current workspace branch tracks Minecraft `26.2`
- Current validation shell uses the target Java `25` toolchain required by the Gradle build.
- The live repository is the authority over older notes that still mention `1.20.1` or Java `17`

### Validated code and runtime scope
- `shared/src/main/java/org/geysermc/hydraulic/pack/PackManager.java`
- `shared/src/main/java/org/geysermc/hydraulic/pack/ModResourceIndex.java`
- `shared/src/main/java/org/geysermc/hydraulic/pack/PackUtil.java`
- `shared/src/main/java/org/geysermc/hydraulic/block/BlockPackModule.java`
- `shared/src/main/java/org/geysermc/hydraulic/item/ItemPackModule.java`
- `shared/src/main/java/org/geysermc/hydraulic/metadata/*`
- `shared/src/main/java/org/geysermc/hydraulic/compat/*`
- `shared/src/test/java/org/geysermc/hydraulic/metadata/MetadataLoaderTest.java`
- `shared/src/test/java/org/geysermc/hydraulic/compat/MappingResolverTest.java`
- `fabric/run/config/hydraulic/metadata/*`
- `fabric/run/config/hydraulic/reports/*`

### Validated Geyser constraints
- Geyser is the correct foundation for Bedrock pack delivery.
- Geyser can register Bedrock resource packs and deliver them to clients.
- Geyser does not provide a normal server-side Bedrock behavior-pack or add-on execution model that Hydraulic can treat as its primary behavior architecture.
- Hydraulic should therefore treat generated Bedrock resource packs as first-class and generated Bedrock behavior content as optional and secondary to Hydraulic runtime bridges.
- Public Geyser APIs already help with resource packs and custom block, item, and entity registration, but menu and block-entity runtime integration remains constrained and may require carefully targeted seams rather than broad assumptions.

### NetherNet protocol boundary (2026-09-13)
- `df-mc/nethernet-spec` is the original reverse-engineered specification. `PrismarineJS/node-nethernet` and `LucienHH/bedrock-portal-nethernet` are MIT JavaScript/TypeScript implementations with working UDP discovery, WebRTC data-channel connection APIs, and 10,000-byte segment behavior. `bedrock-v/nethernet` is a current MIT V implementation with server-data version 7, identity-bound SDP, non-trickle ICE, multi-homing controls, and 262,143-byte segments.
- The current wire facts are typed discovery packets on UDP `7551`, AES-ECB/HMAC-SHA256 using the little-endian uint64 `0xdeadbeef` application key, uint32-length-prefixed response/message payloads, server-data v7 varints and strings, numeric uint64 signaling IDs, `CONNECTERROR`, two named data channels, and raw segment payloads with a countdown byte. The older 10,000-byte README rule is retained by the JavaScript implementations but is not universal across current implementations; Hydraulic uses the current 262,143-byte ceiling and 256-segment counter limit.
- Hydraulic implements only the deterministic wire-format slice in `shared/src/main/java/org/geysermc/hydraulic/compat/nethernet`: typed discovery encode/decode, server-data v7 encode/decode, signaling parsing, and raw reliable-channel segment reassembly. The implementation uses standard JDK cryptography, strict size limits, constant-time HMAC comparison, and fail-closed malformed-input handling.
- This is not a live NetherNet transport. Hydraulic does not ship a WebRTC stack, DTLS/SCTP integration, identity/JWS verification, Xbox Live/PlayFab authentication, signaling credentials, a UDP discovery listener, or a Geyser session replacement. The original specification also says direct connections were not supported.
- NetherNet codec evidence is covered by focused unit tests for authentication tampering, malformed lengths and metadata, signaling grammar, segment boundaries, out-of-order segments, and safety limits. No client connectivity or `CLIENT_OBSERVED` result may be inferred from those tests.
- Validation on 2026-09-13: the focused NetherNet suite passes all 10 tests and touched-file diagnostics are clean. The full `:shared:test` suite reaches 362 tests but has one unrelated pre-existing Loom `ClassFormatError` while initializing `StateDefinitionTest` (`LootPoolSingletonContainer`), so full-suite release readiness remains blocked independently of this protocol slice.

## Executive Verdict

The architecture is directionally correct, and the current workspace has a valid Java 25 build and
clean-world Fabric/Geyser startup, but it is not release-verified because gameplay, persistence,
pack-remediation, real-mod, and physical-client gates remain open.

The implementation work already checked into the repo is substantial and materially better than the
earlier prototype state: discovery, machine-runtime, menu, packet-action, transfer, sync, corpus, and
invalidation layers exist in code and are codified in the repository. However, the current environment
cannot claim full production completion. The build and server start under Java 25, but no official
Bedrock client has completed the required gameplay and persistence matrix, and several universal
contracts remain incomplete.

The main performance problem is not one isolated slow method.

The main problem is duplicated discovery and repeated parsing across multiple subsystems.

Today the same mod can be touched repeatedly by:

- `ModResourceIndex`
- `CompatibilityManager` asset scanning
- `PackUtil.getModUUID()` tree hashing
- resource-pack readers
- model indexing
- actual conversion

That must be replaced with:

```text
                 MOD / RESOURCE ROOTS
                         |
                         v
              +-----------------------+
              | UNIVERSAL INDEX       |
              |                       |
              | files                 |
              | resources             |
              | registries            |
              | models                |
              | textures              |
              | blockstates           |
              | data                  |
              | fingerprints          |
              | dependencies          |
              +-----------+-----------+
                          |
             +------------+------------+
             |            |            |
             v            v            v
      COMPATIBILITY   CONVERSION   VALIDATION
         ANALYSIS       ENGINE       ENGINE
             |            |            |
             +------------+------------+
                          |
                          v
                 GENERATED ARTIFACTS
                          |
                    PERSISTENT CACHE
                          |
                          v
                     GEYSER / BEDROCK
```

One discovery pass. One source of truth. Aggressive caching. Lazy parsing. Bounded memory. Parallel work only when data shows it helps.

## Locked Architecture

```text
                         HYDRAULIC
                             |
                      Mod Discovery
                             |
                             v
                   +--------------------+
                   | UNIVERSAL INDEX    |
                   +---------+----------+
                             |
          +------------------+------------------+
          |                  |                  |
          v                  v                  v
    Registry Facts     Resource Facts      Runtime Facts
          |                  |                  |
          +------------------+------------------+
                             |
                             v
                  +-----------------------+
                  | DISCOVERY IR          |
                  +-----------+-----------+
                              |
                              v
                  +-----------------------+
                  | COMPATIBILITY IR      |
                  +-----------+-----------+
                              |
          +-------------------+-------------------+
          |                   |                   |
          v                   v                   v
     Automatic           Knowledge           Adapter
    Translators           Engine              Engine
          |                   |                   |
          +-------------------+-------------------+
                              |
                              v
                  +-----------------------+
                  | COMPILED              |
                  | COMPATIBILITY PLAN    |
                  +-----+-----------+-----+
                        |           |
                        |           +---------------------+
                        |                                 |
                        v                                 v
              +------------------+             +------------------+
              | RESOURCE IR      |             | BRIDGE IR        |
              +--------+---------+             +--------+---------+
                       |                                |
                       v                                v
              Bedrock Resource Pack           Hydraulic Runtime Bridges
                       |                                |
                       +---------------+----------------+
                                       |
                                       v
                               Validation / QA
                                       |
                                       v
                           Content-Addressed Artifact Cache
                                       |
                                       v
                              Geyser Pack Delivery
                                       |
                                       v
                                 Bedrock Player
```

The compatibility layer stays above the current Hydraulic conversion pipeline, but it must stop being a collection of independent rediscovery passes.

## Guiding Principles
- Keep the current Hydraulic conversion pipeline as the base engine.
- Make the live repo and runtime artifacts authoritative over stale notes.
- Fix root-cause duplication before broadening adapter count.
- Keep compatibility semantics correct before optimizing hot paths.
- Prefer one indexed source of truth over repeated filesystem walks.
- Prefer typed IR and compiled runtime plans over flexible runtime interpretation.
- Treat metadata as an override and patch layer, not the primary implementation strategy.
- Prefer shared compatibility logic in `shared/` over platform-specific logic.
- Prefer graceful degradation over aborting a full conversion run.
- Separate presentation compatibility from interaction, behavior, and network compatibility.
- Keep Bedrock resource generation first-class and Bedrock behavior generation optional.
- Make runtime dispatch identifier-driven and approximately `O(1)`.
- Bound memory, not just thread count.
- Prove optimization claims with artifacts and measurements.

## Asymmetric Version Lifecycle: Forced Bedrock Target vs Cross-Version Java Mod Consumption

A core reality of cross-platform Minecraft compatibility is version asymmetry:

```text
+-----------------------------------------------------------------------------------+
| BEDROCK ENVIRONMENT (Forced Client Updates)                                       |
| - Automatic store updates across Windows, Android, iOS, Xbox, PlayStation, Switch |
| - Non-negotiable client protocol & schema versions (e.g., Geometry 1.21+, Blocks) |
| - Hard requirement: Hydraulic MUST emit packs strictly matching active Bedrock   |
+-----------------------------------------------------------------------------------+
                                         ▲
                                         │  (Target Bedrock Emission Engine)
+-----------------------------------------------------------------------------------+
| HYDRAULIC UNIVERSAL IR & NORMALIZATION CORE                                       |
| - Presentation IR (Version-agnostic models, textures, animations, materials)      |
| - Recipe & Data IR (Normalized inputs, outputs, catalysts, process durations)     |
| - Capability IR (Normalized item, fluid, energy, menu, and machine behaviors)     |
+-----------------------------------------------------------------------------------+
                                         ▲
                                         │  (Multi-Era Ingestion & Reflection)
+-----------------------------------------------------------------------------------+
| JAVA MOD ECOSYSTEM (Historical Fragmentation & Delayed Updates)                   |
| - Legacy Models (1.8 - 1.20.4 parented models, multipart blockstates)             |
| - Modern Item Definitions (1.20.5+ / 1.21+ / 26.2 data component schemas)         |
| - Disparate Capability APIs (Forge IItemHandler, NeoForge, Fabric Transfer, etc.) |
| - Server reality: Mods continue running on servers even after versions advance    |
+-----------------------------------------------------------------------------------+
```

### 1. The Bedrock Reality: Forced Updates are a Hard Requirement
- Bedrock players are subject to mandatory platform updates from consumer app stores. A server cannot hold Bedrock clients back on older protocol versions.
- Hydraulic therefore treats the **current Bedrock version and its schema specifications as an uncompromisable target constraint**.
- Generated resource packs, attachables, custom block definitions, and Geyser transport handoffs must always compile to the current Bedrock client specification.

### 2. The Java Reality: Mod Fragmentation & Cross-Version Longevity
- Java server mods rarely update synchronously with Minecraft releases. Server operators frequently run forward-ported mods, legacy modpacks, connector shims, or long-standing server versions.
- Hydraulic must **consume mod assets, datapacks, and runtime capabilities from any Minecraft version era** without failing when encountering legacy structures or modern schema shifts.

### 3. Architecture for Cross-Version Mod Ingestion & Current Bedrock Emission
1. **Multi-Era Asset & Model Normalization**:
   - Ingests classic `models/block` / `models/item` hierarchies (parent resolution, element coordinates, rotation matrices, texture maps).
   - Ingests modern 1.21.4+ `assets/<namespace>/items/*.json` definition files.
   - Degrades gracefully on unsupported third-party custom loaders (e.g. Citadel custom item models) to legacy model fallbacks or 2D sprite representations without interrupting conversion.
   - Emits unified geometry, materials, and attachables conforming to the current Bedrock format specification.

2. **Universal Recipe & Datapack Ingestion**:
   - Ingests classic JSON crafting/smelting formats alongside modern component-based datapack recipes and kinetic assembly formats (Create, Mekanism, Thermal).
   - Normalizes all recipe schemas into the canonical `RecipeIR` (inputs, outputs, catalysts, durations, byproducts).

3. **Cross-Version Capability Normalization**:
   - Discovers and binds across legacy Forge capabilities (`IItemHandler`, `IFluidHandler`, `IEnergyStorage`), modern NeoForge capability registrations, Fabric Transfer API (`Storage<T>`), Botarium, and native `Container`/`BlockEntity` implementations.
   - Normalizes these into the single `ItemTransfer`, `FluidTransfer`, and `EnergyTransfer` runtime bridge substrate.

4. **Decoupled Cache Invalidation**:
   - `ConversionKey` separately fingerprints the Java source mod assets, the metadata configuration, and the **target Bedrock / Geyser version**.
   - When Bedrock updates its schemas, Hydraulic regenerates the Bedrock artifacts cleanly from the cached universal index without requiring any changes to the source Java mod jars.

## Canonical Capability Contract

Every compatibility reference, analyzer result, and runtime bridge must map to
the same capability vocabulary. A capability result must include its support
level, evidence, limitations, persistence method, runtime hooks, and confidence.

| Capability | Required semantics |
| --- | --- |
| `BLOCK_ENTITY_DATA` | State identity, serialization, synchronization, persistence, and safe patching |
| `MACHINE_INVENTORY` | Slot roles, capacity, sided access, filters, insertion, extraction, and simulation |
| `ITEM_TRANSFER` | Stack identity, count limits, insert/extract, simulation, sided access, and failure behavior |
| `FLUID_RUNTIME` | Fluid identity, block/item representation, tanks, capacity, and translation |
| `FLUID_TRANSFER` | Fill, drain, simulation, capacity, sided access, and cross-fluid rejection |
| `ENERGY_TRANSFER` | Capacity, receive/extract, rates, simulation, sided access, and persistence |
| `MENU_CONTAINER` | Slot semantics, transactions, menu type, interaction, and fallback behavior |
| `AUTOMATION_ACCESS` | Pipes, conveyors, routing, filtering, sided transfer, and network membership |
| `MACHINE_PROCESSING` | Recipes, inputs, outputs, catalysts, progress, energy/fluid consumption, and state |
| `PRESENTATION` | Models, textures, animations, geometry, particles, sounds, and state visuals |
| `NETWORK_SYNC` | Custom packets, clientbound state, serverbound interaction, and synchronization risk |

Use the support vocabulary `NATIVE`, `AUTOMATIC`, `ADAPTED`, `APPROXIMATED`,
`VISUAL_ONLY`, and `UNSUPPORTED`. A score must never override a missing
critical capability, and visual conversion must never imply gameplay support.

Global executable-state invariant:

```text
EXECUTABLE
= every required capability
  + every required operation
  + failure semantics
  + authoritative state mutation
  + persistence
  + synchronization
  + verified transport handoff
```

These stages remain separate: visual support is not interaction support,
interaction support is not behavior support, behavior support is not network
support, and a verified transport handoff is not proof of Bedrock client
observation. `CLIENT_OBSERVED` is only a manual, human-attested result.

## Java Normalization And Bedrock Feasibility

Forge capabilities, Fabric Transfer APIs, and mod-specific APIs are inputs to
one normalized model. The normalizer must preserve simulation semantics,
sided access, slot and tank identity, capacity, filtering, and failure
isolation. Reflection or metadata may discover a capability, but a compiled
runtime plan must not advertise executable behavior without a concrete
operation.

Primary normalization references are Forge `IItemHandler`, `IFluidHandler`,
and `IEnergyStorage`, Fabric item and fluid transfer abstractions, Botarium,
and the existing Hydraulic typed bridge contracts.

Bedrock feasibility must be evaluated in this order:

```text
native Bedrock API
  -> supported Script API
  -> proven generic emulation
  -> approximation with explicit degradation
  -> unsupported
```

Generated resource packs remain first-class. Behavior packs and scripts are
optional supplements, while Java server state remains authoritative. Prefer
current parameterized or flattened custom-component patterns over obsolete
static designs; generated runtime state should use typed identifiers and
fields rather than late string interpretation.

## Capability-Slice Priorities And Acceptance

Implement capability breadth in this order:

1. Item transfer and inventory: slot semantics, insertion, extraction, simulation, side restrictions, filters, stack handling, full-inventory behavior, and transaction boundaries.
2. Fluid transfer: normalized stacks and tanks, fill/drain, identity checks, capacity, side restrictions, container representation, and machine I/O. A bucket icon fallback is presentation only.
3. Machine execution: inputs, consumption, requirements, progress, outputs, output capacity, reset behavior, and persisted state. Generic processing precedes mod-specific adapters.
4. Energy: source, network, storage, rates, sided access, consumption, simulation, and persistence. An energy bar alone is insufficient.
5. Menus, interaction, and block entities: slot roles, menu archetypes, transactions, state, synchronization, and explicit fallback behavior.
6. Automation and interoperability: routing, filtering, pipes or conveyors, network membership, cross-addon communication, and typed synchronization.

Each capability slice is complete only when it has:

1. A typed normalized contract and validation rules.
2. A compiled runtime-plan projection.
3. A production call path or an explicit reportable unsupported result.
4. Unit tests for normal, simulation, boundary, malformed, and failure cases.
5. Integration coverage at the nearest Fabric/Geyser seam when applicable.
6. Persistence and cache-invalidation coverage when state or corpus evidence changes.
7. Report evidence distinguishing visual, interaction, behavior, and network support.
8. Provenance, version constraints, and licensing/admissibility documentation.

No phase is complete because an interface exists. No adapter may report success
for an operation it cannot execute.

## Runtime Traceability And Validation Evidence

Runtime execution status must distinguish server-side execution from client-observed behavior:

```text
EXECUTABLE
  -> SYNCHRONIZED
  -> TRANSPORT_HANDOFF_VERIFIED
  -> CLIENT_OBSERVED
  -> VALIDATED
```

`TRANSPORT_HANDOFF_VERIFIED` means Hydraulic produced encoded synchronization and handed a concrete packet or update to a real Geyser/Hydraulic transport boundary. `CLIENT_OBSERVED` means a Bedrock client actually received and displayed the resulting authoritative Java state. These must never be collapsed into one claim.

Every Bedrock-originated runtime action should carry one traceable path:

```text
RuntimeTraceId
  -> target resolution
  -> transfer request
  -> transaction result
  -> state change set
  -> sync batch
  -> encoded sync operation
  -> transport delivery result
```

One input action must map to one authoritative Java mutation path and zero or more explicit synchronization operations. Encoding success alone is not delivery success, and delivery handoff alone is not client observation.

## Bedrock Addon Corpus Strategy

The Bedrock addon corpus is part of the long-term compatibility substrate, but it is not a replacement for the universal index, metadata override layer, or compiled runtime plan.

Its role is to maximize the pool of free, inspectable Bedrock addons Hydraulic can study, classify, rank, and reuse as compatibility evidence.

### Source policy
- Treat GitHub as the first-class source for inspectable Bedrock addons, scripts, GameTest projects, Script API projects, and reusable implementation patterns.
- Treat CurseForge as a discovery and metadata source only unless a project links to an inspectable source repository with clear reuse terms.
- Do not treat "free to download" as permission to incorporate code or assets.
- Prefer evidence that can be inspected, versioned, diffed, and traced back to a stable repository or published source archive.

### Corpus contract

The corpus should normalize each addon into a typed record that can later feed compatibility analysis, reporting, ranking, and adapter planning.

Minimum shape:

```text
AddonCorpusEntry
  -> identity
  -> source
  -> license and admissibility
  -> versions
  -> behavior pack facts
  -> resource pack facts
  -> script and GameTest facts
  -> blocks, items, entities, recipes
  -> storage, machine, transfer, fluid, energy facts
  -> UI and networking facts
  -> dependencies
  -> confidence
  -> provenance
  -> evidence pointers
```

The corpus should capture what an addon appears to do, how that conclusion was derived, and whether the evidence is strong enough to inform planning.

### Storage and execution boundary

The corpus must remain separate from the runtime metadata patch layer.

Suggested layout:

```text
config/hydraulic/
  corpus/
    sources/
    curated/
    generated/
  cache/
    bedrock-addon-index/
```

- `config/hydraulic/corpus` is the stable, user-visible home for curated snapshots, source manifests, and generated knowledge artifacts.
- `config/hydraulic/cache/bedrock-addon-index` is the compiled lookup surface Hydraulic can rehydrate quickly at startup.
- External harvesting should publish versioned snapshots for Hydraulic to consume offline.
- Hydraulic startup must not depend on live crawling, remote availability, or background scraping.

### Hydraulic integration rules
- Keep corpus ingestion in a dedicated shared subsystem rather than forcing external addon data into the existing metadata schema.
- Treat corpus facts as advisory evidence by default.
- Let metadata remain the explicit override layer when server owners need to assert or correct behavior.
- Enrich reports and compatibility objects before letting corpus-backed facts influence compiled runtime plans.
- Never allow bridge factories or hot runtime dispatch paths to consult the raw corpus directly.
- Compile only stable, typed, validated corpus-backed decisions into the runtime dispatch table.

### Evidence extraction priorities
- Start with deterministic extraction from manifests, pack structure, Script API usage, GameTest usage, custom components, UI files, recipes, and declared content.
- Keep heuristic classification explicit, scored, and reversible.
- Rank common reusable patterns such as storage, machines, transfer, fluids, energy, automation, UI, and networking before investing in mod-specific adapters.

### Capability Research Matrix

| Capability | Research targets |
| --- | --- |
| `BLOCK_ENTITY_DATA` | Block runtime and persistence references; large open-source addons |
| `MACHINE_INVENTORY` | Block inventory APIs; industrial, machine, and item-transfer projects |
| `ITEM_TRANSFER` | Item pipes, storage transfer, inventory manipulation, and Java transfer APIs |
| `FLUID_RUNTIME` | Fluid systems, tanks, liquid machines, and bucket/block representations |
| `FLUID_TRANSFER` | Fluid handlers, pipes, tanks, fill/drain, and identity semantics |
| `ENERGY_TRANSFER` | Energy storage, power networks, generators, batteries, and transfer APIs |
| `MENU_CONTAINER` | Inventory APIs, UI frameworks, storage, and furniture addons |
| `AUTOMATION_ACCESS` | Machines, pipes, conveyors, routing, filtering, and interoperability frameworks |
| Generated packs | Regolith, Bedrock Examples, Bedrock Boost, and addon registries |
| Runtime components | Custom components, ADK-LIB, component registries, and Script API samples |
| Persistent state | Dynamic properties, database projects, and persistent block or machine data |
| Cross-addon communication | Bedrock-Core discovery, replicated state, typed RPC, UI, and network layers |

### Expanded Research Catalogue

The catalogue is a discovery backlog, not permission to copy or redistribute
third-party code or assets. Sources must be evaluated for version, license,
provenance, implementation pattern, and limitations before they influence
Hydraulic.

- **Official runtime and API:** Mojang Bedrock Samples, Microsoft Creator documentation, Microsoft custom-component samples, Script API documentation, TypeScript starters, and how-to or build-challenge samples. Use these for supported pack structure, scripting, components, events, persistence, and version constraints.
- **Block runtime and inventory:** block and item component documentation, inventory components, component registries, Scripting V2, and Bedrock block samples. Extract concrete inventory access, slot manipulation, block interaction, and block-entity limits.
- **Industrial and machine systems:** UtilityCraft, DoriosStudios projects, and public machine or processing-addon repositories. Extract machine identity, processing, upgrades, multiblocks, storage, automation, ports, and state synchronization.
- **Energy systems:** energy, power, RF/FE, generator, battery, cable, and network projects. Verify source, network, storage, transfer, consumption, rate limiting, and persistence rather than merely an energy bar.
- **Fluid systems:** fluid, tank, pipe, liquid-transfer, gas, and machine projects. Extract identity, capacity, fill/drain, sided access, container representation, machine interaction, and cross-fluid rejection.
- **Item transfer and logistics:** item-transfer, pipe, transport, logistics, conveyor, hopper, filter, routing, and automation-network projects. Test insertion, extraction, filtering, routing, stack handling, and full-inventory behavior.
- **Storage and persistence:** database, dynamic-property, world-storage, persistent block-data, and persistent machine-data projects. Extract durable keys, lifecycle, migration, bounded caching, synchronization, and corruption handling.
- **Component and interoperability frameworks:** ADK LIB, Microsoft custom components, Bedrock-Core, BDS documentation, and community API projects. Use them for component registration, discovery, replicated state, typed RPC, UI, networking, and cross-addon boundaries.
- **Build and generated-addon architecture:** Regolith, its filters and schemas, Bedrock Examples, Bedrock Boost, and addon registries. Study deterministic generation, schema validation, generated identifiers, packaging, and reproducible output.
- **Large open-source addons:** Remon Furniture, Medieval Furniture Remastered, OriginsPE, ADK, UtilityCraft, DoriosLib, DoriosCore, and other inspectable libraries. Use them as architecture and regression cases, never as assumed dependencies.
- **Java normalization references:** Forge Capabilities, Fabric Transfer API, Botarium, and equivalent inspectable abstractions for item, fluid, and energy semantics.

### Corpus Extraction Template

Every accepted source record must include:

```text
SOURCE
LICENSE AND ADMISSIBILITY
MINECRAFT VERSION
SCRIPT API VERSION
CAPABILITY
IMPLEMENTATION PATTERN
LIMITATIONS
PERFORMANCE CHARACTERISTICS
PERSISTENCE METHOD
RUNTIME HOOKS
TRANSFER SEMANTICS
UI METHOD
REUSABILITY
PHLODGATE ADAPTER CANDIDATE
EVIDENCE POINTERS
CONFIDENCE
```

The engineering question for every record is whether Bedrock has a viable
runtime seam for the Java capability and what strongest proven implementation
pattern can be used. Search results and download pages alone are not evidence
of support or reuse rights.

### Prohibited Shortcuts

- Do not copy or redistribute third-party code or assets without verified rights.
- Do not treat free downloads, CurseForge pages, or search results as reuse permission.
- Do not make raw corpus data a runtime dependency.
- Do not make behavior-pack execution the foundation of Java-server behavior.
- Do not advertise visual conversion as gameplay compatibility.
- Do not add mod-specific adapters before generic normalized capability contracts are executable.
- Do not hide missing critical behavior behind a weighted compatibility score.
- Do not make unsupported, simulated, or diagnostic-only behavior indistinguishable in reports.

## Current State Summary

### What is already implemented
- Pack orchestration still centers correctly in `PackManager`.
- A real `compat` foundation already exists under `shared/`.
- The offline corpus storage slice is now live: `AddonCorpusLoader` creates `sources/`, `generated/`, and `curated/`, scans normalized local JSON snapshots, validates them, applies deterministic `curated > generated > sources` precedence, persists the existing versioned index and manifest, and leaves a cached index untouched when no local snapshots exist. Remote harvesting remains intentionally outside Hydraulic startup.
- The offline corpus importer is now live: `CorpusSnapshotImporter` converts a local Bedrock addon directory or ZIP into a normalized generated entry by extracting manifests, pack structure, assets, recipes, functions, scripts, UI, custom-component evidence, GameTest usage, dependencies, and deterministic capability hints. It enforces bounded input size, rejects symlinks and unsafe archive paths, requires caller-provided source/license facts, and never executes or copies third-party source assets.
- Deterministic metadata loading and precedence already exist.
- The first universal-index seam is now live: `ModResourceIndex` indexes both asset and data inventory categories, and `CompatibilityManager` reuses that indexed data instead of doing its own second mod-root filesystem walk for content inventory generation.
- Pack identity now also uses the shared indexed view: full-tree `PackUtil.getModUUID()` hashing has been replaced by a persisted `ConversionKey` derived from indexed mod resources plus loaded metadata state, so metadata-only changes now invalidate stale cached packs.
- A first-class cache layout now exists under `config/hydraulic/cache` for index, compatibility, conversion, validation, and manifest artifacts. Compatibility inventory and report output can now be reused from cache when indexed mod fingerprints and metadata state are unchanged, while conversion and validation outputs are mirrored into the same artifact tree.
- Compatibility cache reuse is no longer keyed only by metadata plus indexed mod fingerprints. The cache key and persisted manifest now also carry a compatibility-engine fingerprint derived from the current compatibility manager, analyzer registry and analyzers, and capability adapter registry, so analyzer or adapter code changes invalidate stale compatibility artifacts without requiring manual cache deletion.
- Model lookup is no longer built from an eager all-model startup flattening pass. `ModResourceIndex` now records generic model file paths, and `IndexedModelProvider` lazily deserializes mod and vanilla models on demand through a bounded cache with negative caching for missing entries.
- Custom model conversion no longer walks the full parsed model asset set for a mod. The converter now iterates indexed model keys for the active mod and stitches those models through `IndexedModelProvider`, so the model stage no longer depends on `ResourcePack#models()` as an eager source of truth.
- The cached index snapshot now rehydrates live `ModResourceIndex` instances on repeat startup when mod roots, indexed files, and indexed directories are unchanged, so the index cache is now a real execution shortcut rather than metrics-only persistence.
- Texture output resolution for item, bow, and block conversion no longer recomputes identical Bedrock texture paths at every call site. A shared bounded `TextureResolutionCache` now sits behind `TexturePackModule` and records hit, miss, eviction, and size evidence in `performance-report.json`.
- Texture conversion now also builds a per-pack dependency graph from converted models and equipment assets before the texture stage runs. The converter records discovered, selected, and omitted texture counts per conversion batch, accumulates the selected texture set across multiple extraction passes for the same mod, and now loads file-backed textures directly from `ModResourceIndex` instead of depending on an eager parsed `ResourcePack#textures()` walk.
- Block texture registration no longer walks `ResourcePack#textures()` eagerly during post-processing or scans the full indexed texture map for the mod. Hydraulic now resolves only the selected texture keys back through `ModResourceIndex`, and block flipbook registration lazily reads only selected texture `.mcmeta` files when animation metadata is actually needed.
- Block material caching no longer requires a preprocessing pass over every parsed model in the pack. Hydraulic now stitches and persists material entries only on demand when block registration actually consumes a block-state model or metadata-selected material override.
- Block preprocessing no longer walks the full parsed blockstate asset set for a mod. `ModResourceIndex` now resolves blockstate file paths for the registered block IDs owned by that mod, and Hydraulic deserializes only those indexed blockstates that the runtime block registration path can actually consume.
- Item preprocessing no longer walks the full parsed item-definition asset set for a mod. Hydraulic now resolves indexed item asset paths per registered item, deserializes modern item definitions only when they exist, and falls back to lazy model-provider classification for legacy item-model assets.
- That indexed modern item-definition path now also treats unsupported third-party schemas as a degradation case instead of a preprocessing failure. On the validated Citadel Fabric runtime, unsupported `citadel:custom_item_model` definitions now downgrade to targeted `ItemPackModule` warnings plus legacy model fallback, with pack conversion and server startup still succeeding.
- Item and bow post-processing no longer resolve runtime texture-binding models through the parsed Java `ResourcePack`. Those post-process paths now resolve base item models, block fallback models, and bow override models through the shared indexed `ModelStitcher.Provider` as well, removing another remaining dependency on eager parsed-pack model lookup during conversion.
- Item texture emission now also reuses per-item resolved texture bindings computed during preprocess, so the post-process texture stage no longer needs to restitch the same item models and block fallback models just to recover the same output texture keys.
- Pack invalidation is no longer limited to a mod's own indexed fingerprint plus metadata. `ModResourceIndex` now records external namespaces referenced by model and equipment assets, and `ConversionKey` now folds the transitive fingerprints of dependent indexed mods into the persisted cache identity.
- Typed compatibility data already exists:
  - `CompatibilityObject`
  - `CapabilityProfile`
  - `SupportResult`
  - `CompatibilityFinding`
  - `Confidence`
  - `Provenance`
  - `ModFingerprint`
- Analyzer-backed compatibility reporting already exists for blocks, items, recipes, entities, menus, fluids, and block entities.
- Compatibility analysis no longer linearly scans the analyzer set per content descriptor. Hydraulic now routes the current block, item, entity, fluid, block-entity, menu, and recipe analyzers through an explicit kind-keyed `AnalyzerRegistry`.
- `MappingResolver` already carries compact resolved block metadata for current block runtime consumers.
- `MetadataIndex` already precompiles compact menu and block-entity patch templates.
- Hydraulic already emits `content-inventory.json`, `compatibility-report.json`, `performance-report.json`, and `pack-validation-report.json`.
- Eager pack preparation already happens during Hydraulic startup, so conversion evidence survives later Geyser bootstrap or HTTPS failures.
- Pack module discovery now precedes resource-pack parsing. Hydraulic registers all `PackModule` services before deciding whether parsed `ResourcePack` objects are needed, and skips the `MinecraftResourcePackReader`/`NioDirectoryFileTreeReader` pass unless a module explicitly overrides `requiresParsedPacks()`. The active `BlockPackModule` and `ItemPackModule` preprocessors consume only `ModResourceIndex` and the lazy model provider, so the current production path no longer materializes duplicate parsed packs before conversion. Preprocessing still receives an empty collection when an individual mod root cannot be read, preserving graceful degradation. Future modules that genuinely consume parsed pack objects must opt in explicitly.
- The unreachable embedded `PackCdnServer` was removed from production startup. It listened on a fixed port (`8088`) but had no URL producer, Geyser registration path, or production call site; Geyser currently receives local pack paths through `GeyserDefineResourcePacksEvent`. Removing it eliminates an unconditional server-thread startup side effect, a recurring port-collision failure, and an unconsumed duplicate delivery architecture. Remote/CDN hosting remains a separate feature and must return only with a complete URL/configuration/registration contract.
- Post-generation pack validation findings are now merged into `compatibility-report.json` as a `packValidation` section after pack preparation, so manual actions and validation issues are visible next to compatibility findings instead of only in the sibling `pack-validation-report.json` artifact.
- Current runtime consumers already use compatibility decisions for block registration, item registration and exposure, armor and bow attachables, metadata-backed custom entity registration when metadata explicitly opts into a visual-only entity downgrade, and metadata-backed entity interaction prompts.
- Current runtime bridges already include explicit metadata-backed menu fallback, block-entity patch translation, entity interaction prompt translation, and a first fluid-adjacent bucket icon fallback at real Geyser seams.
- The menu fallback bridge no longer reparses validated fallback container names on the live unsupported-open-screen path; the compiled runtime plan now carries a typed `ContainerType` directly.
- Bedrock-backed menu actions now enter through translated Java container packets after Minecraft's server-thread scheduling barrier. Hydraulic validates live menu ownership, state IDs, slot bounds, changed-slot claims, and button encodings, then lets vanilla own mutation, records exact component-aware before/after snapshots as a traceable transaction, and requests `AbstractContainerMenu.broadcastFullState()` for canonical rejection/completion synchronization. Explicit `menu.button.<id>=button|toggle` facts classify custom button semantics; the bundled menu-machine fixture provides a persistent toggle target. Focused Java tests pass, and a live Fabric run reached Minecraft `Done` plus Geyser readiness without menu-mixin application failure; physical Bedrock invocation and observation remain open.
- Menu fallback metadata validation and compilation now also resolve against the live protocol `ContainerType` enum instead of a brittle hardcoded name list, which fixes the real `crafter_3x3` fallback case and broadens the existing generic menu seam to the current protocol container set.
- The block-entity patch bridge is no longer limited to constant tag synthesis; compiled patch templates can now copy selected values from the live Java block-entity NBT into Bedrock output through explicit `$java.<path>` patch values, including list-backed source paths and list-backed Bedrock destination paths addressed through numeric segments.
- Those live menu and block-entity bridge factories now also require explicit compiled adapter bindings, so runtime instantiation follows the same capability-driven adapter selection surfaced in `compatibility-report.json`.
- Those factories now also require the exact typed bridge kinds they implement, not just any same-domain runtime bridge. Menu fallback translation binds only when the compiled plan declares `MENU_CONTAINER`, and block-entity patch translation binds only when the compiled plan declares `BLOCK_ENTITY_DATA`.
- Entity analysis and runtime dispatch now also compile an explicit metadata-backed interaction prompt into entity plans, bind a dedicated `ENTITY_INTERACTION_PROMPT` adapter, and override Bedrock hover text at Geyser's `BedrockInteractTranslator` seam while reusing the existing downstream Java interact packet path.
- Live runtime validation now also shows that explicit entity prompt slice in the generated compatibility artifact: the bundled `hydraulic_test_mod:barrel_cube` fixture records `interaction_prompt = Open Barrel Cube`, binds `entity.interaction_prompt`, and drops `entity_interaction_bridge` while still surfacing `entity_behavior_bridge` as the remaining unsupported entity seam.
- EME-compatible presentation profile evidence is now indexed and validated during entity post-processing. `EntityPresentationProfileScanner` recognizes server/render profile pairs, validates bounded JSON, checks referenced model/texture files against the authoritative index, and writes per-mod `entity-presentation` reports. This remains presentation evidence; it does not promote entity behavior or physical-client support.
- Deeper entity action routing now has a typed `EntityInteractionActionPlan` compiled from metadata facts. `BedrockEntityActionRouter` validates target identity, interaction range, optional held-item requirements, and the `use`, `attack`, `mount`, or `dismount` action before scheduling the authoritative Java mutation on the server thread. The Geyser interaction mixin cancels only successfully recognized compiled contracts; unsupported or malformed actions preserve the normal translator path.
- Focused entity action tests, the full `:shared:test` task, and touched-file diagnostics pass under Java 25. A Fabric runtime smoke attempt did not reach Minecraft because a fresh redirected Gradle cache spent its bounded window configuring/downloading; a subsequent combined build hit the known Windows Loom `:shared:jar` manifest-modification blocker. No Mixin application or physical Bedrock observation is claimed from that attempt.
- Entity AI and custom-network evidence now compiles through `EntityBehaviorContract` and `EntityNetworkContract`. The analyzer recognizes a bounded reusable AI vocabulary and validates explicit network direction/channel facts, but marks both as non-executable unless a concrete bridge exists; custom-network findings remain `UNSUPPORTED` rather than being translated heuristically.
- Item creative exposure now also treats the compiled `ITEM_BEHAVIOR` bridge kind as authoritative, so wearable and bow presentation adapters no longer keep behavior-required items in Bedrock creative inventory just because their visual bridge is available.
- Unsupported menu-open and block-entity fallback diagnostics now also consume precompiled runtime-dispatch candidate indexes instead of rescanning the full compatibility report during live warnings.
- Those menu and block-entity diagnostic candidate paths now also resolve through typed `RuntimeBridgeKind` group queries inside `RuntimeDispatchTable` instead of depending on separate legacy special-case bridge lists.
- Unsupported menu-open handling now also preserves the resolved live Java menu identifier across the mixin, fallback, and warning paths, so the runtime warning can bind directly to the matched compiled menu plan and its explicit menu bridge requirements instead of only reporting a container type plus global candidates.
- State-aware block identifier grouping and per-state runtime metadata now also compile into the runtime dispatch table, so block registration and block-item placement no longer need to re-derive those mappings through `MappingResolver` when compiled entries already exist.
- The remaining block-item texture fallback decision path now also consumes compiled block compatibility plans instead of looking raw objects back up from the compatibility report during conversion.
- Runtime requirements are no longer compiled only as opaque strings. `RuntimeDispatchTable` now also indexes typed `RuntimeBridgeKind` categories for the currently known bridge requirements, so current runtime consumers and future bridge factories can bind directly to structured categories such as menu, block-entity, item, entity, block, and fluid bridge kinds.
- Transfer and machine bridge creation now also flows through typed `RuntimeDispatchTable` entry points instead of requiring callers to reconstruct plans or invoke factories ad hoc. Runtime-backed item, fluid, and energy bridges fail closed unless every direction declared by the compiled capability facts has a concrete executable operation, preventing metadata from advertising silent no-op behavior. Item, fluid, and energy transaction creation is also exposed through the runtime dispatch table.
- Critical machine capability policy is now enforced during compatibility compilation: machine and transfer patch facts are recognized as behavior-required, processing or inventory failures are serialized as `critical_failure` evidence, and affected objects and mod profiles become `VISUAL_ONLY` with score `0` instead of advertising high presentation-only compatibility.
- A generic `MachineProcessingBridge` now executes compiled machine plans through the normalized item-transfer bridge: it matches declared input/output stacks, tracks bounded progress, checks simulated output capacity before mutation, consumes input, and emits output on completion. Creation is capability-gated on machine behavior, machine inventory, and executable item transfer support.
- Stateful processing bridge instances are now retained by their live processing and mixed-resource fixture block entities, rather than reconstructed every server tick. Compiled-plan changes rebuild the retained bridge, while unchanged plans preserve recipe progress across ticks. Tick-driven dirty-state delivery still requires an explicitly identified compatible Bedrock container/session; broadcasting inventory packets to all connected sessions is intentionally prohibited because it could overwrite unrelated player inventories.
- A unified `MultiResourceTransaction` now coordinates item, fluid, and energy operations behind one simulation boundary before any runtime mutation, commits validated operations into one merged `StateChangeSet`, records dirty state once, and compensates committed operations in reverse order if a later commit fails. Focused regression coverage proves a synthetic mixed-resource machine shape with two item inputs, one fluid input, one energy input, two item outputs, and one fluid output succeeds atomically, while missing item, missing fluid, insufficient energy, and blocked output cases reject without mutating other resources or emitting dirty changes.
- A `MixedResourceMachineProcessingBridge` now executes generic machine processing recipes with item inputs, fluid inputs, energy inputs, item outputs, fluid outputs, and optional energy output through `MultiResourceTransaction`. The factory refuses mixed processing unless the compiled plan has machine behavior and inventory support and every resource required by the recipe has an executable bridge. Mixed-resource recipes can now be compiled directly from `CompiledCompatibilityPlan.inventoryFacts()` using indexed item/fluid/energy recipe facts, with malformed facts failing closed. Focused tests verify metadata fact preservation, compiled-fact bridge construction, mixed-resource processing, dirty-state synchronization, and missing resource bridge rejection.
- Resource automation now has a request-oriented execution path for item, fluid, and energy movement. `ResourceAutomationAccess` accepts `TransferRequest`, `FluidTransferRequest`, and `EnergyTransferRequest` objects, executes them through the shared transaction substrate with optional dirty-state recording, preserves sided access facts and filtering evidence, and binds through `RuntimeDispatchTable` without callers reconstructing bridge factories.
- Runtime target discovery now has a shared resolver that maps a world position key to a runtime target, resolves executable item/fluid/energy automation capabilities through the compiled runtime dispatch table, builds typed transfer requests, and executes them through the transaction and dirty-state path. A Minecraft-backed target source resolves `ServerLevel` + position into a live block entity target and lets compiled bridge factories determine which runtime capabilities are executable. A Geyser-session target source resolves the current Java player level for a Bedrock-backed session and feeds the same Minecraft source. Focused tests verify position-based item, fluid, and energy transfers, synchronization after a discovered item transfer, missing-target rejection, and missing-capability rejection. The first production mutation contract is now live: a server-authoritative block interaction can execute an explicit compiled `interaction.block_use.action = insert_held_item` contract through this resolver, atomically insert the bounded held-item count, and consume the player's hand only after a full target commit (while preserving creative-mode infinite materials). Live Bedrock-client observation of that action remains an integration gate.
- Runtime traceability now carries `RuntimeTraceId` through target resolution, transfer results, `StateChangeSet`, `SyncBatch`, encoded sync changes, and transport delivery results. Focused tests verify one traced runtime action can be followed from position-based target discovery through item transfer, dirty-state recording, sync planning, sync encoding, and transport delivery status, including rejected missing-target paths.
- Bedrock block-use inventory transactions route through a non-cancelling Geyser translator mixin into the runtime action router for traced target recognition. Mutation ownership remains on the Minecraft server thread in the target block's real interaction path, avoiding unsafe world access from the network translator and preventing duplicate vanilla/Hydraulic mutations. The first explicit action contract supports bounded held-item insertion into a declared slot and fails closed for absent/malformed metadata, missing capability, insufficient held count, blocked output, or partial transfer. Focused tests verify routing, exact-count commit, target rejection, insufficient held state, and no player-side consumption before commit.
- The explicit compiled block-use action now also supports a bounded shift-click extraction counterpart. Metadata can declare `interaction.block_use.extract_slot`, `extract_item`, `extract_count`, and `extract_side`; `BehaviorFactExtractor` preserves those keys, `BlockUseActionPlan` validates the item identifier and count fail-closed, and `BedrockRuntimeActionRouter` executes extraction only while the player is sneaking. The source transaction commits through the existing item-transfer bridge before the extracted stack is delivered to the player inventory or dropped into the world; recipient preflight prevents a failed delivery from removing source state. Focused coverage proves fact propagation, default count handling, malformed identifiers, extraction success, rejection, preflight failure, and insertion fallback when no extraction contract exists. This is a generic, metadata-driven action primitive for any compiled block plan, not automatic discovery of third-party machine semantics.
- **Zero-trust audit correction (2026-09-12) and immediate fix**: the packet-interception path (`BedrockInventoryTransactionTranslatorMixin` -> `BedrockRuntimeActionRouter.route(session, packet, registry)`) previously only performed target discovery and a debug log; it never executed the compiled `insert_held_item` action, and neither that path nor `executeBlockUse` ever forwarded transaction dirty-state into the sync/transport pipeline (both called `discovery.transferItem(...)` with a `null` `DirtyStateTracker`). `BedrockRuntimeActionRouter` now executes the compiled block-use action generically for any block resolved through `RuntimeTargetDiscovery` (not only the hand-wired test fixture), and maintains a per-`GeyserSession` `DirtyStateTracker`/`SyncDispatcher`/`GeyserSyncTransport` triple so a committed mutation is immediately drained and delivered back to the originating Bedrock client through the existing `SyncPlanner`/`SyncEncoder`/`GeyserSyncTransport` pipeline. `executeBlockUse` (used by non-Bedrock-packet call sites such as `ProcessingMachineBlock`) now also resolves the acting player's `GeyserConnection` via `GeyserApi.api().connectionByUuid(...)` and synchronizes through the same per-session pipeline when the player is Bedrock-backed. This closes the packet-path mutation gap and the previously-orphaned `GeyserSyncTransport`/`SyncDispatcher`/`SyncPlanner` wiring for every block that declares a compiled `interaction.block_use.action`, generalizing beyond the single fixture block. Verified: `:shared:test` (full suite) and `:shared:compileJava`/`:fabric:compileJava` succeed, and a live `:fabric:runServer` run applies the modified mixin without a Mixin transform failure. **Still not solved by this fix**: there is still no generic mixin/interception point for arbitrary third-party mod block classes that never call into Hydraulic's runtime router at all (a mod's own `useItemOn` never reaches `BedrockRuntimeActionRouter` unless the object's compiled plan is reached through the Bedrock packet-interception path above, or the block explicitly calls `executeBlockUse`). Live Bedrock-client observation of the delivered `InventorySlotPacket` remains an unverified manual integration gate.
- **Universal generic block-use interception shipped (2026-09-12), closing the "still not solved" gap above.** A new `ServerPlayerGameModeMixin` injects at `HEAD` of vanilla `ServerPlayerGameMode.useItemOn(ServerPlayer, Level, ItemStack, InteractionHand, BlockHitResult)` -- the single, non-overridable, per-mod-agnostic entry point every server-authoritative block-use interaction flows through, for every mod's block class, before any block-specific Java code runs. The injection calls `BedrockRuntimeActionRouter.executeBlockUse(...)` for the interacted block position and cancels with `InteractionResult.SUCCESS_SERVER` only when the compiled `interaction.block_use.action` actually mutates state (`Status.MUTATED`); every other status (no compiled plan, no capability, rejected mutation) falls through untouched to vanilla/mod behavior, so third-party machines with their own real Java interaction logic are never double-fired or blocked. This means any block identifier Hydraulic has compiled a `block_use` action for -- including a completely unmodified, un-mixed-into `create:mechanical_press` or `mekanism:*` block -- now receives that action automatically, with **zero changes to that mod's source or bytecode required**. Wiring is now driven entirely by Hydraulic metadata/compiled facts, not by mod cooperation; this is the correct meaning of "generic capability adapter before mod-specific adapter" applied to the block-use action specifically. The handler is wrapped in a blanket `try/catch (Throwable)` with a warn-and-fall-through log, because this seam now runs on every block interaction server-wide and a single malformed compiled plan must never break vanilla or third-party block interaction. Verified: `:shared:compileJava`/`:fabric:compileJava` succeed, `:shared:test` full suite green, and two live `:fabric:runServer` runs confirm the mixin applies against the real remapped `ServerPlayerGameMode` class with no `MixinTransformerError`/apply failure (the server reached `Done (5.974s)! Run /geyser help for help!` and `Started Geyser on UDP port 19132`). **What this does NOT solve, and could not honestly be claimed to solve in one pass**: this only makes the *held-item-insertion* block-use action universally wireable. It does not discover or invent new capability facts for Create, Mekanism, Thermal, or any other unreviewed mod -- a server owner or Hydraulic maintainer still has to author the `interaction.block_use.action`/slot/count metadata (or a future automatic-discovery pass per Phase 5B/6 has to derive it) before a specific third-party machine block does anything. Fluid/energy/mixed-resource ticking paths (`MachineProcessingBridge.tick`, `MultiResourceTransaction`) still have no equivalent generic interception point and still do not auto-flush to a session. Live Bedrock-client packet observation remains an unverified manual integration gate; "universal arbitrary-mod support" in the sense of automatic semantic discovery of unknown mod machine behavior remains Phase 5B/6/7 work, not something this change claims to deliver.

- Synchronization planning, encoding, dispatch, and the first Geyser transport handoff now consume transaction-generated dirty state into Bedrock-facing encoded records. Inventory-slot updates are delivered through `GeyserSession.sendUpstreamPacket(...)` as `InventorySlotPacket`s, and `container.property.N` updates are delivered as `ContainerSetDataPacket`s for concrete menu/property state. Generic state updates still report `UNSUPPORTED` until a concrete packet/state mapping exists. Focused tests verify the Geyser session handoff boundary with an injected packet sender; live Bedrock-client application remains a runtime regression requirement. As of 2026-09-12, `GeyserSyncTransport`/`SyncDispatcher`/`SyncPlanner` are also reachable from production code (via `BedrockRuntimeActionRouter`'s per-session sync pipeline described above), closing the prior orphaned-subsystem finding for the block-use insertion action specifically; other mutation paths (ticking machine processing, mixed-resource transactions) still do not auto-flush to a session and remain future wiring work.
- A normalized `FluidContainerBridge` now transfers mutable container state into and out of executable fluid tank bridges through the compiled runtime path, with simulation support, capacity validation, and direct dispatch exposure. This advances fluid execution beyond bucket texture presentation without assuming a particular mod fluid API.
- Executable inventory access now exposes real runtime slot counts and item state through the typed machine bridge, while automation access delegates sided insertion and extraction through the same validated item-transfer bridge and preserves compiled filtering facts.
- Fluid container transfers now verify the live tank fluid identity before mutating container state, preventing cross-fluid insertion or extraction when a runtime adapter is permissive.
- Fluid compatibility objects now also have a first-class compiled runtime lookup and runtime-dispatch metrics bucket, so future fluid runtime consumers can bind to `dispatchTable().fluid(...)` and inspect fluid lookup hit/miss evidence through `performance-report.json` instead of falling back to the generic string-typed plan path.
- Fluid compatibility plans now also carry a compiled `fluidRuntimeRequirements` subset plus `requiresFluidRuntime`, matching the structured runtime bridge projection already used for menus and block entities. Focused shared tests now verify that fluid bridge intent can be consumed from typed runtime kinds without reopening the raw analyzer requirement list.
- Fluid analysis and runtime dispatch now also compile an explicit metadata-backed bucket icon bridge into fluid and bucket item plans, bind a dedicated `FLUID_BUCKET_TEXTURE_FALLBACK` adapter, and let `ItemPackModule` reuse that compiled fluid plan when a custom bucket item has no direct model asset.
- Live runtime validation now also shows that fluid slice in the generated compatibility artifact: the bundled `hydraulic_test_mod:barrel_fluid` fixture records `bucket_item = hydraulic_test_mod:barrel_bucket`, `bucket_texture = hydraulic_test_mod:barrel_pack`, binds `fluid.bucket_texture_fallback`, and drops `fluid_translator` while still surfacing `fluid_runtime_bridge` as the remaining unsupported fluid seam. The paired `hydraulic_test_mod:barrel_bucket` item now records `fluid_source = hydraulic_test_mod:barrel_fluid` and reports the explicit bucket fallback instead of a generic missing-asset warning.
- Runtime dispatch now also suppresses Bedrock creative exposure for bucket items whose linked source fluid still requires a fluid runtime bridge, even when presentation can fall back to a metadata-backed bucket icon. That keeps the current fluid bridge slice from advertising creative-safe usage before deeper fluid runtime support exists.
- Post-generation pack validation now also consumes the texture dependency graph's concrete selected texture set, so `pack-validation-report.json` can structurally fail missing generated texture outputs and warn on leftover unreferenced texture files instead of only validating archive shape.
- Conversion invalidation now also preserves explicit indexed model and equipment dependency edges and hashes only the concrete referenced model and texture file stamps they traverse, so unrelated asset churn in a dependent namespace no longer invalidates another mod's cached pack output.

### What is materially better than earlier assessments
- Runtime semantic discovery is no longer limited to API-presence evidence: a bounded public-contract classifier now derives inventory, fluid, energy, processing/ticking, and menu facts from live runtime object types without invoking arbitrary methods.
- Automatic local recipe discovery now scans mod/data recipe roots without Hydraulic metadata, reuses the existing generic and specialized serializers, preserves catalyst/byproduct/condition evidence, and reports malformed or unsupported schemas instead of silently ignoring them.
- Machine profiles now normalize multiple indexed recipes, fluid inputs/outputs, energy values, and malformed numeric facts with fail-closed defaults.
- A machine synchronization coordinator now connects authoritative machine ticks to dirty-state coalescing, sync encoding, and configured transport delivery for callers that bind real machine block entities to the coordinator.
- The fork is no longer only a block metadata experiment.
- Compatibility reporting is already a real regression surface.
- Runtime bridge seams now exist in production code, even if they remain narrow.
- Validation artifacts now exist after pack generation.
- Performance artifacts now include real cache evidence for some hot paths.
- Live Fabric runtime validation now shows the model-provider startup slice is effectively reduced to indexed setup cost rather than eager model deserialization; the current run recorded `modelIndexBuildMillis = 6` while pack conversion and Geyser registration still completed.
- The runtime artifact now also records lazy model-provider hit, miss, eviction, size, and indexed-model counts, so bounded on-demand model behavior is measurable instead of inferred.
- Live Fabric runtime validation now also confirms the remaining parsed-model conversion seam is gone: custom model extraction still completes through indexed model keys only, the current run recorded `modelIndexBuildMillis = 2` with `modelProviderCache.hits = 174`, `misses = 26`, and `indexedModels = 18`, and both generated packs remained `valid = true` in `pack-validation-report.json`.
- Live Fabric runtime validation now also shows real index rehydration on unchanged startup; the current dev run reported `artifactCache.index.hits = 2` and `misses = 2`, which matches partial reuse for filesystem-backed mod roots while dev-time virtual roots safely fall back to rebuild.
- Live Fabric runtime validation now also shows shared texture-resolution cache reuse during conversion; the current dev run reported `textureResolutionCache.hits = 19`, `misses = 18`, and `evictions = 0`, proving repeated item, bow, and block texture-output resolution is now observable and already benefits from central reuse.
- Live Fabric runtime validation now also shows the texture dependency graph is active in the conversion path. The current bundled test mod reported `discoveredTextures = 17`, `selectedTextures = 17`, and `omittedTextures = 0`, which means the graph is wired correctly even though this small fixture pack currently references every discovered texture.
- Focused extractor coverage now also proves the texture stage no longer depends on parsed-pack texture enumeration: `SelectiveTextureExtractor` can ignore `ResourcePack`-only textures and still emit the indexed selected texture set directly from on-disk `ModResourceIndex` paths.
- Live Fabric runtime validation now also confirms the structural texture-coverage check matches the current fixture pack: after conversion, both `hydraulic` and `hydraulic_test_mod` were `valid = true` in `pack-validation-report.json`, with no missing selected textures and no leftover unreferenced texture files.
- Live Fabric runtime validation now also confirms that the next broader indexed texture consumer is real rather than theoretical: a full `:fabric:runServer` startup still converts packs, registers content, and emits stable reports after switching selective texture extraction to indexed file-backed paths, with the current fixture mod still reporting `selectedTextures = 17` and the runtime artifact now showing `textureResolutionCache.hits = 19`.
- Focused `TextureDependencyGraphTest` coverage now also proves the selected-texture set is retained across multiple extraction passes for the same mod, closing a real multi-root correctness gap that would otherwise drop earlier root selections before validation and block post-processing.
- Live Fabric runtime validation now also confirms the next block-pipeline reduction is live: custom block registration still completes after removing the eager all-model material pass, with material storage now populated only when a block-state model or explicit metadata override is actually used.
- Live Fabric runtime validation now also confirms the next indexed block-pipeline step is live: pack conversion and custom block registration still complete after replacing the eager parsed-blockstate asset walk with indexed per-block blockstate loading, and both generated packs remain `valid = true` in `pack-validation-report.json`.
- Live Fabric runtime validation now also confirms the adjacent item-pipeline reduction is live: pack conversion still completes, `hydraulic_test_mod` still registers 16 custom items, and the compatibility-backed block-item fallback path still triggers after replacing the eager parsed item-definition walk with indexed per-item loading.
- Live Fabric runtime validation now also confirms the remaining item and bow post-process model lookups can ride the shared indexed model provider: a fresh `:fabric:runServer` still converted both packs, generated the bow attachable for `hydraulic_test_mod:barrel_bow`, registered 16 custom items, kept both packs `valid = true`, and recorded `modelProviderCache.hits = 194` with `misses = 26` in `performance-report.json`.
- Live Fabric runtime validation now also confirms the adjacent item texture-binding reuse slice is live: with the Citadel fixture still present, `:fabric:runServer` converted all three packs, preserved the warning-only unsupported-schema fallback, kept the compatibility-backed `golden_barrel` block-item fallback path, and still registered 21 custom items through Geyser after moving repeated item texture binding work into preprocess.
- Live Fabric runtime validation with `citadelfabric-26.2-1.2.0.jar` now also confirms unsupported indexed modern item schemas degrade cleanly: the old preprocessor exception path is gone, false deserialize error logs are suppressed, three Citadel item definitions fall back through warning-only handling, and conversion plus Geyser startup still complete.
- Live Fabric runtime validation now also confirms the dependency-aware invalidation slice is live on the real storage path: Hydraulic rewrote per-mod `conversion-key.json` files with `HYDRAULIC_CONVERSION_KEY_V3`, persisted `dependencyFingerprint` and `dependentModCount`, and safely forced reconversion after the cache identity expanded to explicit resource-edge hashing.
- The current visual-only entity downgrade validation path is now repository-backed instead of depending on a hand-maintained ignored dev file: the bundled test mod seeds `hydraulic_test_mod.golden_barrel.json` into Hydraulic's config metadata directory before `SERVER_STARTING`, and a fresh runtime boot after deleting the existing dev copy still restored the metadata-backed `barrel_cube` entity override and `barrel_pack` item behavior gating from tracked resources.
- Focused shared regression coverage now also proves the compatibility cache identity expands with the compatibility-engine fingerprint and that persisted compatibility manifests retain that fingerprint across store/load, closing the stale-report path that previously required manual cache clearing after analyzer changes.
- Focused shared runtime-dispatch coverage now also proves that bucket-item creative exposure follows the linked fluid plan instead of only the bucket item's local presentation state, closing a real false-positive exposure path for partially supported fluids.
- Fresh Java 25 validation after the transaction, synchronization, mixed-machine, automation, and compiled mixed-recipe slices now also confirms the full Gradle `build` succeeds and `:fabric:runServer` reaches Geyser ready state on UDP `19132`. The run converted eight packs with `failedPacks = 0`, registered 857 custom blocks, 989 custom items, and 1 custom entity, and `pack-validation-report.json` marked `create`, `travelersbackpack`, `lootr`, `apollib`, `citadel`, `farmersdelight`, `hydraulic`, and `hydraulic_test_mod` as valid. Create still emits fourteen `pack.path.long` warnings and one manual action for long Bedrock pack paths.
- Architecture and focused execution slices exist across the named Phase 1-10 systems below. Their
  presence is not 100% implementation coverage, universal live binding, or physical-client proof:
  - Phase 1: `CapabilityIR`, `RuntimeCapabilityDiscoveryEngine`, `DynamicCapabilityBinder`, `CapabilityCompletenessEvaluator`.
  - Phase 2: `UniversalAutomationEngine` (Sided filters, prioritized multi-node routes, rate limits).
  - Phase 3: `UniversalMachineRuntime` (Machine state machines, dynamic recipe matching, dirty-state progress/state tracking), `DatapackRecipeCompiler` for deep mod/datapack JSON recipe ingestion with catalyst/byproduct support, `SpecializedRecipeSerializerRegistry` for complex kinetic assemblies (Create Sequenced Assembly, Mekanism Infusion, Thermal Smelter, Farmer's Delight Cooking), and `DynamicDatapackIngestionHook` invoked on `ServerLifecycleEvents.SERVER_STARTED` to automatically compile active World datapack recipes.
  - Phase 4: `UniversalFluidRuntime` (Multi-tank manager, whitelist/capacity validation, world fluid visual approximations).
  - Phase 5: `UniversalEnergyRuntime` (Normalized `EnergyStorageUnit`, power network distribution reports).
  - Phase 6: `UniversalMenuIR` (`MenuIRCompiler`, slot roles, synced data widgets, furnace/storage layouts), Bedrock companion pack inspection form gauges with pagination, item search, and in-world holographic overlay displays (`MachineInspector`, `HolographicOverlay`), and `control_room_remote` in-game item-use-on trigger for instant live machine inspection with real-time property/gauge updates.
  - Phase 7: `UniversalEntityRuntime` (`EntityStateIR`, prompt resolution, Bedrock-to-Java interaction mapping).
  - Phase 8: `UniversalNetworkSyncBridge` (`StateBatchCoalescer`, bidirectional action dispatching), and `MultiClientSyncStressTest` verifying concurrent multi-session packet dispatch.
  - Phase 9: `AutomatedModFingerprinter` (Archetype classification, adapter opportunity auto-ranking), `ExtendedCorpusHarvestingPipeline` for automated open-source Bedrock addon schema ingestion, and `TransitiveDependencyProfileBenchmark` verifying sub-millisecond cache invalidation across 250+ mod random topologies with cycle-safety.
  - Phase 10: `MultiLevelValidationHarness` (Multi-level validation runner, modpack corpus compliance reporting), CI matrix integration in `.github/workflows/build-matrix.yml` and `.github/workflows/pullrequest.yml`, multi-platform physical client observation attestation matrix tooling (`record-client-attestation.ps1`, `publish-attestation-matrix.ps1`), and BDS multi-platform protocol compatibility validation (`test-bds-compatibility.ps1`, `BdsProtocolCompatibilityTest`).
- NetherNet wire-format support: `NetherNetDiscovery`, `NetherNetSignalingMessage`, and `NetherNetFrameCodec` provide bounded, tested current-wire protocol primitives without changing Geyser's active transport path. This is protocol evidence and reusable infrastructure, not a connectivity claim.

### What is still too narrow
- The current validation shell uses Java 25 successfully. Release readiness remains blocked by
  incomplete universal contracts, persistence/restart evidence, pack remediation, and physical
  Bedrock-client validation rather than by the active Java version.
- Discovery facts are not yet universally wired from every arbitrary third-party block entity lifecycle into `CompiledCompatibilityPlan` construction. The new classifier is a reusable evidence source, not proof that an unfamiliar mod has been fully understood.
- Recipe discovery still depends on schemas that expose enough JSON or runtime registry information for the existing serializers; arbitrary hardcoded recipe managers and opaque custom conditions remain explicit unsupported/ambiguous cases.
- The corpus schema, local importer, loader, admissibility checks, matcher, report writer, and compatibility evidence seam are implemented. Startup seeds 15 reviewed Bedrock corpus records into `config/hydraulic/corpus/curated/builtin` (12 admissible) and two admissible Java capability references into `config/hydraulic/corpus/java/curated/builtin`; the bundled records remain offline evidence and do not become runtime bridge inputs. Live remote harvesting, CurseForge API ingestion, and human review of additional records remain intentionally external/offline inputs rather than startup behavior. Server-owned records belong outside the overwritten `builtin` directories.
- Discovery is still duplicated across multiple subsystems.
- Fingerprinting and cache invalidation now carry resource-kind-aware boundaries inside a mod, while cross-mod conversion invalidation remains dependency-aware rather than per-mod-only.
- Resource-pack reading and broader resource resolution are still too eager even though startup model lookup, custom model conversion, item and bow post-processing model resolution, and selective texture extraction now use indexed mod paths.
- Resource-pack parsing is now consumer-gated by the loaded module set, but the active block/item preprocessing modules still require parsed packs, and conversion still rereads mod roots through `PackConverter`. The remaining optimization target is a safe parsed-resource handoff or reusable conversion input representation; no cache was added here because the converter's root reads and mod/resource lifecycle need a validated ownership boundary first.
- Texture-path reuse is now centralized and measured, texture conversion now consults a real dependency graph and load indexed file-backed textures directly for the active mod, block-texture post-processing now uses indexed texture paths plus lazy animation metadata reads, block material persistence is now demand-driven, block preprocessing now loads indexed blockstates only for relevant registered blocks, item preprocessing now loads indexed item assets only for relevant registered items, and conversion invalidation now follows indexed cross-mod dependencies. The remaining gap is that the current fixture packs still reference every discovered texture, and other resource categories still have eager seams.
- Runtime dispatch is now identifier-driven for the shipped bridge seams, unsupported diagnostics, and the first block-state registration paths, but transfer-heavy paths and deeper behavior surfaces still have too much flexible runtime reasoning.
- Runtime dispatch now also compiles the current menu fallback seam into a typed container enum instead of keeping that bridge input as a late-parsed string, but transfer-heavy paths and deeper behavior surfaces still have too much flexible runtime reasoning.
- Runtime bridge requirements now also compile into typed categories instead of only freeform requirement strings, and item/fluid/energy transactions plus mixed-resource coordination are executable through shared runtime infrastructure. Those categories still need broader use in rich menus, block-entity behavior, entity behavior, and rendering.
- Runtime diagnostics now consume typed bridge-group queries for menu and block-entity candidate selection, fluid presentation has a first real bucket-item consumer, generic machine processing has an executable item-transfer-backed bridge, mixed-resource machine processing can execute through one atomic transaction substrate from compiled compatibility facts, inventory and sided automation access are executable through direct dispatch, request-oriented item/fluid/energy automation runs through shared transactions, runtime target discovery can resolve a position into typed transfer execution using live Minecraft block entity and Geyser-session sources, Bedrock block-use and menu actions route into authoritative traced Java mutation, and normalized container-to-tank transfer mutates real fluid state with identity enforcement. Broader machine-to-recipe association, fluid block/state translation, mod-specific machine semantics, non-menu fluid/energy/entity actions, and live Bedrock sync verification remain outside those generic contracts.
- Block-entity runtime translation is now more useful for metadata-backed data bridges because compiled templates can carry live Java tag values through to Bedrock output, but the seam is still patch-driven and does not yet cover interaction or behavior.
- Compatibility analysis now has explicit kind-keyed analyzer dispatch, but it still reconstructs facts too often and still depends on repeated asset discovery.
- Non-block compatibility remains shallower than the block path.
- EME-style profile validation now closes a presentation-input diagnostics gap, but it intentionally does not parse `.bbmodel` geometry, generate Bedrock entity geometry, execute animation clips, or provide a Java-to-Bedrock entity behavior bridge. Those claims remain unsupported until a current-version, license-admissible conversion and client-observation path exists.
- The entity behavior slice now covers explicit interaction primitives only. It does not infer arbitrary AI, invent entity semantics from presentation data, translate custom networking, or promote server-thread execution to `CLIENT_OBSERVED`.
- The entity discovery slice now reports bounded AI and custom-network facts with provenance and fail-closed findings. This is not arbitrary AI execution or custom packet translation: unknown behavior, opaque bytecode, and undeclared network schemas remain explicit unsupported results.
- Live Bedrock-client synchronization verification, non-menu Bedrock action-to-mutation execution, richer menu widgets, block-entity behavior, deeper entity behavior, custom networking, custom rendering analysis, and broad automatic machine recipe discovery remain incomplete.
- The compiled runtime-plan architecture exists for the currently shipped compatibility slices, but it is not yet universal across all capability domains; transfer-heavy and deeper behavior paths still perform flexible runtime reasoning.

## Primary Architectural Correction

The future architecture should not be described primarily as "more analyzers plus more metadata".

The correct architectural correction is:

1. unify discovery
2. formalize intermediate representations
3. compile compatibility into runtime plans
4. cache intermediate and final artifacts
5. only then widen behavior and adapter breadth

The key rule is:

```text
analyze once
compile once
dispatch directly at runtime
```

## Compatibility Domains

The engine should evaluate each object across six domains, not one flattened compatibility status.

```text
HYDRAULIC COMPATIBILITY ENGINE
|
+-- 1. CONTENT
|   +-- blocks
|   +-- items
|   +-- entities
|   +-- fluids
|   +-- block entities
|
+-- 2. PRESENTATION
|   +-- models
|   +-- textures
|   +-- animations
|   +-- particles
|   +-- sounds
|
+-- 3. STATE / DATA
|   +-- block states
|   +-- item components
|   +-- NBT and custom data
|   +-- tags
|   +-- recipes
|
+-- 4. INTERACTION
|   +-- placement
|   +-- breaking
|   +-- use
|   +-- containers
|   +-- GUIs
|   +-- machines
|   +-- entity interactions
|
+-- 5. BEHAVIOR
|   +-- ticking
|   +-- automation
|   +-- fluid handling
|   +-- energy handling
|   +-- server-side rules
|   +-- generic capability adapters
|   +-- mod-specific adapters
|
+-- 6. NETWORK / PROTOCOL
    +-- custom packets
    +-- custom synchronization
    +-- clientbound state requirements
    +-- serverbound interaction requirements
```

This separation is fundamental. A converted model is not proof of compatibility.

## Universal Index

The current `ModResourceIndex` should evolve into a universal, authoritative index shared across the entire pipeline.

Suggested package direction:

```text
org.geysermc.hydraulic.pack.index
  UniversalResourceIndex
  ModIndex
  ResourceFile
  ResourceKind
  ResourceNamespace
  ResourceFingerprint
  ResourceDependency
  IndexBuilder
  IndexCache
  IndexVersion
```

Suggested `ResourceKind` coverage:

```text
BLOCKSTATE
ITEM_DEFINITION
ITEM_MODEL
MODEL
TEXTURE
ANIMATION
SOUND
LANG
RECIPE
TAG
LOOT_TABLE
ENTITY
PARTICLE
FONT
SHADER
GEOMETRY
DATA
UNKNOWN
```

Each indexed resource should track at least:

```text
namespace
relative path
kind
source root
size
last modified
fingerprint
dependencies
```

Each `ModIndex` should eventually expose:

- registry index
- resource index
- blockstate index
- item-definition index
- item-model index
- model index
- texture index
- recipe index
- entity index
- menu index
- block-entity index
- dependency graph
- fingerprint
- compatibility facts

Every subsystem that currently rescans disk should consume this index instead.

## Discovery IR

Hydraulic needs a formal IR boundary between discovery and compatibility decisions.

```text
Java resources and registries
  -> Discovery IR
  -> Compatibility IR
  -> Compiled Compatibility Plan
  -> Resource IR / Bridge IR
```

The discovery layer should gather facts without making Bedrock decisions.

Example direction:

```text
JavaBlockIR
  -> identifier
  -> owning mod
  -> state definition
  -> default state
  -> model references
  -> texture references
  -> collision facts
  -> selection facts
  -> block-entity facts
  -> tags
  -> interaction facts
  -> capability hints
  -> dependencies
```

The same pattern should exist for items, entities, fluids, menus, recipes, and block entities.

## Compatibility IR

The next major shift remains the move from raw mapping tables to typed compatibility objects, but the IR boundary must become explicit.

```text
CompatibilityObject
  -> content type
  -> Java identifier
  -> owning mod
  -> mod fingerprint
  -> discovery facts
  -> capability profile
  -> analyzer findings
  -> metadata patches
  -> adapter bindings
  -> runtime requirements
  -> support results
  -> confidence
  -> provenance
  -> degradation actions
  -> reasons
```

Every object should answer:

1. What capabilities does it require?
2. Which capabilities are automatically representable on Bedrock?
3. Which gaps can metadata patches close?
4. Which gaps can generic capability adapters close?
5. Which gaps require a mod-specific adapter?
6. Which gaps are impossible or not worth simulating?

## Compiled Compatibility Plan

The compatibility layer should disappear from hot runtime paths.

Compatibility analysis belongs to startup and conversion time.
Runtime should execute compiled plans.

Add:

```text
CompiledCompatibilityPlan
  -> object identifier
  -> presentation plan
  -> placement plan
  -> interaction plan
  -> behavior plan
  -> network plan
  -> block-entity plan
  -> container plan
  -> resource-pack references
  -> adapter references
  -> fallback plan
  -> support level
  -> critical failures
  -> confidence
```

Example:

```text
create:mechanical_press

presentation:
  GENERATED_CUSTOM_BLOCK

placement:
  GEYSER_CUSTOM_BLOCK

interaction:
  MACHINE_CONTAINER_BRIDGE

behavior:
  CREATE_KINETIC_ADAPTER

block_entity:
  CREATE_BLOCK_ENTITY_BRIDGE

resource_pack:
  pack-7a82...

confidence:
  0.97
```

This is the object runtime should consult, not flexible metadata or late analyzer logic.

## Runtime Dispatch Architecture

Current runtime dispatch should evolve from "ask every module for every event" to direct lookup.

Target:

```text
Java event
  -> extract identifier
  -> lookup compiled runtime plan
  -> execute bridge or fallback
```

Add:

```text
RuntimeDispatchTable
  block id       -> BlockRuntimePlan
  item id        -> ItemRuntimePlan
  entity id      -> EntityRuntimePlan
  menu id        -> MenuRuntimePlan
  block entity   -> BlockEntityRuntimePlan
  fluid id       -> FluidRuntimePlan
```

Each runtime plan should already contain:

- translator
- adapter
- fallback
- support level
- requirements

This is one of the highest-value runtime optimizations in the fork.

## Persistent Fingerprints And Conversion Keys

The current full-tree hashing approach in `PackUtil.getModUUID()` is too expensive for routine invalidation.

Replace it with persistent incremental fingerprints.

For directories, fingerprint from:

- relative path
- file count
- size
- last modified
- content hash only when metadata shows change

For jars, fingerprint from:

- size
- last modified
- sha-256

Persist something like:

```json
{
  "mod": "create",
  "fingerprint": "...",
  "algorithm": "HYDRAULIC_INDEX_V2",
  "fileCount": 18342
}
```

The cache key must become more precise than "mod changed or not".

Add:

```text
ConversionKey
  -> mod fingerprint
  -> hydraulic version
  -> converter version
  -> metadata fingerprint
  -> target Minecraft version
  -> target Bedrock version
  -> Geyser version
  -> generator schema version
  -> relevant config fingerprint
```

Same key means cache hit and no reconversion.

## Persistent Artifact Cache

Make the cache a first-class subsystem.

Suggested layout:

```text
config/hydraulic/
  cache/
    index/
    compatibility/
    models/
    textures/
    conversions/
    validation/
    manifests/
```

Suggested service surface:

```text
ArtifactCache
  get(key)
  put(key, artifact)
  contains(key)
  invalidate(key)
  invalidateMod(modId)
```

Do not cache only the final pack zip. Cache expensive intermediate results too.

## Lazy Loading And Bounded Memory

Current resource loading is too eager for large modpacks.

Target lifecycle:

```text
INDEX
  -> LOAD ONLY WHAT IS NEEDED
  -> CONVERT
  -> GENERATE
  -> VALIDATE
  -> WRITE CACHE
  -> RELEASE
```

Do not keep the entire modpack resident after generation just because it was loaded once.

### Model system direction
- Replace all-models-resident behavior with a `ModelIndex` of paths and dependencies.
- Parse model JSON on demand.
- Cache parsed models in a bounded LRU cache.
- Resolve parent-model closure lazily and cache resolved closures.

### Texture system direction
- Build a texture dependency graph.
- Convert only textures required by converted models.
- Deduplicate atlas membership.
- Avoid converting every texture simply because it exists.

### Block-state direction
- Keep metadata authoring human-readable.
- Compile runtime block-state resolution into compact tables.
- Prefer state ordinals or packed state keys over string-heavy hot-path evaluation.

## Data-Oriented Compatibility Engine

The compatibility engine should stop reconstructing facts independently for each analysis phase.

For each object:

```text
object
  -> fact collection
  -> capability inference
  -> domain analysis
  -> decision
  -> compiled plan
```

Do not keep a future design where every object linearly searches all analyzers.

Add:

```text
AnalyzerRegistry
  block        -> BlockAnalyzer
  item         -> ItemAnalyzer
  entity       -> EntityAnalyzer
  fluid        -> FluidAnalyzer
  menu         -> MenuAnalyzer
  blockentity  -> BlockEntityAnalyzer
```

That avoids repeated `supports(...)` scans and makes analyzer routing explicit.

## Capability Model

The capability layer remains central, but it must drive both compatibility decisions and compiled runtime planning.

```text
org.geysermc.hydraulic.compat.capability
  CapabilityAnalyzer
  CapabilityProfile
  Capability
  CapabilityRequirement
  CapabilityResult
  CriticalCapability
  CapabilityWeightProfile
```

Example shape:

```text
Create: crushing_wheel

visual:
  model              YES
  texture            YES
  animation          YES

world:
  placeable          YES
  breakable          YES
  directional        YES
  waterloggable      NO

interaction:
  right_click        YES
  inventory          YES
  gui                YES

behavior:
  ticking            YES
  kinetic_system     YES
  redstone           YES
  entity_interaction NO

data:
  block_entity       YES
  custom_data        YES

network:
  custom_packets     NO
  custom_sync        YES
```

## Multidimensional Support Results

Do not store only one support level.

Store domain-level and capability-level support first, then derive the overall result.

Example:

```text
overall      = ADAPTED
visual       = COMPLETE
placement    = COMPLETE
state        = COMPLETE
interaction  = PARTIAL
inventory    = COMPLETE
behavior     = PARTIAL
network      = PARTIAL
audio        = COMPLETE
```

The final report may still expose a single overall status, but it must be derived from richer support results and critical-capability rules.

## Compatibility Taxonomy

Keep coarse compatibility status values where useful, but add a final support vocabulary for human-facing results:

- `NATIVE`
- `AUTOMATIC`
- `ADAPTED`
- `APPROXIMATED`
- `VISUAL_ONLY`
- `UNSUPPORTED`

Also add explicit degradation actions for implementation planning and reporting:

- `APPROXIMATE`
- `SIMPLIFY`
- `SCRIPT`
- `OMIT`

If diagnostics need to describe an intentionally non-executable generated object, use the phrase `diagnostic-only stub`; it must never be emitted as an implementation or support action.

These do not replace support results. They explain how Hydraulic degraded behavior.

## Compatibility Score And Critical Capability Rules

Add a machine-readable score, but never let the score overrule a missing critical capability.

Example weighting direction:

```text
Presentation   10%
Placement      10%
State          10%
Interaction    25%
Behavior       25%
Data           10%
Network        10%
```

Object classes may need different weight profiles.

Examples:

- machines should weight behavior, container semantics, and transfer heavily
- decorative blocks should weight presentation and placement heavily
- entities should weight behavior and interaction heavily

Add critical-capability rules such as:

```text
machine:
  processing = CRITICAL
  inventory  = CRITICAL
  visual     = NON_CRITICAL
```

If critical behavior is missing, the result must remain `UNSUPPORTED` or `VISUAL_ONLY` even when presentation looks good.

## Metadata Evolution Plan

### Current state
Keep deterministic recursive discovery and ownership precedence.

### Next structure

```text
config/hydraulic/metadata/
  builtin/
  mods/
  server/
  user/
```

### Design rule
Metadata is a patch layer over automatic analysis, not the primary mapping source.

External Bedrock addon corpus snapshots must not become the primary contents of `metadata/`; they are a separate evidence source that may inform compatibility analysis and later compile into runtime plans.

Example direction:

```json
{
  "target": "create:andesite_casing",
  "patch": {
    "visual.geometry": "...",
    "state.facing": "...",
    "interaction.use": "create:casing_use"
  }
}
```

### Generated versus manual separation

```text
config/hydraulic/
  generated/
  metadata/
    builtin/
    mods/
    server/
    user/
```

Generated compatibility suggestions must never overwrite server or user intent.

### Compilation rule
Flexible authoring metadata must compile into compact runtime metadata.

Target flow:

```text
Metadata JSON
  -> validated metadata
  -> flexible metadata IR
  -> compiled runtime metadata
  -> compiled compatibility plan
```

Runtime representations should prefer:

- enums
- integer ids
- bit flags
- direct references
- compact arrays

Runtime should avoid:

- generic patch maps
- JSON objects
- string-keyed interpretation in hot paths

## Behavior Fact Extraction

The biggest remaining compatibility leap is not arbitrary Java bytecode translation.

It is behavior fact extraction.

Hydraulic should get better at answering "how does this object behave" by extracting facts such as:

- ticks
- has block entity
- opens menu
- has inventory
- accepts items
- produces items
- consumes items
- uses fluids
- uses energy
- reacts to redstone
- changes state
- spawns entities
- plays sounds
- emits particles
- uses custom networking
- uses server-only logic
- uses custom rendering

Those facts should feed the capability model and the compiled runtime plan.

## Generic Machine, Container, And Transfer Model

This is the highest-return compatibility layer after the performance substrate.

### Machine profile direction

```text
MachineProfile
  inventory
    -> input slots
    -> output slots
    -> fuel slots
    -> upgrade slots

  processing
    -> recipes
    -> duration
    -> progress
    -> outputs

  fluids
    -> input tanks
    -> output tanks

  energy
    -> input
    -> output
    -> capacity

  state
    -> active
    -> progress
    -> orientation

  interaction
    -> menu
    -> block interaction

  automation
    -> sided insertion
    -> sided extraction
    -> filtering
    -> redstone
```

### Transfer capabilities
- `ItemTransfer`
- `FluidTransfer`
- `EnergyTransfer`

### Container direction
Infer container archetypes before writing mod-specific menu adapters.

Examples:

- `CHEST`
- `DOUBLE_CHEST`
- `FURNACE`
- `CRAFTING`
- `PROCESSOR`
- `MACHINE`
- `STORAGE`
- `ENERGY_MACHINE`
- `FLUID_MACHINE`
- `CUSTOM_GRID`

Also map slot semantics, not just slot numbers:

- `INPUT`
- `OUTPUT`
- `FUEL`
- `UPGRADE`
- `FLUID_INPUT`
- `FLUID_OUTPUT`
- `CATALYST`

This is how Hydraulic avoids writing hundreds of UI adapters for machines that share the same semantics.

## Fluids, Entities, Networking, And Rendering

### Fluids
Fluids remain a major missing subsystem.

Hydraulic needs:

- `FluidTranslator`
- `FluidBridge`
- fluid block representation
- bucket and item representation
- tank representation
- transfer semantics
- machine interaction semantics

### Entities
Treat entities as three layers:

- presentation
- interaction
- behavior

Hydraulic should not attempt arbitrary AI translation. It should infer and map a generic AI vocabulary when possible.

Examples:

- wander
- follow
- attack
- flee
- guard
- look_at
- trade
- pickup
- work
- breed

### Networking
Networking is its own compatibility domain.

Detect and report:

- custom packets
- custom synchronization
- clientbound state requirements
- serverbound interaction requirements

Unknown or essential custom network behavior should escalate compatibility risk automatically.

### Custom rendering
Detect:

- block entity renderers
- entity renderers
- item renderers
- custom shaders
- custom vertex pipelines

Then classify:

- standard renderer -> automatic
- known renderer pattern -> generic adapter
- unknown custom renderer -> approximation or unsupported

## Geyser Integration Strategy

Hydraulic should explicitly build on Geyser rather than trying to recreate it.

The architecture should be:

```text
Geyser knowledge
  + Hydraulic conversion
  + Hydraulic compatibility engine
  + Hydraulic runtime bridges
  + optional generated Bedrock behavior content
```

not:

```text
Hydraulic recreates Geyser
```

### Important correction
Do not make a generated Bedrock behavior pack the foundation of the project.

The primary behavior mechanism should be:

```text
Java server authoritative state
  -> Hydraulic semantic translation and runtime bridges
  -> Geyser transport and Bedrock-facing registration
  -> Bedrock client
```

Generated Bedrock behavior content can still exist where useful, but it is secondary to the runtime bridge architecture.

### Pack delivery
Automatic Bedrock resource-pack delivery is realistic and should stay in scope.

Target:

```text
modpack installed
  -> Hydraulic converts
  -> pack generated
  -> content-addressed artifact cached
  -> Geyser registers pack or URL
  -> Bedrock player downloads automatically
```

Consider incremental pack structure later:

- `hydraulic-core.mcpack`
- `create.mcpack`
- `mekanism.mcpack`
- optional feature packs

But design this around actual Geyser resource-pack behavior, not assumptions borrowed from Java resource packs.

## Reporting And Validation Model

The report should answer three things simultaneously:

1. What works.
2. How well it works across each domain.
3. Why support is partial or missing.

### Report modes
- production mode
- diagnostic mode
- deep-audit mode

Suggested outputs:

- `compatibility-summary.json`
- `compatibility-report.json`
- `corpus-summary.json`
- `corpus-admissibility-report.json`
- `adapter-opportunity-report.json`
- deep-audit inventory and dependency artifacts only when explicitly requested

### Validator role
Validation should remain a separate stage after generation.

It should emit structured:

- `errors`
- `warnings`
- `manualActions`

Validation evidence should feed follow-up work for metadata, bridges, generators, and adapters.

## Performance And Observability

The current performance report is useful, but it should evolve into a profiler-lite evidence system.

Target structure:

```text
startup
  discovery
  indexing
  metadata
  compatibility
  cache load

conversion
  per mod
    parsing
    models
    textures
    blocks
    items
    entities
    packaging
    validation

runtime
  bridge calls
  translation calls
  dispatch lookups
  cache hits
  cache misses

memory
  peak heap
  index memory
  model cache
  texture cache
```

Capture when practical:

- count
- total time
- average time
- `p50`
- `p95`
- `p99`
- cache hits
- cache misses

Add stage-level metrics for:

- resource index
- model resolver
- texture resolver
- block-state resolver
- metadata resolver
- compatibility analyzer
- conversion artifact cache
- runtime dispatch

Optimization should be evidence-driven, not intuition-driven.

## Conversion Scheduler

The current thread-pool heuristic is acceptable as a first pass, but the long-term system should schedule conversion work by cost and memory budget.

Target:

```text
ConversionScheduler
  -> max workers
  -> memory budget
  -> queue
  -> priority
  -> estimated cost
```

The scheduler should optimize throughput per memory and I/O budget, not just raw thread count.

Bound memory as well as concurrency.

## Proposed Package Direction

### Keep current high-level classes

```text
org.geysermc.hydraulic.compat
  CompatibilityManager
  CompatibilityRegistry
  CompatibilityReport
  ContentInventory
  MappingResolver
  MappingOwnership
```

### Add or evolve explicit subpackages

```text
org.geysermc.hydraulic.pack.index
  UniversalResourceIndex
  ModIndex
  ResourceFile
  ResourceKind
  ResourceFingerprint
  ResourceDependency
  IndexBuilder
  IndexCache

org.geysermc.hydraulic.compat.analysis
  AnalyzerRegistry
  RegistryAnalyzer
  BlockAnalyzer
  ModelAnalyzer
  StateAnalyzer
  ItemAnalyzer
  ComponentAnalyzer
  EntityAnalyzer
  FluidAnalyzer
  MenuAnalyzer
  BlockEntityAnalyzer
  NetworkAnalyzer
  RenderAnalyzer

org.geysermc.hydraulic.compat.capability
  CapabilityAnalyzer
  CapabilityProfile
  Capability
  CapabilityRequirement
  CapabilityResult
  CriticalCapability
  CapabilityWeightProfile

org.geysermc.hydraulic.compat.model
  CompatibilityObject
  SupportResult
  CompatibilityFinding
  Confidence
  Provenance
  ModFingerprint
  DegradationAction

org.geysermc.hydraulic.compat.ir
  DiscoveryIr
  CompatibilityIr
  ResourceIr
  BridgeIr
  CompiledCompatibilityPlan

org.geysermc.hydraulic.compat.knowledge
  CompatibilityKnowledge
  KnowledgeEntry
  KnowledgeSource
  PatternClassifier

org.geysermc.hydraulic.compat.corpus
  AddonCorpus
  AddonCorpusEntry
  AddonCorpusSource
  AddonCapabilityProfile
  CorpusEvidence
  CorpusIdentity
  CorpusLoader
  CorpusMatcher
  CorpusIndex

org.geysermc.hydraulic.compat.mapping
  ContentPatch
  PatchCompiler
  CompiledPatch
  StateTranslator
  ModelClassifier
  GeometryResolver
  ItemTranslator
  FluidTranslator
  ContainerTranslator

org.geysermc.hydraulic.compat.runtime
  RuntimeDispatchTable
  BlockRuntimePlan
  ItemRuntimePlan
  EntityRuntimePlan
  MenuRuntimePlan
  BlockEntityRuntimePlan
  FluidRuntimePlan

org.geysermc.hydraulic.compat.bridge
  InteractionBridge
  ContainerBridge
  BlockEntityBridge
  FluidBridge
  EntityBridge
  NetworkBridge

org.geysermc.hydraulic.compat.generator
  ResourcePackGenerator
  OptionalBehaviorGenerator
  ReportGenerator
  ValidationArtifactWriter

org.geysermc.hydraulic.compat.adapter
  ModAdapter
  CapabilityAdapter
  AdapterBinding

org.geysermc.hydraulic.cache
  ArtifactCache
  ConversionKey
  FingerprintService
```

The exact package names may move, but the architectural boundaries should not.

## Updated Phase Plan

## Phase 0: Existing Foundation
Priority: completed baseline

Delivered:
- compatibility scaffolding in `shared/`
- deterministic metadata loading
- typed compatibility data
- early compatibility reporting
- first cache evidence in performance artifacts
- first metadata-backed runtime seams for menu and block-entity handling
- post-generation validation artifact emission

This baseline is real and should be preserved.

## Phase 0.5: Local Integration Harness (VS Code)
Priority: high, non-blocking for Phases 1-8

### Companion add-on boundary (implemented)

`Plodgate_Add-on` is the first-class, mod-agnostic Bedrock companion component. Its resource pack
is built and delivered through Geyser by Hydraulic's companion package subsystem, while its
behavior pack remains an explicitly installed/enabled client-local Script API component because
Geyser cannot deploy or execute behavior packs for Java-server sessions. Hydraulic installs the
canonical `phlodgate_bridge` scoreboard objective; the add-on directly polls that objective as its
authoritative companion-mode handshake. This signal proves only server detection, not generic
machine, fluid, automation, or synchronization execution.

The bundled Geyser `2.11.2-SNAPSHOT` API exposes global `GeyserDefineResourcePacksEvent`
registration, which Hydraulic already uses for every built companion `.mcpack`; it does not expose
`SessionLoadResourcePacksEvent`. Resource-pack delivery is therefore automatic for every
Bedrock/Geyser session in this build, but per-session companion-pack selection is not an available
public integration point and must not be represented as implemented.

The locked responsibility split is:

```text
Hydraulic / Java server: capability analysis, compiled plans, authoritative mutation, persistence,
                         synchronization planning, and Geyser transport
Companion add-on:        mod-agnostic client presentation, forms, inspection, local settings, and
                         optionally enabled client-local Script API behavior
```

No companion capability may branch on a Java mod identifier or claim a server behavior unless a
concrete, compiled Java-side bridge and transport result exist. This preserves the intended
`Java content -> runtime plan -> Java bridge -> Geyser -> Bedrock presentation` architecture.

### Behavior-pack execution research gate (unproven)

**Status: `UNSUPPORTED / UNPROVEN`.** No production packet injection or behavior-pack deployment
may be added while this status remains unchanged.

**Falsifiable hypothesis:** a Geyser/Phlodgate implementation might cause an official Bedrock
client connected to a Java server to receive, activate, and execute a Bedrock behavior pack for
that session using protocol and Geyser-side mechanisms only.

The current evidence does not establish that hypothesis: Geyser supports resource-pack delivery,
does not support behavior packs or add-ons, and Java/Geyser sessions do not provide a Bedrock
world runtime. A downloaded, cached, acknowledged, or displayed pack is not an activated or
executing behavior pack.

The investigation must run only in an isolated experimental Geyser fork or extension, never in
Hydraulic production code. It must inspect the current Geyser pack-negotiation flow and exact
Bedrock protocol definitions, identify any behavior-pack stack fields, document why Geyser omits
them if applicable, and record Bedrock client/protocol versions plus packet captures.

The proof ladder is mandatory:

1. Behavior-pack metadata can be represented in the negotiated protocol.
2. The official client requests and downloads the behavior pack.
3. The client accepts the pack into the session pack stack.
4. `@minecraft/server` initializes for that ordinary Java/Geyser session.
5. A behavior-pack script executes or ticks and emits a uniquely generated marker that Hydraulic
  cannot produce itself.
6. That script causes an observable, connected-world effect.
7. The result works on current supported Bedrock versions; reconnect behavior is recorded
  separately as an optional persistence result.

**Pass condition:** an actual behavior-pack Script API program executes on a real official Bedrock
client during an ordinary Java/Geyser session and produces the independent marker plus observable
world effect. **Failure condition:** any lower result, including transfer completion or pack-stack
acknowledgement without Script API execution. Until a pass is recorded, `Plodgate_Add-on/BP` stays
a normally installable standalone Bedrock-world component and never a claimed automatic Geyser
session component.

This phase does not introduce another runtime architecture. It gives the fork a repeatable, local way
to prove the architecture that already exists, using `.vscode` orchestration around the real Gradle/Loom
tasks, the real compatibility runtime classes, and the real handoff/report artifacts already emitted by
`PackManager`. No new production runtime or trace classes are introduced by this phase.

### Grounded facts (verified against the live repo, not assumed)
- `SyncDeliveryStatus` (`shared/src/main/java/org/geysermc/hydraulic/compat/runtime/SyncDeliveryStatus.java`)
  already carries `PLANNED, ENCODED, QUEUED, SENT, APPLIED, ENCODING_FAILED, TRANSPORT_FAILED,
  TARGET_UNAVAILABLE, STALE_STATE, UNSUPPORTED` — this is the closest real analog to the
  `EXECUTABLE -> SYNCHRONIZED -> TRANSPORT_HANDOFF_VERIFIED` progression described above; `SENT`/`APPLIED`
  is the real transport-handoff-verified signal.
- `RuntimeBridgeKind`, `RuntimeTraceId`, `StateChangeSet`, and `SyncBatch` already exist in the same package.
  No `RuntimeTrace`, `transportHandoff`, or `clientObserved` identifier exists anywhere in the codebase.
  `CLIENT_OBSERVED` is therefore not, and must not become, an automated status — it stays a manual,
  human-attested field, matching the runtime-traceability rule above that `TRANSPORT_HANDOFF_VERIFIED`
  and `CLIENT_OBSERVED` must never be collapsed into one claim.
- `CompatibilityHandoffQueue`/`CompatibilityHandoffExporter` already write `HandoffEnvelope` JSON to
  `hydraulic.dataFolder("hydraulic").resolve("cache")/handoff-queue/exports/*.json` (see
  `PackManager.java`), i.e. `fabric/run/config/hydraulic/cache/handoff-queue/exports/*.json` in this dev
  workspace. `PackValidationTracker` already writes `config/hydraulic/reports/pack-validation-report.json`
  with a `perMod` map of `{valid, errors[], warnings[], manualActions[]}`. These are the real runtime
  artifacts the harness validates — no new report format is invented for compatibility data.
- Existing runtime tests (`SyncPlannerTest`, `GeyserSyncTransportTest`, `TransferBridgeRuntimeTest`,
  `RuntimeDispatchTableTest`, `CompatibilityRuntimeDiagnosticsTest`, `RuntimeTargetDiscoveryTest`, and
  neighboring classes under `shared/src/test/java/org/geysermc/hydraulic/compat/runtime/`) assert the
  individual pipeline stages. `RuntimeTargetDiscoveryTest` now also exercises the production
  `GeyserSyncTransport` from Java-side mutation through trace propagation and a concrete
  `InventorySlotPacket` handoff. These tests still stop at the injected Geyser packet boundary; they are
  not a live end-to-end Bedrock client trace and must never be reported as one.
- `test/src/main/java/org/geysermc/hydraulic/fabric/test/` now includes item-transfer, processing,
  fluid, energy, mixed-resource, and menu machines in addition to the baseline blocks, items, fluid,
  menu, and entity. The menu machine has synchronized progress and a persisted toggle. Rich entity
  interaction, a datagen-driven custom recipe fixture, and a dedicated synchronization-stress fixture
  remain the explicit fixture backlog.
- `gradle.properties` sets `org.gradle.daemon=false`. Loom's `runServer` task blocks the invoking Gradle
  process for the lifetime of the dev server (`JavaExec` does not return until the server stops). A second
  `./gradlew` invocation started while `runServer` is still running can block on the Gradle project lock
  until the server is stopped. The harness must never auto-chain a `gradlew` test task after starting the
  dev server in the same task sequence; read-only PowerShell scripts (no `gradlew` invocation) are safe to
  run concurrently with a live server, and are used for artifact/pack validation for exactly this reason.
- Verified by actually running both focused test tasks: `:shared:test` clears `shared/build/test-results/test`
  on every invocation, so running the runtime-pipeline and pack-validation filters as two separate `gradlew`
  calls silently deletes the first call's JUnit XML before the aggregator script can read it. The harness
  therefore runs both `--tests` filters in one `:shared:test` invocation instead of two sequential/parallel ones.

### Harness tasks (implemented in `.vscode/tasks.json`)
- `Hydraulic: Build (Fabric)` / `Hydraulic: Start Dev Server (Fabric)` / `Hydraulic: Start Dev Server
  (Fabric, Debug)` — build and run the real Fabric dev server (`:fabric:runServer`), with a debug variant
  that opens JDWP port 5005 for `.vscode/launch.json`'s `Attach: Fabric Dev Server` configuration.
- `Hydraulic: Start Dev Server (NeoForge) [experimental]` — present because the NeoForge module exists in
  the tree, explicitly labeled experimental/best-effort, excluded from the default compound task.
- `Bedrock: Start BDS (optional)` — runs a user-supplied `dev/bedrock/bedrock_server.exe` on port 19133 if
  present, otherwise prints setup instructions and exits 0. This is Test B (Bedrock addon/script runtime
  validation) and is architecturally unrelated to validating Phlodgate itself (Test A).
- `Hydraulic: Run Runtime Pipeline Tests` / `Hydraulic: Run Pack Validation Tests` / `Hydraulic: Run All
  Focused Integration Tests` — run the real, existing JUnit suites above through `:shared:test --tests`
  filters. Must not be run while a dev server `gradlew` invocation is still active (see Gradle-lock note).
- `Hydraulic: Validate Runtime Artifacts` / `Hydraulic: Validate Generated Packs` / `Hydraulic: Write
  Integration Test Report` — read-only PowerShell scripts under `scripts/` that inspect the real handoff
  export and pack-validation artifacts and write a harness-only
  `config/hydraulic/reports/integration-test-report.json` summary. The `bedrockClientCheck` field is always
  written as `PENDING_MANUAL_CLIENT_CHECK`; it is never auto-derived from `SyncDeliveryStatus` or packet
  encoding alone.
- `Phlodgate: Launch Full Integration Test` — build, start the Fabric dev server, then run the read-only
  artifact/pack/report scripts and print a manual-step banner instructing the operator to connect the real
  Minecraft Bedrock Windows client to `127.0.0.1:19132`, perform the target action, and manually confirm the
  resulting trace before editing `bedrockClientCheck`.
- `Bedrock: Register Phlodgate Server (deep link)` — best-effort `minecraft://?addExternalServer=...` helper,
  documented as unreliable/manual-fallback since VS Code cannot control the Bedrock UWP client itself.

### Test fixture backlog (separate, larger implementation lane)
Expand `test/src/main/java/org/geysermc/hydraulic/fabric/test/` with fixtures mapped to real
`RuntimeBridgeKind` values so each new object exercises a specific, named bridge instead of an invented
category: `directional_block` (`BLOCK_PLACEMENT`/`BLOCK_BEHAVIOR`), a data-bearing `block_entity`
(`BLOCK_ENTITY_DATA`/`BLOCK_ENTITY_BEHAVIOR`), an `inventory_machine`/`item_transfer_machine`
(`MACHINE_INVENTORY`/`ITEM_TRANSFER`), a `fluid_machine` (`FLUID_TRANSFER`), an `energy_machine`
(`ENERGY_TRANSFER`), a `mixed_resource_machine` combining all three plus `MACHINE_BEHAVIOR`, a
`menu_machine` with synced properties (`MENU_CONTAINER`/`MENU_BEHAVIOR`), a richer `entity_interaction`
beyond open-menu (`ENTITY_INTERACTION`/`ENTITY_BEHAVIOR`), a `custom_recipe` fixture exercising
`DataGeneration.java`, and a `synchronization_test` fixture that deliberately mutates state fast enough to
exercise `SyncPlanner` coalescing and produce an observable `SyncBatch`/`RuntimeTraceId` in the exported
handoff envelope. This backlog is intentionally not implemented in the same pass as the tasks/scripts above.

**`item_transfer_machine` shipped and live-verified (2026-09-10).** `test/.../machine/ItemTransferMachineBlock`
+ `ItemTransferMachineBlockEntity` expose real `getContainerSize`/`getItem`/`insertItem`/`extractItem`
methods using `TransferBridgeFactory.ItemStackView` directly (the `test` module now has a `compileOnly`
dependency on `shared` for this), which `TransferBridgeFactory`'s reflective `RuntimeInventoryAdapter`
picks up without any Hydraulic-specific interface implementation. A companion metadata patch
(`hydraulic_test_mod.item_transfer_machine.json`, installed by the generalized, multi-file
`HydraulicTestMetadataBootstrap`) declares `machine.inventory.enabled`, `transfer.item.can_insert`, and
`transfer.item.can_extract`. Verified against a live `:fabric:runServer` run: `compatibility-report.json`
compiles `runtimeRequirements = [block_behavior_bridge, machine_inventory_bridge, item_transfer_bridge]`
for `hydraulic_test_mod:item_transfer_machine`, meaning `TransferBridgeFactory.createItemTransfer(plan,
blockEntity)` will construct a real, `executable() == true` bridge once a live block entity is queried.
**Important, verified nuance:** the static `compatibility-report.json` still marks this object
`overallLevel = VISUAL_ONLY` / `overallScore = 0` with `critical_failure = true` ("machine processing or
inventory runtime is unavailable"), because the compiled critical-capability policy additionally requires
a `has_processing` fact before it will call a "machine" object non-critical-failing — this fixture only
declares inventory/transfer facts, not a processing/recipe contract. This is the compatibility engine
working exactly as documented ("Hard runtime rule: no machine may be marked EXECUTABLE merely because it
can execute an item transaction"), not a bug: the runtime-dispatch layer and the static compatibility-score
layer are two different, intentionally separate gates. A real Fabric-datagen texture/model
(`textures/block/item_transfer_machine.png`, reused from `golden_barrel.png`) was required to clear a
separate `block.asset.missing` finding — `:test:runDatagen` must be run at least once after adding a new
block before `:fabric:runServer`, since `:test:jar` does not transitively trigger datagen on its own.

### Test fixture backlog remaining (not yet implemented)
Richer `entity_interaction`, `custom_recipe` (datagen-driven), and `synchronization_test` remain open.
The fluid, energy, mixed-resource, and menu-machine fixtures described below are now implemented.

**`processing_machine` shipped and live-verified (2026-09-10), closing the semantic-classification item from
the immediate-next-slice review.** Rather than retrofitting `has_processing` onto `item_transfer_machine`
(which is a plain storage box with no recipe semantics — adding the fact there would have been exactly the
score-gaming the architecture plan prohibits), a second, genuinely-processing fixture was added:
`test/.../machine/ProcessingMachineBlock` + `ProcessingMachineBlockEntity` (2 slots: input/output) declare a
real `machine.processing.recipe.0.*` contract (cobblestone → stone, 40-tick duration) and the block's real
`getTicker()` drives the actual production `MachineBridgeFactory.createProcessing(plan, itemTransferBridge)`
→ `MachineProcessingBridge.tick(blockIdentifier)` every server tick — not a bespoke simulation. Verified via
a live `:fabric:runServer` run with no crashes: `compatibility-report.json` now compiles
`runtimeRequirements = [block_behavior_bridge, machine_behavior_bridge, machine_inventory_bridge,
item_transfer_bridge]` (note the new `machine_behavior_bridge`, absent for `item_transfer_machine`), and
`inventoryFacts.has_processing = "true"`.

**Analyzer/runtime classification correction shipped and live-verified (2026-09-10).** `BlockAnalyzer`
no longer treats the presence of any `machine.*` or `transfer.*` declaration as conclusive evidence that
behavior is unavailable. `AnalyzerSupport` now classifies machine readiness from the full processing,
inventory, bidirectional item-transfer, slot, and recipe contract. Recipe and slot validity are delegated
to `MachineBridgeFactory.hasExecutableProcessingContract(...)`, which uses the same parser and validation
rules as `MachineBridgeFactory.createProcessing(...)`; analysis and runtime construction therefore no
longer maintain separate definitions of a valid processing contract. This remains deliberately static:
the report records that the compiled contract can bind the production bridge when a matching live block
entity is resolved, while the factory still rejects a null compiled plan, a non-executable live transfer
bridge, missing capability kinds, malformed facts, or missing required resources.

The correction is fail-closed. Inventory/item-transfer facts without processing remain `UNSUPPORTED`;
processing without inventory or both transfer directions remains `UNSUPPORTED`; missing or negative slots,
empty/malformed recipes, zero counts, and zero duration remain `UNSUPPORTED`. Focused adversarial tests also
retain the existing incomplete `minecraft:piston` regression case. A fresh live Fabric/Geyser report records
the block object for `hydraulic_test_mod:processing_machine` as `overallLevel = APPROXIMATED`,
`overallScore = 93`, `behavior = ADAPTED`, no `critical_failure`, valid input/output slot facts, and the
`block.behavior.executable` finding. In the same report, `hydraulic_test_mod:item_transfer_machine` remains
`overallLevel = VISUAL_ONLY`, `overallScore = 0`, `behavior = UNSUPPORTED`, and `critical_failure = true`.
This proves the transition is tied to the complete processing contract rather than a blanket machine-patch
upgrade. It does not claim Bedrock-client observation; that remains a separate manual integration gate.

**Authoritative block-use mutation and resource fixture family shipped (2026-09-10).** The processing
fixture now opts into a validated `insert_held_item` action targeting input slot `0`, count `1`, side `up`,
and declares sided insertion so `RuntimeDispatchTable.resourceAutomationAccess(...)` can bind. The actual
mutation runs from `ProcessingMachineBlock.useItemOn(...)` on the Minecraft server thread, uses
`RuntimeTargetDiscovery.transferItem(...)`, commits through the production item transaction, and decrements
the held stack only after an exact commit. The Geyser inventory-transaction mixin remains non-cancelling and
trace-only, avoiding network-thread world mutation or duplicate action ownership.

`fluid_machine` and `energy_machine` now provide persistent reflective tank and energy storage using the
exact method shapes consumed by `TransferBridgeFactory`; their static compatibility results remain
conservative because transfer support alone is not a complete processing machine. `mixed_resource_machine`
provides item slots, a fluid tank, and energy storage, and ticks the production
`MixedResourceMachineProcessingBridge` with a cobblestone + water + energy contract. Its fresh live block
report is `APPROXIMATED`/`93`, with `MACHINE_BEHAVIOR`, `MACHINE_INVENTORY`, `ITEM_TRANSFER`,
`FLUID_TRANSFER`, and `ENERGY_TRANSFER` requirements. The mixed parser now shares global
`machine.input_slot`/`machine.output_slot` fallback semantics with the simple processing parser, covered by
a direct regression test.

`menu_machine` now owns a persistent two-slot `Container`, a real `AbstractContainerMenu`, safe shift-click
boundaries, a ticking synchronized progress `DataSlot`, and a persisted enabled-state toggle. Metadata
binds the existing Furnace fallback, copies live Java `progress` into Bedrock `Progress`, and compiles
`menu.button.0=toggle`. The generic menu router validates translated packets on the server thread, delegates
mutation to vanilla, records exact state deltas, and requests canonical full-state resync. The menu behavior
result is `ADAPTED` only for the explicit action contract; undeclared semantics remain unsupported. Physical
Bedrock invocation, rendering, and restart observation remain open. The generated test pack is valid with
zero fixture warnings or manual actions.

### Exit criteria
- The Fabric dev server can be built, started, and attached to for breakpoint debugging entirely from
  VS Code tasks/launch configurations.
- Runtime pipeline and pack validation tests can be run as a focused, independent check without requiring a
  live dev server.
- After a dev server run, the handoff export and pack-validation artifacts can be validated and summarized
  without re-deriving compatibility logic in a new runtime class.
- The only claim of Bedrock client-observed behavior is a manual, human-attested report field — never an
  automated inference from server-side encoding or delivery status.

## Phase 1: Universal Index And Fingerprints
Priority: highest

Build:
- `UniversalResourceIndex`
- shared file and resource classification
- mod dependency and resource dependency recording
- persistent incremental fingerprints
- replacement for full-tree `getModUUID()` hashing

Exit criteria:
- one authoritative discovery pass
- no independent compatibility filesystem walk
- no independent UUID tree hash walk during normal startup

Current status:
- The compatibility inventory walk has been removed from the normal startup path by reusing `ModResourceIndex` asset and data categories.
- Full-tree pack UUID hashing has been removed from the normal startup path and replaced with persisted conversion keys derived from indexed resource fingerprints, transitive dependent-mod fingerprints, and metadata state.
- A first-class artifact cache layout now persists index, compatibility, conversion, and validation artifacts, and compatibility output can already be reused by cache key across repeat startup when the indexed mod/resource and metadata fingerprints match.
- Model lookup now uses indexed file-path resolution plus lazy deserialization through a bounded `IndexedModelProvider`, so startup no longer eagerly builds a global parsed model map before conversion begins.
- Custom model conversion now also iterates indexed model keys through `IndexedModelProvider`, so the model conversion stage no longer depends on a full parsed-pack `models()` walk for each mod.
- Item and bow post-processing now also resolve their Java models through `IndexedModelProvider`, so conversion-time texture binding and bow override resolution no longer need parsed-pack model lookup for those paths.
- The index itself can now be rehydrated from cache on unchanged startup by validating stored mod roots, indexed files, and indexed directories instead of rewalking the resource tree.
- The persisted index now also records immutable fingerprints per indexed resource category, including models, textures, blockstates, item definitions, and data folders, so cache consumers can distinguish targeted resource-kind changes without rebuilding a coarse whole-mod identity. The fingerprint schema is versioned as `HYDRAULIC_INDEX_V2` so older snapshots are rejected safely.
- Shared texture-output resolution now uses a bounded cache in the conversion path, and the performance artifact records its hit/miss/eviction evidence alongside the model-provider cache.
- Texture conversion now records discovered versus selected texture counts from a real dependency graph built from converted models and equipment assets, retains the selected set across multi-root extraction passes, the post-process block texture map no longer blindly registers textures outside that aggregated set, and block preprocessing now resolves blockstates through indexed per-block file lookup instead of a full parsed-pack blockstate walk.
- Phase 1 is still incomplete because broader universal-index boundaries have not yet been split out from `ModResourceIndex`, and several runtime consumers still rely on local resource scans or ad hoc resolution paths outside the compiled/indexed surfaces.

## Phase 2: Artifact Cache And Lazy Loading
Priority: highest

Build:
- `ArtifactCache`
- `ConversionKey`
- index cache
- compatibility cache
- model and texture caches
- lazy resource loading
- bounded LRU model resolution

Exit criteria:
- repeat startup can hit index and compatibility caches
- unchanged mods skip expensive recomputation
- entire modpack is not kept parsed in memory by default

## Phase 3: IR And Compiled Runtime Plan
Priority: highest

Current state:
- first `CompiledCompatibilityPlan` projections now compile from the generated compatibility report into an in-memory `RuntimeDispatchTable`
- current block, item, armor, bow, entity, menu, and block-entity runtime consumers now use direct identifier-driven plan lookup instead of re-reading flexible report and metadata structures on hot paths
- item presentation compilation now avoids early component-binding hazards by using safe runtime probes and conservative fallbacks when item components are not yet bound
- compatibility analysis now also has a first explicit `AnalyzerRegistry`, so descriptor-to-analyzer routing is direct by content kind instead of a repeated `supports(...)` scan
- runtime requirement compilation now also produces typed `RuntimeBridgeKind` indexes, so the compiled plan can distinguish menu, block-entity, block, item, entity, and fluid bridge categories without leaving future runtime consumers to reinterpret freeform strings
- menu and block-entity diagnostic candidate selection now also flows through typed bridge-group queries rather than separate persisted special-case lists
- deeper resource IR and broader lazy resource loading are still pending

Build:
- `DiscoveryIr`
- `CompatibilityIr`
- `ResourceIr`
- `BridgeIr`
- `CompiledCompatibilityPlan`
- `RuntimeDispatchTable`
- compiled metadata and compiled patches

Exit criteria:
- compatibility decisions are compiled out of hot paths
- runtime becomes identifier-driven map lookup

## Phase 4: Conversion Engine Optimization
Priority: very high

Build:
- model dependency graph
- texture dependency graph
- parent-model resolution cache
- compiled block-state tables
- stable generated identifiers
- content-addressed generated assets
- incremental pack generation
- memory-aware conversion scheduler

Exit criteria:
- conversion scales with required assets, not all assets
- repeated runs produce stable identifiers and deterministic outputs

## Phase 5A: Generic Execution Substrate
Priority: very high

Build:
- item transfer bridge
- fluid transfer bridge
- energy transfer bridge
- mixed-resource transaction execution
- machine processing execution
- automation requests and runtime target discovery
- dirty-state tracking, synchronization planning, encoding, and Geyser transport handoff
- runtime tracing and explicit Bedrock-originated action routing

Exit criteria:
- common machine and transfer families execute through generic bridges before mod-specific adapters
- every claimed executable operation has concrete mutation, failure, persistence, synchronization, and transport semantics

Current state:
- The generic execution substrate is substantially implemented and covered by focused shared tests.
- The current transport evidence stops at the Geyser/Hydraulic handoff boundary. A real Bedrock client observation remains a separate manual gate and must not be inferred from packet encoding or a `SENT`/`APPLIED` status.

## Phase 5B: Universal Semantic Discovery
Priority: very high

Build:
- behavior fact extraction from indexed Java resources, registries, capabilities, recipes, block entities, menus, networking, and dependencies
- automatic Java recipe discovery and normalization into machine process facts
- normalized Forge/Fabric/Botarium item, fluid, and energy capability variants
- machine, container, fluid, menu, block-entity, and networking archetype inference
- generic capability matching against concrete executable bridge operations
- compilation of discovered facts into `Discovery IR`, `Compatibility IR`, and `CompiledCompatibilityPlan`

Exit criteria:
- supported semantics are discovered automatically where authoritative Java evidence exists, with provenance and confidence
- discovered behavior never advertises an executable bridge without a concrete operation and validation result
- unsupported or ambiguous semantics degrade explicitly to `APPROXIMATED`, `VISUAL_ONLY`, or `UNSUPPORTED`
- raw discovery data is absent from hot runtime paths; runtime consumes only compiled plans and indexed references

## Phase 6: Knowledge And Pattern Classification
Priority: high

Build:
- Bedrock addon corpus schema and admissibility model
- offline GitHub-first corpus pipeline plus CurseForge discovery metadata ingestion
- corpus snapshot loader, matcher, and compiled corpus index
- `CompatibilityKnowledge`
- pattern classifiers
- machine archetype detection
- container archetype detection
- automatic adapter selection helpers
- cost versus value ranking

Exit criteria:
- the engine learns reusable patterns rather than only accumulating mod names
- corpus snapshots can be loaded offline, matched deterministically, and used to enrich compatibility reporting without direct runtime lookups
- external catalogs are used only to discover candidate sources; every promoted source has independent license, version, provenance, and evidence checks
- optional Creator Tools validation is an operator/CI concern with an explicit tool-version and failure report, never a startup dependency
- Xbox presence broadcasters and BDS/Discord bridges remain external products; Geyser listener observation is not a compatibility capability and must not enter compiled runtime plans

Phase note:
- corpus contract, source-policy, and harvesting work can begin earlier, but Hydraulic consumption belongs here after the typed bridge and compiled-plan seams are stable enough to accept external evidence safely

## Phase 7: Mod-Specific Adapters
Priority: high

Only after Phases 1 through 6 are real should the project invest heavily in dedicated adapters for ecosystems such as:

- Create
- Mekanism
- AE2
- Refined Storage
- Botania
- Ars Nouveau
- Immersive Engineering
- Farmer's Delight

## Phase 8: Distribution And Regression Matrix
Priority: high

Build:
- content-addressed pack artifacts
- pack manifest generation
- Geyser pack registration flow
- optional remote hosting support
- compatibility regression matrix
- performance regression matrix
- pack regression matrix

Exit criteria:
- Bedrock resource delivery is reproducible
- compatibility breadth and performance regressions are measurable in CI

## Current-State Execution Roadmap

Use the live Hydraulic repo and its runtime artifacts as the control document for execution order.

### Locked execution order

1. replace duplicated discovery with a universal index
2. replace full-tree pack UUID hashing with incremental persistent fingerprints
3. add a first-class artifact cache and precise conversion keys
4. move model loading to lazy indexed access, then widen that approach to the remaining resource categories; startup lookup and custom model conversion now use indexed model paths, selective texture extraction now loads file-backed textures through the index, and the first shared texture-resolution cache plus dependency-graph slices are now shipped, but broader resource loading is still eager
5. compile compatibility decisions into runtime plans and direct dispatch tables
6. deepen the resource IR, model dependency graph, and texture dependency graph
7. compile block-state and metadata-heavy paths into compact runtime structures
8. widen generic bridges for block entities, machines, fluids, energy, and entity actions; menu click/button routing is implemented for compiled action contracts, while richer widgets and physical-client proof remain open
9. publish and ingest versioned Bedrock addon corpus snapshots as offline evidence; keep GitHub as the primary inspectable source, keep CurseForge as discovery metadata unless linked source exists, and keep raw corpus access out of hot runtime paths
10. add knowledge and classifier layers after generalized bridge seams exist
11. add mod-specific adapters after the substrate is stable
12. expand pack delivery and CI-scale compatibility matrices

NetherNet protocol work is an enabling slice across steps 8 and 12, not a new
Bedrock behavior architecture. A future live transport must first prove the
WebRTC/signaling/session boundary independently, then bind its reliable data
channel to Geyser's actual session lifecycle and complete the manual
`CLIENT_OBSERVED` validation ladder. The current codec must remain inert until
those prerequisites exist.

This order is intentional. Do not start writing dozens of adapters before the universal index, cache, and compiled runtime plan exist.

The Bedrock addon corpus can begin earlier as an external schema and harvesting effort, but it must not short-circuit the execution order above by becoming a direct runtime dependency.

### Current execution anchors
- `shared/src/main/java/org/geysermc/hydraulic/pack/PackManager.java`
- `shared/src/main/java/org/geysermc/hydraulic/pack/ModResourceIndex.java`
- `shared/src/main/java/org/geysermc/hydraulic/pack/PackUtil.java`
- `shared/src/main/java/org/geysermc/hydraulic/pack/PerformanceReportTracker.java`
- `shared/src/main/java/org/geysermc/hydraulic/metadata/BlockMapping.java`
- `shared/src/main/java/org/geysermc/hydraulic/metadata/BlockStateRule.java`
- `shared/src/main/java/org/geysermc/hydraulic/metadata/MetadataLoader.java`
- `shared/src/main/java/org/geysermc/hydraulic/compat/MappingResolver.java`
- `shared/src/main/java/org/geysermc/hydraulic/compat/adapter/CapabilityAdapterRegistry.java`
- `shared/src/main/java/org/geysermc/hydraulic/compat/runtime/MenuPatchTranslatorFactory.java`
- `shared/src/main/java/org/geysermc/hydraulic/compat/runtime/BlockEntityPatchTranslatorFactory.java`

### Current execution rules
- preserve compatibility semantics first, then optimize
- use the live repo and runtime artifacts as truth over older prose
- update `README.md` and this plan in the same change set as any user-visible capability, runtime boundary, report, validation result, or supported-workflow change; verify each claim against code, focused tests, or recorded runtime artifacts before commit
- replace duplicated discovery before widening compatibility breadth
- compile flexible metadata before using it in runtime paths
- treat the Bedrock addon corpus as offline evidence, not as a live runtime dependency
- keep GitHub first-class for inspectable sources and treat CurseForge as discovery metadata unless a source repo is present
- never treat downloadable or free addon pages as proof of reuse rights
- treat Geyser runtime and pack-delivery constraints as hard architectural inputs
- keep README and this plan aligned with what was actually validated

## Prioritized Next Milestones

1. **P0.1 Fluid actions:** route Bedrock fill/drain intent through a compiled contract into a discovered
  live tank, commit atomically on the Java thread, persist identity and amount, and synchronize the
  authoritative result.
2. **P0.2 Energy actions/state:** add bounded receive/extract actions and a concrete client-visible state
  projection without pretending Bedrock has a native universal energy system.
3. **P0.3 Entity actions:** complete use, attack, mount, and dismount routing through live Java entities.
4. **P0.4 Persistence:** execute save, shutdown, restart, rebind, and state comparison for every promoted
  mutable fixture and generic runtime contract.
5. **P0.5 Automation lifecycle:** verify chunk unload/reload, block break/replacement, dimension changes,
  disconnect, and restart with no stale binding, duplication, loss, or cross-session delivery.
6. **P0.6 Pack remediation:** classify every remaining runtime failure as a generic generator defect,
  adapter requirement, or explicit unsupported result and resolve all release-blocking defects.
7. **P1 Physical Bedrock:** collect manual E1-E10 evidence for each promoted round trip.
8. **P1 Real mods:** validate Create first, then expand the evidence-backed ecosystem matrix.

Each milestone must answer one architectural question: can the same compiled generic contract operate a
newly discovered runtime object without checking for the fixture or mod identifier? If not, the generic
seam remains the work item. Additional static metadata or report polish is not a substitute for a failing
end-to-end contract.

The indexed model-conversion, texture/invalidation, compatibility-cache, direct dispatch, recipe IR,
live binding, menu transaction, transfer/transaction, machine processing, automation, traceability, and
Geyser handoff slices are shipped at their recorded evidence levels. None of them promote physical-client
or arbitrary-mod completion without the remaining gates above.

Hard runtime rule: no machine may be marked `EXECUTABLE` merely because it can execute an item transaction. A machine is `EXECUTABLE` only when every resource required by its compiled processing contract has a verified runtime execution path, including atomic failure handling and state-change propagation.

## What Not To Do
- Do not keep extending `BlockStateRule` with every future concern.
- Do not leave multiple subsystems independently walking the same mod roots.
- Do not keep full-tree hashing every mod on ordinary startup.
- Do not keep the entire modpack parsed and resident by default.
- Do not keep compatibility reasoning in hot runtime paths.
- Do not equate asset presence with gameplay compatibility.
- Do not let a weighted score hide missing critical behavior.
- Do not make menu, entity, or fluid metadata look complete before runtime bridges exist.
- Do not pivot into hand-authoring hundreds of per-mod JSON files as the main strategy.
- Do not let generated metadata overwrite server-owner or user intent.
- Do not let one failed asset or unsupported mechanic abort the whole modpack conversion.
- Do not make Hydraulic startup depend on crawling GitHub, CurseForge, or any other remote addon source.
- Do not treat free download pages as proof that code or assets may be incorporated or redistributed.
- Do not recreate Geyser's base knowledge when Hydraulic can consume it.
- Do not describe Hydraulic as a direct Forge jar to Fabric jar converter.
- Do not attempt full Forge or NeoForge API emulation inside Hydraulic's Bedrock compatibility layer.
- Do not make Bedrock behavior-pack delivery the foundational assumption for mod behavior.
- Do not treat decompiled third-party code as a normal implementation input.

## Universal 10-15 Systems Architecture & Master Roadmap

### System 1: Universal Runtime Capability Layer
The fundamental bridge between arbitrary Java objects and Bedrock operations must follow a normalized capability pipeline:

```text
Java Object
     ↓
Capability Discovery (Indexed facts + reflection + registry inspection)
     ↓
Normalized Capability Model (Forge / Fabric Transfer / Botarium / Native)
     ↓
Runtime Binding (Direct typed dispatch resolution)
     ↓
Hydraulic Runtime Bridge (Item, Fluid, Energy, Menu, Block Entity)
     ↓
Bedrock Operation (Packet translation, UI presentation, authoritative sync)
```

Normalized capability hierarchy:
```text
Machine / Block / Entity
 ├── Inventory (input slots, output slots, fuel, upgrade, catalyst, sided rules)
 ├── Item Transfer (insert, extract, count limits, stack preservation, simulation)
 ├── Fluid Transfer (fill, drain, tanks, capacity, fluid identity, temperature/viscosity)
 ├── Energy (receive, extract, capacity, transfer rates, storage vs generation vs consumption)
 ├── Processing (recipe identity, input consumption, output generation, progress ticks, catalysts)
 └── State (active/idle, progress scalar, configuration modes, redstone response)
```

### System 2: Universal Machine Runtime
Generic machine abstraction avoiding per-mod code duplication:
```text
MachineRuntime
 ├── identity (Namespaced Java identifier + blockstate metadata)
 ├── position (ServerLevel + BlockPos)
 ├── state (Active, Idle, Blocked, Error, Powered)
 ├── inventory (Input/Output/Fuel slot mapping & transaction borders)
 ├── fluids (Input/Output tanks with volume and identity gates)
 ├── energy (Internal buffer, transfer caps, required energy per tick)
 ├── processing (Active recipe match, tick progression, completion emission)
 ├── automation (Sided capability exposure to external pipes/hoppers)
 ├── redstone (High, Low, Ignored, Pulsed operating modes)
 ├── interaction (Bedrock block-use insertion/extraction & GUI dispatch)
 └── synchronization (Dirty-state capture -> SyncPlanner -> Bedrock packet handoff)
```
Target mod reduction:
- **Create**: Mechanical Press → Item Input (press bed), Kinetic Requirement (power/energy equivalent), Item Output, Recipe (pressing).
- **Mekanism**: Enrichment Chamber → Item Input, Energy Input, Item Output, Recipe (enriching).
- **Thermal**: Pulverizer → Item Input, Energy Input, Primary/Secondary Item Output, Recipe (pulverizing).
- **Immersive Engineering**: Crusher → Multiblock Input, Energy Input, Item Outputs, Recipe (crushing).

### System 3: Universal Automation & Transfer
Normalized transfer contract across items, fluids, and energy:
- **Operations**: `insert`, `extract`, `simulate`, `canInsert`, `canExtract`.
- **Context**: `side` (Direction), `slot` / `tank` index, `filter` predicate, `priority` integer, `limit` transfer budget.
- **Participants**: Pipes, conveyors, hoppers, machines, storage drawers, fluid tanks, energy cables, and logistics networks all participate through the single `ResourceAutomationAccess` substrate.

### System 4: Universal Fluid Runtime
First-class fluid capability foundation:
- **Core Types**: `FluidIdentity`, `FluidStack`, `FluidContainer`, `FluidTank`, `FluidTransfer`, `FluidCapability`, `FluidHandler`.
- **Operations**: `fill(FluidStack, Simulation)`, `drain(FluidStack/Amount, Simulation)`, `capacity()`, `amount()`, `fluid()`, `canFill(FluidIdentity)`, `canDrain(FluidIdentity)`.
- **Transfer Scenarios**:
  - Container $\leftrightarrow$ Tank: bucket $\to$ tank, tank $\to$ bucket, portable canister $\to$ tank.
  - Machine Fluid I/O: input tank filling, output tank evacuation, recipe fluid consumption/generation.
  - Automation Transfer: pipe $\to$ tank, tank $\to$ pipe, machine $\to$ tank.
  - World Interaction: Fallback to approximated source/flow block presentations without breaking server-side fluid physics.

### System 5: Universal Energy Runtime
Normalization of disparate power systems (Forge Energy / Redstone Flux / Tech Reborn Energy / Botania Mana equivalents):
- **Core Abstraction**: `EnergyStorage` (`capacity`, `stored`, `maxReceive`, `maxExtract`, `receive`, `extract`).
- **Network Classification**:
  - `EnergyStorage`: Local battery, capacitor, machine buffer.
  - `EnergyTransfer`: Conductor, cable, wireless transceiver.
  - `EnergyGeneration`: Generator, dynamo, solar panel, passive thermal source.
  - `EnergyConsumption`: Active machine cycle, powered tool charging, beacon effect.

### System 6: Generic Block Entity Runtime
Continuous synchronization between Java block entity state and Bedrock client view:
```text
Java BlockEntity
       ↓
State Discovery (NBT, data components, attached capabilities, field trackers)
       ↓
Normalized BlockEntity State (Persistent fields, inventory, fluids, energy, progress, facing)
       ↓
Runtime Synchronization (Diff detection -> SyncPlanner -> Coalesced batches)
       ↓
Bedrock Representation (Block entity NBT tags, container data, animation state)
```

### System 7: Generic Menu Translation (Menu IR)
Translation of arbitrary Java `ScreenHandler` / `AbstractContainerMenu` hierarchies into Bedrock-compatible UIs:
```text
Menu IR
 ├── slots (index, x, y, stack, interactable)
 ├── slot types (INPUT, OUTPUT, FUEL, UPGRADE, STORAGE, CRAFTING_IN, CRAFTING_OUT)
 ├── player inventory (hotbar, main inventory, offhand, armor)
 ├── transfer rules (shift-click target preferences, insertion validations)
 ├── buttons & widgets (toggle buttons, mode selectors, page tabs)
 ├── properties (progress scalar, energy bar, fluid level, heat gauge)
 └── actions (serverbound button clicks, mode toggles, craft triggers)
```
Workflow:
```text
Java ScreenHandler / Menu ──> Menu Analyzer ──> Menu IR ──> Bedrock Form / Container Screen
```

### System 8: Entity Runtime & Deep Interaction
Complete lifecycle and interaction mapping for modded mobs, vehicles, machines-as-entities, and projectiles:
- Lifecycle: `spawn`, `despawn`, `position`, `rotation`, `velocity`, `metadata`, `attributes`, `health`, `equipment`.
- Interaction Loop:
```text
Bedrock right-click / attack
     ↓
Geyser translation / Hydraulic Action Router
     ↓
Authoritative Java interaction dispatch
     ↓
Server-side state mutation (inventory change, mount, damage, effect)
     ↓
Synchronize updated entity state to Bedrock client
```

### System 9: Custom Networking & Synchronization
Unified bidirectional synchronization pipeline:
- **Server $\to$ Client**: Authoritative Java runtime state $\to$ Change Detection (`DirtyStateTracker`) $\to$ Normalized `StateChangeSet` $\to$ `SyncPlanner` $\to$ `SyncEncoder` $\to$ `GeyserSyncTransport` (`InventorySlotPacket`, `ContainerSetDataPacket`, custom telemetry).
- **Client $\to$ Server**: Bedrock interaction packet $\to$ `BedrockRuntimeActionRouter` $\to$ Authoritative server execution (`ServerPlayerGameModeMixin` / direct target resolution) $\to$ State mutation $\to$ Automatic synchronization response.

### System 10: Bedrock Semantic Rendering Translation
Multi-stage visual asset and model classification:
- **Input**: Java blockstate JSON, multipart models, display transforms, entity renderers, texture mcmeta animations, tint rules.
- **Classification**:
  - `NATIVE`: Direct vanilla block/item mapping.
  - `AUTOMATIC`: Fully generated Bedrock geometry, material, and attachable.
  - `APPROXIMATED`: Simplified geometry (e.g. 2D sprite fallback or closest 3D archetype) with explicit degradation tracking.
  - `VISUAL_ONLY`: Static decorative model without interactive/ticking behavior.
  - `UNSUPPORTED`: Custom vertex/shader pipelines that cannot be represented in Bedrock pack schemas.

### System 11: Universal Recipe Discovery & Normalization
Extraction and normalization across diverse recipe providers:
- **Sources**: Vanilla recipe registry, custom JSON datapack serializers, Forge/Fabric recipe types, hardcoded machine managers.
- **Normalized Recipe IR**:
  - Inputs (Item stacks with tags/counts, Fluid stacks, required Energy per tick).
  - Outputs (Primary items, secondary/byproduct items with chance scalars, output fluids).
  - Processing Conditions (Processing time in ticks, catalyst requirements, heat/kinetic minimums).

### System 12: Mod Discovery & Capability Detection
Fingerprinting and automated capability classification:
```text
Discover Mod ──> Index Resources/Registries ──> Inspect Capabilities ──> Classify Capabilities ──> Match Generic Bridges ──> Bind Fallback Adapters ──> Compile Runtime Dispatch Plan
```

### System 13: Thin Adapters Hierarchy
Standardized escalation path prioritizing generic automation over hardcoded per-mod logic:
```text
1. Vanilla / Geyser Native
          ↓
2. Automatic Discovery & Compilation
          ↓
3. Generic Capability Bridge (Hydraulic Substrate)
          ↓
4. Metadata Overrides & Patches
          ↓
5. Generic Emulation (Approximation)
          ↓
6. Mod-Specific Thin Adapter
          ↓
7. Explicit Unsupported Classification (No Silent Failures)
```

### System 14: Multi-Level Real Bedrock Validation Matrix
Five-level testing pyramid ensuring end-to-end correctness:
- **Level 1 — Unit**: Resource indexing, JSON parsing, IR compilation, transaction compensation, recipe validation.
- **Level 2 — Java Integration**: Fabric server bootstrap, mod registry resolution, Geyser startup, pack generation.
- **Level 3 — Packet & Runtime**: Injected Geyser packet transport, sync encoding, trace ID propagation, mixin interception.
- **Level 4 — Real Bedrock Client (Manual Gate)**: Physical Windows/Android/iOS client connection, block placement, interaction, GUI manipulation, item extraction, visual observation (`CLIENT_OBSERVED`).
- **Level 5 — Regression Modpacks**: Automated pack conversion and report verification across heavyweight mod fixtures (Create, Mekanism, Thermal, AE2, Farmer's Delight).

#### Physical Bedrock Client Environment Isolation & Loopback Setup

Because Phlodgate targets standard Geyser translation, a custom or headless terminal client is not used—the server is the terminal. The exact client testing path depends on the environment setup:

1. **Option 1: Standard Minecraft for Windows (Same Machine Testing)**
   - *Constraint:* Windows AppX packages (UWP apps) run inside an AppContainer network sandbox and cannot connect to `localhost` / `127.0.0.1` by default.
   - *Workaround:* Run the loopback exemption command in an Administrator PowerShell window:
     ```powershell
     CheckNetIsolation LoopbackExempt -a -n="Microsoft.MinecraftUWP_8wekyb3d8bbwe"
     ```
   - Connect to `127.0.0.1:19132` in the Minecraft Friends/Servers list.

2. **Option 2: Minecraft Preview (Future Schema Validation)**
   - *Purpose:* Validates custom addon geometry, blocks, and V2 metadata against active store schema changes before forced retail updates.
   - *Workaround:* Apply the loopback exemption command for the Windows Beta/Preview package:
     ```powershell
     CheckNetIsolation LoopbackExempt -a -n="Microsoft.MinecraftWindowsBeta_8wekyb3d8bbwe"
     ```

3. **Option 3: Mobile Clients (Android / iOS LAN Testing)**
   - *Purpose:* Verifies touch interactions, UI layout scaling, and `PaginatedMenuForm` arrays without screen overflow.
   - Connect client to the development PC's local LAN IPv4 address (e.g. `192.168.1.XX:19132`) on the same Wi-Fi network.

#### Bedrock E1–E10 Observation Evidence Ladder
- **E1 — Connection**: Session handshake, Geyser UDP 19132 connection, and `phlodgate_bridge` scoreboard objective presence.
- **E2 — Pack Delivery**: Client requests, downloads, acknowledges, and activates generated `.mcpack` without client-side JSON schema rejection.
- **E3 — Rendering**: Visual presentation of custom blocks, items, attachables, particles, and kinetic animations.
- **E4 — Block Interaction**: Authoritative server-thread execution of right-click (`insert_held_item`) and sneak-click (`extract_item`) actions.
- **E5 — Menu Interaction**: Open translated menu, manipulate slots, toggle buttons, and verify canonical full-state resync.
- **E6 — Inventory Transfer**: Sided automation, container slot bounds, and transactional stack preservation.
- **E7 — Machine Execution**: Dynamic recipe matching, progressive progress scalar updates, fluid/energy consumption, and output emission.
- **E8 — Bidirectional Synchronization**: Java $\leftrightarrow$ Bedrock real-time dirty-state coalescing and packet delivery without ghost items or stale properties.
- **E9 — Persistence & Restart**: World save, server shutdown, server restart, re-connection, and state rehydration verification.
- **E10 — Real Third-Party Mod Ecosystem**: In-world validation starting with Create (presses, mixers, kinetics), followed by Mekanism, Thermal, and AE2.

### System 15: Capability Completeness Evaluation Framework
Per-object granular compliance verification matrix preventing false-positive compatibility claims:
```text
OBJECT: <namespace>:<identifier>

CONTENT:        [PASS | FAIL | N/A] (Block, Item, Entity, Fluid registration)
PRESENTATION:   [PASS | FAIL | N/A] (Model geometry, Textures, Particles, Animations)
STATE:          [PASS | FAIL | N/A] (Block states, NBT properties, Tag synchronization)
INTERACTION:    [PASS | FAIL | N/A] (Placement, Breaking, Right-click block use, Sneak-click extract)
BEHAVIOR:       [PASS | FAIL | N/A] (Inventory, Item I/O, Fluid I/O, Energy, Processing, Automation)
NETWORK:        [PASS | FAIL | N/A] (Packet synchronization, Custom telemetry, Action routing)
MENU:           [PASS | FAIL | N/A] (Container opening, Slot bounds, Synced properties, Action buttons)

OVERALL STATUS: [FULL_SUPPORT | PARTIAL_SUPPORT | VISUAL_ONLY | UNSUPPORTED]
```

---

## Mod Compatibility & Implementation Report

### Local Bedrock Play-Test Report (2026-09-14)

#### Implementation Summary
- Implemented negative-destroy-time handling in custom block registration so unbreakable Java blocks no
  longer abort Geyser custom block population.
- Implemented an unmapped Java block-state fallback at the Geyser `BlockMappings` boundary. Each missing
  Java block-state ID is logged once and translated to Bedrock air instead of throwing during chunk
  translation.
- Implemented an unmapped Java item fallback at the Geyser `GeyserItemStack.asItem()` boundary. Each Java
  item ID outside Geyser's vanilla/custom item table is logged once and translated to Bedrock air instead
  of aborting inventory content translation.
- Applied a local validation profile for the Bedrock client run: creative, forced gamemode, offline Java
  auth, Geyser offline auth, disabled Geyser custom content/resource-pack forcing, and a fresh flat
  peaceful no-structure world. The earlier normal dev worlds were preserved under timestamped backup
  directories in the ignored `fabric/run/` tree.

#### Verification Performed
- Refreshed Gradle snapshot dependencies and rebuilt Fabric classes under Java 25.
- Ran focused compile validation after each production-code change: `:shared:compileJava :fabric:classes`
  passed after the final block and item fallback changes.
- Ran repeated live `:fabric:runServer` sessions. Geyser reached UDP `19132`, the official Windows Bedrock
  client connected as `SmokyDaStona`, Geyser established a Java downstream session, and Minecraft logged
  the player joining the Java server.
- Observed that the original Geyser chunk translator `NullPointerException` for missing block mappings was
  replaced by the explicit Hydraulic fallback log. Observed that the original `ClientboundContainerSetContentPacket`
  `IndexOutOfBoundsException` from `GeyserItemStack.asItem()` was replaced by the explicit Hydraulic item
  fallback log.

#### Residual Failures And Security/Quality Review
- The Bedrock client still did not produce clean `CLIENT_OBSERVED` gameplay evidence. After the fallback
  hardening, Geyser continued to log downstream metadata decode errors such as `Index ... out of bounds for
  length 158`, which is consistent with unsupported modded entity/data metadata entering Geyser's current
  protocol translators.
- The new fallbacks are deliberately fail-soft and lossy. They prevent server-side crashes and preserve a
  test connection path, but they also render unsupported Java content as air. Reports and release claims
  must treat these as diagnostics and degradation, never as support for the affected content.
- The attached Minecraft Bedrock data folder includes installed marketplace/cache content and an installed
  PhlodgateA pack whose manifest license is `All Rights Reserved`. It was used only as local evidence and
  was not copied into Hydraulic. Maintained, testable source remains the sibling `Plodgate_Add-on` project.
- The active Geyser version in validation was `2.11.2-SNAPSHOT` on Minecraft `26.2`; every compatibility
  result is scoped to that version pair.

#### Prioritized Next Steps
1. Add a targeted Geyser entity metadata guard or translator diagnostic that identifies the exact Java
   entity/metadata type causing the remaining `length 158` decode errors, then decide whether to omit,
   downgrade, or map that metadata explicitly.
2. Add report output for every block/item fallback ID so compatibility artifacts record which Java content
   was hidden as air during client validation.
3. Re-enable custom content in a controlled small fixture pack after the metadata decoder issue is isolated;
   do not use the 247-mod corpus as the first clean client-observation gate.
4. Run the sibling `Plodgate_Add-on` test/typecheck/package workflow and use it only as a manually installed
   companion validation surface; do not claim Geyser behavior-pack execution.
5. Repeat E1-E3 on the flat fixture world, then expand to E4-E8 only after downstream metadata errors are
   gone for at least one controlled fixture run.

#### Release-Readiness Criteria Summary
- `Implementation complete` is not claimed. The current change set improves failure isolation and diagnostic
  survivability, but release readiness still requires clean Bedrock client observation, no recurring Geyser
  downstream decode errors, explicit reporting for all lossy fallbacks, persistence/restart validation, and
  the E1-E10 manual attestation ladder for promoted features.

This table records evidence, not projected compatibility. `NOT ASSESSED` means no current object-level
runtime and physical-client evidence supports a compatibility classification.

| Mod ecosystem | Verified artifact evidence | Verified runtime evidence | Physical Bedrock evidence | Current classification |
| --- | --- | --- | --- | --- |
| **Hydraulic test fixtures** | Generated fixture pack previously validated | Item/fluid/energy/machine substrates and menu packet routing have focused tests; Fabric and Geyser reach readiness | None | `SERVER-VERIFIED SLICES / CLIENT UNVERIFIED` |
| **Create** | Generated pack previously validated with long-path warnings | No complete Create object contract | None | `ARTIFACT VERIFIED / RUNTIME NOT ASSESSED` |
| **Farmer's Delight** | Generated pack previously validated; specialized recipe serializer tests exist | No complete cooking-machine live-binding round trip | None | `ARTIFACT VERIFIED / RUNTIME NOT ASSESSED` |
| **Traveler's Backpack** | Generated pack previously validated | No complete backpack-specific runtime contract | None | `ARTIFACT VERIFIED / RUNTIME NOT ASSESSED` |
| **Lootr** | Generated pack previously validated | No complete per-player container behavior round trip | None | `ARTIFACT VERIFIED / RUNTIME NOT ASSESSED` |
| **Citadel / Apollib** | Schema fallback and generated-pack startup were previously validated | No broad entity-behavior compatibility proof | None | `PRESENTATION-ONLY EVIDENCE` |
| **Mekanism** | No current active-runtime artifact proving the listed machines | Generic mixed-resource substrate only | None | `NOT ASSESSED` |
| **Thermal Series** | No current active-runtime artifact proving the listed machines | Generic mixed-resource substrate only | None | `NOT ASSESSED` |
| **Immersive Engineering** | No current active-runtime artifact proving multiblocks | Generic transfer substrate only | None | `NOT ASSESSED` |
| **Botania** | No current active-runtime artifact proving mana semantics | No verified mana normalization contract | None | `NOT ASSESSED` |
| **Applied Energistics 2** | No current active-runtime artifact proving terminal behavior | Pagination/search infrastructure is not an AE2 round trip | None | `NOT ASSESSED` |
| **Refined Storage** | No current active-runtime artifact proving grid behavior | Pagination/search infrastructure is not a Refined Storage round trip | None | `NOT ASSESSED` |
| **Storage Drawers** | No current active-runtime artifact proving drawer behavior | Generic inventory binding is not drawer-specific proof | None | `NOT ASSESSED` |

No ecosystem is currently classified `NATIVE`, `AUTOMATIC`, or `ADAPTED` end to end. Those labels require
object-level contract evidence plus physical Bedrock observation for every critical capability.

---

## Prioritized Implementation Roadmap (Phases 1-10)

1. **Phase 1: Runtime Foundation & Capability IR**
   - Universal Capability IR & typed capability schemas.
   - Capability discovery & reflection engine for Forge/Fabric/Botarium.
   - Dynamic capability binding & runtime dispatch registry.
   - Automated Capability Completeness Evaluator reporting.

2. **Phase 2: Inventory, Item Transfer & Sided Automation**
   - Universal inventory abstraction & multi-slot transactional safety.
   - Sided insertion/extraction rules, stack preservation, and simulation.
   - Filtered transfers, priorities, and pipe/conveyor network routing.

3. **Phase 3: Universal Machine Processing Engine**
   - Generic Machine IR (States: Idle, Running, Blocked, Powered).
   - Dynamic recipe matcher & multi-input/output processing cycles.
   - Machine progress tracking, tick-driven state updates, and dirty-state broadcasting.

4. **Phase 4: Universal Fluid Runtime & Tank Transfer**
   - Fluid IR, normalized FluidStack, and multi-tank capacity validation.
   - Container $\leftrightarrow$ Tank bidirectional transfers (bucket, canister, tank).
   - Machine fluid I/O integration and world fluid presentation fallbacks.

5. **Phase 5: Universal Energy & Power Networks**
   - Normalized EnergyStorage IR (FE/RF/TechReborn/Mana).
   - Storage, generation, consumption, and rate-limiting contracts.
   - Power network distribution and battery buffer management.

6. **Phase 6: Generic Menu Translation (Menu IR) & UI Automation**
   - Menu IR compiler mapping Java ScreenHandlers to Bedrock container archetypes.
   - Progress bar, energy meter, and fluid gauge property synchronization.
   - Action buttons, mode selectors, and serverbound button transaction routing.

7. **Phase 7: Deep Entity Runtime & Complex Interactions**
   - Entity state IR, custom attributes, equipment, and metadata syncing.
   - Bedrock right-click/attack action routing to authoritative Java handlers.
   - Rideable entities, vehicle physics synchronization, and animation states.

8. **Phase 8: Network Synchronization & Bidirectional Action Routing**
   - Server $\to$ Client change tracking with dirty-state coalescing and batching.
   - Client $\to$ Server action translation through non-intrusive server-thread mixins.
   - Real-time container property & inventory slot packet delivery.

9. **Phase 9: Automated Mod Fingerprinting & Plan Optimization**
   - Machine-learning/heuristic pattern classification for unmapped mods.
   - Capability-based adapter ranking and automatic graceful degradation.
   - Persistent conversion key optimization and cross-mod dependency pruning.

10. **Phase 10: Multi-Level Validation & Modpack Regression Suite**
    - Automated Level 1-3 test harness execution in CI.
    - Level 4 Bedrock client manual verification protocol.
    - Level 5 multi-modpack regression corpus (Create + Mekanism + Thermal + AE2).

---

## Release-Readiness Criteria & Verification Protocol

Before declaring any compatibility feature or release candidate complete, the following gates must pass unconditionally:

1. **Zero-Tolerance Policy for Placeholder Logic**:
   - `0` `TODO`, `FIXME`, empty methods, or mock implementations in production code paths.
   - Every declared capability must have a concrete, executable Java runtime operation.
   - No visual-only representation may be reported as gameplay-compatible.

2. **Fail-Closed Security & Stability Gate**:
   - Malformed metadata, missing textures, invalid recipes, or unmapped capabilities must degrade gracefully with structured warnings without crashing the server thread.
   - All client-originated Bedrock interaction packets must be validated on the Java server thread before mutating authoritative game state.

3. **Build & Test Verification**:
   - Clean compilation under Java 25 and Minecraft 26.2 across all active modules (`:shared`, `:fabric`, `:test`).
   - 100% pass rate across the JUnit test suite in `:shared:test` and `:fabric:test`.
   - Zero compilation warnings in newly touched compatibility and runtime subsystems.

4. **Performance & Memory Boundaries**:
   - Runtime dispatch table lookups must execute in $\mathcal{O}(1)$ time.
   - Model, texture, and index caches must be bounded with strict LRU eviction policies.
   - Zero memory leaks across repeated modpack reload or pack conversion runs.

5. **Attestation & Provenance Clarity**:
   - `TRANSPORT_HANDOFF_VERIFIED` must only be reported when concrete packets are handed to Geyser's transport boundary.
   - `CLIENT_OBSERVED` must remain a strictly manual, human-verified attestation.

---

## Bottom Line

The fork is pointed in the right direction, but the main missing piece is now execution architecture rather than conceptual vocabulary.

The most important correction is not "add more metadata".
It is:

```text
Universal Index
  -> Compatibility IR
  -> Compiled Compatibility Plan
  -> Runtime Dispatch Table
  -> Resource Pack + Hydraulic Runtime Bridges
  -> Validation + Content-Addressed Cache
```

That architecture matches the live repository, matches Geyser's actual strengths and limits, and keeps the project on the only realistic scaling path:

```text
automatic
  -> metadata patch
  -> generic capability adapter
  -> mod-specific adapter
  -> unsupported
```

The true skeleton-key goal is not to turn every Java mod into an independent Bedrock add-on.
It is to let Bedrock clients participate in a Java mod ecosystem while the Java server remains authoritative and Hydraulic supplies the missing visual, semantic, and interaction bridge layers.

The Bedrock addon corpus supports that goal by supplying reusable evidence, capability patterns, licensing-aware source intelligence, and adapter-prioritization data without replacing Hydraulic's Java-side authority or its compiled runtime bridge architecture.
