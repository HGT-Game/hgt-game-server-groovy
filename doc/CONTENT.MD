```
├─doc                                           // 项目说明文档
└─src
    ├─main
    │  ├─groovy                                 // Groovy 文件
    │  │  └─io
    │  │      └─github
    │  │          └─hdfg159
    │  │              ├─game                    // 游戏 模块
    │  │              │  ├─client               // 游戏客户端
    │  │              │  ├─config               // 服务器配置
    │  │              │  ├─constant             // 常量
    │  │              │  ├─data                 // 抽象游戏数据管理类 / 游戏数据基础类
    │  │              │  ├─domain               // 游戏传输领域模型
    │  │              │  ├─enumeration          // 协议 / 错误码 / 事件 枚举
    │  │              │  ├─handler              // Netty 处理器（连接 / 日志 / 消息分发 / Websocket 消息处理）
    │  │              │  ├─server               // 游戏服务器主类
    │  │              │  ├─service              // 游戏系统服务
    │  │              │  │  ├─avatar            // 玩家系统
    │  │              │  │  └─soup              // 海龟汤系统
    │  │              │  └─util                 // 工具类
    │  │              └─web                     // Web 模块
    │  │                  ├─domain              // Web 领域模型
    │  │                  ├─enumeration         // Web 枚举
    │  │                  └─verticle            // Web Verticle
    │  ├─java                                   // Java 文件
    │  │  └─io
    │  │      └─github
    │  │          └─hdfg159
    │  │              └─common                  // 公共模块
    │  │                  └─util                // 公共工具类
    │  └─resources                              // 资源目录
    │      ├─config                             // 配置文件 json
    │      └─protobuf                           // Protobuf 传输协议
    └─test                                      // 测试
        └─groovy
```