# 华师课表 Android 客户端

这是一个纯 Android SDK/Java 项目，不依赖外部 AndroidX 库。功能包括：

- 统一身份认证登录
- WebView 自动抓取学生课程表
- 每周/每月自动更新与手动更新
- 手机本地日历导入
- 上课前 30/15/5 分钟提醒
- 浅色/深色蓝白主题
- 登录失败原因提示与重试
- 日程/周程/更多底部导航
- 一周网格课表与校历周次/日期匹配
- Android 15 状态栏安全区适配
- 自动点击学校“确定登录”确认按钮
- 消息提醒/闹钟提醒两种上课提醒方式
- 左右滑动切换页面
- 今日课程/本周概览/一周课表三种桌面小组件
- 修复 RemoteViews 不支持 View 导致的小组件加载错误
- 页面切换淡入淡出过渡
- 确认登录按钮原生触摸自动点击

## 构建

1. 安装 JDK 17 或更高版本。
2. 安装 Android SDK Platform 35 和 Build Tools 35.0.0。
3. 将 `local.properties.example` 复制为 `local.properties`，修改 SDK 路径。
4. 运行：

```powershell
.\gradlew.bat assembleDebug
```

如果没有 Gradle Wrapper，可使用已安装的 Gradle 8.7；项目已在 Gradle 8.7、Android Gradle Plugin 8.5.2 下构建验证。
