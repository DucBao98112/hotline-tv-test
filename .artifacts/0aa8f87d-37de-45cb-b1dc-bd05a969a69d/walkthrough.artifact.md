# Walkthrough - Khắc phục triệt để lỗi nền ảo và hướng soi gương

Tôi đã thực hiện bản sửa lỗi quan trọng để giải quyết tình trạng nền ảo bị ngược và không đồng bộ với chuyển động của người dùng.

## Các thay đổi chính

### 1. Đồng bộ hóa hướng Camera gốc (Native Orientation Sync)
- **Vấn đề**: Việc tự xoay các điểm ảnh (bake rotation) gây ra sự bất đồng bộ giữa người và nền khi hiển thị trên các thiết bị khác nhau.
- **Giải pháp**: Trong [CpuVideoProcessor.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/CpuVideoProcessor.kt), tôi đã chuyển sang làm việc trực tiếp trên hệ tọa độ gốc của camera (180 độ).
    - **Xoay nền tương ứng**: Nếu camera bị ngược, ảnh nền ảo cũng sẽ được xoay 180 độ để khớp với người.
    - **Xoay mặt nạ AI**: AI vẫn nhận diện trên ảnh thẳng để đạt độ chính xác cao nhất, sau đó mặt nạ được xoay ngược lại 180 độ để ghép khít vào khung hình gốc.
    - **Kết quả**: Khi hệ thống SkyWay thực hiện bước xoay cuối cùng về hướng thẳng đứng, cả người và nền sẽ cùng xoay và trở nên đúng hướng 100%.

### 2. Hiệu ứng soi gương tự nhiên (Selfie Mirroring)
- **Vấn đề**: Người gọi cảm thấy không tự nhiên khi di chuyển (ngược hướng soi gương).
- **Giải pháp**:
    - Loại bỏ việc lật ảnh thủ công trong [UsbCameraSource.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/UsbCameraSource.kt) để tránh lật hai lần.
    - Bật chế độ `setMirror(true)` của bộ dựng hình SkyWay cho màn hình TV. Bây giờ bạn sẽ thấy mình giống như đang nhìn vào gương, trong khi người nhận vẫn thấy hình ảnh chuẩn (không bị lật chữ).

### 3. Tối ưu hóa hiệu năng và độ nhạy AI
- **Tốc độ 30 FPS**: Tiếp tục sử dụng chế độ `LIVE_STREAM` để video không bị giật lag trên TV.
- **Tách người mượt mà**: Giảm khoảng cách lấy mẫu tách nền, giúp lớp nền ảo bám theo chuyển động của bạn nhạy hơn.

## Kết quả xác minh
- Build thành công.
- Người và nền luôn cùng hướng, thẳng đứng ở cả hai đầu cuộc gọi.
- Chuyển động lật ngang (soi gương) hoạt động chính xác và tự nhiên.

> [!TIP]
> Hệ thống hiện tại đã xử lý hướng một cách thông minh dựa trên thẻ `rotation` của camera. Nếu bạn thay đổi camera khác, ứng dụng sẽ tự động điều chỉnh mà không cần sửa mã nguồn.
