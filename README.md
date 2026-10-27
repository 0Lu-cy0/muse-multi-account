<div align="center">

# 📱 Muse Multi-Account

### Chạy nhiều tài khoản Muse trên một điện thoại Android — Run multiple Muse accounts on one Android phone

[![Release](https://img.shields.io/badge/release-1.0.0-brightgreen?style=for-the-badge)](https://github.com/0Lu-cy0/muse-multi-account/releases/tag/v1.0.0)
[![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?style=for-the-badge&logo=android)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/arch-arm64--v8a-blue?style=for-the-badge)](https://developer.android.com/ndk)
[![Performance](https://img.shields.io/badge/performance-first-orange?style=for-the-badge)](#)

**🎯 Mục tiêu / Goal:** nhiều tài khoản online cùng lúc, thông báo liên tục — không giới hạn 2 bản, không đóng băng tài khoản.

</div>

---

## ✨ Tính năng / Features

| 🎨 | Mô tả / Description |
|---|---|
| 👥 | **Nhiều tài khoản cùng lúc** — tạo bao nhiêu clone cũng được, mỗi clone đăng nhập độc lập / Create as many clones as you want, each logged in independently |
| 🔔 | **Thông báo gắn tên clone** — biết ngay tin nhắn thuộc tài khoản nào / Notifications labelled with the clone name |
| ⚡ | **Hiệu năng là số một** — giao diện tối giản, tập trung vào chạy mượt / Performance first, minimal UI |
| 🔒 | **Không kèm APK gốc** — bạn tự nạp file APK của mình, app không đóng gói hay sửa đổi nó / No target APK bundled — you supply your own APK |

## 🚀 Bắt đầu nhanh / Quick Start

1. 📥 Tải bản mới nhất ở **[Releases](https://github.com/0Lu-cy0/muse-multi-account/releases)** và cài đặt.
2. 📱 Mở app, bấm **Pick APK** và chọn file APK Muse trên điện thoại của bạn.
3. ➕ Đặt tên clone, bấm **Create**, rồi **Launch**.
4. 🔑 Đăng nhập tài khoản trong clone — xong!

> ⚠️ **Lưu ý quan trọng / Important:** Đừng gỡ cài đặt app chủ khi cập nhật, nếu không toàn bộ clone và tài khoản đã đăng nhập sẽ mất.
> Do NOT uninstall the host app when updating, or all clones and logins will be lost.

## 📊 Trạng thái / Status

- ✅ **1.0.0 — Clone đầu tiên chạy hoàn chỉnh:** hiện màn đăng nhập và đăng nhập thành công (đã kiểm chứng trên Galaxy A23, Android 14).
- 🔄 **Đang test:** 2 clone cùng online, nhãn thông báo, độ trễ, RAM, pin, chạy nền — rồi mới tới 3 và 4.
- 🧪 **Chưa kiểm chứng cho mọi dòng máy:** hiện chỉ mới chứng minh trên một máy Android 14 chip arm64.

## 📖 Hành trình dự án / Project Journey

Muốn biết dự án đã đi từ thiết kế tới ngày chạy được như thế nào, qua những lỗi gì? Đọc ở đây — toàn bộ câu chuyện, song ngữ Việt – Anh:

➡️ **[docs/README_JOURNEY.md](docs/README_JOURNEY.md)**

## 🛠️ Mã nguồn / Source

| 📂 | Nội dung / Contents |
|---|---|
| `host/` | Mã nguồn app chủ (Java) / Host app source |
| `engine/` | Mã nguồn engine BlackBox (Java, AIDL, C++) / Engine source |
| `build/` | Script build thủ công và thư viện phụ thuộc / Manual build scripts |
| `docs/` | Tài liệu theo chủ đề / Theme-grouped docs |
| `artifacts/aars/` | Các bản engine đã build / Built engine AARs |
| `archive/` | Mã cũ đã bị thay thế, chỉ để đối chiếu / Superseded old sources |

---

<div align="center">

💜 **Made for people who live on many accounts — một app, nhiều tài khoản, không phải đổi qua đổi lại.**

</div>
