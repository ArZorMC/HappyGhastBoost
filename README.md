# 🎈 HappyGhastBoost

Boost system for Happy Ghast mounts in Minecraft 1.21.7+

---

## 🔧 Features
- 🚀 Boost meter display for players riding the `happy_ghast` entity
- 📊 Action bar UI using [MiniMessage](https://docs.advntr.dev/minimessage/)
- ⚙️ Configurable bar style and boost behavior
- 🎚️ Boost Presets: grant different boost behavior via permissions
- 🪵 Adjustable logging level via admin command
- ♻️ Reload configuration and messages without restarting

---

## 📦 Requirements
- **Minecraft 1.21.7** or higher
- **Paper** or a compatible fork (e.g., Purpur, Pufferfish)

---

## 🚫 Compatibility

This plugin **requires** Minecraft 1.21.7+ due to the new `happy_ghast` entity.  
It will **not** function on older versions.

---

## 🧩 Commands

| Command                          | Description                      | Permission               |
|----------------------------------|----------------------------------|--------------------------|
| `/happyghastboost logdisable`   | Disable all plugin logging       | `happyghastboost.admin`  |
| `/happyghastboost logbasic`     | Enable basic logging             | `happyghastboost.admin`  |
| `/happyghastboost logdebug`     | Enable debug logging             | `happyghastboost.admin`  |
| `/happyghastboost logverbose`   | Enable full verbose logging      | `happyghastboost.admin`  |
| `/happyghastboost reload`       | Reload config and messages       | `happyghastboost.admin`  |

> 🔄 Alias: `/hgb`

---

## 🔐 Permissions

| Node                                  | Description                                                  | Default |
|---------------------------------------|--------------------------------------------------------------|---------|
| `happyghastboost.admin`              | Use admin commands like logging and reload                   | OP      |
| `happyghastboost.use`                | Allows player to use Happy Ghast boost features              | false   |
| `happyghastboost.preset.vip`         | Applies the `vip` boost preset from `config.yml`             | false   |
| `happyghastboost.preset.ultra`       | Applies the `ultra` boost preset from `config.yml`           | false   |

> ⚠️ **OPs automatically have all permissions, including all presets.**  
> Use a permissions plugin (e.g., LuckPerms) or `/deop` for accurate testing.

---

## 🎚️ Boost Presets

Boost presets allow you to define alternate behaviors for specific players based on permissions.<br>
Each preset can override the following:

- `boost-speed` – boost velocity multiplier (`1.0` = normal)
- `refill-rate` – how fast boost recharges when not active
- `drain-rate` – how fast boost depletes while active
- `particle` – optional particle effect shown during boost

### 🔧 Configuration Example

```yaml
presets:
  vip:
    boost-speed: 1.1
    refill-rate: 0.03
    drain-rate: 0.0025
    particle: END_ROD

  ultra:
    boost-speed: 1.3
    refill-rate: 0.05
    drain-rate: 0.0018
    particle: SOUL_FIRE_FLAME
```

To assign a player to a preset, give them the permission:
- happyghastboost.preset.<preset-name>

For example:

- happyghastboost.preset.vip
- happyghastboost.preset.ultra

✅ If no matching preset is found, global defaults from config.yml are used.<br>
⚠️ If a player has multiple matching permissions, the first match found is used.<br>
👉 To avoid unpredictable behavior, assign only one happyghastboost.preset.* permission per player.<br>
⚠️ Boost speeds above 1.5 may cause rubberbanding or client desync.

---

## 📁 Config Files

- `**config.yml**` – Configure boost charge/refill rates, speed behavior, ramp-up time, action bar format, and logging
- `**messages.yml**` – Fully translatable MiniMessage strings for charge state labels and action bar UI

---