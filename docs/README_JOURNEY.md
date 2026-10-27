# Hành trình của dự án này — từ thiết kế tới ngày app chạy được

> Đây KHÔNG phải tài liệu mô tả app. Đây là nhật ký hành trình:
> dự án đã bắt đầu từ đâu, đã đi qua những ngã rẽ nào, đã vấp
> những lỗi gì, và cuối cùng đã chạy được bằng cách nào.
> Tài liệu mô tả app nằm ở `README.md`, không phải ở đây.

## 1. Điểm xuất phát (27-10-2026)

Yêu cầu ban đầu rất rõ ràng và cũng rất khó: một app có thể chạy
nhiều tài khoản Muse cùng lúc, mỗi tài khoản đăng nhập độc lập,
nhận thông báo liên tục — không giới hạn 2 bản như Dual Messenger,
không đóng băng tài khoản không đang mở như Secure Folder.
Mục tiêu thực tế đặt ra: 2 tài khoản trước, rồi 3, rồi 4 chỉ khi
3 đã qua. Hiệu năng là yêu cầu số một, giao diện có thể xấu cũng
được.

## 2. Thiết kế: tại sao lại là container ảo

Ba hướng đã bị loại trước khi viết code:

- **Dùng sẵn Dual Messenger / Secure Folder / Work Profile:**
  trần 2–3 bản, không đạt "bao nhiêu cũng được".
- **Đóng gói lại APK thành nhiều package khác nhau:**
  đổi package + ký lại sẽ phá vỡ chữ ký gốc của Meta, kéo theo
  đăng nhập, FCM và thanh toán đều hỏng. Không làm được.
- **Viết lại app:** không ai viết lại được Muse.

Hướng còn lại duy nhất: **một app chủ (host) nạp APK gốc do
người dùng tự cung cấp vào bên trong**, mỗi clone là một user
ảo với dữ liệu riêng, không phải một bản cài đặt thật. APK gốc
không bao giờ được đóng gói, sửa đổi hay đưa lên repo này.
Stack được chốt: host Java, engine C++ qua NDK theo mô hình
BlackBox/VirtualApp. Flutter, React Native và Rust đều bị loại.
Gradle trên máy build này hỏng toàn cục (cả project trắng cũng
lỗi), nên toàn bộ quá trình build chuyển sang biên dịch thủ công
bằng javac + aapt2 + apksigner — và cách đó đã gắn bó với dự án
từ đó tới nay.

## 3. Những bản đầu: từ khung xương tới khung thật

- **v1:** chỉ là khung host, chưa nối engine. Launch trả về
  "chưa tích hợp".
- **v2:** nối engine BlackBox thật. Lần đầu tiên Launch không
  còn là giả nữa.
- **v3 (sau một vòng rà soát toàn bộ):** sửa một loạt vấn đề
  — quyền thừa, provider bị export, mã user ảo mặc định về 0,
  tên file import trùng nhau, lưu clone không nguyên tử, xóa
  và cập nhật clone. Đây là bản đầu tiên có kỷ luật kỹ thuật.
- **v4:** thêm gắn nhãn thông báo theo tên clone, để về sau
  phân biệt được thông báo của tài khoản nào. Phần này tới nay
  vẫn chưa được kiểm chứng trên điện thoại thật, vì đường chạy
  còn chưa thông.

## 4. Chuỗi lỗi trên điện thoại thật — và cách mỗi lỗi bị hạ

Từ đây, mỗi lần test trên điện thoại thật lại lòi ra một lỗi
mới, và mỗi lỗi đều có bằng chứng log riêng, không phải đoán:

1. **Tiến trình phụ chết vì thiếu Application (v4a → v4b).**
   Host không có lớp Application, nên tiến trình `:black` chạy
   khởi tạo engine với context rỗng. Sửa: thêm Application gọi
   khởi tạo cho mọi tiến trình.
2. **Màn hình chờ vỡ layout (v4b → v4c).**
   Layout dùng ConstraintLayout nhưng bộ công cụ thủ công không
   có thư viện lõi của nó. Sửa: thay bằng LinearLayout thuần.
3. **Bẫy fallback: Launch trắng tinh (v4c → v4d/v4e/v4f).**
   Engine hỏi hệ thống "app này đã cài chưa", hệ thống trả lời
   "rồi" vì app Muse thật đang có trên máy, thế là engine bỏ qua
   bước cài ảo và mở... không gì cả. Sửa: không tin câu trả lời
   đó nữa, tự kiểm tra danh sách ảo trực tiếp.
4. **Lỗi cờ PendingIntent của Firebase (v4d → v4e).**
   Trong môi trường ảo không có Google Play Services, Firebase
   chạy đường cũ và tạo PendingIntent không cờ, Android 12+
   cấm. Sửa: chặn riêng đúng lỗi đó, đồng thời cố cài GMS thật
   vào user ảo trước khi chạy. Lỗi này về sau chưa từng tái
   hiện, vì guest chết trước khi tới được đoạn đó.
5. **Lỗi Settings làm guest chết ngay khi hiện màn hình
   (v4f → v4g → v4h).** Đây là lỗi dài nhất. Ban đầu tưởng
   provider trả về con trỏ rỗng, sửa thành trả về con trỏ rỗng
   bảng — không đủ. Rồi phát hiện đường gọi Settings đi qua
   `call` chứ không qua `query`, và các provider lấy sau lúc
   khởi tạo chưa từng được bọc. Sửa: bọc cả provider lấy muộn,
   giữ nguyên tên lệnh và dùng UID thật cho provider hệ thống.
6. **Hồi quy: guest chết còn sớm hơn (v4h → v4i).**
   Bản sửa ở bước 5 vô tình bọc cả provider dịch vụ nội bộ của
   BlackBox và làm hỏng tên lệnh gọi dịch vụ. Sửa: không thay
   tên lệnh, không bọc provider nội bộ. Nhưng test cho thấy vẫn
   chết đúng chỗ — giả thuyết tên lệnh bị bác bỏ.
7. **Lỗi gốc thật: lệch mã UID (v4i → v4j).**
   Log bản chẩn đoán ghi rõ mã nguồn là 10000 — mã UID ảo lúc
   app chưa nạp cấu hình — trong khi máy chủ chỉ chấp nhận mã
   UID thật của tiến trình gọi. Mọi máy chủ provider thật đều
   kiểm tra điều này, nên mọi cuộc gọi đều bị từ chối, mọi dịch
   vụ đều trả về rỗng, guest không tạo nổi Application. Sửa:
   ghi mã UID thật ở mọi nơi từng ghi mã ảo.

Giữa chuỗi này còn có một bài học về quy trình, không phải về
code: file tải qua đính kèm chat nhiều lần bị hỏng trên máy
người test, trong khi file trên máy build vẫn nguyên vẹn. Từ đó
quy tắc là luôn kiểm tra kích thước + mã băm trước khi cài, và
ưu tiên link tải trực tiếp. Cũng từ đó, log được lọc sau khi ghi
xong thay vì lọc trực tiếp lúc đang ghi, vì lọc trực tiếp làm
mất dữ liệu.

## 5. Bước ngoặt: ngừng đoán, bắt đầu đo (v4k)

Sau khi giả thuyết tên lệnh bị bác bỏ, dự án đổi phương pháp:
thay vì mỗi bản đoán một nguyên nhân, bản v4k chỉ thêm log chẩn
đoán ở từng chặng của đường lấy dịch vụ — gọi tới được stub
chưa, tới được máy chủ chưa, máy chủ trả về cái gì, lấy client
có thất bại không — mà không đổi hành vi nào khác. Một lần chạy
duy nhất đã trả lời tất cả: mọi dịch vụ đều lấy được, mã UID hai
bên trùng nhau. Giả thuyết UID được chứng minh, không còn là
đoán nữa.

## 6. Ngày app chạy được (27-10-2026)

- **19:16:02** — Launch clone, cài guest và GMS thành công.
- **19:16:08–19:16:09** — Màn hình chính của Muse được tạo và
  hiện lên, guest không chết nữa.
- **19:19** — Người dùng xác nhận: hiện màn đăng nhập và đăng
  nhập thành công. Đây là clone đầu tiên chạy hoàn chỉnh từ đầu
  tới cuối.

Hai dòng FATAL còn lại trong log hôm đó không phải của guest:
một của Google Play Services, một của Play Store chạy nền.

## 7. Những gì vẫn chưa xong (tính tới lúc viết file này)

- Kiểm chứng 2 clone cùng online: tạo clone thứ hai với tên
  khác, đăng nhập tài khoản thứ hai, kiểm tra nhãn thông báo,
  bấm thông báo có mở đúng tài khoản không, độ trễ, RAM, pin,
  và việc hệ thống có tắt app ngầm không. Rồi mới tới 3, rồi 4
  chỉ khi 3 đã qua.
- Phần gắn nhãn thông báo (v4) và phần sửa lỗi PendingIntent
  (v4e) vẫn chưa từng được kiểm chứng trên một guest sống sót.
- Câu hỏi "VPN giả" vẫn chưa có quyết định: đổi IP thật, chỉ
  gắn cờ giả, giả GPS, hay cho tất cả clone qua một VPN chung
  của host. Chưa build gì cho hướng này trước khi có lựa chọn.
- Một repo công rỗng tạo nhầm trước đây vẫn cần người dùng xóa
  tay trên GitHub, vì token API không có quyền xóa repo.

## 8. Bài học đọng lại

- Đừng đoán nguyên nhân khi có thể đo được nó: một bản chỉ ghi
  log, không sửa gì, đã kết thúc chuỗi đoán mò dài nhất của dự
  án.
- Mỗi lần sửa phải có một test phân biệt được đúng/sai, và test
  đó phải chạy trên điện thoại thật, không phải trên lý thuyết.
- Khi một bản sửa làm lỗi chuyển sang sớm hơn thay vì biến mất,
  đó là tín hiệu bản sửa đã chạm vào đúng khu vực — nhưng chạm
  sai chỗ. Quay lại và đọc lại log thay vì đoán thêm cơ chế mới.
- Giữ lại mọi bằng chứng theo phiên bản (log, mã băm, số phiên
  bản): chính những thứ đó đã giúp phân biệt lỗi mới với lỗi cũ
  khi các file log cứ dài ra và chồng lấn nhau.

---

# Project journey — from design to the day the app ran

> This is NOT an app description. This is the project journal:
> where the project started, which turns it took, which bugs it
> hit, and how it finally ran. The app description lives in
> `README.md`, not here.

## 1. Starting point (27-10-2026)

The original requirement was clear and hard: one app that can run
many Muse accounts at the same time, each logged in independently
and receiving notifications continuously — not limited to 2 copies
like Dual Messenger, and not freezing the inactive accounts like
Secure Folder. The practical target was: 2 accounts first, then 3,
then 4 only if 3 passed. Performance was requirement number one;
an ugly UI was acceptable.

## 2. Design: why a virtual container

Three directions were ruled out before any code was written:

- **Dual Messenger / Secure Folder / Work Profile:** capped at
  2–3 copies, so "as many as you want" was impossible.
- **Repackaging the APK into several different packages:**
  changing the package name and re-signing breaks Meta's original
  signature, which breaks login, FCM and billing. Not viable.
- **Rewriting the app:** nobody rewrites Muse.

The only remaining direction: **a host app that loads the
user-supplied original APK inside itself**, each clone being a
separate virtual user with its own data, not a real install. The
original APK is never bundled, modified, or uploaded to this repo.
The stack was settled: Java host, C++ engine via NDK, following
the BlackBox/VirtualApp model. Flutter, React Native and Rust
were ruled out. Gradle was broken VM-wide (even a blank project
failed), so the whole build moved to manual javac + aapt2 +
apksigner — and that toolchain has stayed with the project ever
since.

## 3. Early builds: from skeleton to real frame

- **v1:** host skeleton only, no engine attached. Launch returned
  "not integrated".
- **v2:** the real BlackBox engine attached. Launch stopped being
  fake.
- **v3 (after a full audit):** fixed a batch of issues — excess
  permissions, exported providers, a virtual user id defaulting
  to 0, import filename collisions, non-atomic clone storage,
  missing clone delete/update. The first build with real
  engineering discipline.
- **v4:** per-clone notification labelling, so later it would be
  possible to tell which account a notification belongs to. To
  this day this part has never been verified on a real phone,
  because the launch path was not working yet.

## 4. The bug chain on a real phone — and how each bug fell

From here, every test on a real phone exposed a new bug, each
with its own log evidence — not guesses:

1. **The helper process died for lack of an Application
   (v4a → v4b).** The host had no Application class, so the
   `:black` process initialised the engine with a null context.
   Fix: add an Application that initialises for every process.
2. **The splash screen broke its layout (v4b → v4c).** The
   layout used ConstraintLayout, but the manual toolchain had no
   constraintlayout-core library. Fix: replace it with a plain
   LinearLayout.
3. **The fallback trap: a blank screen on Launch (v4c →
   v4d/v4e/v4f).** The engine asked the system "is this app
   installed?", the system answered "yes" because the real Muse
   app was on the phone, so the engine skipped the virtual
   install and opened... nothing. Fix: stop trusting that answer
   and check the virtual list directly.
4. **Firebase's PendingIntent flag bug (v4d → v4e).** With no
   Google Play Services in the virtual environment, Firebase
   took its old path and created a PendingIntent with no flags,
   which Android 12+ forbids. Fix: swallow exactly that error,
   and try installing real GMS into the virtual user before
   launch. This bug never recurred afterwards, because the guest
   died before reaching that code.
5. **The Settings bug that killed the guest as soon as its
   screen appeared (v4f → v4g → v4h).** The longest bug. At
   first it looked like a provider returning a null cursor, and
   returning an empty cursor instead was not enough. Then it
   turned out the Settings path goes through `call`, not
   `query`, and providers acquired lazily after init had never
   been wrapped. Fix: wrap late-acquired providers too, preserve
   the method name, and use the real host UID for system
   providers.
6. **Regression: the guest died even earlier (v4h → v4i).**
   The step-5 fix accidentally wrapped BlackBox's own internal
   service provider and corrupted the service-call method name.
   Fix: never replace the method name, never wrap internal
   providers. But the test still died in exactly the same
   place — the method-name hypothesis was disproved.
7. **The real root cause: UID mismatch (v4i → v4j).**
   The diagnostic log showed the source UID was 10000 — the
   virtual UID before app config loads — while the server only
   accepts the real UID of the calling process. Every real
   provider server checks this, so every call was rejected,
   every service came back null, and the guest could not even
   create its Application. Fix: write the real host UID
   everywhere a virtual UID used to be written.

Along the way there was also a process lesson, not a code one:
files downloaded via chat attachments repeatedly arrived
corrupted on the test machine while the build machine's copies
were intact. Since then the rule is: always verify size + hash
before installing, and prefer the direct download link. Logs are
also filtered after capture finishes, not while recording,
because live filtering lost data.

## 5. The turning point: stop guessing, start measuring (v4k)

After the method-name hypothesis was disproved, the project
changed method: instead of one build per guessed cause, v4k only
added diagnostic logging at every hop of the service-lookup path
— did the call reach the stub, did it reach the server, what did
the server return, did acquiring the client fail — without
changing any behaviour. A single run answered everything: all
services resolved, and the UIDs on both sides matched. The UID
hypothesis was proven, no longer a guess.

## 6. The day the app ran (27-10-2026)

- **19:16:02** — Launch clone, guest and GMS installed
  successfully.
- **19:16:08–19:16:09** — Muse's main screen was created and
  shown; the guest no longer died.
- **19:19** — The user confirmed: the login screen appeared and
  login succeeded. The first fully working clone, end to end.

The two FATAL lines left in that day's log were not the guest's:
one from Google Play Services, one from the Play Store's
background process.

## 7. What is still unfinished (as of writing)

- Verify 2 clones online together: create a second clone with a
  different name, log in a second account, check notification
  labels, check that tapping a notification opens the right
  account, check delay, RAM, battery, and whether the system
  kills the app in the background. Then 3, then 4 only if 3
  passes.
- The notification labelling (v4) and the PendingIntent fix (v4e)
  have still never been verified on a surviving guest.
- The "fake VPN" question is still undecided: a real IP change,
  a fake flag only, fake GPS, or all clones through one shared
  host VPN. Nothing has been built for it before a choice is
  made.
- An accidentally created empty public repo still needs manual
  deletion by the user on GitHub, because the API token has no
  repo-deletion permission.

## 8. Lessons that stuck

- Don't guess a cause when you can measure it: one logging-only
  build ended the longest guessing chain of the project.
- Every fix needs a test that can distinguish right from wrong,
  and that test must run on a real phone, not in theory.
- When a fix moves the crash earlier instead of removing it,
  that is a signal the fix touched the right area — but the
  wrong spot. Go back and read the log instead of guessing
  another mechanism.
- Keep evidence per version (logs, hashes, version numbers):
  that is what made it possible to tell a new bug from an old
  one when log files kept growing and overlapping.
