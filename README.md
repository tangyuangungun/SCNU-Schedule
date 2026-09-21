# 华师课表

华南师范大学研究生课表抓取、同步、日历导入和上课提醒项目。

## 目录结构

- `android/`：Android 客户端源码，纯 Java + Android SDK，支持浅色/深色主题。
- `python/`：Python Selenium 课表抓取脚本及虚构示例输出。
- `outputs/`：正式签名的 v1.4.0 APK、Android 使用说明及历史测试文件。

## 功能

- 用户输入统一身份认证账号和密码。
- 自动进入研究生服务平台并抓取课程表。
- 支持每周、每月自动更新，以及手动更新。
- 支持将课程导入手机本地日历。
- 支持提前 30 分钟、15 分钟、5 分钟提醒。
- 支持蓝白简约浅色模式和深色模式。
- 登录失败时显示原因，并可再次点击重试。
- 登录后提供日程、周程和更多三个底部入口；周程以一周网格展示课程。
- 按秋季与春季校历自动计算周次，并将周次与日期对应。
- “更多”中集中提供更新课表、导入日历、提醒、版本号和后续更新入口。
- 修复学校“确定登录”确认页需要手动操作的问题，现可自动识别并点击。
- 上课提醒支持“消息提醒”和“闹钟提醒”两种方式。
- 支持左右滑动切换日程、周程和更多页面。
- 提供“今日课程”“本周概览”“一周课表”三种桌面小组件尺寸。
- 修复小尺寸和大尺寸桌面小组件的加载错误。
- 页面左右滑动加入淡入淡出过渡效果。
- 统一认证“确定登录”改为原生触摸自动确认，降低学校页面对脚本点击的拦截。

## 安装包

正式签名安装包位于 `outputs/SCNU-Schedule-v1.4.0.apk`，并将在 GitHub Releases 中发布。`outputs/SCNU-Schedule-v1.3.0.apk` 仅用于历史测试，不建议继续分发。

切换到正式签名后，已安装测试版的用户需要卸载一次，再安装正式版；后续正式版可在同一签名下覆盖升级。

从 GitHub Releases 或 QQ 群下载后，请核对 Release 中公布的 SHA-256 和签名证书指纹。

## 说明

学校页面或登录流程发生变化时，Android 端的 WebView 选择器和解析逻辑可能需要同步更新。

## 隐私与安全

- 仓库不包含真实账号、学号、密码或课程表数据。
- 运行 Python 脚本时，请设置 `SCNU_ACCOUNT` 和 `SCNU_PASSWORD` 环境变量，不要把真实凭据写入源码或提交到 Git。
- `python/sample_output/` 中的姓名、学号、课程、教师和地点均为虚构示例。
- Android 客户端使用 Android Keystore 生成的 AES-GCM 密钥加密账号和密码，旧版明文会在首次读取时自动迁移。
- 用户可取消“加密保存登录信息”；未保存时，密码只保留在当前应用进程内。

详细内容见 [PRIVACY.md](PRIVACY.md)、[SECURITY.md](SECURITY.md) 和 [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md)。

## 构建正式版

1. 将 `android/release-signing.properties.example` 复制到仓库外并填写真实路径和密码。
2. 设置环境变量 `SCNU_KEYSTORE_PROPERTIES` 指向该文件。
3. 执行 `android\gradlew.bat clean assembleRelease`。
4. 使用 `apksigner` 验证签名并保存 SHA-256。