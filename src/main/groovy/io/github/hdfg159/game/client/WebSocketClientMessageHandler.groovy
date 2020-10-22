package io.github.hdfg159.game.client

import groovy.util.logging.Slf4j
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame

/**
 * Project:starter
 * Package:io.github.hdfg159.game.handler
 * Created by hdfg159 on 2020/10/22 23:21.
 */
@Slf4j
class WebSocketClientMessageHandler extends ChannelOutboundHandlerAdapter {
	@Override
	void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		if (msg instanceof ByteBuf) {
			ByteBuf buf = (ByteBuf) msg
			super.write(ctx, (Object) new BinaryWebSocketFrame(buf), promise)
			return
		}
		
		super.write(ctx, msg, promise)
	}
}
