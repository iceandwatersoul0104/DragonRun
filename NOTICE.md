# NOTICE

DragonRun（`com.acooldog.fuckrun`）以 GNU General Public License v3.0 发布。

## 上游工作

本项目的部分设计与算法来自以下 GPL 项目，遵循其许可证要求在此致谢并同样以 GPL-3.0 开源：

### Acooldog/fuckschoolrun (GPL-3.0)

<https://github.com/Acooldog/fuckschoolrun>

- `sim/SimulationEngine.java` 的路线插值、速度/强度抖动、路径与海拔偏移模型参考其 `RouteSimulationEngine`；
- `xposed/LocationHooks.java` 的多层定位注入与 `AMapLocation`/`Location` 字段处理参考其 `LocationSimulationGateway` 与 LSPosed 集成方式；
- NMEA 语句格式（GPRMC/GPGGA/GPGSV/GPGSA/GPVTG）与卫星参数取值范围参考其 `CompleteNmeaGenerator`；
- 反检测覆盖范围（RootBeer、`File.exists`、`Settings.Secure`、`Build`、`Debug`）参考其 root 诊断模块。

### uy-li/runhook (GPL-3.0)

<https://github.com/uy-li/runhook>

- `xposed/SensorHooks.java` 的传感器接管方式（hook `registerListener` 捕获 `SensorEventListener`，改写加速度计 / 计步器 / 计步检测事件）参考其 `JoyrunHook`；
- 步频区间 172–182 步/分与悬浮开关交互参考其实现。

## 第三方组件

| 组件 | 版本 | 许可证 | 用途 |
| --- | --- | --- | --- |
| Leaflet | 1.9.4 | BSD-2-Clause | 地图渲染（`app/assets/leaflet/`） |
| 高德地图栅格瓦片 | — | 高德服务条款 | 地图底图（在线加载，不含任何 SDK 或 Key） |

## 声明

- 本项目不包含任何上游项目的闭源代码或未授权资源；
- 目标应用（步道乐跑）的商标、资源与代码归其权利人所有，本项目仅通过运行时 hook 与其交互，不再分发其任何内容；
- 本项目与步道乐跑、高德、字节跳动及其他广告平台无任何关联。
