<div align="center">

# 📱 Muse Multi-Account

### Run multiple Muse accounts on one Android phone

[![Release](https://img.shields.io/badge/release-1.0.0-brightgreen?style=for-the-badge)](https://github.com/0Lu-cy0/muse-multi-account/releases/tag/v1.0.0)
[![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?style=for-the-badge&logo=android)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/arch-arm64--v8a-blue?style=for-the-badge)](https://developer.android.com/ndk)
[![Performance](https://img.shields.io/badge/performance-first-orange?style=for-the-badge)](#)

**🎯 Goal:** many accounts online at the same time, with continuous notifications — not limited to 2 copies, no frozen accounts.

🌐 **Tiếng Việt:** xem bản tiếng Việt tại **[README.vi.md](README.vi.md)**.

</div>

---

## ✨ Features

| 🎨 | Description |
|---|---|
| 👥 | **Many accounts at the same time** — create as many clones as you want, each logged in independently |
| 🔔 | **Clone-labelled notifications** — tell at a glance which account a message belongs to |
| ⚡ | **Performance first** — minimal UI, focused on running smoothly |
| 🔒 | **No target APK bundled** — you supply your own APK; the app never bundles or modifies it |

## 🚀 Quick Start

1. 📥 Download the latest release from **[Releases](https://github.com/0Lu-cy0/muse-multi-account/releases)** and install it.
2. 📱 Open the app, tap **Pick APK** and choose the Muse APK file on your phone.
3. ➕ Name the clone, tap **Create**, then **Launch**.
4. 🔑 Log in to the account inside the clone — done!

> ⚠️ **Important:** Do NOT uninstall the host app when updating, or all clones and logins will be lost.

## 📊 Status

- ✅ **1.0.0 — First clone fully working:** login screen appears and login succeeds.
- 🔄 **In testing:** 2 clones online together, notification labels, delay, RAM, battery, background running — then 3, then 4.

> ⚠️ **ONLY TESTED ON ONE PHONE**
>
> - 📱 **Tested device:** Samsung Galaxy A23 (SM-A235F/DS), Android 14, One UI 6.1, 6 GB RAM, arm64-v8a chip.
> - 🚫 **NOT applied to and NOT verified on other phone models.** Use on another device (different brand, Android version or chip) may fail or behave differently — every brand customises Android differently.

## 📖 Project Journey

How did this project go from design to the day it ran, and through which bugs? The full story, in Vietnamese and English:

➡️ **[docs/README_JOURNEY.md](docs/README_JOURNEY.md)**

## 🛠️ Source

| 📂 | Contents |
|---|---|
| `host/` | Host app source (Java) |
| `engine/` | BlackBox engine source (Java, AIDL, C++) |
| `build/` | Manual build scripts and build dependencies |
| `docs/` | Theme-grouped docs |
| `artifacts/aars/` | Built engine AARs |
| `archive/` | Superseded old sources, for reference only |

---

## 🤝 Contributing

For all contributions to this project, please contact via email: **nc12042004@gmail.com**

---

<div align="center">

💜 **Made for people who live on many accounts — one app, many accounts, no more switching back and forth.**

</div>
