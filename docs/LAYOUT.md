# Bố cục cây mã nguồn / Source tree layout

Tiếng Việt: mọi file được gom theo chủ đề, không để rời rạc.
English: all files are grouped by theme, nothing is left scattered.

- `README.md` (gốc / root): file mô tả và giới thiệu dự án duy
  nhất ở ngoài — mọi tài liệu khác đều nằm trong `docs/`.
  (The only description/introduction file at the root — every
  other document lives in `docs/`.)
- `docs/`: tài liệu theo chủ đề — hành trình dự án
  (`README_JOURNEY.md`), ghi chú từng bản build (`BUILD_*.md`),
  đánh giá engine và hiệu năng.
  (Theme-grouped documents: project journey, per-build notes,
  engine evaluation and performance.)
- `host/`: mã nguồn app chủ bản mới nhất (Java), layout và
  manifest. (Latest host app source, resources and manifest.)
- `engine/`: mã nguồn engine BlackBox (Java, AIDL, C++) bản
  mới nhất. (Latest BlackBox engine source.)
- `build/`: script build thủ công và thư viện phụ thuộc để build.
  (Manual build scripts and build dependencies.)
- `artifacts/aars/`: các bản engine đã build, gom chung một chỗ.
  (Built engine AARs, grouped in one place.)
- `archive/`: mã nguồn cũ đã bị thay thế (host Kotlin cũ, host
  Java cũ, app mẫu gốc) — giữ lại để đối chiếu, không phải mã
  đang dùng. (Superseded old sources, kept for reference only.)

Không có file APK của app khách, không có log, không có dữ liệu
người dùng trong cây này.
(No guest-app APK, no logs, no user data in this tree.)
