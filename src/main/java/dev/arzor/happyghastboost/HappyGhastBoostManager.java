package dev.arzor.happyghastboost;

// ======================
// 📦 Imports
// ======================
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

// ======================
// 🔧 Boost Manager Class
// ======================
public class HappyGhastBoostManager implements Listener {

    // ======================
    // 🔒 Fields & State Maps
    // ======================
    private final HappyGhastBoost plugin;

    // ⛽ Boost & Charge State

    // ⚡ Stores boost-related state for each player (charge level, boost status, timers, etc.)
    private final Map<UUID, BoostState> boostStates = new HashMap<>();

    // 💾 Stores the charge level of a ghast after a player dismounts, so it can be restored on remount
    private final Map<UUID, Double> ghastChargeLevels = new HashMap<>();

    // 🎮 Player & Entity Mapping

    // 🗺 Maps each ghast's UUID to the UUID of the player currently piloting it
    private final Map<UUID, UUID> ghastToPilot = new HashMap<>();

    // 📍 Remembers where the ghast was last tick for calculating movement deltas
    private final Map<UUID, Location> lastGhastLocation = new HashMap<>();

    // 🧹 Temporary suppression of boost logic right after dismount to avoid ghost ticks
    private final Set<UUID> recentlyUnregistered = new HashSet<>();

    // 🗂️ Cache of player UUID → latest known name (Java players only)
    private final Map<UUID, String> knownPlayerNames = new HashMap<>();

    // 🧪 Logging Snapshot Filters

    // 🧭 Throttle timestamps for per-key spam suppression
    private final Map<String, Long> logTimestamps = new HashMap<>();

    // 🧪 Keeps track of the last verbose log line for each player to avoid spam
    private final Map<UUID, String> lastVerboseSnapshot = new HashMap<>();

    // 🧪 Keeps track of the last action bar state per player to avoid logging duplicate values
    private final Map<UUID, String> lastBarSnapshot = new HashMap<>();

    // 🧠 Used to suppress duplicate verbose logs about dot/speed/alignment
    private final Map<UUID, String> lastDotSpeedSnapshot = new HashMap<>();

    // ⏱️ Task Scheduling

    // 🔁 Main Bukkit task that ticks all active ghast pilots
    private BukkitTask loopTask;

    // 💾 Repeating task to auto-save charge data to disk every few minutes
    private BukkitTask autoSaveTask;

    // ======================
    // ⚙️ Configurable Values (loaded from config.yml)
    // ======================
    private double drainRate;
    private double refillRate;
    private double boostSpeed;
    private int updateInterval;
    private boolean trailEnabled;
    private Particle trailType;

    // 💾 YAML config used to persist ghast charge states between restarts
    private File chargesFile;

    // ======================
    // 🧱 Constructor & Setup
    // ======================
    // 🔧 Constructor: loads plugin reference and applies settings from config.yml
    public HappyGhastBoostManager(HappyGhastBoost plugin) {
        this.plugin = plugin;
        loadSettings();
        loadLoggingMode();
    }

    public void reloadSettings() {
        plugin.reloadConfig();
        loadSettings();
        log(LoggingMode.DEBUG, "Config", "Settings reloaded from config.yml and messages.yml.");
    }

    private void loadSettings() {
        // 🧪 Pull boost behavior config from config.yml
        drainRate = plugin.getConfig().getDouble("drain-rate", 0.02);
        refillRate = plugin.getConfig().getDouble("refill-rate", 0.005);
        boostSpeed = plugin.getConfig().getDouble("boost-speed", 0.7);
        updateInterval = plugin.getConfig().getInt("update-interval", 2);
        trailEnabled = plugin.getConfig().getBoolean("particle-trail.enabled", true);

        String raw = plugin.getConfig().getString("particle-trail.type", "FLAME");
        try {
            trailType = Particle.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            trailType = Particle.FLAME;
            plugin.getLogger().warning("Invalid particle type in config: " + raw + ". Defaulting to FLAME.");
            log(LoggingMode.DEBUG, "Config", "Particle fallback: using FLAME");
        }
    }

    // ======================
    // 🔁 Main Boost Loop
    // ======================
    // ⏯ Called from main class to start the tick loop and autosave timer
    public void start() {
        if (loopTask != null) return; // ✅ Already started

        // Read thresholds from config
        final double dotThreshold = plugin.getConfig().getDouble("forward-dot-threshold", 0.85);
        final long forwardHoldThreshold = plugin.getConfig().getLong("forward-hold-ms", 1200L);

        // 🕒 Start periodic auto-save every 5 minutes
        long autoSaveIntervalTicks = 20L * 60 * 5; // 5 minutes in ticks
        if (autoSaveTask != null) autoSaveTask.cancel();
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, plugin::saveChargeData, autoSaveIntervalTicks, autoSaveIntervalTicks);

        // 🎮 The core tick loop that runs every X ticks and handles player-ghast boosting
        loopTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // 🕒 Track the current tick timestamp
            long now = System.currentTimeMillis();

// 🛡️ Grace period after losing alignment before cancelling boost
            long misalignmentGrace = plugin.getConfig().getLong("boost-misalignment-grace-ms", 300);

            // Iterate over all piloted ghasts
            for (Map.Entry<UUID, UUID> entry : ghastToPilot.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getValue());
                if (player == null || !player.isOnline()) continue;

                // ⛔ Skip players without the required permission (if enabled in config)
                if (plugin.getConfig().getBoolean("require-permission", false)
                        && !player.hasPermission("happyghastboost.use")) {
                    continue;
                }

                UUID playerId = player.getUniqueId();
                BoostState state = boostStates.get(playerId);
                if (state == null) continue;

                // ⛔ Skip if player was just unregistered to prevent carryover tick
                if (recentlyUnregistered.remove(playerId)) continue;

                // ⛔ Skip if not riding a valid Happy Ghast
                Entity vehicle = player.getVehicle();
                if (!(vehicle instanceof HappyGhast ghast) || !ghast.isValid()) continue;

                UUID ghastId = ghast.getUniqueId();
                Location current = ghast.getLocation();
                Location previous = lastGhastLocation.get(ghastId);

                boolean movingForward = false;

                // 🧭 Calculate delta movement and dot product to detect directional alignment
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

                            String last = lastDotSpeedSnapshot.get(playerId);
                            if (!snapshot.equals(last)) {
                                lastDotSpeedSnapshot.put(playerId, snapshot);
                                // 🧪 Throttle alignment snapshot spam (1 per 300ms max)
                                if (!isThrottled("align-" + playerId, 300)) {
                                    log(LoggingMode.VERBOSE, "Tick", "%s tick: %s grace=%dms", player.getName(), snapshot, (now - state.lastValidForwardTime));
                                }
                            }
                        }
                    }
                }

                // 💾 Store this position for next tick
                lastGhastLocation.put(ghastId, current.clone());

                if (movingForward) {
                    // ▶ Player is now considered to be "holding forward"
                    if (!state.isHoldingForward) {
                        state.isHoldingForward = true;

                        // ✅ Only set if not already holding forward
                        if (state.forwardStartTime == 0L) {
                            state.forwardStartTime = now;
                            log(LoggingMode.DEBUG, "Input", "%s started forward glide.", player.getName());
                        }
                    }

                    long heldFor = now - state.forwardStartTime;
                    log(LoggingMode.VERBOSE, "Input", "%s holding forward (%d ms)", player.getName(), heldFor);

                    // 🚀 Start boost if held long enough and not blocked by cooldown
                    if (!state.boosting && heldFor >= forwardHoldThreshold && !state.mustReleaseBeforeNextBoost) {
                        state.boosting = true;
                        state.boostStartTime = now; // Start the ramp-up timer
                        log(LoggingMode.BASIC, "Boost", "%s triggered BOOST!", player.getName());
                    } else {
                        if (!state.boosting && plugin.getConfig().getBoolean("verbose-debug")) {
                            log(LoggingMode.DEBUG, "Boost", "%s ❌ Cannot boost: held=%dms (required=%dms), mustRelease=%s", player.getName(), heldFor, forwardHoldThreshold, state.mustReleaseBeforeNextBoost);
                        }
                    }

                } else {
                    // 🛑 Player is no longer considered aligned based on dot product check

                    if (state.isHoldingForward) {
                        log(LoggingMode.DEBUG, "Input", "%s stopped forward glide.", player.getName());
                    }

                    // ⏱️ Calculate how long it's been since the last valid forward alignment
                    long timeSinceValid = now - state.lastValidForwardTime;

                    // 🕒 Only reset forward state and cancel boost if grace period has passed
                    if (timeSinceValid > misalignmentGrace) {

                        // ⏹ Reset forward input tracking
                        state.isHoldingForward = false;
                        state.forwardStartTime = 0L;

                        // ✅ Allow reboosting again later, once player lets go and presses forward again
                        state.mustReleaseBeforeNextBoost = false;

                        // ⛔ Stop the boost only after misalignment grace period expires
                        if (state.boosting) {
                            log(LoggingMode.BASIC, "Boost", "%s ⛔ BOOST LOST: misaligned for %dms (limit=%dms)",
                                    player.getName(), timeSinceValid, misalignmentGrace);
                            state.boosting = false;
                        }
                    }
                }

                // ⚡ Charge drain if boosting, otherwise slowly refill
                if (state.boosting && state.charge > 0.0) {
                    state.charge -= drainRate;
                    applyBoost(player, state); // apply actual motion and particles

                    // ❌ Stop boost if charge runs out
                    if (state.charge <= 0.0) {
                        state.charge = 0.0;
                        state.boosting = false;
                        state.mustReleaseBeforeNextBoost = true;
                        log(LoggingMode.BASIC, "Boost", "%s boost ended due to empty charge.", player.getName());
                    }

                } else {
                    if (state.charge < 1.0) {
                        state.charge += refillRate;
                    }
                }

                // Clamp charge to 0–1
                state.charge = Math.max(0.0, Math.min(1.0, state.charge));

                // 🖥 Update HUD with charge level and boost state
                updateActionBar(player, state);

                // 🧪 Verbose per-tick snapshot
                if (plugin.getConfig().getBoolean("verbose-debug")) {
                    String snapshot = "charge=" + String.format("%.2f", state.charge) + " boosting=" + state.boosting;
                    if (!snapshot.equals(lastVerboseSnapshot.get(playerId))) {
                        lastVerboseSnapshot.put(playerId, snapshot);
                        // 🧪 Throttle verbose tick logging (1 per 500ms max)
                        if (!isThrottled("tick-" + playerId, 500)) {
                            log(LoggingMode.VERBOSE, "Tick", "Tick: %s %s", player.getName(), snapshot);
                        }
                    }
                }
            }
        }, 0L, updateInterval); // run every X ticks
    }

    // ======================
    // 🧩 Event Listeners
    // ======================
    @EventHandler
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getMount() instanceof HappyGhast ghast)) return;

        // ✅ Only assign the player as pilot if they are the first passenger (controller seat)
        if (ghast.getPassengers().isEmpty() || ghast.getPassengers().getFirst().equals(player)) {
            registerPilot(player, ghast);
        } else {
            log(LoggingMode.DEBUG, "Mount", "Mount skipped: %s is not the controlling passenger.", player.getName());
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDismounted() instanceof HappyGhast ghast)) return;

        UUID ghastId = ghast.getUniqueId();
        UUID playerId = player.getUniqueId();

        // ✅ Ensure the player dismounting is the one we assigned
        if (playerId.equals(ghastToPilot.get(ghastId))) {
            unregisterPilot(player);

            // 🕐 Wait one tick, then check if a new rider has taken their place
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<Entity> passengers = ghast.getPassengers();
                if (!passengers.isEmpty() && passengers.getFirst() instanceof Player newPilot) {
                    registerPilot(newPilot, ghast);
                    log(LoggingMode.BASIC, "Pilot", "Pilot reassigned to %s 1 tick after dismount.", newPilot.getName());
                }
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 🧹 Remove from boost tracking
        unregisterPilot(player);

        // 🧭 Check if player was riding a Happy Ghast
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof HappyGhast ghast) {
            // ⏳ Schedule one-tick delay to give time for other passengers to be seated
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<Entity> passengers = ghast.getPassengers();
                if (!passengers.isEmpty() && passengers.getFirst() instanceof Player newPilot) {
                    registerPilot(newPilot, ghast);
                    log(LoggingMode.BASIC, "Pilot", "Pilot reassigned to %s after %s quit.", newPilot.getName(), player.getName());
                }
            });
        }

        // 📝 Store their name while still online
        knownPlayerNames.put(player.getUniqueId(), player.getName());

        // 💾 Optional: immediately persist charges while name is resolvable
        saveChargeData();  // <- safe and effective here
    }

    @EventHandler
    public void onGhastDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof HappyGhast ghast)) return;

        UUID ghastId = ghast.getUniqueId();

        // 🧹 Remove any saved charge or pilot association
        ghastChargeLevels.remove(ghastId);
        UUID pilotId = ghastToPilot.remove(ghastId);

        if (pilotId != null) {
            Player pilot = Bukkit.getPlayer(pilotId);
            if (pilot != null && pilot.isOnline()) {
                pilot.sendActionBar(Component.empty()); // clean up UI if needed
            }

            log(LoggingMode.BASIC, "Death", "Happy Ghast %s died. Unassigned pilot %s and cleared saved charge.", ghastId, pilotId);
        } else {
            log(LoggingMode.BASIC, "Death", "Happy Ghast %s died. Cleared saved charge.", ghastId);
        }
    }

    // ======================
    // 🎮 Pilot Management
    // ======================
    public void registerPilot(Player player, HappyGhast ghast) {
        UUID playerId = player.getUniqueId();
        UUID ghastId = ghast.getUniqueId();

        // 🎮 Track who is riding what
        ghastToPilot.put(ghastId, playerId);

        // 🧠 Initialize or reuse player's boost state
        BoostState state = boostStates.get(playerId);
        if (state == null) {
            state = new BoostState();
            boostStates.put(playerId, state);
        } else {
            // 🔄 Reset transient values, but retain charge
            state.boosting = false;
            state.isHoldingForward = false;
            state.forwardStartTime = 0L;
            state.mustReleaseBeforeNextBoost = false;
        }

        // 🔋 If this ghast had a charge saved from a previous rider, restore it now
        Double savedCharge = ghastChargeLevels.remove(ghastId);
        if (savedCharge != null) {
            state.charge = Math.max(0.0, Math.min(1.0, savedCharge));
            log(LoggingMode.DEBUG, "Charge", "Restored saved charge for ghast %s: %.2f", ghastId, state.charge);
        }

        // 🧭 Cache ghast location now to prevent directional misalignment on first tick
        if (player.getVehicle() == ghast && ghast.isValid()) {
            lastGhastLocation.put(ghast.getUniqueId(), ghast.getLocation().clone());
        }

        // 🎯 Show the action bar to the pilot immediately
        updateActionBar(player, boostStates.get(playerId));

        log(LoggingMode.BASIC, "Pilot", "Assigned %s as pilot of Ghast %s", player.getName(), ghastId);
    }

    public void unregisterPilot(Player player) {
        UUID playerId = player.getUniqueId();
        BoostState state = boostStates.remove(playerId);

        // 🗑 Find ghast associated with this player and remove the pilot link
        UUID ghastId = null;
        for (Map.Entry<UUID, UUID> entry : ghastToPilot.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                ghastId = entry.getKey();
                break;
            }
        }

        if (ghastId != null) {
            ghastToPilot.remove(ghastId);

            // 💾 Save the current charge for that ghast (if applicable)
            if (state != null) {
                ghastChargeLevels.put(ghastId, state.charge);
                log(LoggingMode.DEBUG, "Charge", "Saved charge for ghast %s: %.2f", ghastId, state.charge);
            }
        }

        // 🧼 Clean up UI and debug logs
        player.sendActionBar(Component.empty());
        recentlyUnregistered.add(playerId);
        lastVerboseSnapshot.remove(playerId);

        log(LoggingMode.BASIC, "Pilot", "Unregistered pilot %s", player.getName());
    }

    // ======================
    // 🧠 Boost Mechanics
    // ======================
    private void applyBoost(Player player, BoostState state) {
        // 🎯 Apply motion and particles to the player's ghast
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof HappyGhast ghast) {
            if (!ghast.isValid()) return; // 🛑 Prevent boosting dead or invalid entities

            Vector direction = player.getLocation().getDirection().normalize();

            // ⏱️ Compute how far into the boost ramp-up we are
            long rampUpDuration = plugin.getConfig().getLong("boost-rampup-speed-ms", 1500L);
            long elapsed = System.currentTimeMillis() - state.boostStartTime;

            // 📈 Calculate ramp factor between 0.0 and 1.0
            double progress = Math.min(1.0, (double) elapsed / rampUpDuration);
            double adjustedSpeed = boostSpeed * progress;

            // 🚀 Apply velocity based on ramped boost speed
            Vector velocity = direction.multiply(adjustedSpeed);
            ghast.setVelocity(velocity);

            // 💨 Optional trail particle effect
            if (trailEnabled) {
                Location loc = ghast.getLocation().add(0, 0.5, 0);
                player.getWorld().spawnParticle(trailType, loc, 3, 0.1, 0.1, 0.1, 0);
                log(LoggingMode.VERBOSE, "Particles", "Spawned particle %s at %s", trailType, loc.toVector());
            }

            log(LoggingMode.VERBOSE, "Velocity", "Applied velocity to ghast: %s", velocity);
        }
    }

    private void updateActionBar(Player player, BoostState state) {
        if (plugin.getConfig().getBoolean("require-permission", false)
                && !player.hasPermission("happyghastboost.use")) {
            return; // ⛔ Don't show bar to unprivileged users
        }

        if (state == null) return;

        double percent = state.charge;
        boolean boosting = state.boosting;

        // 🔠 Determine which status label to show
        String labelKey = boosting
                ? "boosting"
                : (percent >= 1.0 ? "boost-ready" : "charging");

        Component label = MessageUtil.get(labelKey); // localized label component
        String bar = MessageUtil.buildBoostBar(plugin, percent, boosting); // visual bar (|||||)
        String percentStr = String.valueOf((int) (percent * 100)); // integer percent

        TagResolver resolver = TagResolver.builder()
                .resolver(Placeholder.component("label", label))
                .resolver(Placeholder.parsed("bar", bar))
                .resolver(Placeholder.unparsed("percent", percentStr))
                .build();

        String format = plugin.getConfig().getString("actionbar-format", "<label> <bar> <percent>%");

        player.sendActionBar(MiniMessage.miniMessage().deserialize(format, resolver));

        // 🧪 Verbose log snapshot suppression (group by ~5%)
        if (plugin.getConfig().getBoolean("verbose-debug")) {
            UUID uuid = player.getUniqueId();
            int roundedPercent = (int) (percent * 100 / 5) * 5;
            String logSnapshot = "charge~" + roundedPercent + "% boosting=" + boosting;

            String last = lastBarSnapshot.get(uuid);
            if (!logSnapshot.equals(last)) {
                lastBarSnapshot.put(uuid, logSnapshot);
                // 🧪 Throttle action bar snapshot logging (1 per 250ms max)
                if (!isThrottled("actionbar-" + uuid, 250)) {
                    log(LoggingMode.VERBOSE, "HUD", "ActionBar update → %s: %s", player.getName(), logSnapshot);
                }
            }
        }
    }

    // ======================
    // 📦 Player Boost State
    // ======================
    private static class BoostState {
        // 🔋 Charge level (0.0 = empty, 1.0 = full)
        double charge = 1.0;

        // 🚀 Whether currently boosting
        boolean boosting = false;

        // 🕓 When boost was initiated (used for ramp-up)
        long boostStartTime = 0L;

        // ▶ Whether currently holding forward
        boolean isHoldingForward = false;

        // 🕓 When forward movement started (ms)
        long forwardStartTime = 0L;

        // ✅ When last forward alignment was valid
        long lastValidForwardTime = 0L;

        // ❌ Prevents reboost until forward input is released and pressed again
        boolean mustReleaseBeforeNextBoost = false;
    }

    // ======================
    // 📁 Charge Persistence
    // ======================
    public void loadChargeData() {
        chargesFile = new File(plugin.getDataFolder(), "charges.yml");

        // 📁 If file doesn't exist, create it and write header comment
        if (!chargesFile.exists()) {
            try {
                if (chargesFile.createNewFile()) {
                    try (PrintWriter writer = new PrintWriter(chargesFile)) {
                        writer.println("# This file stores persistent Happy Ghast charge levels.");
                        writer.println("# It is managed automatically — do not edit manually.");
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create or write to charges.yml: " + e.getMessage());
            }
        }

        // 📄 Load charge config from disk
        FileConfiguration chargesConfig = YamlConfiguration.loadConfiguration(chargesFile);
        ConfigurationSection section = chargesConfig.getConfigurationSection("charges");

        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID ghastId = UUID.fromString(key);
                    double charge = section.getDouble(key);
                    ghastChargeLevels.put(ghastId, Math.max(0.0, Math.min(1.0, charge)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid UUID in charges.yml: " + key);
                }
            }
            log(LoggingMode.DEBUG, "Charge", "Loaded %d saved charge levels from charges.yml.", ghastChargeLevels.size());
        }
    }

    public void saveChargeData() {
        if (chargesFile == null) return;

        int saved = 0;

        try (PrintWriter writer = new PrintWriter(chargesFile)) {
            // 🧾 Write header
            writer.println("# This file stores persistent Happy Ghast charge levels.");
            writer.println("# It is managed automatically — do not edit manually.");
            writer.println("charges:");

            for (Map.Entry<UUID, Double> entry : ghastChargeLevels.entrySet()) {
                UUID uuid = entry.getKey();
                double value = Math.max(0.0, Math.min(1.0, entry.getValue()));

                // 🔍 Unified player name resolution
                String name = null;

                // ✅ Prefer online name (Java or Bedrock)
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) {
                    name = online.getName();
                }

                // 🌉 Then try Floodgate (for Bedrock UUIDs)
                if (name == null && FloodgateApi.getInstance().isFloodgatePlayer(uuid)) {
                    name = FloodgateApi.getInstance().getPlayer(uuid).getUsername();
                }

                // 📁 Fallback to offline player cache
                if (name == null) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                    name = offline.getName();
                }

                // 🧠 Fallback to known memory cache (Java only)
                if (name == null) {
                    name = knownPlayerNames.get(uuid);
                }

                // ❓ Final fallback if all else fails
                if (name == null) {
                    name = "(unknown)";
                }

                // 📝 Write name comment (omit if unknown)
                if (!"(unknown)".equals(name)) {
                    writer.println("  # " + name);
                }

                // 💾 Write charge with clean formatting
                writer.println("  " + uuid + ": " + String.format(Locale.US, "%.4f", value));
                saved++;
            }

            log(LoggingMode.DEBUG, "Charge", "Saved %d charge levels to charges.yml.", saved);

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save charges.yml: " + e.getMessage());
        }
    }

    // ======================
    // 🛠️ Logging Utilities
    // ======================
    public enum LoggingMode {
        OFF, BASIC, DEBUG, VERBOSE
    }

    private LoggingMode loggingMode = LoggingMode.BASIC;

    // 🔄 Loads the configured logging mode from config.yml
    private void loadLoggingMode() {
        String raw = plugin.getConfig().getString("logging-mode");
        if (raw == null) {
            plugin.getLogger().warning("⚠️ 'logging-mode' is missing from config.yml. Defaulting to BASIC.");
            loggingMode = LoggingMode.BASIC;
            return;
        }

        try {
            loggingMode = LoggingMode.valueOf(raw.trim().toUpperCase());
            plugin.getLogger().info("✅ Loaded logging-mode: " + loggingMode.name());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("❌ Invalid logging-mode in config.yml: '" + raw + "'. Defaulting to BASIC.");
            loggingMode = LoggingMode.BASIC;
        }
    }

    // ✅ Allow runtime updates via commands
    public void setLoggingMode(LoggingMode mode) {
        this.loggingMode = mode;
    }

    // 📊 Determines whether to log based on the current mode
    private boolean shouldLog(LoggingMode level) {
        return loggingMode.ordinal() >= level.ordinal();
    }

    // 📋 Structured logging with categories
    private void log(LoggingMode level, String category, String message, Object... args) {
        if (shouldLog(level)) {
            plugin.getLogger().info(String.format(
                    "[HappyGhastBoost:%s:%s] %s",
                    level.name(), category, String.format(message, args)
            ));
        }
    }

    // ⏱️ Prevents spam by only logging once per interval (in ms) per key
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isThrottled(String key, long intervalMs) {
        long now = System.currentTimeMillis();
        long last = logTimestamps.getOrDefault(key, 0L);
        if (now - last >= intervalMs) {
            logTimestamps.put(key, now);
            return false;
        }
        return true;
    }
}