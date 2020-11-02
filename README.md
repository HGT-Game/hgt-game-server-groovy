# HGT Game Server

海龟汤 游戏服务端

# 简介

HGT Game Server 是一个基于海龟汤故事实现的游戏服务端，主要语言是 Groovy & Java。

# 技术

| -- | 说明|
|---|---|
| Netty | 构建游戏基础网络通信(Websocket连接)
| Vert.x | 构建 Web 服务器 / EventBus
| RxJava | 搭配异步编程 / 线程处理
| Protobuf | 游戏传输协议
| MongoDB | 游戏服务端持久化数据库(过期缓存数据持久化)
| Caffeine Cache | 游戏数据内存缓存

# 说明

- [目录结构](doc/CONTENT.MD)

# 运行

- 运行环境 : `JDK 11 +`
- 更改数据库配置文件和服务器配置文件 : `config/mongodb.json` & `config/server.json`
- 运行游戏服务端启动入口类 : `io.github.hdfg159.Main`

# 贡献

欢迎参与项目贡献！比如提交PR修复一个bug，或者新建 Issue 讨论新特性或者变更。

# License

hgt-game-server is under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0) - see the [LICENSE](LICENSE) file for details.