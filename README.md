# 🎈 HappyGhastBoost

Boost system for Happy Ghast mounts in Minecraft 1.21.7+

---

## 🔧 Features
- 🚀 Boost meter display for players riding the `happy_ghast` entity
- 📊 Action bar UI using [MiniMessage](https://docs.advntr.dev/minimessage/)
- ⚙️ Configurable bar style and boost behavior
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

| Node                    | Description                                         | Default |
|-------------------------|-----------------------------------------------------|---------|
| `happyghastboost.admin` | Use admin commands like logging and reload         | OP      |
| `happyghastboost.use`   | Allows player to use Happy Ghast boost features    | false   |

---

## 📁 Config Files

- `**config.yml**` – Configure boost charge/refill rates, speed behavior, ramp-up time, action bar format, and logging
- `**messages.yml**` – Fully translatable MiniMessage strings for charge state labels and action bar UI

---