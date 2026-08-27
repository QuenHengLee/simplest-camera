# Simplest Camera — 長輩極簡相機（Open Camera fork）

目標：給長輩用的最簡相機。只留 **即時預覽 + 大快門鍵 + 切換前後鏡頭 + 圖庫**，其餘全部拿掉。
基底：Open Camera（GPL v3, net.sourceforge.opencamera）。debug 版以 `.dev` 後綴與正版並存。

## 建置 / 安裝（可攜工具鏈）
```powershell
$env:JAVA_HOME="C:\Users\heats\android-dev\jdk17"
$env:ANDROID_HOME="C:\Users\heats\android-dev\sdk"
cd C:\Users\heats\simplest-camera
.\gradlew.bat --no-daemon assembleDebug
$adb="C:\Users\heats\android-dev\sdk\platform-tools\adb.exe"
& $adb install -r -g app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n net.sourceforge.opencamera.dev/net.sourceforge.opencamera.MainActivity
```

## 兩層精簡法
- **A. 設定就能關（免改碼）** — 存在 default SharedPreferences。因為是 debuggable 版，可用
  `adb shell run-as net.sourceforge.opencamera.dev` 直接寫 `shared_prefs` XML，一次設定好交給長輩。
- **B. 要改碼才能拿掉** — 沒有設定開關的按鈕/入口。

---

## 螢幕按鈕清單（activity_main.xml 的 view id → 拿掉方式）

### A 類：設定可隱藏（key 在 preferences_sub_gui.xml）
| 按鈕 (view id) | 功能 | 設定 key |
|---|---|---|
| face_detection | 臉部偵測 | preference_show_face_detection |
| cycle_flash | 閃光燈循環 | preference_show_cycle_flash |
| focus_peaking | 對焦峰值 | preference_show_focus_peaking |
| auto_level | 自動水平 | preference_show_auto_level |
| stamp | 浮水印章(日期/GPS) | preference_show_stamp |
| text_stamp | 文字章 | preference_show_textstamp |
| store_location | GPS 定位 | preference_show_store_location |
| cycle_raw | RAW 循環 | preference_show_cycle_raw |
| white_balance_lock | 白平衡鎖 | preference_show_white_balance_lock |
| exposure_lock | 曝光鎖 | preference_show_exposure_lock |
| cycle_lock_orientation | 方向鎖 | preference_show_cycle_lock_orientation |
| preview_shots | 拍後縮圖 | preference_show_preview_shots |
| zoom_seekbar | 變焦滑桿 | preference_show_zoom_slider_controls |
| audio_control | 聲控快門 | preference_audio_control (in camera_controls_more) |

### B 類：要改碼（activity_main.xml 移除/隱藏 + MainUI.java 停用點擊）
| 按鈕 (view id) | 功能 | 備註 |
|---|---|---|
| exposure | 曝光/ISO 滑桿鈕 | 無設定開關 |
| switch_video | 相片↔錄影切換 | 若只給拍照就拿掉 |
| popup | ⋮ 進階選單總入口 | 建議拿掉（長輩勿誤觸） |
| settings | ⚙️ 設定齒輪 | 建議拿掉（設定先鎖好） |

### 一定保留
take_photo（快門）, switch_camera（前後鏡頭）, gallery（圖庫）, preview（預覽）

---

## ⋮ 彈出選單（PopupView.java）— 若拿掉 popup 鈕即全部消失
拍照模式(標準/DRO/HDR/全景/連拍/包圍/…)、解析度、影片畫質、計時器、連拍、閃光、對焦模式、ISO、白平衡。

## 設定選單（preferences*.xml）— 若拿掉 settings 鈕即長輩看不到
相機效果、相機控制、預覽、使用者介面、相片/影片/位置/影像處理設定、線上說明、Camera API、關於等。

---

## 要編輯的檔案地圖
- 隱藏按鈕開關定義：`app/src/main/res/xml/preferences_sub_gui.xml`
- 螢幕按鈕版面：`app/src/main/res/layout/activity_main.xml`
- 按鈕顯示/點擊邏輯：`app/src/main/java/net/sourceforge/opencamera/ui/MainUI.java`
- ⋮ 選單內容：`app/src/main/java/net/sourceforge/opencamera/ui/PopupView.java`
- 主流程/設定入口：`app/src/main/java/net/sourceforge/opencamera/MainActivity.java`
- app id/名稱：`app/build.gradle`（已加 debug `.dev` 後綴）

---

## 實作進度

### Phase 1 — 完成並實機驗證（2026-08-27）
依 `_design_handoff/design_handoff_simple_camera/README.md` 規格，以「疊加層 + 複用 Open Camera 管線」實作：
- 疊加層 `res/layout/simple_ui.xml` + 控制器 `java/.../SimpleCameraUI.java`
- drawable 前綴 `sc_*`（觀景窗框、綠/紅快門、膠囊、停止鍵、紅框等）
- `MainActivity`：`SIMPLE_UI` 旗標、`applySimpleUiPrefs()`（關 show_* 與資訊疊圖）、初始化 SimpleCameraUI、`cameraSetup` 內曝光鈕顯示加 `!SIMPLE_UI` 守衛
- `MainUI.showGUI`/`setImmersiveMode`：`SIMPLE_UI` 強制隱藏 settings/popup/exposure/switch_camera/switch_video/take_photo/gallery
- `MyApplicationInterface`：`startedVideo`/`stoppedVideo` → SimpleCameraUI 錄影 UI；`cameraSetup` → refreshMode

已驗證：相機畫面乾淨（無曝光鈕/資訊字/對焦圈）、照片↔影片切換、綠→紅快門、錄影畫面（**紅框緩慢脈動 0.15↔1 約 1.8s 一次作為錄影提示**、紅計時膠囊、單一停止鍵）、停止存檔（VID_...mp4）、即時預覽正常、與正版 Open Camera 並存（.dev）。

### v2 修訂 — 完成並實機驗證（2026-08-27）
目標機確認為 **Samsung A56**（U11 僅測試機），UI 已改為響應式。
- 文字：分頁「拍照/錄影」；相簿鍵改「照片」並顯示**上一張縮圖**；換鏡頭改動態「前/後」。
- 排版：改 iPhone 式——OC 預覽全幅（WYSIWYG），控制列**浮在預覽下方**（含漸層 scrim），按鈕縮小（快門 112dp、側鍵 64dp）。錄影紅框與計時膠囊**動態貼齊實際預覽矩形**（監聽 R.id.preview），任何螢幕比例都貼合。
- **鎖直向**：UI 不再隨手機旋轉（`setWindowFlagsForCamera` SENSOR→PORTRAIT）。
- 移除錄影時 OC 的 2 顆多餘按鈕（暫停、錄影中拍照）與 OC 重複的錄影時間文字。
- **修正錄影閃退**：OC 停止錄影時用隱藏的圖庫鍵（寬 0）縮放縮圖→createScaledBitmap 崩潰；SIMPLE_UI 下跳過 OC 縮圖（改用自家 MediaStore 縮圖）。加雙擊競態防護。

### Phase 2 — 待做
- [ ] App 內「我的照片」兩欄格線（MediaStore 縮圖 + 相對時間標籤「今天/昨天/星期日/8月23日」+ 影片時長後綴 + 「回到相機」滿寬鍵）。目前「我的照片」鍵開系統相簿。
- [ ] 拍照白閃 + 縮圖 tuck 動畫已實作，但尚未在強光實拍下微調位移終點。
- [ ] `showFlip` 設定（關閉換鏡頭鍵）目前為常顯，可加開關。
