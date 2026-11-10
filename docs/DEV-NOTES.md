# 开发笔记

面向接手的人：接口契约、踩过的坑、每次发版改了什么。

## 1. 接口契约（2026-10 实测）

| 接口 | 用途 | 备注 |
|---|---|---|
| `POST /api/v1/web/login` `{username,password}` | 登录 | token **只在 `Set-Cookie: dc_web`**，之后每个请求都要 `Cookie: dc_web=<token>`（`X-DC-Token` 头无效） |
| `GET /api/state` | 用户 + 限额 + 任务列表 | 字段 `task_id/file_name/state/progress/stage_text/output{output_id,name,size,locked}/unlocked/created_at` |
| `POST /api/convert` | 提交转换 | multipart：`target`(word/excel/html/pdf)、`is_ocr=true`、`model=vlm`、`page_ranges`、`file` |
| `GET /api/task/{id}` | 单任务轮询 | 与 `/api/state` 的 tasks[] 同源同字段 |
| `POST /api/preview` `{output_id}` | 生成水印预览 | 返回 `ptoken/pages/has_more/images` |
| `GET /api/preview/img/{ptoken}/{i}` | 第 i 页 PNG | index 从 1 起 |
| `GET /api/download/{output_id}` | 下载产物 | 用 **output_id**，不是 task_id |
| `POST /api/unlock` `{output_id,code}` | 授权码解锁 | 仅付费模式需要 |
| `POST /api/tasks/clear` | 清已完成 | |
| `GET /api/v1/config` | 限额 / 价格 | 免登录 |

## 2. 三个必须记住的坑

**① `X-DC-Channel: mp` 决定免费还是付费。**
带 → 免费，产物一产出即可下载；不带 → 按 Web 付费版处理，下载直接
`400 {"err":"not_unlocked","msg":"该文件尚未授权，无法下载"}`。
漏带的症状很好认：每个已完成的任务都挂着「授权码解锁」。已封装在 `AuthInterceptor`。

**② `output.locked` 恒为 `true`（免费通道也不改）。**
直接拿它判断可下载性，免费任务会全部显示未授权。`TaskItem.from(..., freeChannel)`
里按「有产物 = 可下」归一。另外实测 `output` 里**没有 `pages` 字段**。

**③ 上传文件名只能靠 multipart 的 `filename`。**
所以暂存副本必须保留原始文件名（`Files.copyTo`），拍照合成的那份命名为
`扫描件_yyyyMMdd_HHmmss.pdf`。中文名可正常落库。

## 3. 版本变更

### v1.0.4 —— 真机截图暴露的三个问题

1. **预览页被状态栏压住**：预览是全屏浮层，盖在 `MainShell` 之上，拿不到它的
   `safeDrawingPadding()`，标题与状态栏图标叠在一起。修法：浮层自己加 `safeDrawingPadding()`。
2. **分享文件名带产物 ID**：缓存路径原为 `out/<outputId>__名字.docx`，分享给微信时用户看到的
   就是 `f4620f9e…docx`。改成 `out/<outputId>/名字.docx`，用子目录隔离。
3. **「我的」页版本号显示的是服务端版本**（v1.0.0），改用 `BuildConfig.VERSION_NAME`。

### v1.0.2 —— 大图导致「看起来卡死」

App 把手机原图（长边 2400px）整页塞进 PDF，2 页就 8.1 MB。服务端转 Word 时向 MinerU 申请
`extra_formats:["docx"]`，超大图片页会让云端 docx 生成慢到 **10~20 分钟**，期间
`extract_progress` 为空，端上只能显示「云端解析中（已用时 N 秒）」+ 进度条钉在 30%，看着就是死。

对照实验（同一份 8.1MB / 2 页 PDF）：

| 场景 | 耗时 |
|---|---|
| 直连云端，不带 docx | **25 秒** |
| 直连云端，带 docx | 15~20 分钟 |
| 走完整服务端链路 | 10~20 分钟 |

修法：`PdfComposer` 合成前把每页压到 **长边 1600px + JPEG 80**（约 200 DPI，OCR 足够），
并按体积预算逐级降档（1600→1280→1080→900）。2 页从 8.1MB 降到 1.3MB 量级。
同时在任务卡片上明说「云端生成中，1–5 分钟属正常」，避免误判为卡死。

> 注意：压缩解决的是上传与解析开销，**docx 那条链路的排队时间压不掉**。

### v1.0.1 —— 修启动闪退

1.0.0 一打开就崩，根因是界面里用了 `painterResource(R.mipmap.ic_launcher_round)`：
API 26+ 该资源解析到 `mipmap-anydpi-v26/*.xml` 的 `<adaptive-icon>`，Compose 的
`painterResource` 只认 vector / 位图，解这种 XML 直接抛异常，Splash 第一帧就崩。

修法：`tools/gen_icon.py` 另外导出普通 PNG（`drawable-*/brand_mark.png`，5 档），界面统一用它；
`mipmap` 只留给系统桌面图标。**规则：Compose 里永远不要 painterResource 一个 adaptive-icon。**

同时加了 `CrashLog`：未捕获异常写到私有目录，下次启动把堆栈摊在首屏（可复制），没 adb 也能定位。

## 4. 其它注意事项

- 提交后**不能**删要上传的那份文件：所有权交给 `ConvertService`，服务结束时按
  `parentFile.name == "stage"` 清理。提前删会让服务读到 0 字节。
- 提交完服务端还没登记任务（正在上传），此时任务列表为空，轮询不能就此停掉
  —— `AppViewModel.pollUntil` 负责这三分钟窗口。
- 相册走系统 Photo Picker、拍照走系统相机，所以**不需要** CAMERA / 存储权限。
- 通知渠道用 `IMPORTANCE_DEFAULT` + 静音 + 不振动；`IMPORTANCE_LOW` 在华为/荣耀锁屏会直接不显示。
- 分享用 FileProvider（`cache/out`），保存走 MediaStore 的「下载/伟哥扫描王」，Android 8/9 退到私有目录。

## 5. 图标

`tools/gen_icon.py`（PIL 程序化绘制，4× 超采样）输出：5 档 mipmap（含 round）、adaptive 前景
（`drawable-xxxhdpi` 432px，图案压在 74% 安全区）、界面用的 `brand_mark.png`、
`icon-src/icon-1024.png` 与 `play-store-512.png`。

构图：白色纸张（右上折角）+ 四角扫描框 + 一道琥珀色抽象「W」光束，琥珀渐变底（#FF7A18 → #DE540A）。
`icon-src/` 里另有一张图像模型出的概念稿，仅作参考，实际用的是重绘版（无水印、色准、小尺寸更稳）。
