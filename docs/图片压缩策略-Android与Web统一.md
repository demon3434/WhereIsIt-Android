# 图片压缩策略（Android 与 Web 统一）

## 目标
- 用户无感：用户只负责选图/拍照，不需要手动改图。
- 上传约束：提交保存时，单张图片必须 `<= 900KB`。
- 视觉优先：在满足大小限制前提下，尽量保留肉眼清晰度。

## 统一参数（当前线上建议）
- 大小上限：`900KB`（`900 * 1024` 字节）
- 规格上限：长边 `<= 1600px`
- 初始 JPEG 质量：`82`
- 分辨率兜底下限：长边不低于 `720px`
- 兜底缩放步长：长边每轮乘以 `0.85`

## 压缩顺序（必须保持）
1. 读取原图，解析宽高。
2. 先按长边 `1600` 做采样解码与缩放（先卡规格上限）。
3. 若有透明通道（如 PNG），先铺白底再转 JPEG。
4. 用质量 `82` 压 JPEG，检查是否 `<= 900KB`。
5. 若超限，先降质量（每次 `-8`，最低到 `56`）。
6. 仍超限时，再降分辨率（长边 `*0.85`，但不低于 `720`），并把质量重置到 `82` 继续循环。
7. 直到 `<= 900KB`；若到最小规格仍超限，返回失败提示。

结论：优先降质量，次优降分辨率；两者交替兜底，保证尽量清晰。

## Android 关键代码（当前实现）
文件：`app/src/main/java/com/whereisit/findthings/data/repository/ItemRepository.kt`

```kotlin
private companion object {
    const val MAX_UPLOAD_IMAGE_BYTES = 900 * 1024
    const val SPEC_MAX_LONG_EDGE = 1600
    const val SPEC_JPEG_QUALITY = 82
    const val MIN_LONG_EDGE = 720
}
```

```kotlin
private suspend fun uriToPart(index: Int, uri: Uri): MultipartBody.Part = withContext(Dispatchers.IO) {
    val bytes = compressToUploadJpeg(uri)
    val body: RequestBody = bytes.toRequestBody("image/jpeg".toMediaType())
    MultipartBody.Part.createFormData("files", "upload_${System.currentTimeMillis()}_$index.jpg", body)
}
```

```kotlin
private fun compressToUploadJpeg(uri: Uri): ByteArray {
    // 读取图片、按规格上限缩放到长边 <= 1600
    // 透明图先铺白底，再转 JPEG
    // 先质量 82 压缩，超限则优先降质量，再降分辨率兜底
    // 最终必须 <= 900KB
}
```

核心循环（原样逻辑）：

```kotlin
while (true) {
    output.reset()
    current.compress(Bitmap.CompressFormat.JPEG, quality, output)
    result = output.toByteArray()
    if (result.size <= MAX_UPLOAD_IMAGE_BYTES) break

    if (quality > 56) {
        quality -= 8
        continue
    }

    val currentLongEdge = max(current.width, current.height)
    if (currentLongEdge <= MIN_LONG_EDGE) break

    val nextTarget = (currentLongEdge * 0.85f).toInt().coerceAtLeast(MIN_LONG_EDGE)
    val downsized = resizeLongEdgeIfNeeded(current, nextTarget)
    if (downsized !== current) {
        current.recycle()
        current = downsized
        quality = SPEC_JPEG_QUALITY
        continue
    }
    break
}
```

## Web 端对齐实现建议
- 浏览器端用 `Canvas` / `createImageBitmap` 压缩：
  - 先把长边限制到 `1600`
  - `canvas.toBlob("image/jpeg", quality)`，起始 `quality=0.82`
  - 按相同步骤降质量、再降分辨率，直到 `blob.size <= 900*1024`
- 上传前统一走压缩函数，不允许“原图直传”分支。
- Web 与 Android 使用同一套参数，避免端间行为不一致。

## 验收用例
- 5MB 常规 JPG：压缩后应明显小于 900KB，细节可接受。
- 高噪点照片：可能触发“降质量 + 降分辨率”多轮兜底。
- 透明 PNG：转白底 JPEG 后仍需满足 <= 900KB。
- 极端大图：若到最小规格仍超限，应返回明确错误提示。

