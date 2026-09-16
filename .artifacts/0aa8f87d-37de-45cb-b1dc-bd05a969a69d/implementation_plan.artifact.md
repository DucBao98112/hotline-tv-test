# Implementation Plan - Khắc phục triệt để lỗi nền ảo nhỏ và hướng soi gương

Dựa trên phản hồi "vẫn bị nhỏ không full" và "ảnh phản chiếu bị ngược", tôi xác định được logic hiện tại đang gặp vấn đề ở việc tính toán ma trận vẽ (Matrix drawing) và hướng lật ảnh (Mirroring).

## User Review Required

> [!IMPORTANT]
> - **Sửa lỗi nền nhỏ**: Tôi sẽ thay thế việc dùng ma trận (Matrix) bằng việc dùng `Rect` (Source and Destination rectangles) để thực hiện `CENTER_CROP`. Cách này đảm bảo 100% hình nền sẽ lấp đầy khung hình mà không sai lệch.
> - **Sửa hướng soi gương**: Tôi sẽ đồng bộ lại việc xoay và lật ảnh. Nếu camera bị ngược (180 độ), tôi sẽ xoay thẳng lại và áp dụng hiệu ứng lật ngang (Horizontal Flip) chuẩn để bạn thấy mình như đang soi gương một cách tự nhiên.
> - **Khớp Mask tuyệt đối**: Tôi sẽ tính toán lại ma trận AI để đảm bảo mặt nạ bám khít vào người, không bị lệch khi di chuyển.

## Proposed Changes

### 1. Xử lý Video (Video Processing)

#### [MODIFY] [CpuVideoProcessor.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/CpuVideoProcessor.kt)
- **Sửa hàm `drawCenterCrop`**: Dùng `canvas.drawBitmap(bitmap, srcRect, dstRect, paint)` để đảm bảo nền ảo luôn lấp đầy 100% khung hình video (1280x720).
- **Chuẩn hóa Bake Rotation**:
    - Xoay 180 độ (nếu cần) để người thẳng đứng.
    - Cập nhật ma trận AI để Mask luôn khớp với ảnh đã xoay.
- **Tối ưu hóa bộ đệm**: Đảm bảo tất cả bitmap trung gian được giải phóng để app không bị chậm.

### 2. Tích hợp hiển thị (Local Preview)

#### [MODIFY] [CallSession.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/call/CallSession.kt) & [RoomSession.kt](file:///Users/owner/Desktop/hotlinetv-android-skyway-v2-hotline 2/app/src/main/java/com/gc/waravi/skyway/room/RoomSession.kt)
- Đảm bảo `SurfaceViewRenderer` nhận được khung hình đã chuẩn hóa và hiển thị ở chế độ Mirror (soi gương) chuẩn.

## Verification Plan

### Manual Verification
1. **Kiểm tra lấp đầy**: Xác nhận hình nền che phủ hoàn toàn màn hình, không còn viền đen hay khung nhỏ.
2. **Kiểm tra soi gương**: Khi bạn giơ tay trái, hình ảnh trên TV phải hiện ở bên trái (giống soi gương).
3. **Kiểm tra căn chỉnh**: Xác nhận viền nền ảo không bị hở hoặc lệch khi bạn di chuyển.
