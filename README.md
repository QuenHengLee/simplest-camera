# Simplest Camera（長輩用極簡相機）

A stripped-down, elder-friendly camera app — a fork of **[Open Camera](https://opencamera.org.uk/)** by Mark Harman.

只保留最必要的功能，給不熟悉智慧型手機的長輩使用。相機管線、權限、儲存完全沿用 Open Camera，只重寫了 UI 層。

## 主要改動

- 只有一個相機畫面 + 四個大按鈕：**拍照 / 錄影**切換、**快門**、**照片**（相簿，顯示上一張縮圖）、**前/後**（切換鏡頭）。
- iPhone 式版面：全幅預覽（WYSIWYG，與照片實際比例一致），控制列浮在畫面下方，立體感圓形快門。
- 錄影時觀景窗有**紅色邊框緩慢脈動**作為「錄影中」提示，頂部顯示計時膠囊，畫面上只剩一顆「停止」鍵。
- **鎖定直向**，UI 不隨手機轉動；響應式，自動適配不同螢幕比例。
- 移除變焦、閃光模式、濾鏡、HDR、設定入口、進階選單等所有會讓長輩困惑的功能。

實作細節見 [`_SIMPLIFY-PLAN.md`](_SIMPLIFY-PLAN.md)。UI 疊加層主要在 `SimpleCameraUI.java` 與 `res/layout/simple_ui.xml`。

## 建置

```
JAVA_HOME=<jdk17>  ANDROID_HOME=<sdk>  ./gradlew assembleDebug
```
需要 Android SDK platform 36 / build-tools 36、Gradle 8.13（wrapper 自動下載）。

## 授權 / License

GPL v3（承襲 Open Camera）。原始作者 Mark Harman，原專案：<https://sourceforge.net/p/opencamera/code/>。
本專案為其衍生作品，依 GPL v3 條款釋出並保留原始授權與版權聲明（見 `gpl-3.0.txt`、`opencamera_source.txt`）。
