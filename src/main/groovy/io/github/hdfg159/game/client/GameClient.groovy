package io.github.hdfg159.game.client

import groovy.util.logging.Slf4j
import io.github.hdfg159.common.util.IdUtils
import io.github.hdfg159.game.domain.dto.GameMessage
import io.github.hdfg159.game.domain.dto.SoupMessage
import io.github.hdfg159.game.enumeration.ProtocolEnums
import io.github.hdfg159.game.util.GameUtils
import io.netty.bootstrap.Bootstrap
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
	def static channel
	
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
		
		channel = startFuture.channel()
		for (; ;) {
			def cmd = System.in.newReader().readLine()
			log.info " CMD:${cmd} ".center(100, "=")
			
			// 下面业务代码
			if (channel.active) {
				switch (cmd) {
					case "1002":
						login("admin", "admin")
						break
					case "10021":
						login("admin123", "admin123")
						break
					case "1003":
						register("admin", "admin")
						break
					case "10031":
						register("admin123", "admin123")
						break
					case "9999999":
						test()
						break
					case "2001":
						soupRoomHall()
						break
					case "2002":
						createSoupRoom()
						break
					case "2004":
						leaveSoupRoom()
						break
					case "2005":
						prepareSoupRoom()
						break
					default:
						break
				}
			}
		}
	}
	
	def static soupRoomHall() {
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_ROOM_HALL, null)
		channel.writeAndFlush(reg)
	}
	
	def static createSoupRoom() {
		def req = SoupMessage.CreateRoomReq.newBuilder().setName(IdUtils.idStr.substring(0, 5)).setMax(10).build()
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_CREATE_ROOM, req)
		channel.writeAndFlush(reg)
	}
	
	def static joinSoupRoom() {
		def req = SoupMessage.JoinRoomReq.newBuilder().setRoomId("ads").build()
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_JOIN_ROOM, req)
		channel.writeAndFlush(reg)
	}
	
	def static leaveSoupRoom() {
		def req = SoupMessage.LeaveRoomReq.newBuilder().build()
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_LEAVE_ROOM, req)
		channel.writeAndFlush(reg)
	}
	
	def static prepareSoupRoom() {
		def req = SoupMessage.PrepareReq.newBuilder().setOk(true).build()
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_PREPARE, req)
		channel.writeAndFlush(reg)
	}
	
	def static register(username, password) {
		def registerReq = GameMessage.RegisterReq.newBuilder()
				.setUsername(username)
				.setPassword(password)
				.build()
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_REGISTER, registerReq)
		channel.writeAndFlush(reg)
	}
	
	def static login(username, password) {
		def login = GameUtils.reqMsg(
				ProtocolEnums.REQ_LOGIN,
				GameMessage.LoginReq.newBuilder()
						.setUsername(username)
						.setPassword(password)
						.build()
		)
		channel.writeAndFlush(login)
	}
	
	def static test() {
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
