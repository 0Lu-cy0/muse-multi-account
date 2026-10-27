<div align="center">

# 📱 Muse Multi-Account

### Chạy nhiều tài khoản Muse trên một điện thoại Android

[![Release](https://img.shields.io/badge/release-1.0.0-brightgreen?style=for-the-badge)](https://github.com/0Lu-cy0/muse-multi-account/releases/tag/v1.0.0)
[![Android](https://img.shields.io/badge/Android-10%2B-3DDC84?style=for-the-badge&logo=android)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/arch-arm64--v8a-blue?style=for-the-badge)](https://developer.android.com/ndk)
[![Performance](https://img.shields.io/badge/performance-first-orange?style=for-the-badge)](#)

**🎯 Mục tiêu:** nhiều tài khoản online cùng lúc, thông báo liên tục — không giới hạn 2 bản, không đóng băng tài khoản.

🌐 **English:** see the English version at **[README.md](README.md)**.

</div>

---

## ✨ Tính năng

| 🎨 | Mô tả |
|---|---|
| 👥 | **Nhiều tài khoản cùng lúc** — tạo bao nhiêu clone cũng được, mỗi clone đăng nhập độc lập |
| 🔔 | **Thông báo gắn tên clone** — biết ngay tin nhắn thuộc tài khoản nào |
| ⚡ | **Hiệu năng là số một** — giao diện tối giản, tập trung vào chạy mượt |
| 🔒 | **Không kèm APK gốc** — bạn tự nạp file APK của mình, app không đóng gói hay sửa đổi nó |

## 🚀 Bắt đầu nhanh

1. 📥 Tải bản mới nhất ở **[Releases](https://github.com/0Lu-cy0/muse-multi-account/releases)** và cài đặt.
2. 📱 Mở app, bấm **Pick APK** và chọn file APK Muse trên điện thoại của bạn.
3. ➕ Đặt tên clone, bấm **Create**, rồi **Launch**.
4. 🔑 Đăng nhập tài khoản trong clone — xong!

> ⚠️ **Lưu ý quan trọng:** Đừng gỡ cài đặt app chủ khi cập nhật, nếu không toàn bộ clone và tài khoản đã đăng nhập sẽ mất.

## 📊 Trạng thái

- ✅ **1.0.0 — Clone đầu tiên chạy hoàn chỉnh:** hiện màn đăng nhập và đăng nhập thành công.
- 🔄 **Đang test:** 2 clone cùng online, nhãn thông báo, độ trễ, RAM, pin, chạy nền — rồi mới tới 3 và 4.

> ⚠️ **CHỈ MỚI THỬ NGHIỆM TRÊN MỘT MÁY DUY NHẤT**
>
> - 📱 **Máy đã test:** Samsung Galaxy A23 (SM-A235F/DS), Android 14, One UI 6.1, RAM 6 GB, chip arm64-v8a.
> - 🚫 **CHƯA áp dụng và CHƯA kiểm chứng trên các loại máy khác.** Dùng trên máy khác (hãng khác, phiên bản Android khác, chip khác) có thể không chạy được, hoặc gặp lỗi khác — vì mỗi hãng tùy biến Android một kiểu.

## 📖 Hành trình dự án

Muốn biết dự án đã đi từ thiết kế tới ngày chạy được như thế nào, qua những lỗi gì? Đọc ở đây — toàn bộ câu chuyện, song ngữ Việt – Anh:

➡️ **[docs/README_JOURNEY.md](docs/README_JOURNEY.md)**

## 🛠️ Mã nguồn

| 📂 | Nội dung |
|---|---|
| `host/` | Mã nguồn app chủ (Java) |
| `engine/` | Mã nguồn engine BlackBox (Java, AIDL, C++) |
| `build/` | Script build thủ công và thư viện phụ thuộc |
| `docs/` | Tài liệu theo chủ đề |
| `artifacts/aars/` | Các bản engine đã build |
| `archive/` | Mã cũ đã bị thay thế, chỉ để đối chiếu |

---

<div align="center">

💜 **Dành cho người sống với nhiều tài khoản — một app, nhiều tài khoản, không phải đổi qua đổi lại.**

</div>
