# Spine runtime cho màn hình chiến đấu

Battle page sử dụng Spine WebGL runtime 3.5 trong:

`src/main/resources/static/lib/spine/spine-runtime-3.5/spine-ts`

Repo chứa TypeScript source; thư mục `spine-ts/build` bị ignore theo `.gitignore` của runtime. Vì vậy bundle được tham chiếu bởi trang battle chưa có sẵn sau khi clone repo.

## Build trên Windows

Yêu cầu Node.js/npm hoặc TypeScript compiler `tsc`.

Từ thư mục gốc project, chạy:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-spine-runtime.ps1
.\gradlew.bat bootRun
```

Script chỉ biên dịch WebGL backend bằng `tsconfig.webgl.json`, tạo:

`src/main/resources/static/lib/spine/spine-runtime-3.5/spine-ts/build/spine-webgl.js`

File build nằm trong thư mục bị ignore, không được commit vào Git. Khi build/deploy từ một checkout mới, phải chạy script này trước Gradle để bundle được đưa vào classpath static resources.

## Mapping nhân vật

`SpineConfig.js` hiện có một asset `character` là `test-hero`. Battle integration dùng asset này làm fallback cho các unit chưa có mapping riêng.

Khi có asset riêng, khai báo trong `SPINE_CONFIG.characters` với key trùng một trong các giá trị `spineSkeleton`, `avatarCode` hoặc `heroCode` của unit. Giá trị cấu hình dùng cùng cấu trúc với `SPINE_CONFIG.character` (code, skeleton, atlas, textures, scale, offsetX, offsetY).

## Lưu ý giấy phép

License đi kèm Spine Runtime 3.5 giới hạn quyền sử dụng mặc định ở mục đích cá nhân/nội bộ. Việc phân phối ứng dụng chứa runtime hoặc phát hành game cần giấy phép Spine phù hợp từ Esoteric Software. Hãy xác nhận quyền sử dụng trước khi phát hành bản web công khai hoặc APK.
