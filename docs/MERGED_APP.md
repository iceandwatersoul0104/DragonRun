> 说明：本文是最初开发过程的记录（逆向结论、算法与实机数据），路径已按当前仓库结构统一写作 `app/`。

# DragonRun（原 FuckRun 融合版，com.acooldog.fuckrun）——重建说明与实机验证记录

> 目标：用自建 App 复现「牢大肘击校园跑」的三项核心能力（路线模拟 / 绘制路线 / 查看已有路线）与 ROOT 反检测，
> 并把开源「步频模拟」（uy-li/runhook → zcvic/FUCK-RUN-）的传感器接管整合进同一套 UI。
> 作用对象：步道乐跑 `com.lptiyu.tanke`（加壳 App，定位走 **高德 AMap SDK**，非系统 LocationManager）。
> 实机：Xiaomi Mi 10 / Android 13 / Magisk 30400 / zygisk_lsposed v2.0.2，设备。

---

## 1. 产物

| 项目 | 值 |
|---|---|
| 包名 | `com.acooldog.fuckrun` |
| 版本 | 1.0（versionCode 1），minSdk 28，targetSdk 35 |
| APK | `app/build/DragonRun.apk`（约 112 KB，无第三方依赖、无地图 SDK key） |
| 源码 | `app/src/com/acooldog/fuckrun/**` |
| 编译期桩 | `app/stubs/de/robv/android/xposed/**`（只参与编译，**不打包**） |
| 构建脚本 | `app/build.ps1`（aapt2 + javac + d8 + zipalign + apksigner，不依赖 Gradle） |
| LSPosed | 模块与宿主同 APK：`assets/xposed_init = com.acooldog.fuckrun.xposed.Module` |

模块只在 `com.lptiyu.tanke` 进程内装载；UI 与模块通过**文件**交换状态，因此宿主被杀也不影响目标侧继续模拟。

---

## 2. 架构

```
┌──────────────────────── 宿主进程 com.acooldog.fuckrun ────────────────────────┐
│ MainActivity / DrawRouteActivity / SimulationActivity / CadenceActivity      │
│ SettingsActivity / FloatingService / StateService                            │
│   RouteStore(files/routes/*.json)  SimController(内存 + 文件 + 引擎)          │
│         │ 每 200ms tick / 每次状态变更                                        │
│         ▼                                                                    │
│  /storage/emulated/0/Android/media/com.acooldog.fuckrun/state.json           │
└──────────────────────────────────┬───────────────────────────────────────────┘
                                   │ 500ms 轮询（跨进程、无需 Provider 权限）
┌──────────────────────────────────▼───────────────────────────────────────────┐
│              目标进程 com.lptiyu.tanke（LSPosed 注入）                        │
│ Module.init → Probe(诊断/自动化) + LocationHooks + SensorHooks + AntiDetect  │
│   StateFile.start → State.parse → Simulator.update                           │
│   Simulator: SimulationEngine(路线, 配置, seed) → Frame(WGS84)               │
│   注入：① AMapLocationClient.setLocationListener 捕获 → 直接回调 listener     │
│         ② LocationManager.requestLocationUpdates 捕获 → 200ms 分发           │
│         ③ AMapLocation / android.location.Location 的 getter 全量改写         │
│         ④ 构造 AMapLocation 后回填 setLatitude/Longitude/…（让 SDK 认为有效） │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 2.1 状态文件（唯一跨进程通道）

- 路径：`ctx.getExternalMediaDirs()[0]/state.json` → `/storage/emulated/0/Android/media/com.acooldog.fuckrun/state.json`
- 写：`SimController.write()` 原子写（tmp + `getFD().sync()` + rename），并 `setReadable(true,false)`；同时保留
  `filesDir/state.json` 作为兜底。
- 读：模块侧 `xposed/StateFile.java` 依次尝试 4 个候选路径，每 500 ms 一次，内容变化才 `State.parse`。
- JSON 字段：`running`、`startedAt`(elapsedRealtime)、`routeId/routeName`、`points[{lat,lng,alt}]`、`config{...}`、
  `cadenceOn/Min/Max`、`antiRoot/antiMock/silentLog`、`satellites/hdop/accuracy`、`ts`。

> 为什么不用 ContentProvider / 广播 / LocalSocket：MIUI 的 `WakePathChecker` 会拒绝目标 App 唤醒本 App
> （`Failed to find provider info for com.acooldog.fuckrun.state`），abstract LocalSocket 又被 SELinux 拒绝
> （`Permission denied`）。Android/media 目录是唯一两边都能访问且无需权限的落点。

### 2.2 路线引擎（按已提取的牢大算法重写）

`sim/SimulationEngine.java`：`STEP_LENGTH_METERS=0.8`；确定性噪声
`noise(x)=fract(sin(x*12.9898+seed*0.0001)*43758.5453)*2-1`；每 tick（默认 200 ms）推进：

- 基础速度：速度模式取 `speedMps`；步频模式 `0.8*cadence/60`。
- 动态强度：`target = base + noise(ticks*0.02)*range*0.8`，夹到 `[max(0.5, base*0.35), base+range]`，`speed += (target-speed)*0.18`。
- 路径漂移：沿 `bearing+90°` 偏移 `amplitude*noise*0.6`，平滑系数 0.18。
- 海拔：`baseAlt = altitudeBase + heightCm/100 + range*noise*0.5`，平滑 0.12。
- `frameAt(elapsedMs)` 为纯函数（每次重置状态后按 tick 推进），`durationMs()` 用于循环回绕，因此**长时间运行不会停**。
- 输出 WGS84；注入时由 `Geo.wgs84ToGcj02` 转 GCJ-02（高德坐标系），`gcj02ToBd09` 供百度分支备用。

坐标系常量与实现见 `util/Geo.java`（`A=6378245.0`、`EE=0.006693421622965943`、`X_PI=π*3000/180`，反解 6 次迭代）。

### 2.3 定位注入（关键：AMap 不走系统定位）

1. **AMap 监听器直调**：hook `AMapLocationClient.setLocationListener(Object)` 收集 listener，每 200 ms 用反射调用
   `listener.onLocationChanged(AMapLocation)`；对象由 `AMapLocation(Location)` 构造，构造后回填
   `setLatitude/setLongitude/setAltitude/setBearing/setSpeed/setAccuracy/setTime`。
2. **系统 LocationManager**：hook `requestLocationUpdates` / `requestSingleUpdate` 收集 `LocationListener`，
   `removeUpdates` 移除；主线程 Handler 每 200 ms 分发。
3. **getter 改写**：`AMapLocation` 的 `getLatitude/getLongitude/getAltitude/getBearing/getSpeed/getAccuracy/getTime/
   getLocationType(=1)/getErrorCode(=0)/getErrorInfo("success")/isMock(=false)/getProvider("gps")/getCoordType("GCJ02")/
   getSatellites/getGpsAccuracyStatus(=1)`；`android.location.Location` 的同类 getter +
   `isFromMockProvider=false`（反射写 `mIsFromMockProvider` 用 try/catch 包住，Android 13 字段已不可写）。
4. 精度与信号：`accuracy = max(3.0, 配置值)`，extras 写 `satellites/hdop/meanCn0=36/maxCn0=42/source=gps`。

> 只做 1+3 时高德仍报「GPS 信号无」；补上第 4 步（回填 AMapLocation 真实字段）后运行页变为 **「GPS 信号 优」**。

### 2.4 传感器接管（步频模块移植）

`xposed/SensorHooks.java`：hook `SensorManager` / `SystemSensorManager` / `com.oplus.sensor.OplusSensorManager` 的
`registerListener`，对每个 `SensorEventListener` 的 `onSensorChanged` 改值（按类名去重）：

- `TYPE_ACCELEROMETER`：`x=0`，`y=1.5·cos(2πft)+jitter(±0.15)`，`z=9.81+3.5·sin(2πft)+jitter`；
- `TYPE_STEP_COUNTER`：首帧记基线，之后 `base + elapsed·(cadence/60)`；
- `TYPE_STEP_DETECTOR`：恒 1；
- 末尾 `event.timestamp = elapsedRealtimeNanos()`。

`cadenceOn` 由 false→true 时重置基线并按 `[cadenceMin, cadenceMax]` 重新随机步频。

### 2.5 反检测

`xposed/AntiDetect.java`：

- `File.exists`：命中 `(?i).*(/su$|/su/|/magisk|/busybox$|xposed|frida|zygisk|supersu|superuser).*` → `false`；
- `Settings.Secure.getString("mock_location")` → `"0"`；
- `com.scottyab.rootbeer.RootBeer` 的 `isRooted/checkForRoot/detectRootManagementApps/…` → `false`；
- `Build.TAGS` 含 `test-keys` → `release-keys`；`Build.TYPE` → `user`；`Debug.isDebuggerConnected()` → `false`。

`xposed/Probe.java`（诊断兼自动化，早于 `Application.onCreate` 安装）：

- 打印每个 Activity 的 `onCreate` 类名与 Intent extras；
- hook `Dialog.show`，打印对话框类名与其中全部 TextView 文本；
- 自动点掉「发现新版本」弹窗、自动点「恢复」未完成记录 → 这样才能用正常流程进入 `MapRunActivity`
  （root 直接 `am start` 只能得到浏览态，没有底部「开始」控件）。

---

## 3. 源码文件清单

```
app/
├─ AndroidManifest.xml            权限/组件/xposed 元数据（xposedscope=com.lptiyu.tanke）
├─ build.ps1                      构建脚本
├─ res/values/{strings,colors,styles,arrays}.xml
├─ res/drawable/{card_bg,btn_primary,btn_ghost,btn_danger,input_bg,chip_bg}.xml
├─ res/layout/{activity_main,activity_draw,activity_simulation,activity_cadence,activity_settings}.xml
├─ assets/xposed_init             com.acooldog.fuckrun.xposed.Module
├─ assets/map.html                Leaflet + 高德栅格瓦片（GCJ-02），无 key
├─ assets/leaflet/{leaflet.js,leaflet.css}
├─ stubs/de/robv/android/xposed/**  编译期桩
└─ src/com/acooldog/fuckrun/
   ├─ App.java  MainActivity.java  DrawRouteActivity.java  SimulationActivity.java
   ├─ CadenceActivity.java  SettingsActivity.java  FloatingService.java  StateService.java
   ├─ StateProvider.java  StatusReceiver.java
   ├─ util/Geo.java
   ├─ sim/{SimConfig,Frame,SimulationEngine,SimController}.java
   ├─ model/{Route,RoutePoint}.java
   ├─ data/{RouteStore,AppPrefs}.java
   └─ xposed/{Module,Hooks,Probe,LocationHooks,SensorHooks,AntiDetect,State,StateFile,
              StateChannel,Simulator,ProviderClient}.java
```

`StateChannel`（abstract LocalSocket）为早期方案，代码保留但运行时会失败告警，可忽略/后续删除。

---

## 4. 构建

前置（已装好）：

- Android SDK：`%ANDROID_SDK_ROOT%`（platforms/android-35、build-tools/35.0.0）
- JDK 21：`%JAVA_HOME%`
- 调试证书：`tools/debug.keystore`（alias `androiddebugkey`，口令 `android`）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File app\build.ps1
# 成功输出：OK -> app\build\DragonRun.apk
# 注意：脚本末尾退出码常为 1（PowerShell 5.1 对原生命令 stderr 的处理），以是否打印 OK 为准
```

构建流程：`aapt2 compile` → `aapt2 link`（不带 `-A`，assets 由脚本按 `/` 路径写入）→ `javac --release 8`
（src + stubs + gen）→ `jar cf app.jar -C classes com`（只打包 `com/**`）→ `d8 --release --min-api 28` →
ZipArchive 追加 `classes.dex` 与 `assets/**` → `zipalign -f 4` → `apksigner sign`。

### 常见坑

- **不要用 PowerShell `Set-Content -Encoding UTF8` 改 Java 源**：PS 5.1 会写 BOM，javac 报
  `非法字符: '\ufeff'`。改源码只用编辑器工具，或
  `[IO.File]::ReadAllText($p,[Text.Encoding]::UTF8)` + `WriteAllText(..., New-Object Text.UTF8Encoding($false))`。
- `build.ps1` 每次清空 `app/build/`，辅助脚本（`tools/nodes.py`、`tools/ocr.ps1`）不要放进去。
- `xposed` 桩里 `MethodHookParam.method` 是 `Member`，要取返回类型必须
  `((java.lang.reflect.Method) p.method).getReturnType()`；向 `float`/`double` 返回值 `setResult` 时必须装箱成
  对应类型，否则 hook 内抛异常会被静默吞掉（本次故障根因之一）。

---

## 5. 安装与启用

```powershell
adb -s device push app\build\DragonRun.apk /data/local/tmp/dragonrun.apk
adb -s device shell "su -c 'pm install -r -t /data/local/tmp/dragonrun.apk'"
# MIUI 直接 adb install 会 INSTALL_FAILED_USER_RESTRICTED，必须走 root pm install
```

### 启用 LSPosed 模块（管理器 UI 抓不到列表时用 DB 直改）

```sql
-- /data/adb/lspd/config/modules_config.db
INSERT OR REPLACE INTO modules_state(module_pkg_name,user_id,enabled,scope_request_blocked)
  VALUES('com.acooldog.fuckrun',0,1,0);
INSERT OR REPLACE INTO scope(module_pkg_name,app_pkg_name,user_id)
  VALUES('com.acooldog.fuckrun','com.lptiyu.tanke',0);
```

拉库 → Python 改 → 推回（同时删除 `-wal`/`-shm`，`chown root:root`、`chmod 600`）→ `adb reboot`。
`com.uy_li.runhook` 与 `com.x.cnmb` 曾在测试期被置 `enabled=0` 以免抢 hook；需要恢复时把对应行改回 1 即可。

### 其它准备

- 目标 App 权限：`pm grant com.lptiyu.tanke android.permission.ACCESS_FINE_LOCATION`、
  `ACCESS_COARSE_LOCATION`、`ACCESS_BACKGROUND_LOCATION`；`appops set com.lptiyu.tanke android:mock_location allow`。
- 悬浮窗：`appops set com.acooldog.fuckrun SYSTEM_ALERT_WINDOW allow`（重装 APK 会重置 appops，需重新设置）。

---

## 6. 实机验证记录（2026-11-11）

| 项目 | 结果 |
|---|---|
| 模块装载 | `FuckRun: waiting for Application.onCreate` → `hooks installed`（目标 pid 内） |
| AMap 监听器 | `AMapLocationClient` 双 client 捕获：`AMap startLocation, listeners=1 running=true` ×2 |
| 定位分发 | `pulse gps=6 amap=2 lat=34.2891… lng=108.0728… spd=3.55~4.24`，坐标沿「测试环线」推进（GCJ-02） |
| 目标运行页 | `GPS 信号 优`；距离由 0.00 → 0.13 → 0.47 km 持续增长；打卡点距离实时更新（田径场北 42.51 米…） |
| 停止 | App 内「停止模拟」→ `state.json` `running:false`，目标侧不再推进 |
| 步频 | `cadence ON min=172.0`，`wave type=1 v0=0.0 cad≈180`（加速度计波形生效） |
| 状态持久化 | `files/state.json` + Android/media 双写；`am force-stop` 后 `restore()` 可恢复 |
| 反检测 | File.exists / Settings.Secure / RootBeer / Build / Debug hook 已安装（未见 target 侧异常） |
| 去广告 | 广告 SDK 的 Activity / View / Splash.show* 全部拦截；设置页可开关（默认开） |

> 距离增长速度在有步频注入时会略快于设定速度（目标 App 在 GPS 弱时会叠加「计步器估算」）。
> 需要严格按速度时应关闭步频接管，或把 `cadenceSpm` 调到与速度匹配（`4·speed` 步/分）后再比对。

### 6.1 去广告（`xposed/Ads.java`）

目标 App 的广告栈：穿山甲 **GroMore 7.4.2.1** 聚合，适配 GDT（`com.qq.e`）、百度（`com.baidu.mobads`）、
快手（`com.kwad`）、CSJ（`com.bytedance.sdk.openadsdk`）；APK 内另有 `assets/gdt_plugin/gdtadv2.jar`
与 `assets/bdxadsdk.jar` 两个插件包。开屏广告是原生 View 挂进 `com.lptiyu.tanke:id/id_splash_container`。

实现（三件事，全部在目标进程内）：

1. **拦截广告 Activity**：hook `Activity.startActivity/ForResult/IfNeeded` + `ContextImpl.startActivity`，
   组件类名命中广告根包 → `setResult(null)`。
2. **拦截广告 View**：hook `ViewGroup.addView/addViewInLayout`，`args[0]` 的类名命中根包 → 不加入布局。
   实测拦掉 GDT 信息流：`com.qq.e.comm.plugin.zr`、`com.qq.e.ads.nativ.MediaView`、
   `com.qq.e.ads.nativ.widget.NativeAdContainer`。
3. **开屏不留白**：广告 View 被拦后 `id_splash_container` 会变成空白容器（白屏 + 倒计时「0」），
   所以再 hook `Activity.onCreate`，凡是 `*Splash*` 且包名为 `com.lptiyu.tanke` 的页面，
   350 ms 后直接 `startActivity(MainActivity)` 并 `finish()`，用户看不到广告位。
   实测冷启动约 1.5 s 即落到 `MainActivity`，主页数据与「开始乐跑」流程均正常。

开关：首页「去广告」卡片一键切换（点卡片即开/关，右侧 chip 显示「已开启 / 已关闭」），
设置页也有同名开关；`AppPrefs.K_AD_BLOCK`（默认 true），
经 state.json 的 `adBlock` 字段下发给模块；关闭后开屏会恢复抖音开屏广告（已 A/B 验证）。

已知：CSJ 的类多数由 `Class.forName`/native 加载，`ClassLoader.loadClass` 钩子看不到，
但它们最终仍以 View 形式加入布局，所以开屏/信息流仍被第 2 条覆盖；仅当某广告不走 View 时才可能漏。

---

### 6.2 品牌与主题（DragonRun / 蓝白与红黑）

应用名改为 **DragonRun**（包名仍 `com.acooldog.fuckrun`，避免 LSPosed 作用域与状态通道失效）。

- 图标：`res/drawable/ic_launcher_foreground.xml`（矢量红龙 + 火焰）+ `res/mipmap-anydpi-v26/ic_launcher.xml`
  （adaptive-icon，背景 `@color/icon_bg #0E0709`）；Manifest `android:icon/roundIcon="@mipmap/ic_launcher"`。
- 双主题：颜色改为主题属性（`res/values/attrs.xml` 11 个 color attr），`styles.xml` 定义
  `AppTheme`（蓝白）与 `AppTheme.Red`（红黑）；所有布局/drawable 用 `?attr/xxx`，Java 端用
  `util/Palette.of(context)` 取色。
  - 蓝白：bg `#F4F6FA`、card `#FFFFFF`、primary `#2F6FED`、primary_dark `#1F54C0`。
  - 红黑：bg `#120A0C`、card `#1E1114`、primary `#E23A2E`、primary_dark `#8E1B13`。
- 选择与持久化：`AppPrefs.theme()` / `setTheme("blue"|"red")`（默认 blue）；设置页「外观样式」卡片有
  `btn_theme_blue`/`btn_theme_red` 与 `tv_theme`；切换后以 `CLEAR_TASK` 重启 `MainActivity` 使主题生效。
  本 ROM 上 SharedPreferences 实际落在 `/data/misc/apexdata/.../prefs/com.acooldog.fuckrun/fuckrun.xml`，
  已验证强制停止并重启后仍保持所选配色。
- 动态龙标：`ui/DragonLogoView.java` 纯 Canvas 绘制（头颅/双角/牙/发光眼 + 5 层火焰），
  `ValueAnimator` 2200ms 循环驱动呼吸缩放与火焰摆动，颜色取自 `Palette`；五个 Activity 顶部都放了龙标
  （首页 88×84dp，其余 56×52dp，位于标题左侧即整屏左上角）。
- 验证（1080×2340）：蓝白下状态栏 `(31,84,192)`、背景 `(244,246,250)`、龙标墨色 `#2F6FED`；
  红黑下状态栏 `(142,27,19)`、背景 `(18,10,12)`、龙标墨色 `#E23A2E`；
  相隔 300ms 两张截图的龙标区域分别约有 1386（红）/666（蓝）个像素变化 → 动画在跑。
- 构建产物改名为 `app/build/DragonRun.apk`。

### 6.3 入场动画与环境粒子（`ui/DragonIntroView`、`ui/AmbientView`、`ui/DragonArt`）

- `DragonArt`：统一的龙绘制原语（设计坐标：头在原点朝 +X，身体向 -X 拖尾 88 单位，火焰从嘴前喷出）。
  `flame(Palette)` 给出三色：红黑 `#FF4D2E/#FF8A2A/#FFD36A`，蓝白 `#3FA9FF/#8FE4FF/#FFFFFF`。
- 冷启动入场：`util/Ui.playIntro()` 在 `android.R.id.content` 上叠一层 `DragonIntroView`，
  `PathMeasure` 沿两段三次贝塞尔驱动龙（起点屏幕上方 → 俯冲横掠右侧 → 转弯回左上角落点 `(62dp,46dp)`），
  3000ms `AccelerateDecelerate`；嘴前按切线喷 180 个径向渐变粒子，落点前缩放收小并淡出；
  结束回调里常驻龙标从 0.55 倍 `OvershootInterpolator` 弹入。入场期间点击可立即跳过（`anim.cancel()`）。
  只有冷启动播放：`introPlayed` 静态标记 + `savedInstanceState==null`，换主题重启时带 `skip_intro=true` 不再播。
- 环境粒子：`util/Ui.ambient()` 把 `AmbientView` 插到内容层 index 0（不拦截触摸）；红黑主题 20 个火星
  自下而上（`#FF5A2E`，每 5 个 `#FFB35A`），蓝白主题 38 个雪花自上而下（`#9CC4F0`，每 7 个纯白，
  另绘不透明核心以便在浅色背景上可见）。布局根节点已去掉 `android:background="?attr/bg"`，
  由 `windowBackground` 透出，粒子才能显示在卡片之间的空白处。
- 实机验证（device，1080×2340）：
  - 红黑冷启动逐帧取样：t≈0.6s 龙身出现在右中（body 色 ~3883 px、火焰 ~1280 px），t≈0.9–1.1s 掠过左上
    （~3679/~2211 px），t≈1.4s 后落定，左上龙标稳定 ~1340 px，明显大于旧版。
  - 静止两帧（间隔 420ms）差异 ~14616 px；屏上可检出火星色像素 254（`#FF5A2E`）+47（`#FFB35A`）。
  - 蓝白：状态栏 `(31,84,192)`，雪花色像素 2863→1195、间隔 350ms 差异 ~36149 px；龙标与雪花同时动。
- 首页 header 高度 84dp、设置/步频/绘制/模拟页 header 64dp；点击穿透正常（模拟页「开始模拟」等可点）。

---

## 7. 已知限制 / 后续可做

1. **目标 App 加壳**：`classes.dex` 89 MB 仅 5 个类，真实代码运行时解密；静态 jadx 无效，只能运行时 hook。
   `.cache/classes.jar` 亦为加密载荷。
2. **目标有第二个进程**（同 uid，无 `:suffix`）未装 hook；若其承担部分定位/计步逻辑，注入不覆盖。
3. **步频监听器覆盖有限**：只捕获 `registerListener` 之后注册的 listener；测试中只看到广告 SDK 的 listener。
   若要让所有监听器一致，需 hook `SystemSensorManager$SensorEventQueue.dispatchSensorEvent` 改 `float[] values`。
4. **「GPS 信号」状态**依赖高德内部状态机，目前靠回填字段把状态变成「优」；进一步可 hook
   `AMapLocationClientOption`/`AMapLocationQualityReport` 之类。
5. 未实现：NFC、剪贴板/基站/WiFi 伪造、Gitee 后端上传、蜂鸣/提醒音等牢大其余能力（按需求裁剪）。
6. `StateChannel`（abstract LocalSocket）与 `ProviderClient` 为历史方案，可删除以精简代码。

---

## 8. 快速使用流程

1. 打开「DragonRun」→「＋ 绘制路线」在地图上点若干点 → 保存（≥2 点）。
2. 首页点该路线「查看」→ 选速度/步频、圈数（默认 100）→「开始模拟」。
3. 点「打开步道乐跑」→ 正常进入「开始乐跑」→ 运行页即显示被接管的距离/配速。
4. 需要步频时在「步频」页开「启用步频接管」，并可用悬浮胶囊随时开关。
5. 模拟状态由前台服务保活；App 被系统杀掉后重新打开会自动恢复（依据 `startedAt` + `state.json`）。
