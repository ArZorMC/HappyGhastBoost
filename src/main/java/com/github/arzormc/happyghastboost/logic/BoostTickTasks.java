package com.github.arzormc.happyghastboost.logic;

import com.github.arzormc.happyghastboost.HappyGhastBoost;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class BoostTickTasks {

    private final BoostManager manager;
    private final HappyGhastBoost plugin;

    private BukkitTask loopTask;

    public BoostTickTasks(BoostManager manager) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
    }

    // ======================
    // 🔁 Main Boost Loop
    // ======================
    public void start() {
        if (loopTask != null) return;

        final double dotThreshold = plugin.getConfig().getDouble("forward-dot-threshold", 0.85);
        final long forwardHoldThreshold = plugin.getConfig().getLong("forward-hold-ms", 1200L);

        loopTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            long misalignmentGrace = plugin.getConfig().getLong("boost-misalignment-grace-ms", 300);

            for (Map.Entry<UUID, UUID> entry : manager.getPilotManager().getGhastToPilotMap().entrySet()) {
                Player player = Bukkit.getPlayer(entry.getValue());
                if (player == null || !player.isOnline()) continue;

                if (plugin.getConfig().getBoolean("require-permission", false)
                        && !player.hasPermission("happyghastboost.use")) {
                    continue;
                }

                UUID playerId = player.getUniqueId();
                BoostState state = manager.getBoostStates().get(playerId);
                if (state == null) continue;

                if (manager.getPilotManager().getRecentlyUnregistered().remove(playerId)) continue;

                Entity vehicle = player.getVehicle();
                if (!(vehicle instanceof HappyGhast ghast) || !ghast.isValid()) continue;

                UUID ghastId = ghast.getUniqueId();
                Location current = ghast.getLocation();
                Location previous = manager.getPilotManager().getLastGhastLocations().get(ghastId);

                boolean movingForward = false;

                if (previous != null) {
                    Vector delta = current.toVector().subtract(previous.toVector());
                    double speed = delta.lengthSquared();

                    double minSpeed = plugin.getConfig().getDouble("min-forward-speed", 0.01);
                    double effectiveThreshold = state.boosting ? dotThreshold : (dotThreshold + 0.05);

                    if (speed > minSpeed || state.boosting) {
                        Vector horizontalDelta = delta.clone().setY(0).normalize();
                        float yaw = ghast.getLocation().getYaw(); // facing yaw
                        Vector facing = new Vector(-Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw))).normalize();

                        double dot = facing.dot(horizontalDelta);
                        movingForward = dot >= effectiveThreshold;

                        if (movingForward) {
                            state.lastValidForwardTime = now;
                        }

                        if (plugin.getConfig().getBoolean("verbose-debug")) {
                            String snapshot = String.format("dot=%.3f aligned=%s speed=%.6f boosting=%s threshold=%.2f",
                                    dot, movingForward, speed, state.boosting, effectiveThreshold);

                            String last = manager.getLastDotSpeedSnapshot().get(playerId);
                            if (!snapshot.equals(last)) {
                                manager.getLastDotSpeedSnapshot().put(playerId, snapshot);

                                if (!manager.getLogger().isThrottled("align-" + playerId, 300)) {
                                    manager.getLogger().logVerbose("Tick", "%s tick: %s grace=%dms", player.getName(), snapshot, (now - state.lastValidForwardTime));
                                }
                            }
                        }
                    }
                }

                manager.getPilotManager().getLastGhastLocations().put(ghastId, current.clone());

                if (movingForward) {
                    if (!state.isHoldingForward) {
                        state.isHoldingForward = true;
                        if (state.forwardStartTime == 0L) {
                            state.forwardStartTime = now;
                            manager.getLogger().logDebug("Input", "%s started forward glide.", player.getName());
                        }
                    }

                    long heldFor = now - state.forwardStartTime;
                    manager.getLogger().logVerbose("Input", "%s holding forward (%d ms)", player.getName(), heldFor);

                    if (!state.boosting && heldFor >= forwardHoldThreshold && !state.mustReleaseBeforeNextBoost) {
                        state.boosting = true;
                        state.boostStartTime = now; // Start the ramp-up timer
                        manager.getLogger().logBasic("Boost", "%s triggered BOOST!", player.getName());
                    } else {
                        if (!state.boosting && plugin.getConfig().getBoolean("verbose-debug")) {
                            manager.getLogger().logDebug("Boost", "%s ❌ Cannot boost: held=%dms (required=%dms), mustRelease=%s", player.getName(), heldFor, forwardHoldThreshold, state.mustReleaseBeforeNextBoost);
                        }
                    }

                } else {
                    if (state.isHoldingForward) {
                        manager.getLogger().logDebug("Input", "%s stopped forward glide.", player.getName());
                    }

                    long timeSinceValid = now - state.lastValidForwardTime;

                    if (timeSinceValid > misalignmentGrace) {
                        state.isHoldingForward = false;
                        state.forwardStartTime = 0L;
                        state.mustReleaseBeforeNextBoost = false;

                        if (state.boosting) {
                            manager.getLogger().logBasic("Boost", "%s ⛔ BOOST LOST: misaligned for %dms (limit=%dms)",
                                    player.getName(), timeSinceValid, misalignmentGrace);
                            state.boosting = false;
                        }
                    }
                }

                if (state.boosting && state.charge > 0.0) {
                    state.charge -= state.drainRate;
                    manager.applyBoost(player, state);

                    if (state.charge <= 0.0) {
                        state.charge = 0.0;
                        state.boosting = false;
                        state.mustReleaseBeforeNextBoost = true;
                        manager.getLogger().logBasic("Boost", "%s boost ended due to empty charge.", player.getName());
                    }

                } else {
                    if (state.charge < 1.0) {
                        state.charge += state.refillRate;
                    }
                }

                state.charge = Math.max(0.0, Math.min(1.0, state.charge));
                manager.updateActionBar(player, state);

                if (plugin.getConfig().getBoolean("verbose-debug")) {
                    String snapshot = "charge=" + String.format("%.2f", state.charge) + " boosting=" + state.boosting;
                    if (!snapshot.equals(manager.getPilotManager().getLastVerboseSnapshot().get(playerId))) {
                        manager.getPilotManager().getLastVerboseSnapshot().put(playerId, snapshot);

                        if (!manager.getLogger().isThrottled("tick-" + playerId, 500))
                            manager.getLogger().logVerbose("Tick", "Tick: %s %s", player.getName(), snapshot);
                        }
                    }
                }
            }, 0L, manager.getUpdateInterval());
        }
}


