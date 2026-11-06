# 网络拦截

## S-BLOCK-001

Status: Candidate

Statement: 网络拦截只针对当前 Android 用户下的 `com.mobiletools.systemhelper`，覆盖该目标的 IPv4 和 IPv6 流量。

Reason: 拦截对象和流量范围决定工具对设备上其他应用的影响，调整范围需要明确的产品意图。

Evidence: [项目说明](../../README.md) 记载了目标范围，[BlockService.onStartCommand](../../src/BlockService.kt) 配置了单包允许列表及 IPv4、IPv6 默认路由。真机拦截效果仍待验证。
