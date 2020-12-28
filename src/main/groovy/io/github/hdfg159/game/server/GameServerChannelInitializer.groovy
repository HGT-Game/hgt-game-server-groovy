package io.github.hdfg159.game.server

import io.github.hdfg159.game.config.ServerConfig
import io.github.hdfg159.game.domain.dto.GameMessage
import io.github.hdfg159.game.handler.*
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import io.netty.handler.timeout.IdleStateHandler
import io.vertx.reactivex.core.Vertx

import java.util.concurrent.TimeUnit

/**
 * Project:starter
 * <p>
 * Package:io.github.hdfg159.game
 * <p>
 * 游戏服务端通道初始化
 * @date 2020/7/15 10:30
 * @author zhangzhenyu
 */
class GameServerChannelInitializer extends ChannelInitializer<Channel> {
    private static final LogHandler LOG_HANDLER = new LogHandler()

    private static final ProtobufEncoder PROTOBUF_ENCODER = new ProtobufEncoder()
    private static final ProtobufDecoder PROTOBUF_DECODER = new ProtobufDecoder(GameMessage.Message.getDefaultInstance())

    private static final WebSocketBinaryMessageOutHandler WEBSOCKET_BINARY_MESSAGE_OUT_HANDLER = new WebSocketBinaryMessageOutHandler()
    private static final WebSocketBinaryMessageInHandler WEBSOCKET_BINARY_MESSAGE_IN_HANDLER = new WebSocketBinaryMessageInHandler()

    private MessageHandler messageHandler
    private ConnectionHandler connectionHandler

    private Vertx vertx
    private ServerConfig config
    private GameMessageDispatcher dispatcher

    GameServerChannelInitializer(Vertx vertx, ServerConfig config) {
        this.vertx = vertx
        this.config = config
        this.dispatcher = new GameMessageDispatcher(vertx)
        this.messageHandler = new MessageHandler(vertx, dispatcher)
        this.connectionHandler = new ConnectionHandler(config.maxConnection, dispatcher)
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        def pipeline = ch.pipeline()
        pipeline.addLast(new IdleStateHandler(10 * 60, 0, 0, TimeUnit.SECONDS))

                .addLast(new HttpServerCodec())
                .addLast(new HttpObjectAggregator(65536))

                .addLast(new WebSocketServerCompressionHandler())
                .addLast(new WebSocketServerProtocolHandler("/", null, true, 65536, true))
                .addLast(WEBSOCKET_BINARY_MESSAGE_OUT_HANDLER)
                .addLast(WEBSOCKET_BINARY_MESSAGE_IN_HANDLER)
                .addLast(PROTOBUF_DECODER)
                .addLast(PROTOBUF_ENCODER)
        if (config.log) {
            pipeline.addLast(LOG_HANDLER)
        }

        pipeline.addLast(connectionHandler).addLast(messageHandler)
    }
}
