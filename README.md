# AdBlocker — Android 全局广告屏蔽

无需 Root，利用 Android VpnService API 创建本地 VPN，在 DNS 层面拦截广告域名，实现全应用广告屏蔽。

## 工作原理

```
所有 App 网络流量 → TUN 虚拟网卡 → PacketReader
  ├── DNS 查询 (UDP 53) → DnsCodec → 检查屏蔽列表
  │   ├── 命中 → 返回 0.0.0.0（广告域名被阻止）
  │   └── 未命中 → 转发到 1.1.1.1（正常解析）
  ├── TCP → TcpRelay（NIO 转发到真实服务器）
  └── UDP → UdpRelay（DatagramSocket 转发）
```

## 功能特性

- **全局广告拦截**：适用于所有 App（浏览器、游戏、工具等）
- **DNS 级过滤**：200+ 内置广告/追踪域名，支持父域名匹配
- **实时统计**：通知栏显示已拦截数量
- **Material 3 界面**：Jetpack Compose + 暗色模式
- **高性能转发**：Java NIO Selector TCP 中继 + UDP 会话管理
- **低资源占用**：ConcurrentHashMap NAT 表，自动清理过期连接

## 构建

```bash
./gradlew assembleDebug
```

或使用 GitHub Actions 自动构建（[下载 APK](https://github.com/Xzj0021/adblocker/actions)）。

## 技术栈

| 层级 | 技术 |
|------|------|
| VPN 框架 | Android VpnService API |
| UI | Jetpack Compose + Material 3 |
| 数据持久化 | Room (SQLite) |
| 并发 | Kotlin Coroutines + NIO Selector |
| DNS 解析 | 自实现 DNS 协议编解码 |
| IP/TCP/UDP | 自实现协议栈（头部解析+校验和） |

## 使用

1. 安装 APK
2. 点击开关 → 授权 VPN 权限
3. 打开任何 App 验证广告是否被屏蔽

## 限制

- DNS 级过滤无法阻止与正常内容混在一起的广告（如 YouTube 视频广告）
- 不能与其他 VPN 应用同时使用
- 不支持 DoH/DoT 加密 DNS

## 许可

MIT License
