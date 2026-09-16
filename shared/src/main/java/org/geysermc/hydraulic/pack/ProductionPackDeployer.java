package org.geysermc.hydraulic.pack;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Stages and packages generated {@code .mcpack} files for production Geyser server deployment
 * and public Bedrock distribution, producing a verified distribution manifest.
 */
public final class ProductionPackDeployer {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Logger logger;
    private final Path storageDirectory;
    private final Path destinationDirectory;

    public ProductionPackDeployer(
        @NotNull Logger logger,
        @NotNull Path storageDirectory,
        @NotNull Path destinationDirectory
    ) {
        this.logger = logger;
        this.storageDirectory = storageDirectory;
        this.destinationDirectory = destinationDirectory;
    }

    @NotNull
    public DeploymentResult deploy() throws IOException {
        Files.createDirectories(this.destinationDirectory);
        if (!Files.isDirectory(this.storageDirectory)) {
            this.logger.warn("Storage directory {} does not exist, no packs deployed", this.storageDirectory);
            return new DeploymentResult(List.of(), 0, 0, this.destinationDirectory);
        }

        List<DeployedPackInfo> deployedPacks = new ArrayList<>();
        long totalBytes = 0;

        try (var stream = Files.walk(this.storageDirectory, 2)) {
            List<Path> packFiles = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".mcpack"))
                .sorted()
                .toList();

            for (Path packPath : packFiles) {
                DeployedPackInfo packInfo = this.processPack(packPath);
                if (packInfo != null) {
                    deployedPacks.add(packInfo);
                    totalBytes += packInfo.fileSizeBytes();
                }
            }
        }

        Path manifestPath = this.destinationDirectory.resolve("pack-distribution-manifest.json");
        DistributionManifest manifest = new DistributionManifest(
            "1.0.0",
            System.currentTimeMillis(),
            deployedPacks.size(),
            totalBytes,
            deployedPacks
        );
        Files.writeString(manifestPath, GSON.toJson(manifest), StandardCharsets.UTF_8);
        this.logger.info("Successfully deployed {} production pack(s) ({} total bytes) to {}", deployedPacks.size(), totalBytes, this.destinationDirectory);

        return new DeploymentResult(deployedPacks, deployedPacks.size(), totalBytes, this.destinationDirectory);
    }

    @Nullable
    private DeployedPackInfo processPack(@NotNull Path packPath) {
        String fileName = packPath.getFileName().toString();
        String modId = fileName.substring(0, fileName.length() - ".mcpack".length());
        Path targetPath = this.destinationDirectory.resolve(fileName);

        try {
            PackMetadata metadata = readPackMetadata(packPath);
            byte[] fileBytes = Files.readAllBytes(packPath);
            String sha256 = Hashing.sha256().hashBytes(fileBytes).toString();

            Files.copy(packPath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            return new DeployedPackInfo(
                modId,
                fileName,
                metadata.name(),
                metadata.uuid(),
                metadata.version(),
                fileBytes.length,
                sha256,
                targetPath.toString()
            );
        } catch (Exception e) {
            this.logger.error("Failed to deploy pack {} to {}", packPath, targetPath, e);
            return null;
        }
    }

    @NotNull
    private static PackMetadata readPackMetadata(@NotNull Path packPath) throws IOException {
        try (ZipFile zip = new ZipFile(packPath.toFile())) {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null) {
                return new PackMetadata(packPath.getFileName().toString(), "unknown-uuid", "1.0.0");
            }

            try (InputStream stream = zip.getInputStream(manifestEntry);
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject header = json.getAsJsonObject("header");
                String name = header != null && header.has("name") ? header.get("name").getAsString() : packPath.getFileName().toString();
                String uuid = header != null && header.has("uuid") ? header.get("uuid").getAsString() : "unknown-uuid";
                String version = "1.0.0";
                if (header != null && header.has("version") && header.get("version").isJsonArray()) {
                    version = header.getAsJsonArray("version").toString();
                }
                return new PackMetadata(name, uuid, version);
            }
        }
    }

    private record PackMetadata(String name, String uuid, String version) {
    }

    public record DeployedPackInfo(
        @NotNull String modId,
        @NotNull String fileName,
        @NotNull String packName,
        @NotNull String packUuid,
        @NotNull String packVersion,
        long fileSizeBytes,
        @NotNull String sha256,
        @NotNull String deployedPath
    ) {
    }

    public record DistributionManifest(
        @NotNull String schemaVersion,
        long timestampEpochMillis,
        int packCount,
        long totalSizeBytes,
        @NotNull List<DeployedPackInfo> packs
    ) {
        public DistributionManifest {
            packs = List.copyOf(packs);
        }
    }

    public record DeploymentResult(
        @NotNull List<DeployedPackInfo> deployedPacks,
        int count,
        long totalSizeBytes,
        @NotNull Path destinationDirectory
    ) {
        public DeploymentResult {
            deployedPacks = List.copyOf(deployedPacks);
        }
    }
}
