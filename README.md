# 短信自动化 App - 编译指南

## 功能说明
- 后台发送短信（不占用前台画面）
- 和电脑端 web.html 同步任务（同一 WiFi 下）
- 自动分割长短信
- 发送进度通知栏显示

---

## 方案一：用 Android Studio 编译（推荐，最稳定）

### 步骤 1：下载安装 Android Studio
1. 访问 https://developer.android.com/studio
2. 下载并安装（约 1GB，免费）
3. 首次启动会下载 Android SDK（约 500MB）

### 步骤 2：打开项目
1. 启动 Android Studio
2. 点「Open Project」
3. 选择 `C:\Users\XX\WorkBuddy\2026-06-25-06-19-48\android-sms-app` 文件夹
4. 等待 Gradle 同步完成（首次约 5-10 分钟）

### 步骤 3：编译 APK
1. 菜单栏点「Build」→「Build Bundle(s) / APK(s)」→「Build APK(s)」
2. 等待编译完成（约 2-5 分钟）
3. 编译完成后，右下角会弹出通知，点「locate」查看 APK 文件
4. APK 文件路径：`app/build/outputs/apk/debug/app-debug.apk`

### 步骤 4：安装到手机
1. 把 `app-debug.apk` 传到手机（微信/数据线）
2. 手机打开「设置」→「安全」→ 开启「未知来源安装」
3. 点击 APK 文件安装

---

## 方案二：用 GitHub Actions 在线编译（无需安装 Android Studio）

### 步骤 1：创建 GitHub 仓库
1. 注册 GitHub 账号（https://github.com，免费）
2. 点击右上角「+」→「New repository」
3. 仓库名填 `sms-auto-app`，点「Create repository」

### 步骤 2：上传代码
**方法 A：用 GitHub 网页上传**
1. 在仓库页面点「uploading an existing file」
2. 把 `android-sms-app` 文件夹内所有文件拖进去
3. 点「Commit changes」

**方法 B：用 Git 命令（需安装 Git）**
```bash
cd C:\Users\XX\WorkBuddy\2026-06-25-06-19-48\android-sms-app
git init
git add .
git commit -m "initial commit"
git remote add origin https://github.com/你的用户名/sms-auto-app.git
git push -u origin main
```

### 步骤 3：下载编译好的 APK
1. 推送代码后，GitHub Actions 会自动编译（约 5 分钟）
2. 在仓库页面点「Actions」标签
3. 点最新的 workflow 运行记录
4. 在「Artifacts」区域点「app-debug.apk」下载

---

## 方案三：直接找我拿编译好的 APK（最快）

如果你不想自己编译，可以：
1. 把项目代码发给我（通过 GitHub 或其他方式）
2. 我帮你编译成 APK
3. 提供下载链接

---

## 使用说明

### 第一次使用
1. 打开 App，输入服务器地址（如 `http://192.168.2.11:8080`）
2. 点「保存地址」
3. 授予短信发送权限（App 会自动弹窗请求）
4. 建议点「申请后台运行」让系统不杀后台

### 工作流程
1. **电脑端**：打开 `http://localhost:8080/web.html`，添加联系人，创建任务
2. **手机端**：打开 App，点「同步任务」，点「开始执行」
3. App 会在后台发送短信，通知栏显示进度

### 注意事项
- 短信内容中的 `{姓名}` 会自动替换为联系人姓名
- 每条短信间隔 2 秒（避免被运营商判定为垃圾短信）
- 长短信会自动分割成多条发送
- 发送记录保存在手机本地（点「查看日志」查看）

---

## 故障排除

### 编译失败
- 检查 Android Studio 是否正确安装 SDK
- 检查 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 是否正确
- 尝试「File」→「Invalidate Caches / Restart」

### App 安装后无法发送短信
- 检查是否已授予短信发送权限
- 检查服务器地址是否正确（电脑和手机在同一 WiFi）
- 查看 App 内的日志（点「查看日志」）

### 同步任务失败
- 确保电脑端服务器正在运行（`server.py`）
- 确保手机和电脑在同一 WiFi
- 检查服务器地址是否填写正确（不含路径，如 `http://192.168.2.11:8080`）

---

## 技术栈
- Kotlin + Coroutines（后台任务）
- OkHttp（网络请求）
- SMS Manager（发送短信）
- Foreground Service（后台保活）
- ViewBinding（UI 绑定）
