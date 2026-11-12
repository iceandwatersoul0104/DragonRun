# DragonRun

> 步道乐跑（`com.lptiyu.tanke`）路线模拟 / 步频接管 / 去广告 —— 一个自包含的 LSPosed 模块。
> 单 APK：既是管理界面，也是注入模块。

<p align="left">
  <img alt="platform" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square" />
  <img alt="framework" src="https://img.shields.io/badge/LSPosed-API%2093%2B-2F6FED?style=flat-square" />
  <img alt="license" src="https://img.shields.io/badge/License-GPL--3.0-E23A2E?style=flat-square" />
</p>

DragonRun 把两个思路合并进了一个应用：**按路线模拟跑步轨迹**（速度模式 / 步频模式，自然的速度、路径与海拔抖动）与**传感器步频接管**，并额外提供**一键去广告**。所有注入逻辑运行在 LSPosed 作用域内的步道乐跑进程中，管理界面与注入模块通过一个共享状态文件解耦。

---

## 功能

| 模块 | 说明 |
| --- | --- |
| 🗺️ 路线绘制 | 内置 Leaflet + 高德栅格瓦片（GCJ-02），点选地图即可画出路线，支持撤销、清空、以当前位置为中心加点 |
| 🏃 路线模拟 | 沿路线推进，支持循环圈数；速度模式（km/h）或步频模式（步/分）；可选动态强度、自然路径偏移、海拔漂移、NMEA 会话参数 |
| 🎚️ 步频接管 | 改写加速度计 / 计步器 / 计步检测三类传感器事件，把步频锁定在设定区间（默认 172–182 步/分）；可拖动悬浮球一键开关 |
| 🚫 去广告 | 拦截开屏 / 信息流 / 激励视频：广告 Activity 不启动、广告 View 不加入布局、开屏自动跳过；首页卡片一键切换，重启目标 App 生效 |
| 🛡️ 反检测 | 隐藏 root / Magisk / Xposed / Frida 痕迹，抹除模拟定位标记，RootBeer 检测返回 false，`mock_location` 置 0 |
| 🎨 双主题 | 蓝白（蓝龙・冰焰）与红黑（红龙・火焰），选择持久保存；冷启动有飞龙入场动画，背景有火星 / 雪花粒子 |

## 环境要求

- Android 8.0（API 26）及以上，**已 Root**
- **Magisk**（Zygisk 开启）+ **LSPosed**（API 93+）
- 目标应用：步道乐跑 `com.lptiyu.tanke`
- 已在 MI 10 / Android 13 / Magisk 30400 / LSPosed 2.0.2 上验证

> 没有 Root / LSPosed 时，只能使用界面里的路线绘制与参数设置，注入、步频、去广告均不会生效。

## 安装与启用

1. 安装 `DragonRun.apk`（Release 页附件）。
2. 打开 **LSPosed 管理器 → 模块**，勾选 **DragonRun**。
3. 进入 **作用域**，只勾选 **步道乐跑**（`com.lptiyu.tanke`）。
4. 强制停止步道乐跑，再重新打开。
5. 打开 DragonRun，首页状态条显示 **「模块已连接 · 注入生效」** 即表示链路正常。

## 使用流程

1. **画路线**：首页「＋ 绘制路线」→ 地图上依次点选 → 「保存」。
2. **配参数**：点路线卡片「查看」→ 选择速度或步频、设置循环圈数、按需打开动态强度 / 路径偏移 / 海拔漂移 / NMEA。
3. **开始模拟**：点「开始模拟」→ 打开步道乐跑并进入跑步页，距离与配速会按路线推进。
4. **步频 / 去广告 / 反检测**：首页「步频」页调区间并开启接管；首页「去广告」卡片一键切换；「设置」页可调定位信号参数与反检测开关。

> 开启步频接管后，高德可能叠加「计步器估算」导致速度偏快；把步频设为约速度的 4 倍，或关闭步频接管即可对齐。

## 从源码构建

不使用 Gradle，直接用 Android SDK 工具链构建（aapt2 → javac → d8 → zipalign → apksigner）。

**依赖**

- JDK 17+（`JAVA_HOME`）
- Android SDK：`platforms;android-35`、`build-tools;35.0.0`
- Windows PowerShell 5.1+（脚本使用 `System.IO.Compression` 打包 assets）

**构建**

```powershell
# 可选：指定 SDK / JDK（否则回退到 <repo>\tools\android\sdk 与 <repo>\tools\jdk-21）
$env:ANDROID_SDK_ROOT = 'C:\Android\Sdk'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'

powershell -NoProfile -ExecutionPolicy Bypass -File app\build.ps1
# 输出：app\build\DragonRun.apk
```

首次构建会在 `tools\debug.keystore` 自动生成调试签名（`androiddebugkey` / `android`）。发布正式版请替换为自己的 keystore。

**安装（MIUI 等限制 `adb install` 的机型）**

```bash
adb push app/build/DragonRun.apk /data/local/tmp/dragonrun.apk
adb shell su -c 'pm install -r -t /data/local/tmp/dragonrun.apk'
```

## 项目结构

```
app/
  AndroidManifest.xml
  build.ps1                  # 无 Gradle 构建脚本
  assets/
    map.html                 # Leaflet 地图页（高德瓦片，GCJ-02）
    leaflet/                 # Leaflet 1.9.4（BSD-2-Clause）
    xposed_init              # 模块入口
  res/                       # 纯 framework 控件，双主题（attrs + 两套 colors/styles）
  src/com/acooldog/fuckrun/
    MainActivity.java        # 首页：路线列表、去广告卡片、模块状态
    DrawRouteActivity.java   # 地图画线（WebView + JS Bridge）
    SimulationActivity.java  # 模拟参数与实时数据
    CadenceActivity.java     # 步频区间与悬浮开关
    SettingsActivity.java    # 主题、反检测、信号参数
    FloatingService.java     # 悬浮步频开关
    StateService.java        # 前台服务，保持状态文件新鲜
    StateProvider.java
    sim/                     # 路线引擎（沿路线插值 + 速度/海拔/路径抖动）
    data/                    # 路线存储、偏好、状态推送
    ui/                      # 龙形绘制、入场动画、环境粒子
    util/                    # 坐标转换（WGS84/GCJ-02/BD-09）、主题、调色板
    xposed/
      Module.java            # 入口：只处理 com.lptiyu.tanke
      LocationHooks.java     # 四层定位注入
      SensorHooks.java       # 传感器波形改写
      Ads.java               # 广告拦截
      AntiDetect.java        # 反检测
      StateFile.java         # 读取宿主写入的状态文件
  stubs/                     # Xposed API 编译期桩（不打包进 APK）
docs/
  MERGED_APP.md              # 设计、算法、逆向结论与实机验证记录
```

## 工作原理（简述）

- **状态通道**：宿主把路线与运行参数写入
  `/storage/emulated/0/Android/media/com.acooldog.fuckrun/state.json`，模块内守护线程每 500 ms 读取。
  不使用 ContentProvider（MIUI 拦截跨应用唤醒）与 abstract LocalSocket（SELinux 拒绝跨 uid 连接）。
- **定位注入（四层）**：
  1. hook `AMapLocationClient.setLocationListener`，主动回调监听器；
  2. hook `LocationManager.requestLocationUpdates`，向系统监听器分发；
  3. 改写 `AMapLocation` / `Location` 的全部 getter；
  4. 回填 `AMapLocation` 的真实字段（坐标、海拔、速度、精度、时间），让高德判定为有效 GPS。
- **路线引擎**：按累计距离在折线上插值；速度基线由 km/h 或「步频 × 步长」推导，叠加低频噪声、限幅、平滑；路径按航向 +90° 偏移，海拔按基础值 + 身高推导漂移。
- **去广告**：广告最终只有两种落地 —— `startActivity` 或 `addView`，两处都拦；开屏额外在 `Splash` 页面 350 ms 后跳主页，避免白屏等待。

## 已知限制

- 目标 App 的 `classes.dex` 是加密壳（89 MB 载荷，仅 5 个类头），无法静态反编译；本项目的 hook 点均来自运行时观测。
- 目标 App 存在多个进程，模块只在主进程安装 hook。
- 部分广告 SDK（如 CSJ）通过 `Class.forName` / native 加载，`loadClass` 钩子抓不到，但其 View 仍会被 `addView` 层拦截。
- NMEA 语句生成、基站 / WiFi 伪造未实现；本项目只覆盖定位与传感器两条链路。

## 免责声明

本项目仅供**个人学习、Android 逆向与 LSPosed 技术研究**使用。请勿用于违反所在学校 / 单位的跑步考核规定，或违反步道乐跑用户协议的场景。使用本工具产生的一切后果（包括账号处罚、成绩作废等）由使用者自行承担。作者不对因使用本项目造成的任何直接或间接损失负责。

## 致谢与许可

本项目以 **GPL-3.0** 发布，见 [LICENSE](LICENSE)。它建立在以下开源工作之上：

- [Acooldog/fuckschoolrun](https://github.com/Acooldog/fuckschoolrun)（GPL-3.0）—— 路线模拟引擎、NMEA 与定位伪造思路；
- [uy-li/runhook](https://github.com/uy-li/runhook)（GPLv3）—— 步频传感器接管思路；
- [Leaflet](https://leafletjs.com/)（BSD-2-Clause）—— 地图渲染；
- 高德地图栅格瓦片服务 —— 仅作底图展示。

详见 [NOTICE.md](NOTICE.md)。
