# Handoff: 長輩用極簡相機 App（Simple Camera for Elders）

## Overview
一個刪到只剩必要功能的相機 App，使用者是年長者。整支 App 只有兩個畫面（相機、我的照片）與四個控制項（拍照／錄影、我的照片、換鏡頭、照片-影片模式切換）。刻意移除：變焦、閃光燈模式、濾鏡、人像、比例、格線、計時器、設定、HDR、原況照片、連拍、暫停錄影、拍後的保留／刪除詢問。

視覺方向是「讓手機假裝成一台傻瓜相機」：深色機身、滿版觀景窗、一顆有實體感的圓形快門。

## About the Design Files
本包內的 `.dc.html` 檔是**用 HTML 做的設計稿（design references）**，用來表達外觀與行為，**不是可以直接搬進產品的程式碼**。任務是把這些設計稿在目標專案既有的環境裡**重新實作**（Android / Kotlin + Jetpack Compose、React Native、Flutter 等），沿用該專案既有的元件與樣式規範。若專案尚未成形，請選擇最適合的框架再實作。

使用者已表明會以開源專案 **Open Camera**（Android）作為底層相機實作，因此最可能的目標是 Android 原生（Java/Kotlin）。這份設計只描述 UI 層，相機管線、權限、儲存沿用 Open Camera 既有實作。

## Fidelity
**High-fidelity。** 顏色、字級、間距、圓角、動畫時間都是最終值，請照這份文件的數值實作。字型若目標平台沒有 Noto Sans TC，可用系統中文黑體（Android: Noto Sans CJK TC）取代。

## Screens / Views

### 1. 相機（Camera）— 預設畫面
**Purpose**：對準、按快門或錄影。畫面上不存在任何其他功能。

**Layout**（以 402 × 874 pt 的手機畫布描述，實作時請用彈性佈局）
- 根容器：垂直 flex，背景 `linear-gradient(#232320, #191916)`。
- **觀景窗**：`flex: 1`，外距 `54px 12px 0`（上方 54px 讓開狀態列），圓角 10px，邊框 `7px solid #0E0E0C`，內陰影 `inset 0 0 0 1px rgba(255,255,255,.12)`。內容為即時預覽（設計稿中是斜紋佔位圖）。
- **模式切換列**：置中，上下留白 16px。膠囊容器 `background #0E0E0C`、`border 1px solid rgba(255,255,255,.14)`、圓角 14px、內距 5px、子項間距 5px。
  - 兩顆分頁鍵「照片」「影片」：各 `min-width 104px`、高 56px、圓角 10px、20px/700。
  - 選中：背景 `#E8B008`，文字 `#14150F`。未選中：透明背景，文字 `rgba(255,255,255,.6)`。
- **控制列**：內距 `16px 20px 46px`，水平 space-between，間距 14px。
  - 左：**我的照片**（88 × 88，圓角 16px，`border 2px solid rgba(255,255,255,.28)`，背景 `#191916`，白字 16px/600，兩行「我的／照片」，line-height 1.25）。按下態背景 `#0E0E0C`。
  - 中：**快門**（140 × 140 圓形，照片模式外圈 `border 9px solid #1F7A4D`（綠，與錄影紅明確區隔）、影片模式外圈 `#B3261E`，照片模式填色 `radial-gradient(circle at 38% 32%, #F2F0EA, #CFCCC4)`、文字「快門」`#14150F` 19px/700；影片模式填色 `radial-gradient(circle at 38% 32%, #E8564A, #B3261E)`、文字「錄影」白色）。立體感用 `box-shadow: 0 8px 0 rgba(0,0,0,.55)`，按下時 `translateY(6px)` 並把陰影縮到 `0 2px 0`。
  - 右：**換鏡頭**（88 × 88，樣式同左鍵，文字 18px/600）。此鍵可設定關閉（見 Design Tokens 的 `showFlip`）。

**注意**：相簿鍵的文字必須是「我的照片」而非「照片」，否則會與模式切換的「照片」撞名 — 這是設計上刻意的防呆。

### 2. 錄影中（Recording）
- 觀景窗與上一畫面同尺寸，但頂部置中疊一個紅色計時膠囊：背景 `#B3261E`、白字、內距 `10px 18px`、圓角 999px；左側 15px 白色圓點，右側 `m:ss` 等寬數字 23px/700。
- 控制列只剩一顆置中的 **停止**（140 × 140 圓形，`border 9px solid #B3261E`，背景 `#0E0E0C`，內含 22px 白色圓角方塊 + 文字「停止」19px/700 白色）。
- **沒有暫停鍵**，也沒有模式切換、相簿、換鏡頭 — 錄影中畫面上只有一個可按的東西。

### 3. 我的照片（Gallery）
- 兩欄格線，欄距 12px，內距 `56px 14px 0`。
- 每格：正方形縮圖（`aspect-ratio 1`，圓角 8px，`border 5px solid #0E0E0C`），下方 6px 處一行說明文字 16px/600、`rgba(255,255,255,.72)`。
- 說明文字用相對時間而非時間戳：「今天」「昨天」「星期日」「8月23日」。影片項目後綴時長：「今天 · 影片 0:14」。
- 底部一顆滿寬 **回到相機**（高 92px，圓角 18px，背景 `#E8B008`，文字 `#14150F` 27px/800），內距 `16px 20px 46px`。按下態 `#C89706`。

## Interactions & Behavior

### 拍照
1. 按快門 → 全螢幕白色閃光疊層 170ms。
2. 閃光消失，觀景窗內容**縮進左下角的相簿鍵**：一個覆蓋整個觀景窗的方塊（`transform-origin: bottom left`，外框 `0 0 0 3px rgba(232,176,8,.85)`）以 580ms `cubic-bezier(.5,0,.75,.9)` 從 `translate(0,0) scale(1)` 動到 `translate(21px,180px) scale(.13)`，同時 opacity 0.96 → 0。
   - 位移值是針對 402 × 874 畫布算出來的，實作時請改成**動態計算**：從觀景窗左下角算到相簿鍵中心。
3. 600ms 後回到相機，可以立刻拍下一張。
- **不顯示「已儲存」畫面，不問要不要保留，一律直接存檔。** 這個縮圖動畫就是唯一的回饋。

### 錄影
1. 切到「影片」模式 → 主按鈕變成紅色「錄影」。
2. 按下 → 進入錄影畫面，計時從 0:00 每秒 +1。
3. 按停止 → 走與拍照相同的存檔動畫，回到相機。**沒有暫停。**

### 其他
- 按「我的照片」→ 相簿；按「回到相機」→ 相機。任何畫面離相機都不超過一次點擊。
- 「換鏡頭」在設計稿中是空實作，實際接前後鏡頭切換。
- 所有按鍵都有明顯的按下態（顏色變化或位移），給長輩清楚的觸覺回饋。
- 每個可點擊區域最小 56px 高，主要動作 88–140px。

## State Management
```
screen : 'camera' | 'tuck' | 'recording' | 'photos'   // 'tuck' 是存檔動畫播放中，仍顯示相機畫面
mode   : 'photo' | 'video'
flash  : boolean                                      // 快門白閃
secs   : number                                       // 錄影秒數，每秒 +1
```
轉換：
- `shoot()` → flash=true → 170ms → flash=false, screen='tuck' → 600ms → screen='camera'
- `startRec()` → screen='recording', secs=0, 啟動 1s interval
- `stopRec()` → 清除 interval → 同 tuck 流程
- 元件卸載時務必清除 interval。

## Design Tokens
**Colors**
- 機身漸層 `#232320` → `#191916`
- 內陷／深色面 `#0E0E0C`
- 按鍵面 `#191916`，描邊 `rgba(255,255,255,.28)`
- 主要強調（選中態、主要按鈕）`#E8B008`，按下態 `#C89706`
- 快門綠環 `#1F7A4D`（拍照）／錄影紅 `#B3261E`，錄影鍵漸層亮端 `#E8564A`
- 深色文字 `#14150F`；白字 `#fff`；次要白字 `rgba(255,255,255,.72)` / `rgba(255,255,255,.6)`
- 快門金屬面 `radial-gradient(circle at 38% 32%, #F2F0EA, #CFCCC4)`

**Typography** — Noto Sans TC（中文）+ Libre Franklin（拉丁），等寬數字用 ui-monospace
- 主要按鈕 27–29px / 800
- 快門與大鍵文字 19–20px / 700
- 小鍵文字 16–18px / 600
- 相簿說明 16px / 600
- 錄影計時 23px / 700 等寬

**Spacing**：6 / 12 / 14 / 16 / 20 / 46 / 54 px
**Radius**：4 / 8 / 10 / 14 / 16 / 18 / 999 px
**Shadow**：實體按鍵 `0 8px 0 rgba(0,0,0,.55)`（按下 `0 2px 0`）；內陷 `inset 0 0 0 1px rgba(255,255,255,.12)`

**可設定項**
- `showFlip`（boolean，預設 true）：是否顯示「換鏡頭」鍵。關掉後控制列只剩相簿與快門。

## Assets
無外部素材。觀景窗與縮圖在設計稿中是 CSS 斜紋佔位圖（`repeating-linear-gradient(45deg, #2A2A26 0 10px, #232320 10px 20px)`），實作時換成真實相機預覽與縮圖。所有圖示都是純幾何形狀（圓形、圓角方塊），可直接用 shape 或既有 icon set 繪製。

## Files
- `Simple Camera.dc.html` — 本設計（方向 1c）。在瀏覽器直接開啟即可操作原型：切換照片／影片、按快門、錄影計時、看我的照片。
- `ios-frame.jsx` / `support.js` — 只是讓原型能在瀏覽器跑起來的外框與執行檔，**不是設計的一部分**，實作時忽略。

在瀏覽器直接開啟 `.dc.html` 即可操作原型。
