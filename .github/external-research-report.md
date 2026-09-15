# External Research And Integration Report

Date: 2026-09-15
Scope: source review for Hydraulic-Phlodgate, including `Broadcaster-master.zip` and the seven additional repositories supplied for Bedrock/entity research

## Executive Result

The supplied repositories and archive do not form a drop-in implementation library for Hydraulic. They fall into five groups:

- curated catalogs: `awesome-fabric`, `awesome-minecraft`, and `awesome-minecraft-bedrock`
- Bedrock authoring and validation tools: `minecraft-creator-tools` and `bridge-core/editor`
- Bedrock Script API examples and references: `JaylyDev/ScriptAPI`
- a BDS and Discord bridge: `InnateAlpaca/BedrockBridge`
- an Xbox Live presence broadcaster: `Broadcaster-master.zip` / MCXboxBroadcast

No third-party source code, generated asset, executable, or dependency was copied into Hydraulic. The safe integration surface is offline research metadata and validation guidance. Hydraulic runtime behavior remains Java-server authoritative and continues to consume only typed compiled plans.

## Additional Repository Disposition (2026-09-15)

| Source | Verified useful surface | License / risk | Hydraulic disposition |
| --- | --- | --- | --- |
| [PowerNukkitX](https://github.com/PowerNukkitX/PowerNukkitX) | Java Bedrock-server architecture, custom item/block/entity registration, vanilla-like AI, containers, commands, and world generation | LGPL-3.0; an alternative Bedrock server, not a Java/Geyser bridge | Reference only for Bedrock-native capability vocabulary and server-side lifecycle ideas. Do not link, shade, or replace Geyser/Fabric with it. |
| [AzureLib](https://github.com/AzureDoom/AzureLib) | Bedrock-model-oriented Java animation concepts for item, block, and entity presentation; keyframe/easing/event concepts | MIT repository metadata, but the repository advertises additional licenses and contains artistic/model material requiring separate review | Use only as offline presentation research. Do not copy renderer code, models, textures, or secondary-licensed material. |
| [ExtraBiomes](https://github.com/finleyaubin/ExtraBiomes) | Paired Java/Bedrock content organization, biome/structure/entity/block pack breadth, and cross-edition content parity cases | MIT repository license; artistic assets and third-party credits still require provenance review | Use as a regression-corpus candidate and schema/reference case. Do not import its assets or claim Java-to-Bedrock parity without object-level evidence. |
| [minecraft-bedrock-edition-vanilla-pack](https://github.com/hexagonlionfirewall/minecraft-bedrock-edition-vanilla-pack) | No trustworthy Hydraulic implementation surface identified | Security red flags: password-protected executable download, instructions to disable Windows Defender, encrypted binaries/configuration, and credential-like files | Explicitly rejected. Do not download, execute, vendor, inspect as an asset source, or place its URLs/binaries in reports or runtime configuration. |
| [mcpe-bedrock-script](https://github.com/valeria-vasquez-87/mcpe-bedrock-script) | README-level examples of Script API entity, block, item, UI, event, and scheduling vocabulary | No source tree or published license evidence was found; README redirects to an external download | Documentation-only, low-confidence reference. No code, script, package, or external download is admissible. |
| [BedrockMotion](https://github.com/EaseCation/BedrockMotion) | Platform-independent Bedrock animation pipeline: bone targets, keyframe interpolation, animation controllers, render controllers, MoLang, and pack parsing | GPL-3.0 | Strong architecture reference for a future isolated animation subsystem, but prohibited as embedded/shaded runtime code under the current distribution model. No dependency added. |
| [GeyserDisplayEntity](https://github.com/GeyserExtensionists/GeyserDisplayEntity) | Geyser extension seam for display entities and item-display mappings; useful for identifying Bedrock transform/spawn synchronization concerns | AGPL-3.0; README credits code from a Geyser display-entity branch | Reference-only. Do not copy, link, shade, or derive a Hydraulic runtime bridge from its code. Any display-entity implementation must use independently authored code and current Geyser APIs. |

### Security disposition

The vanilla-pack repository is not treated as a normal open-source content source. Its README
explicitly instructs users to disable Windows Defender and execute a password-protected installer,
while the repository listing includes encrypted binaries and credential-like files. Hydraulic does
not need any of those files for pack conversion or validation, so no local download or execution is
justified.

The GPL-3.0 and AGPL-3.0 projects remain useful for architectural understanding only. A license
does not make source compatible with Hydraulic's current distribution model, and no copied snippets,
generated derivatives, shaded jars, or assets from them are admissible.

## Broadcaster Archive Audit

The archive contains 96 files: 67 Java sources, 3 Kotlin sources, Gradle Kotlin build logic, one bundled JAR, and no Hydraulic-compatible resource or compatibility IR. The project is `MCXboxBroadcast`, licensed GPL-3.0, and its stated purpose is to advertise an existing Geyser/Bedrock server as a joinable Xbox Live session.

Its main surfaces are:

- `MCXboxBroadcastExtension`: Geyser lifecycle, console commands, Bedrock ping/MOTD synchronization, and session startup/shutdown.
- `AuthManager` and `SessionManagerCore`: Microsoft/Xbox authentication, token refresh, session creation, friend/session REST calls, and authenticated WebSocket control.
- `NetherNet*`: Xbox/Bedrock signaling, encryption, compression, packet encoding, and WebRTC transport support.
- `FileStorageManager` and `ConfigLoader`: local credential/session/config persistence and YAML configuration.
- notification, gallery, friend, and standalone bootstrap modules.

The archive's dependency surface includes Gson, Java-WebSocket, Methanol, MinecraftAuth, Bedrock protocol libraries, Netty NetherNet transport, WebRTC natives for six platforms, SQLite, and Configurate. These dependencies are unrelated to Hydraulic's conversion and runtime bridge contracts and would materially expand its authentication, native-library, and network attack surface.

The Geyser extension reads Geyser's Bedrock listener/MOTD/player-count state and publishes it to Xbox Live. It does not generate resource packs, register custom content, normalize Java mod capabilities, compile compatibility plans, or translate authoritative Java machine state. No source line in the archive provides a safe implementation for Hydraulic's universal index, corpus, runtime dispatch, or Bedrock pack pipeline.

## Source Disposition

| Source | Verified role | License observed | Hydraulic disposition |
| --- | --- | --- | --- |
| [Siphalor/awesome-fabric](https://github.com/Siphalor/awesome-fabric) | CC0 catalog of Fabric resources, libraries, and tools | CC0-1.0 | Use as a discovery index for candidate APIs and mod ecosystems. Do not treat entries as dependencies or proof of compatibility. |
| [LiteDevelopers/awesome-minecraft](https://github.com/LiteDevelopers/awesome-minecraft) | MIT catalog of Minecraft libraries, plugins, platforms, and tools | MIT | Use as a discovery index for protocol, inventory, GUI, and server references. Review each linked project's own license before use. |
| [Mojang/minecraft-creator-tools](https://github.com/Mojang/minecraft-creator-tools) | Mojang web and CLI tooling for Bedrock project creation, validation, packaging, and rendering | MIT code; `res/latest/van` assets are subject to the Minecraft EULA | Optional external validation/reference tool. Do not vendor its assets or make Hydraulic startup depend on Node.js or a network service. |
| [bridge-core/bridge.](https://github.com/bridge-core/bridge.) and [bridge-core/editor](https://github.com/bridge-core/editor) | Bedrock add-on editors with schema-aware JSON, scripting, project, and validation workflows | GPL-3.0 | Study editor-data and validation concepts only. Do not embed or link GPL application code into Hydraulic. The newer `editor` repository supersedes the old `bridge.` repository. |
| [CAIMEOX/awesome-minecraft-bedrock](https://github.com/CAIMEOX/awesome-minecraft-bedrock) | MIT Bedrock documentation, IDE, protocol, server, and authoring catalog | MIT | Use as a source-discovery index. Entries remain separately licensed and are not runtime evidence. |
| [JaylyDev/ScriptAPI](https://github.com/JaylyDev/ScriptAPI) | MIT community Bedrock Script API samples and references | MIT, with contributor attribution in source files | Use stable-branch samples as offline patterns for corpus classification and Bedrock capability evidence. Do not execute them through Java/Geyser sessions. |
| [InnateAlpaca/BedrockBridge](https://github.com/InnateAlpaca/BedrockBridge) | MIT Bedrock Dedicated Server add-on for Discord chat and commands | MIT | Not a Hydraulic dependency. Its BDS-only modules, permissions, experiments, and Discord token flow are outside Geyser's Java-server runtime. |
| `Broadcaster-master.zip` / MCXboxBroadcast | GPL-3.0 Xbox Live presence broadcaster with Geyser extension and standalone bootstrap | GPL-3.0 | Reference-only. Do not copy, link, shade, or add its source, JAR, Xbox auth, NetherNet, WebRTC, SQLite, or credential-storage code to Hydraulic. |

## Useful Technical Findings

### Catalogs

The three awesome lists are useful for candidate discovery, not implementation. They contain links and descriptions rather than stable APIs. A catalog entry must be promoted into the Hydraulic corpus only after its linked project has been inspected for version, license, source provenance, and an evidence pointer.

### Creator Tools

Minecraft Creator Tools provides a useful external quality gate for Bedrock projects. Its current documentation describes local validation and a CLI distributed as `@minecraft/creator-tools`, requiring Node.js 22 or later. This is compatible with an operator-run or CI-run validation step, but not with Hydraulic startup: generated packs, target schemas, and the Java server must remain usable without Node.js, npm, remote access, or Mojang asset redistribution.

### Script API

The official Script API surface includes world, dimension, block, entity, item, and UI modules. Jayly's repository explicitly distinguishes the stable branch from the preview-oriented main branch and documents server-only modules such as `@minecraft/server-net`. These are valid evidence sources for classifying Bedrock capabilities, but they do not establish that a behavior pack can execute inside an ordinary Geyser Java-server session. That unresolved runtime boundary remains a manual research gate, not an implementation claim.

### bridge.

The old `bridge.` repository is archived-era tooling and points to the newer `bridge-core/editor` repository. Its strongest reusable idea is schema-aware authoring and validation: structured project layouts, versioned schemas, diagnostics, and deterministic packaging. These ideas belong in offline pack validation and corpus tooling, not in the Hydraulic runtime bridge layer.

### BedrockBridge

BedrockBridge is designed for a Bedrock Dedicated Server world and uses an add-on plus Discord bot, permissions, experiments, and server-only modules. That execution model is materially different from Geyser translating a Java server. It cannot be used as evidence that Hydraulic can deploy or execute a behavior pack for a Bedrock client connected to Java.

### Broadcaster

Broadcaster is a presence and social-session product, not a compatibility product. Its Geyser integration observes listener state and schedules authenticated Xbox Live updates. That is outside Hydraulic's mission and cannot be used to justify a client-observed Bedrock behavior claim. Its README also warns that it emulates client features and may conflict with platform terms of service; Hydraulic must not silently create or manage Xbox credentials, friend relationships, or external presence sessions.

## Security And Licensing Review

- Do not copy source files, pack assets, sample scripts, schemas, or binaries from these repositories into Hydraulic without a separately recorded admissibility decision.
- A permissive license on a catalog does not relicense projects listed by that catalog.
- MIT and CC0 sources still require correct attribution where applicable; Jayly's repository calls out contributor attribution in individual script files.
- GPL-3.0 editor code is not an acceptable embedded runtime dependency for the current Hydraulic distribution model.
- Mojang Creator Tools code is MIT, but its bundled vanilla assets have separate Minecraft EULA terms.
- Do not accept remote URLs, downloaded archives, npm packages, or scripts as runtime inputs without bounded size, path, checksum, and provenance validation.
- Never place Discord tokens, Bedrock permissions files, or server credentials in Hydraulic configuration or reports.
- Never import Broadcaster's GPL-3.0 code into the current Hydraulic distribution, including through shading, a jar-in-jar dependency, copied snippets, or generated derivatives.
- Treat Xbox Live device tokens, XSTS tokens, Minecraft session tokens, WebSocket authorization headers, session dumps, and screenshots as secrets or personal data; they must never enter compatibility artifacts, corpus snapshots, logs, or test fixtures.
- Do not reproduce Broadcaster's automatic public-IP discovery pattern in Hydraulic. Any future operator-controlled endpoint integration must use explicit configuration, bounded timeouts, TLS verification, redaction, and opt-in behavior.
- Do not open NetherNet, WebRTC, or arbitrary inbound listener surfaces from Hydraulic's pack/runtime pipeline.

## Implementation Decision

The implemented change for this research pass is intentionally limited to repository documentation and architecture constraints. No runtime dependency is justified by the supplied evidence. The existing corpus loader remains the correct ingestion boundary: local snapshots only, explicit provenance, admissibility checks, deterministic precedence, and no raw corpus access from hot runtime paths.

For `Broadcaster-master.zip`, the implementation decision is explicitly **reference-only / inadmissible for runtime reuse**. The archive was extracted only into an ignored temporary audit directory outside the source tree; no archive content was copied into Hydraulic. The only useful architectural observation is that Geyser lifecycle hooks can observe Bedrock listener state, but Hydraulic already has its own Geyser lifecycle and reporting surfaces, and the Broadcaster behavior is not a compatibility capability.

The next executable integration gate is optional operator/CI validation of generated Bedrock projects with the installed Creator Tools CLI. It must remain outside Hydraulic startup and must report its own tool version, input path, result, and failure reason. The existing pack validation report remains authoritative when that external tool is unavailable.

For the seven additional repositories, the implementation decision is **offline research and
explicit non-integration**. No new dependency, runtime bridge, asset, script, executable, network
client, Bedrock behavior-pack path, or Geyser extension code was added. The existing typed corpus
and indexed presentation-profile path remain the only production integration surfaces because they
preserve provenance and fail closed without expanding Hydraulic's runtime trust boundary.

## Verification Sources

- GitHub repository pages and license files for all seven supplied repositories, accessed 2026-09-13.
- [Minecraft Creator Tools overview](https://learn.microsoft.com/en-us/minecraft/creator/documents/mctoolsoverview), accessed 2026-09-13.
- [Minecraft Script API reference](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/), accessed 2026-09-13.
- `Broadcaster-master.zip`, locally audited without execution on 2026-09-13; archive README, GPL-3.0 license, Gradle manifests, all 96 archive paths, and static security/API scans were reviewed.
