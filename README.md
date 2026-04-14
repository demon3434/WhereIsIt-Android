![WhereIsIt App Icon](app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)

# WhereIsIt Android

WhereIsIt Android 是一个用于“查找物品 / 管理物品位置”的移动端应用，帮助用户快速定位物品、减少重复寻找时间。

## 项目简介

本项目基于 Android + Kotlin 开发，采用现代 Android 开发栈（Jetpack Compose、ViewModel、Repository 等），并包含网络访问与局域网服务发现能力，用于与后端服务交互。

## 主要功能

- 用户登录与会话管理
- 物品查询与展示
- 标签/筛选相关交互
- 与服务端 API 通信
- 局域网自动发现服务

## 技术栈

- Kotlin
- Jetpack Compose
- Android ViewModel
- Repository 分层
- Retrofit / 网络请求封装
- Gradle Kotlin DSL

## 项目结构

```text
app/
  src/main/java/com/whereisit/findthings/
    data/        # 数据模型、网络层、仓储层
    ui/          # Compose UI、页面与主题
  src/main/res/ # 图片与资源文件
docs/           # 项目文档与开发记录
gradle/         # Gradle Wrapper
```

## 快速开始

1. 使用 Android Studio 打开项目根目录。
2. 同步 Gradle 依赖。
3. 根据本地环境配置服务地址与测试参数。
4. 运行 `app` 模块到模拟器或真机。
