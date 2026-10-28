<div align="center">

# 📱 Muse Multi-Account for Android

### Run multiple Muse accounts on one Android phone — Android only, not for iOS

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
| 🔄 | **Self-update (v10 and later)** — check for and install updates from inside the app; see [Self-update (v10 and later)](#self-update-v10-and-later) |

## 🚀 Quick Start

1. 📥 Download the latest release from **[Releases](https://github.com/0Lu-cy0/muse-multi-account/releases)** and install it.
2. 📱 Open the app, tap **Pick APK** and choose the Muse APK file on your phone.
3. ➕ Name the clone, tap **Create**, then **Launch**.
4. 🔑 Log in to the account inside the clone — done!

> ⚠️ **Important:** Do NOT uninstall the host app when updating, or all clones and logins will be lost.

## 🔄 Self-update (v10 and later)

From v10 onward, the app can check for and install its own updates from inside the app — you no longer need a PC and adb for every update.

> ⚠️ **v9 and earlier do not have this feature** — including the public 1.0.0 release. The first install of v10 must still be done manually (install over the existing app, never uninstall first, so clones and logins survive). Only from v10 onward are updates done in the app.

**How to update:**
1. Open the app and open the **Ghi chú** (Notes) section at the bottom of the main screen.
2. Tap **Kiểm tra cập nhật**.
3. If a newer version exists, the app shows its version and what changed.
4. The app downloads the update, verifies its SHA-256, and only then opens the system installer for you to confirm.

**What you need:** internet access, access to GitHub Releases, and the one-time "allow install from this source" permission for the host app in system Settings (the app will guide you there if it is not allowed yet).

**Limits to know:**
- Android always asks you to confirm in the system installer — there is no silent install.
- Updates come from published GitHub Releases of this repo. The git tree holds source code, not APKs, so an update is only offered once a newer release is published.
- Every update must carry the same app signature as the installed app, otherwise Android refuses the update.

## 📊 Status

- ✅ **1.0.0 — First clone fully working:** login screen appears and login succeeds.
- 🔄 **In testing:** 2 clones online together, notification labels, delay, RAM, battery, background running — then 3, then 4.

> ⚠️ **ONLY TESTED ON ONE PHONE**
>
> - 📱 **Tested device:** Samsung Galaxy A23 (SM-A235F/DS), Android 14, One UI 6.1, 6 GB RAM, arm64-v8a chip.
> - 🚫 **NOT applied to and NOT verified on other phone models.** Use on another device (different brand, Android version or chip) may fail or behave differently — every brand customises Android differently.

## ⚠️ Account safety — read before you use

- 📧 **Use email, not a phone number, for your accounts.** A phone number is a strong identifier: it can link multiple accounts together, and to a real person, a SIM and a carrier. Prefer a separate email address for each account.
- 📱 **If you use a phone number anyway, that is your choice and your risk.** Possible consequences include: your accounts being linked together, extra verification checks, accounts being flagged or locked, or losing access to an account.
- 🛠️ **What we can and cannot do.** What we can guarantee is that the app itself runs well: clones stay isolated, and where technically possible we separate each clone's identity and network. We cannot control how you use your accounts. These warnings are given upfront so you decide knowing the risks. Where we can technically prevent a risk, we will add that protection; until then, this warning stands.

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
