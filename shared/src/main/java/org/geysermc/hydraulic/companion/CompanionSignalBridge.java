package org.geysermc.hydraulic.companion;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * Installs a real, Bedrock-visible server signal that companion packages can use to
 * detect a live Hydraulic-Phlodgate server: a vanilla scoreboard objective, which
 * Geyser translates to the Bedrock protocol exactly like any other Java scoreboard
 * objective. This is the only "behavior" signal that can genuinely cross the
 * Java-to-Bedrock boundary through Geyser without a Bedrock behavior pack executing
 * on the client, since Geyser does not install or run behavior-pack scripts.
 */
public final class CompanionSignalBridge {
    public static final String OBJECTIVE_NAME = "phlodgate_bridge";
    private static final String DISPLAY_NAME = "Hydraulic-Phlodgate";

    private final Logger logger;
    private boolean installed;

    public CompanionSignalBridge(@NotNull Logger logger) {
        this.logger = logger;
    }

    public void install(@NotNull MinecraftServer server) {
        try {
            Scoreboard scoreboard = server.getScoreboard();
            Objective existing = scoreboard.getObjective(OBJECTIVE_NAME);
            if (existing == null) {
                try {
                    scoreboard.addObjective(
                            OBJECTIVE_NAME,
                            ObjectiveCriteria.DUMMY,
                            Component.literal(DISPLAY_NAME),
                            ObjectiveCriteria.DUMMY.getDefaultRenderType(),
                            false,
                            null
                    );
                } catch (IllegalArgumentException duplicate) {
                    if (scoreboard.getObjective(OBJECTIVE_NAME) == null) {
                        throw duplicate;
                    }
                    this.logger.debug("Companion detection signal '{}' was installed concurrently", OBJECTIVE_NAME);
                }
                this.logger.info("Installed companion detection signal: scoreboard objective '{}'", OBJECTIVE_NAME);
            }
            this.installed = true;
        } catch (Exception e) {
            this.installed = false;
            this.logger.warn("Failed to install companion detection scoreboard signal '{}'", OBJECTIVE_NAME, e);
        }
    }

    public boolean isInstalled() {
        return this.installed;
    }
}
