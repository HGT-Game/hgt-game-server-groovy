package io.github.hdfg159.game.client

import groovy.util.logging.Slf4j
import io.github.hdfg159.common.util.IdUtils
import io.github.hdfg159.game.domain.dto.GameMessage
import io.github.hdfg159.game.enumeration.ProtocolEnums
import io.github.hdfg159.game.util.GameUtils
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel

/**
 * Project:starter
 * <p>
 * Package:io.github.hdfg159.game.domain
 * <p>
 * 游戏客户端
 * @date 2020/7/15 14:21
 * @author zhangzhenyu
 */
@Slf4j
@Singleton
class GameClient {
	ChannelFuture startFuture
	EventLoopGroup group
	def port = 9998
	def host = "127.0.0.1"
	
	void start() {
		group = new NioEventLoopGroup()
		def bootstrap = new Bootstrap()
		bootstrap.group(group)
				.channel(NioSocketChannel.class)
				.remoteAddress(host, port)
				.handler(new GameClientChannelInitializer())
		
		startFuture = bootstrap.connect().sync()
		log.info("${this.class.simpleName} started and listening for connections on ${startFuture.channel().localAddress()}")
		
		// 关闭 Client
		Runtime.addShutdownHook {
			log.info("shutdown client,closing  ...")
			stop()
			log.info "shutdown client success"
		}
		
		def channel = startFuture.channel()
		def cmd = System.in.newReader().readLine()
		log.info " CMD:${cmd} ".center(100, "=")
		
		// 下面业务代码
		if (channel.active) {
			login(channel)
		}
	}
	
	def static register(Channel channel) {
		// def username = UUID.randomUUID().toString()
		def username = "admin"
		def password = "admin"
		def registerReq = GameMessage.RegisterReq.newBuilder()
				.setUsername(username)
				.setPassword(password)
				.build()
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_REGISTER, registerReq)
		channel.writeAndFlush(reg)
	}
	
	def static login(Channel channel) {
		def login = GameUtils.reqMsg(
				ProtocolEnums.REQ_LOGIN,
				GameMessage.LoginReq.newBuilder()
						.setUsername("admin")
						.setPassword("admin")
						.build()
		)
		channel.writeAndFlush(login)
	}
	
	def static test(Channel channel) {
		def testReq = GameMessage.TestReq.newBuilder()
				.setStr(IdUtils.idStr)
				.build()
		def test = GameUtils.reqMsg(ProtocolEnums.REQ_TEST, testReq)
		channel.writeAndFlush(test)
	}
	
	void stop() {
		try {
			startFuture.channel().closeFuture()
		} finally {
			group.shutdownGracefully().sync()
		}
		log.info("${this.class.simpleName} stopped success")
	}
	
	static void main(String[] args) {
		getInstance().start()
	}
}
