# FIX PLAYBOOK — Sổ tay sửa lỗi mở clone (Muse multi-account)

Tài liệu này ghi lại toàn bộ chuỗi sửa lỗi mở clone từ ngày 28/10/2026
(v15 → v20, từ "mở clone vẫn lỗi" tới "vào app được, đăng nhập còn nguyên"),
để lần sau gặp lỗi tương tự thì tra cứu thẳng, không phải mò lại từ đầu.

Đọc kèm: `BUILD_V15.md` … `BUILD_V20.md` (chi tiết từng bản), và các file
log `muse-log-nho-*.txt` (bằng chứng từng lần test trên điện thoại thật).

## 0. Quy trình lấy log (bắt buộc trước mọi lần chẩn đoán)

Trên máy tính, dùng adb của Google platform-tools (KHÔNG dùng bản adb của
MuMuPlayer):

```
cd /d D:\Users\AMC\AppData\Local\platform-tools-latest-windows\platform-tools
adb devices
adb logcat -c
```

→ Trên điện thoại: tắt hẳn app, mở lại từ đầu, bấm mở clone (một lần),
ghi lại giờ làm lại và kết quả nhìn thấy trên màn hình.

→ Sau đó mới đổ log ra file:

```
adb logcat -d > D:\APP_clone\muse-log-day.txt
findstr /i "MuseLaunch MuseImport SplashScreen ActivityStack AndroidRuntime ContentProviderStub BActivityThread BlackBoxCore BlackManager ServiceManager" D:\APP_clone\muse-log-day.txt > D:\APP_clone\muse-log-nho.txt
```

Gửi file nhỏ (`muse-log-nho.txt`) kèm giờ test và kết quả từng clone.

Quy tắc đã rút ra bằng xương máu:

- `adb logcat -d` chỉ đổ MỘT LẦN toàn bộ log còn trong bộ nhớ đệm rồi thoát,
  không chạy ngầm. Muốn ghi liên tục thì dùng `adb logcat > file` (không có
  `-d`) và bấm Ctrl+C để dừng.
- KHÔNG BAO GIỜ pipe logcat trực tiếp qua findstr (đệm + Ctrl+C sẽ làm file
  lọc rỗng). Luôn ghi file đầy đủ trước, rồi mới lọc SAU trên file đã ghi.
- Danh sách từ khóa lọc PHẢI có `ServiceManager` (để thấy dòng "Lazy repair")
  và `BActivityThread` (để thấy dòng khôi phục bước gắn). Thiếu tag là thiếu
  bằng chứng, như đã xảy ra ở log ngày 28/10 buổi trưa.
- Trước khi bấm mở để test: vuốt bỏ hết các cửa sổ đen cũ trong màn hình đa
  nhiệm, rồi bấm mở MỘT lần duy nhất. Mỗi lần bấm mở lại đều tạo thêm một
  cửa sổ mới trong đa nhiệm — cửa sổ đen cũ còn sót sẽ gây nhầm kết quả.

## 1. Bản đồ chuỗi lỗi: mỗi tầng che tầng sau

Thứ tự mở clone đi qua các tầng sau. Một file log chỉ cho thấy tới lỗi chặn
ĐẦU TIÊN; sửa xong tầng đó thì tầng sau mới có cơ hội chạy và lộ lỗi của nó.
Vì vậy "sửa xong lại lòi lỗi mới" là bình thường, không phải sửa hỏng:

```
Mở clone
  → (1) Đọc danh sách app ảo (package_manager)
  → (2) Màn hình khởi chạy LauncherActivity
  → (3) Mở AuraMainActivity (màn hình chính của Muse)
  → (4) Tiến trình khách gắn ứng dụng (bindApplication)
  → Màn hình chính của Muse có nội dung, đăng nhập còn nguyên
```

| Tầng | Triệu chứng trên màn hình | Dấu vết trong log | Nguyên nhân gốc | Sửa ở bản |
|------|---------------------------|-------------------|-----------------|-----------|
| 1 | Báo "dịch vụ engine chưa sẵn sàng", không mở được | `getService: package_manager, null`, poll 5×1s cạn, `virtual list never became ready; refusing install` | Entry package_manager trong map của ServiceManager bị null từ lúc khởi tạo, giữ null mãi | v15 (0.8.5-pmfix) |
| 2 | Không có màn hình nào, chỉ còn dòng trạng thái "Đã mở" trên Container | `Resources$NotFoundException: Resource ID #0x7f0a001c` tại `LauncherActivity.onCreate:95` (setContentView) | Xem §2 (lệch mã resource từ v11) | v16 (0.8.6-launcherfix) |
| 3a | Màn hình splash logo M rồi treo ở đó | `StackOverflowError` vòng `getRunningAppProcesses` → hook → service → API đã bị móc → … | Xem §3 (họ lỗi đệ quy của móc) | v17 (0.8.7-splashfix) |
| 3b | Qua splash rồi màn hình đen | `StackOverflowError` vòng `getApplicationInfo` → hook → service → … rồi `Service died`, rồi `Unable to makeApplication` | Xem §3 (cùng họ, ở móc quản lý gói) | v18 (0.8.8-pmrecursion) |
| 4 | Qua splash rồi màn hình đen (vẫn đen nhưng log khác) | `NullPointerException` ở `BActivityThread.createBindApplicationData:347` / `handleBindApplication:376`: `PackageInfo.applicationInfo` trên đối tượng null | `getPackageInfo(GET_PROVIDERS)` trả null trong tiến trình `:p0` (xem §4) | v19 (0.8.9-bindfix) |
| 3b (tái phát) | Qua splash rồi màn hình đen | Lại `StackOverflowError` vòng `getApplicationInfo`, hook ở dòng 347 (đã dời dòng so với 287 ở log cũ) | Cách sửa v18 chỉ đúng đôi khi — xem §5 | v20 (0.8.10-pmguard) |

Kết quả cuối (28/10/2026 14:08, log muse-log-nho-10.txt): `AuraMainActivity`
`onActivityCreated` 14:08:28 rồi `onActivityResumed` 14:08:29, KHÔNG còn
StackOverflowError, FATAL, Service died hay lỗi gắn ứng dụng nào. Người dùng
vào thẳng màn hình chính ĐÃ ĐĂNG NHẬP SẴN — đăng nhập sống sót qua chuỗi
cập nhật v15→v20.

## 2. Bài học lớn nhất: mã resource bị biên dịch cứng (baked R)

Engine (BlackBox/Bcore) được lấy DẠNG BIÊN DỊCH SẴN từ file AAR, không biên
dịch lại cùng app chính. Trong Java, các hằng `R.*` là `public static final
int` nên trình biên dịch CHÉP LUÔN GIÁ TRỊ SỐ vào class của engine.

Hệ quả: bất cứ khi nào app chính thêm một LOẠI tài nguyên mới, công cụ aapt2
đánh lại số thứ tự cho mọi loại phía sau, và mọi hằng R đã chép cứng trong
engine đều lệch theo.

Bằng chứng thực tế (so bảng tài nguyên v10 với v11):

- v11 thêm `<string-array name="vpn_countries">` (danh sách nước VPN) → một
  loại `array` mới xuất hiện ở đầu → mọi loại sau dời lên 1: layout
  `0x0a → 0x0b`, drawable, id, string đều dời theo.
- `LauncherActivity` trong engine vẫn tìm layout ở mã cũ `0x7f0a001c`, trong
  khi file layout thật đã chuyển sang `0x7f0b001c` → crash ngay khi nạp
  layout. Lỗi này nằm im từ v11, chỉ lộ ra khi v15 sửa xong tầng 1 để lần mở
  đi tới được màn hình khởi chạy.
- Cùng cơ chế, sâu hơn một tầng: `BlackBoxCore.doAttachBaseContext` nhận
  dạng tiến trình `:black` bằng `getString(R.string.black_box_service_name)`.
  Mã đó bị chép cứng là `0x7f0b001c` — ở bản v4k thì đúng là tên dịch vụ,
  nhưng từ v11 trở đi `0x7f0b001c` lại là file layout `activity_launcher`,
  còn tên dịch vụ đã chuyển sang `0x7f0d0027`. Vì vậy ở mọi bản v11+,
  `:black` bị nhận dạng sai thành tiến trình app khách, `isServerProcess()`
  luôn sai trong toàn bộ tiến trình (không phải lỗi theo luồng), các cách
  sửa dựa vào nhận dạng đó ở v17/v18 không bao giờ có tác dụng, và các móc
  vốn bị tắt ở `:black` trong v4k lại bật lên — sinh ra cả họ lỗi đệ quy.

Cách sửa (v16 + v20):

- Màn hình khởi chạy: bỏ hẳn `setContentView(R.layout...)`, vẽ bằng code
  (LinearLayout dọc, nền trắng, icon 96dp, tên 18sp) — không dùng mã layout
  bị chép cứng nữa.
- Nhận dạng `:black`: kiểm tra bằng tên thật `processName.endsWith(":black")`
  (tên tiến trình trong manifest được giải quyết từ tài nguyên ĐÃ GỘP lúc
  cài đặt, không bị lệch mã). Đã kiểm tra bằng javap: đoạn code mới dùng
  chuỗi `":black"`, không còn gọi `getString` nào ở đó.

QUY TẮC VÀNG cho mọi sửa engine sau này: KHÔNG BAO GIỜ tin một hằng R nào
trong code engine biên dịch sẵn. Muốn dùng tài nguyên thì hoặc vẽ bằng code,
hoặc tra cứu lúc chạy, hoặc dùng chuỗi/tên thật. Thêm loại tài nguyên mới
vào app chính là phải kiểm tra lại toàn bộ hằng R của engine.

## 3. Họ lỗi đệ quy của móc (hook recursion) — nhận dạng và cách sửa

Mẫu lỗi chung, gặp ở cả móc ActivityManager, móc quản lý gói và móc thông
báo:

```
App khách gọi API hệ thống (vd: getRunningAppProcesses, getApplicationInfo)
  → móc chặn lại, chuyển cho dịch vụ ảo trong :black
  → dịch vụ ảo lại gọi đúng API hệ thống đó (đã bị móc)
  → móc lại chặn, lại chuyển cho dịch vụ ảo
  → … lặp tới khi tràn ngăn xếp (StackOverflowError, 991KB), :black chết
```

Cách nhận dạng trong log: cùng một dãy khung xếp chồng lặp đi lặp lại
(service → framework → hook → ClassInvocationStub → proxy → …), kết thúc
bằng `StackOverflowError` trên luồng binder của `:black`, rồi tiếp theo là
`Service died: activity_manager / package_manager / storage_manager`.

Ba cách sửa, theo thứ tự ưu tiên (đã áp dụng hết trong v17/v18/v20):

1. **Trả lời từ dữ liệu nội bộ, đừng gọi API đã bị móc.** Ví dụ v17:
   `BActivityManagerService.getRunningAppProcesses` dựng danh sách từ hồ sơ
   tiến trình nội bộ (`BProcessManagerService`), không chạm API hệ thống nữa.
2. **Cho qua (bypass) đúng chỗ, đúng điều kiện.** Khi dịch vụ ảo ở `:black`
   cần đáp án thật của hệ thống thì móc phải cho qua thẳng dịch vụ thật
   (`method.invoke` trên đối tượng gốc), NHƯNG chỉ khi đủ điều kiện.
   Điều kiện đúng là `isServerProcess()` MỘT MÌNH. CÁI BẪY đã bắt được ở
   v17: `isBlackProcess()` cũng đúng trong tiến trình app KHÁCH (vì nó chỉ
   có nghĩa là "đang chạy dưới BlackBox"), nên dùng nó sẽ đưa đáp án thật
   không lọc cho app khách và phá luôn việc gắn nhãn thông báo. Đoạn code
   cũ `BindServiceCommon` dùng điều kiện gộp đó vẫn giữ nguyên, không sửa.
   Ngoài ra bypass phải giới hạn theo gói khi cần: ở v18, bypass quản lý
   gói chỉ áp dụng khi hỏi đúng gói host — gói ảo vẫn phải đi qua dịch vụ
   (hệ thống thật không biết gì về gói ảo, trả về sẽ là "không tìm thấy"
   trong khi dịch vụ ảo có đáp án đúng).
3. **Chốt chống lặp (re-entrancy guard) làm lưới an toàn cuối cùng.**
   Đếm độ sâu theo luồng (ThreadLocal) trong móc; nếu cùng luồng quay lại
   móc lần nữa thì đi thẳng tới dịch vụ thật, không vòng lại. Luôn trả lại
   độ sâu trong `finally` (luồng binder được dùng lại từ pool, cờ cũ sót
   lại sẽ làm sai các lần gọi sau). Dòng log khi chốt hoạt động (tag
   `BlackManager`, đã nằm trong danh sách lọc): `PM guard tripped`.

Kết quả kiểm chứng ở v20: lần mở thành công KHÔNG có dòng `PM guard tripped`
nào — chứng tỏ chỉ riêng việc sửa nhận dạng `:black` (§2) đã đủ, chốt chống
lặp không cần phải hoạt động. Nếu lần sau log có dòng đó mà vẫn mở được thì
chốt đã cứu một vòng lặp khác; nếu vẫn lỗi thì xem §6.

Các móc đã rà soát một thể ở v17 (đừng rà lại từ đầu nếu không cần): toàn
bộ 37 móc ActivityManager + mọi proxy mà `HookManager.init()` cài ở tiến
trình khách và `:black`. Đã sửa: `GetServices` (danh sách dịch vụ tổng hợp
từ hồ sơ nội bộ), 6 móc thông báo thay đổi (chỉ đi qua dịch vụ thật khi ở
tiến trình dịch vụ, để thông báo clone vẫn giữ nhãn tên clone). Đã kiểm tra
và giữ nguyên: các móc còn lại (gọi `method.invoke` trên đối tượng gốc,
trả tổng hợp, hoặc dịch vụ không gọi API đã bị móc sau khi sửa).

## 4. Lỗi gắn ứng dụng (bindApplication) và cơ chế khôi phục v19

Sau khi `:black` đã sống, bước gắn ứng dụng của tiến trình khách vẫn có thể
chết theo hai kiểu — phân biệt bằng log:

- **Kiểu hệ quả (log-7):** `:black` chết trước → bước gắn thất bại với
  `ClassCastException: ContextImpl cannot be cast to Application` rồi
  `Unable to makeApplication - all fallback attempts failed`. Sửa `:black`
  sống lại là hết, không cần sửa bước gắn.
- **Kiểu riêng (log-8):** `:black` sống, quản lý gói khỏe (binderNull=false),
  nhưng `getPackageInfo(GET_PROVIDERS)` trả null trong tiến trình `:p0` →
  `NullPointerException` ở `createBindApplicationData:347` /
  `handleBindApplication:376` khi đọc `packageInfo.applicationInfo`.

Nguyên nhân sâu của kiểu riêng CHƯA chứng minh được tới một cơ chế duy nhất
(hai ứng viên: một thành phần trong nhánh GET_PROVIDERS gây lỗi làm null cả
gói; hoặc userId trong câu hỏi ở `:p0` khác với lúc tạo tiến trình). Vì vậy
v19 sửa theo hướng "khôi phục có ghi log", không đoán một cơ chế:

Thứ tự khôi phục trong `getBindPackageInfo` (dùng chung cho cả hai chỗ gắn),
mỗi bước đều ghi log dưới tag `BActivityThread`:

1. Gọi gốc với cờ GET_PROVIDERS.
2. Gọi lại với cờ 0 (cờ đã chứng minh là chạy được trong log-8).
3. Nếu userId khác 0 thì gọi lại với userId 0.
4. Tổng hợp `PackageInfo` từ `getApplicationInfo` (providers = mảng rỗng —
   giới hạn đã ghi rõ: đi đường này thì ContentProvider của khách sẽ không
   được cài từ bước gắn, nên lớp này chỉ là lưới cuối).
5. Nếu tất cả đều thất bại: ghi log tên gói, userId và danh sách gói đã cài,
   rồi ném `RuntimeException` có thông tin thay vì NPE trần.

Đồng thời `PackageManagerCompat.generatePackageInfo` được gia cố: mỗi thành
phần trong các nhánh GET_ACTIVITIES / RECEIVERS / SERVICES / PROVIDERS /
INSTRUMENTATION được bọc try/catch, thành phần lỗi hoặc null thì chỉ bị bỏ
qua riêng thành phần đó, không làm null cả gói nữa.

Cách đọc log lần sau (đã ghi trong BUILD_V19.md §6):

- Bước 1 thành công, không có dòng khôi phục: cơ chế (a) đã hết hoặc chỉ
  thoáng qua.
- Có dòng `flags=0 succeeded`: nhánh cờ vẫn còn thành phần gây lỗi — kiểm
  tra tiếp nhánh đó còn ném ở đâu.
- Có dòng `userId=0 succeeded`: cơ chế (b) — sửa tiếp đường dẫn userId của
  AppConfig.
- Có dòng `ALL steps failed` kèm danh sách gói đã cài: gói thật sự vắng mặt
  trong registry của user đó — lỗi trạng thái cài đặt/registry, không phải
  lỗi câu hỏi.

## 5. Sửa quản lý gói v15 (lazy repair) — và cách xác nhận nó

Triệu chứng: mọi dịch vụ khác đều trả binder khỏe, chỉ riêng
`package_manager` luôn null, qua nhiều lần mở và nhiều tiến trình mới — không
phải "khởi động chậm", chờ lâu hơn cũng không hết.

Cơ chế (đã xác minh từ bytecode, nhưng chuỗi kích hoạt chính xác thì chưa
chứng minh tới mức code — ghi rõ để không ai tưởng là đã rõ): map dịch vụ
của `ServiceManager` chỉ xây MỘT lần ở constructor, mỗi dịch vụ lấy từ
singleton tĩnh của nó. Nếu tại thời điểm đó `BPackageManagerService.get()`
trả null (nghi vấn mạnh nhất: một lần gọi đệ quy cùng luồng trong lúc khởi
tạo class làm bẩn entry), map sẽ giữ null mãi mãi vì singleton không bao
giờ được dựng lại.

Cách sửa (v15, engine bcore-v4l): trong `ServiceManager.getServiceInternal`,
khi entry trong cache là null thì dựng lại dịch vụ đó trực tiếp từ singleton
tĩnh của nó (package_manager qua `BPackageManagerService.get()`, tương tự
cho 8 dịch vụ còn lại) rồi cache kết quả. Nếu singleton cũng dựng không
được thì bắt lỗi và trả null — không làm tệ hơn trước.

Cách xác nhận trong log (tag `ServiceManager`, PHẢI có trong danh sách lọc):

- `Lazy repair: rebuilt null entry for package_manager` → sửa có tác dụng.
- `Lazy repair failed for package_manager` → singleton không dựng được,
  hướng điều tra chuyển sang khởi tạo class của dịch vụ quản lý gói.
- `Lazy repair: singleton still null for package_manager` ở lần gọi đầu
  tiên là BÌNH THƯỜNG (chưa kịp tái tạo), không phải thất bại — chỉ khi nó
  xuất hiện mà không có dòng `rebuilt` theo sau mới là vấn đề.

Đã xác nhận thật trong log ngày 28/10: dòng `rebuilt` xuất hiện lúc
12:37:54 và 12:38:26. Từ đó câu hỏi quản lý gói được coi là ĐÃ ĐÓNG, không
mở lại trừ khi log mới cho thấy ngược lại.

## 6. Checklist chẩn đoán nhanh cho lần sau

Khi có log mới, đi theo đúng thứ tự này, đừng nhảy cóc:

1. Có `StackOverflowError` không? → Xem dãy khung lặp: tên phương thức nào
   lặp (getRunningAppProcesses / getApplicationInfo / getPackageInfo / …)?
   Đó chính là móc cần sửa (§3). Nếu là móc đã sửa rồi mà vẫn lặp → kiểm
   tra điều kiện bypass của nó (có đúng tiến trình/gói không) và chốt chống
   lặp có hoạt động không (`PM guard tripped`).
2. Có `Service died` không? → `:black` đã chết. Tìm lỗi gây chết TRƯỚC dòng
   đó (thường là StackOverflowError hoặc NotFoundException), đừng chữa các
   dòng "service is null / using fallback" phía sau — chúng chỉ là hệ quả.
3. Có `Resources$NotFoundException` với mã `0x7f..` không? → Đối chiếu mã
   đó với bảng tài nguyên của APK hiện tại (aapt2 dump resources): mã đó
   thuộc loại nào, có tồn tại không, hay đã dời sang mã khác từ khi thêm
   loại tài nguyên mới (§2). Nghi ngờ đầu tiên luôn là hằng R bị chép cứng
   trong engine.
4. Có `Unable to makeApplication` không? → Xem nguyên nhân kèm theo:
   ClassCastException sau khi `:black` chết = hệ quả (§4 kiểu hệ quả);
   NPE ở `createBindApplicationData` trong khi `:black` sống = kiểu riêng,
   đọc tiếp các dòng khôi phục của v19 (§4).
5. Không có lỗi nào mà vẫn đen/trắng? → Kiểm tra `AuraMainActivity` có
   `onActivityResumed` không. Có Resumed mà màn hình vẫn đen → vấn đề vẽ/
   surface, không phải các tầng trên. Không có Resumed → quay lại bước 1–4
   theo đúng thứ tự tầng (§1).
6. Log có thiếu tag không? → Nếu không thấy dòng cần tìm (Lazy repair, PM
   guard, khôi phục bước gắn), kiểm tra lại danh sách lọc trước khi kết
   luận "không có" — rất có thể dòng đó bị lọc mất chứ không phải không
   xảy ra.

## 7. Quy tắc build và phát hành (đừng bỏ bước nào)

- Mỗi bản là MỘT commit nguồn duy nhất trên main, KHÔNG force-push, KHÔNG
  viết lại commit đã đẩy. Chỉ đẩy code nguồn: KHÔNG đưa file APK, file log,
  APK của app khách, keystore vào git. APK chỉ lên GitHub Release.
- Trước khi giao bản: kiểm tra độc lập file APK — đúng dung lượng byte,
  đúng SHA-256, apksigner VERIFY_OK, cùng chữ ký với mọi bản trước (chữ ký
  debug SHA-256 `f1fb90f5…`), đúng package `com.example.musecontainer`,
  đúng versionCode/versionName/minSdk/targetSdk (badging). Cài đè phải
  dùng `-r`, KHÔNG gỡ app trước — gỡ sẽ mất clone và đăng nhập.
- Phát hành ngay trong cùng lần giao (quy tắc từ 28/10): tạo Release (nội
  dung phải có dòng `versionCode: N` để bộ cập nhật trong app đọc dự phòng
  được) + tải APK lên Release qua trình duyệt (API tải file bị chặn ở máy
  này) + cập nhật `updates.json` trên main + đọc lại file raw để kiểm
  chứng. Cập nhật trong app: tab Tiện ích → "Kiểm tra cập nhật".
- Mọi lần giao bản sửa lỗi đều kèm trong CÙNG MỘT TIN: cách cập nhật +
  cách chạy log + tiêu chuẩn đạt của lần test đó (quy tắc từ 28/10).
- Xóa clone là xóa luôn user ảo của nó (đăng nhập mất theo). Clone bị mất
  mà người dùng không tự xóa mới là lỗi mất dữ liệu — còn người dùng tự
  xóa thì không phải lỗi, và clone đó sẽ không tự quay lại sau cập nhật.
  Muốn có lại thì tạo clone mới và đăng nhập lại từ đầu.

## 8. Quy tắc đánh số phiên bản (Semantic Versioning)

Từ bản 1.0.0 (28/10/2026) trở đi, số phiên bản hiển thị theo dạng
MAJOR.MINOR.PATCH (đây là cách đánh số chuẩn của các app hiện tại):

- Tăng số ĐẦU (MAJOR): thay đổi lớn, mốc quan trọng, hoặc thay đổi không
  tương thích. Ví dụ: 0.8.10 → 1.0.0 là bản hoàn thiện đầu tiên sau chuỗi
  sửa lỗi, không phải vì sửa một lỗi nhỏ.
- Tăng số GIỮA (MINOR): thêm tính năng mới nhưng vẫn tương thích ngược.
  Ví dụ: 1.0.0 → 1.1.0.
- Tăng số CUỐI (PATCH): sửa lỗi nhỏ, không thêm tính năng. Ví dụ:
  1.0.0 → 1.0.1.
- Dãy 0.x có nghĩa là "chưa ổn định": bản đầu tiên chạy được (v4k) được
  đánh lại thành 0.1.0 cho đúng ý nghĩa đó; 1.0.0 là bản ổn định đầu tiên.

Riêng `versionCode` của Android là số nguyên nội bộ, KHÔNG phải số hiển
thị: mỗi bản build phải tăng ít nhất 1 thì hệ thống và bộ cập nhật trong
app mới nhận ra có bản mới. Đổi tên hiển thị mà không đổi code build thì
không cần tăng versionCode (vì bit đã giống nhau), nhưng bản build tiếp
theo PHẢI mang cả tên semver mới trong badging lẫn versionCode tăng tiếp.

Lịch sử mốc (đừng đổi lại):

- 0.1.0 (tag v0.1.0, Release đầu tiên): bản v4k chạy được đầu tiên
  (27/10/2026, file `muse-container-host-v4k-debug.apk`, 1.690.431 byte).
- 0.8.1 → 0.8.10: chuỗi bản sửa giao diện, cập nhật trong app, rồi sửa lỗi
  mở clone (v11→v20, versionCode 22→31).
- 1.0.0 (tag v1.0.0): đúng bit của bản 0.8.10-pmguard (versionCode 31) —
  bản hoàn thiện đầu tiên sau chuỗi sửa lỗi.
