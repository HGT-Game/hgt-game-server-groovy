package io.github.hdfg159.game.client

import io.github.hdfg159.game.domain.dto.GameMessage
import io.github.hdfg159.game.handler.LogHandler
import io.github.hdfg159.game.handler.WebSocketBinaryMessageInHandler
import io.github.hdfg159.game.handler.WebSocketBinaryMessageOutHandler
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.HttpClientCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import io.netty.handler.codec.http.websocketx.WebSocketVersion
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.timeout.IdleStateHandler

import java.util.concurrent.TimeUnit

/**
 * Project:starter
 * <p>
 * Package:io.github.hdfg159.game
 * <p>
 * 游戏客户端通道初始化
 * @date 2020/7/15 10:30
 * @author zhangzhenyu
 */
class GameClientChannelInitializer extends ChannelInitializer<Channel> {
	
	@Override
	protected void initChannel(Channel ch) throws Exception {
		def handShaker = WebSocketClientHandshakerFactory.newHandshaker(
				new URI("ws://127.0.0.1:9998"),
				WebSocketVersion.V13,
				null,
				true,
				new DefaultHttpHeaders()
		)
		
		def sslHandler = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
				.newHandler(ch.alloc())
		
		ch.pipeline()
		// .addFirst(sslHandler)
				.addLast(new IdleStateHandler(0, 0, 180, TimeUnit.SECONDS))
				
				.addLast(new HttpClientCodec())
				.addLast(new HttpObjectAggregator(65536))
				
				.addLast(new WebSocketClientProtocolHandler(handShaker))
				.addLast(new WebSocketBinaryMessageInHandler())
				.addLast(new WebSocketBinaryMessageOutHandler())
				
				.addLast(new ProtobufDecoder(GameMessage.Message.getDefaultInstance()))
				
				.addLast(new ProtobufEncoder())
				
				.addLast(new LogHandler())
				
				.addLast(new HeartbeatHandler())
	}
}
