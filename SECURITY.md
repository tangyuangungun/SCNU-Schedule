# 安全说明

## 报告安全问题

请通过项目仓库的 GitHub Security Advisory 私下报告安全漏洞。请勿在公开 Issue 中发布账号、密码、学号、验证码、Cookie、访问令牌或完整课程表。

如果仓库尚未启用 Private vulnerability reporting，请先在 GitHub 仓库的 Security 设置中启用。

## 凭据存储

- Android 应用使用 Android Keystore 生成不可导出的 AES-GCM 密钥。
- 账号和密码仅以密文保存在应用私有 SharedPreferences 中。
- 旧版本明文凭据会在首次读取时迁移，并在加密写入成功后删除。
- 未勾选“加密保存登录信息”时，密码只保留在当前应用进程内。
- `android:allowBackup="false"`，应用数据不参与 Android 自动备份。

## 发布验证

正式安装包必须：

- 使用项目维护者离线保管的正式密钥签名。
- 在 Release 页面公布 SHA-256。
- 在 Release 页面公布签名证书 SHA-256 指纹。
- 使用递增的 `versionCode`。
- 不包含真实账号、密码、学号、姓名、本机路径或测试数据。

## 用户注意事项

- 仅从项目 GitHub Releases 或管理员发布的 QQ 群文件安装。
- 收到转发的 APK 时，应重新核对 SHA-256 和签名指纹。
- 不要使用来源不明的“修改版”“加速版”或“免登录版”。
- 发现可疑版本时请停止安装并联系维护者。