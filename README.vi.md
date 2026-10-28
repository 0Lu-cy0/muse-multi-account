<div align="center">

# 📱 Muse Multi-Account dành cho Android

### Chạy nhiều tài khoản Muse trên một điện thoại Android — chỉ dành cho Android, không dùng cho iOS

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
| 🔄 | **Tự cập nhật (từ bản v10)** — kiểm tra và cài cập nhật ngay trong app; xem [Tự cập nhật (từ bản v10)](#tự-cập-nhật-từ-bản-v10) |

## 🚀 Bắt đầu nhanh

1. 📥 Tải bản mới nhất ở **[Releases](https://github.com/0Lu-cy0/muse-multi-account/releases)** và cài đặt.
2. 📱 Mở app, bấm **Pick APK** và chọn file APK Muse trên điện thoại của bạn.
3. ➕ Đặt tên clone, bấm **Create**, rồi **Launch**.
4. 🔑 Đăng nhập tài khoản trong clone — xong!

> ⚠️ **Lưu ý quan trọng:** Đừng gỡ cài đặt app chủ khi cập nhật, nếu không toàn bộ clone và tài khoản đã đăng nhập sẽ mất.

## 🔄 Tự cập nhật (từ bản v10)

Từ bản v10 trở đi, app có thể tự kiểm tra và cài cập nhật ngay trong app — bạn không cần máy tính và adb cho mỗi lần cập nhật nữa.

> ⚠️ **Các bản v9 trở về trước không có tính năng này** — kể cả bản 1.0.0 công khai. Lần đầu cài bản v10 vẫn phải cài thủ công (cài đè lên bản cũ, tuyệt đối đừng gỡ cài đặt trước, để clone và tài khoản đăng nhập còn nguyên). Chỉ từ bản v10 trở đi thì cập nhật mới làm trong app được.

**Cách cập nhật:**
1. Mở app, mở mục **Ghi chú** ở cuối màn hình chính.
2. Bấm **Kiểm tra cập nhật**.
3. Nếu có bản mới hơn, app sẽ hiện phiên bản mới và nội dung thay đổi.
4. App tải bản cập nhật về, kiểm tra mã SHA-256, khớp thì mới mở bảng cài đặt của hệ thống để bạn xác nhận.

**Cần có:** mạng internet, truy cập được GitHub Releases, và quyền "cho phép cài đặt từ nguồn này" cho app chủ trong Cài đặt hệ thống, chỉ cần bật một lần (nếu chưa bật, app sẽ dẫn bạn tới đó).

**Giới hạn cần biết:**
- Android luôn bắt bạn xác nhận trong bảng cài đặt của hệ thống — không có chuyện cài trong im lặng.
- Cập nhật lấy từ các bản Releases đã đăng trên repo này. Cây git chỉ chứa mã nguồn, không chứa file APK, nên chỉ khi nào có bản Releases mới hơn thì app mới báo có cập nhật.
- Mọi bản cập nhật phải mang cùng chữ ký với bản đang cài, nếu khác chữ ký thì Android sẽ từ chối cập nhật.

## 📊 Trạng thái

- ✅ **1.0.0 — Clone đầu tiên chạy hoàn chỉnh:** hiện màn đăng nhập và đăng nhập thành công.
- 🔄 **Đang test:** 2 clone cùng online, nhãn thông báo, độ trễ, RAM, pin, chạy nền — rồi mới tới 3 và 4.

> ⚠️ **CHỈ MỚI THỬ NGHIỆM TRÊN MỘT MÁY DUY NHẤT**
>
> - 📱 **Máy đã test:** Samsung Galaxy A23 (SM-A235F/DS), Android 14, One UI 6.1, RAM 6 GB, chip arm64-v8a.
> - 🚫 **CHƯA áp dụng và CHƯA kiểm chứng trên các loại máy khác.** Dùng trên máy khác (hãng khác, phiên bản Android khác, chip khác) có thể không chạy được, hoặc gặp lỗi khác — vì mỗi hãng tùy biến Android một kiểu.

## ⚠️ An toàn tài khoản — đọc trước khi dùng

- 📧 **Dùng email, đừng dùng số điện thoại cho tài khoản.** Số điện thoại là định danh rất mạnh: nó có thể liên kết nhiều tài khoản với nhau, và liên kết tới một người thật, một SIM, một nhà mạng. Mỗi tài khoản nên dùng một email riêng.
- 📱 **Nếu bạn vẫn dùng số điện thoại thì đó là lựa chọn và rủi ro của bạn.** Hậu quả có thể xảy ra gồm: các tài khoản bị liên kết với nhau, bị bắt xác minh thêm, bị gắn cờ hoặc bị khóa, hoặc mất quyền truy cập tài khoản.
- 🛠️ **Những gì chúng tôi lo được và không lo được.** Những gì chúng tôi lo được là app chạy tốt: các clone tách biệt nhau, và chỗ nào làm được về mặt kỹ thuật thì sẽ tách riêng danh tính và mạng cho từng clone. Cách bạn dùng tài khoản ra sao thì chúng tôi không kiểm soát được. Các cảnh báo này được đưa ra trước để bạn quyết định khi đã biết rủi ro. Chỗ nào ngăn được về mặt kỹ thuật thì chúng tôi sẽ bổ sung biện pháp ngăn đó; còn trước khi làm được thì cảnh báo này vẫn có hiệu lực.

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

## 🤝 Đóng góp" body, keeping the existing email line

## 🤝 Đóng góp

Chúng tôi hoan nghênh góp ý giao diện và đóng góp tự nguyện — góp ý giao diện không cần biết code.

- **Góp ý giao diện:** mở một Discussion trong repo để nêu ý tưởng, hoặc mở một Issue bằng mẫu "Góp ý giao diện / UI feedback". Ghi rõ màn hình nào, kèm ảnh chụp màn hình (đã che tên tài khoản và nội dung riêng tư), điện thoại và phiên bản app, vấn đề đang thấy và đề xuất sửa cụ thể. App mới chỉ được kiểm chứng trên Samsung Galaxy A23 (Android 14), nên góp ý từ các máy khác càng quý.
- **Đóng góp code:** xem CONTRIBUTING.md. Tóm tắt: fork repo, tạo nhánh, mở Pull Request về `main`. Nhánh `main` được bảo vệ, và file APK không bao giờ đưa vào cây git — APK chỉ lên GitHub Releases.
- Gửi góp ý hay Pull Request không có nghĩa là chắc chắn được đưa vào — người duy trì quyết định cuối cùng.

Mọi đóng góp cho dự án này, vui lòng liên hệ qua email: **nc12042004@gmail.com**
---

<div align="center">

💜 **Dành cho người sống với nhiều tài khoản — một app, nhiều tài khoản, không phải đổi qua đổi lại.**

</div>
