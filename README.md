# HGT Game Server

海龟汤 游戏服务端

# 简介

HGT Game Server 是一个基于海龟汤故事实现的游戏服务端，主要语言是 Groovy & Java。

# 技术

| -- | 说明|
|---|---|
| [Netty](https://github.com/netty/netty)| 构建游戏基础网络通信(Websocket连接)
| [Vert.x](https://github.com/eclipse-vertx/vert.x) | 构建 Web 服务器 / EventBus
| [RxJava](https://github.com/ReactiveX/RxJava) | 搭配异步编程 / 线程处理
| [Protobuf](https://github.com/protocolbuffers/protobuf) | 游戏传输协议
| [MongoDB](https://github.com/mongodb/mongo) | 游戏服务端持久化数据库(过期缓存数据持久化)
| [Caffeine Cache](https://github.com/ben-manes/caffeine) | 游戏数据内存缓存

# 说明

- [目录结构](doc/CONTENT.MD)

# 运行

- 运行环境 : `JDK 11 +`
- 更改配置文件
    - 游戏日志数据库 : `config/db_log.json` 
    - 游戏数据库 : `config/db_game.json`
    - 游戏配置数据库 : `config/db_config.json`
    - 游戏服务器配置 : `config/server.json`
    - 游戏 web 服务器配置 : `config/web_server.json`
- 运行游戏服务端启动入口类 : `io.github.hdfg159.Main`

# 贡献

欢迎参与项目贡献！比如提交PR修复一个bug，或者新建 Issue 讨论新特性或者变更。

# License

hgt-game-server is under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0) - see the [LICENSE](LICENSE) file for details.