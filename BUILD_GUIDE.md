# Android App 编译安装指南

## 方式一：使用 Android Studio（推荐，最简单）

### 步骤 1：安装 Android Studio
1. 访问 https://developer.android.com/studio
2. 下载并安装 Android Studio（免费）
3. 安装时选择 "Standard" 安装

### 步骤 2：打开项目
1. 启动 Android Studio
2. 选择 "Open an existing project"
3. 选择 `C:\Users\XX\WorkBuddy\2026-06-25-06-19-48\android-sms-app` 文件夹
4. 等待 Gradle 同步完成（第一次需要 5-10 分钟下载依赖）

### 步骤 3：连接手机
1. 手机开启"开发者选项"（设置 → 关于手机 → 连续点击"版本号"7次）
2. 开启"USB调试"
3. 用USB线连接电脑和手机
4. 手机上允许USB调试

### 步骤 4：运行 App
1. 在 Android Studio 顶部点击"运行"按钮（绿色三角形）
2. 选择你的手机设备
3. App 会自动安装并启动

---

## 方式二：命令行编译（无需 Android Studio）

### 前提：安装 Android SDK
```bash
# 下载 Android Command Line Tools
# https://developer.android.com/studio#command-line-tools-only

# 设置环境变量
set ANDROID_HOME=C:\Android\Sdk
set PATH=%PATH%;%ANDROID_HOME%\tools;%ANDROID_HOME%\platform-tools
```

### 编译 APK
```bash
cd android-sms-app
./gradlew assembleDebug
```

APK 会生成在：`app\build\outputs\apk\debug\app-debug.apk`

### 安装到手机
```bash
adb install app\build\outputs\apk\debug\app-debug.apk
```

---

## 方式三：我帮你在线编译（最快）

如果你不想安装 Android Studio，可以：

1. 把 `android-sms-app` 文件夹上传到在线编译服务
2. 推荐：https://codeMagic.io 或 https://www.mobiloud.com
3. 或者使用 GitHub Actions 自动编译

---

## 安装后的使用步骤

### 1. 首次启动 App
- 授予短信发送权限
- 授予电话状态权限
- 授予通知权限

### 2. 配置服务器地址
- 输入：`http://192.168.2.11:8080`
- 点击"保存服务器地址"

### 3. 允许忽略电池优化
- 点击"电池优化"按钮
- 在系统设置中找到本 App
- 选择"不允许电池优化"

### 4. 在电脑端创建任务
- 打开 http://localhost:8080/web.html
- 添加联系人
- 创建发送任务

### 5. 在手机端同步任务
- 打开 App
- 点击"同步任务"
- App 会自动在后台发送短信

---

## 常见问题

### Q: 提示"短信发送失败"
A: 检查是否已授予短信权限，在系统设置 → 应用中查看

### Q: App 被杀后台
A: 点击"电池优化"按钮，允许忽略电池优化；同时在系统多任务界面锁定 App

### Q: 无法连接到服务器
A: 确保手机和电脑在同一 WiFi，且服务器已启动

---

## 快速测试流程

1. 电脑启动服务器：`python server.py`
2. 手机安装并打开 App
3. 配置服务器地址：`http://192.168.2.11:8080`
4. 电脑端创建任务
5. 手机端点"同步任务"
6. 观察短信发送
