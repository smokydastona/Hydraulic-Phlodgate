# External Research And Integration Report

Date: 2026-09-13
Scope: source review for Hydraulic-Phlodgate

## Executive Result

The seven supplied repositories do not form a drop-in implementation library for Hydraulic. They fall into four groups:

- curated catalogs: `awesome-fabric`, `awesome-minecraft`, and `awesome-minecraft-bedrock`
- Bedrock authoring and validation tools: `minecraft-creator-tools` and `bridge-core/editor`
- Bedrock Script API examples and references: `JaylyDev/ScriptAPI`
- a BDS and Discord bridge: `InnateAlpaca/BedrockBridge`

No third-party source code, generated asset, executable, or dependency was copied into Hydraulic. The safe integration surface is offline research metadata and validation guidance. Hydraulic runtime behavior remains Java-server authoritative and continues to consume only typed compiled plans.

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

## Security And Licensing Review

- Do not copy source files, pack assets, sample scripts, schemas, or binaries from these repositories into Hydraulic without a separately recorded admissibility decision.
- A permissive license on a catalog does not relicense projects listed by that catalog.
- MIT and CC0 sources still require correct attribution where applicable; Jayly's repository calls out contributor attribution in individual script files.
- GPL-3.0 editor code is not an acceptable embedded runtime dependency for the current Hydraulic distribution model.
- Mojang Creator Tools code is MIT, but its bundled vanilla assets have separate Minecraft EULA terms.
- Do not accept remote URLs, downloaded archives, npm packages, or scripts as runtime inputs without bounded size, path, checksum, and provenance validation.
- Never place Discord tokens, Bedrock permissions files, or server credentials in Hydraulic configuration or reports.

## Implementation Decision

The implemented change for this research pass is intentionally limited to repository documentation and architecture constraints. No runtime dependency is justified by the supplied evidence. The existing corpus loader remains the correct ingestion boundary: local snapshots only, explicit provenance, admissibility checks, deterministic precedence, and no raw corpus access from hot runtime paths.

The next executable integration gate is optional operator/CI validation of generated Bedrock projects with the installed Creator Tools CLI. It must remain outside Hydraulic startup and must report its own tool version, input path, result, and failure reason. The existing pack validation report remains authoritative when that external tool is unavailable.

## Verification Sources

- GitHub repository pages and license files for all seven supplied repositories, accessed 2026-09-13.
- [Minecraft Creator Tools overview](https://learn.microsoft.com/en-us/minecraft/creator/documents/mctoolsoverview), accessed 2026-09-13.
- [Minecraft Script API reference](https://learn.microsoft.com/en-us/minecraft/creator/scriptapi/), accessed 2026-09-13.
