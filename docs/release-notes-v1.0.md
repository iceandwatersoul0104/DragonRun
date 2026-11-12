# DragonRun 1.0

单 APK：既是管理界面，也是 LSPosed 模块。适用于步道乐跑 `com.lptiyu.tanke`。

## 功能

- **路线绘制**：Leaflet + 高德栅格瓦片，点选地图画线，支持撤销 / 清空 / 以当前位置为中心加点。
- **路线模拟**：沿路线推进、可循环；速度模式或步频模式；动态强度、自然路径偏移、海拔漂移；实时距离 / 配速 / 用时。
- **步频接管**：改写加速度计、计步器、计步检测事件，锁定 172–182 步/分（可调）；悬浮球一键开关。
- **一键去广告**：拦截开屏 / 信息流 / 激励视频，首页卡片切换，重启目标 App 生效。
- **反检测**：隐藏 root / Magisk / Xposed / Frida 痕迹，抹除模拟定位标记，RootBeer 返回 false，`mock_location` 置 0。
- **双主题**：蓝白（蓝龙・冰焰）与红黑（红龙・火焰），持久保存；飞龙入场动画 + 火星 / 雪花背景粒子。

## 运行环境

- Android 8.0（API 26）及以上，已 Root
- Magisk（开启 Zygisk）+ LSPosed（API 93+）
- 已验证：Xiaomi Mi 10 / Android 13 / Magisk 30400 / LSPosed 2.0.2

## 安装

1. 下载并安装 `DragonRun.apk`。
2. 打开 LSPosed 管理器 → 模块 → 勾选 **DragonRun**。
3. 作用域只勾选 **步道乐跑** `com.lptiyu.tanke`。
4. 强制停止步道乐跑后重新打开；DragonRun 首页显示「模块已连接 · 注入生效」即正常。

> MIUI 等机型若 `adb install` 报 `INSTALL_FAILED_USER_RESTRICTED`，可先 push 再 `su -c 'pm install -r -t ...'`。

## 校验

```
SHA-256  177dd929b94a0b6f49db9cdae4d699131d74a657ef94be1cd83c14f5e3bf908e
文件大小  140,916 bytes
```

## 说明

- 许可证：GPL-3.0（源码见仓库；基于 Acooldog/fuckschoolrun 与 uy-li/runhook 的思路，详见 NOTICE.md）。
- 仅供个人学习与 Android 逆向 / LSPosed 技术研究，请遵守所在学校规定与目标应用的用户协议。
