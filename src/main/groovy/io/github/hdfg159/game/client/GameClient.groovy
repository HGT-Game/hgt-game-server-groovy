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
			try {
				log.info " 请输入命令 ".center(100, "=")
				def cmd = System.in.newReader().readLine()
				log.info " CMD:${cmd} ".center(100, "=")
				def args = cmd.split(" ")
				// 下面业务代码
				if (channel.active) {
					switch (args[0]) {
						case "${ProtocolEnums.REQ_LOGIN.protocol}":
							login(args[1], args[2])
							break
						case "${ProtocolEnums.REQ_REGISTER.protocol}":
							register(args[1], args[2])
							break
						case "${ProtocolEnums.REQ_TEST.protocol}":
							test()
							break
						case "${ProtocolEnums.REQ_SOUP_ROOM_HALL.protocol}":
							soupRoomHall()
							break
						case "${ProtocolEnums.REQ_SOUP_CREATE_ROOM.protocol}":
							createSoupRoom(args[1], args[2].toInteger(), args.length < 4 ? null : args[3])
							break
						case "${ProtocolEnums.REQ_SOUP_JOIN_ROOM.protocol}":
							joinSoupRoom(args[1])
							break
						case "${ProtocolEnums.REQ_SOUP_LEAVE_ROOM.protocol}":
							leaveSoupRoom()
							break
						case "${ProtocolEnums.REQ_SOUP_PREPARE.protocol}":
							prepareSoupRoom()
							break
						case "${ProtocolEnums.REQ_SOUP_KICK.protocol}":
							kick()
							break
						case "${ProtocolEnums.REQ_SOUP_CHAT.protocol}":
							chat(args[1])
							break
						case "${ProtocolEnums.REQ_SOUP_ANSWER.protocol}":
							answer(args[1], args[2].toInteger())
							break
						case "${ProtocolEnums.REQ_SOUP_END.protocol}":
							end()
							break
						case "${ProtocolEnums.REQ_SOUP_SELECT_QUESTION.protocol}":
							selectQuestion(args[1])
							break
						case "${ProtocolEnums.REQ_SOUP_LOAD.protocol}":
							load()
							break
						case "${ProtocolEnums.REQ_SOUP_LOAD_NOTE.protocol}":
							loadNote(args[1])
							break
						case "${ProtocolEnums.REQ_SOUP_ADD_NOTE.protocol}":
							addNote(args[1], args[2])
							break
						case "${ProtocolEnums.REQ_SOUP_DELETE_NOTE.protocol}":
							deleteNote(args[1])
							break
						default:
							break
					}
				}
			} catch (Exception e) {
				log.error "命令执行错误:${e.message}"
			}
		}
	}
	
	static def selectQuestion(id) {
		def req = SoupMessage.SelectQuestionReq.newBuilder().setId(id).build()
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_SELECT_QUESTION, req)
		channel.writeAndFlush(reg)
	}
	
	static def kick() {
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_KICK, SoupMessage.KickReq.newBuilder().build())
		channel.writeAndFlush(reg)
	}
	
	static def load() {
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_LOAD, null)
		channel.writeAndFlush(reg)
	}
	
	static def end() {
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_END, null)
		channel.writeAndFlush(reg)
	}
	
	static def answer(id, answer) {
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_ANSWER, SoupMessage.AnswerReq.newBuilder().setId(id).setAnswer(answer).build())
		channel.writeAndFlush(reg)
	}
	
	static def chat(msg) {
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_CHAT, SoupMessage.ChatReq.newBuilder().setContent(msg).build())
		channel.writeAndFlush(reg)
	}
	
	def static soupRoomHall() {
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_ROOM_HALL, null)
		channel.writeAndFlush(reg)
	}
	
	def static createSoupRoom(name, max, password) {
		def builder = SoupMessage.CreateRoomReq.newBuilder()
				.setName(name)
				.setMax(max)
		if (password) {
			builder.setPassword(password)
		}
		
		def reg = GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_CREATE_ROOM, builder.build())
		channel.writeAndFlush(reg)
	}
	
	def static joinSoupRoom(roomId) {
		def req = SoupMessage.JoinRoomReq.newBuilder().setRoomId(roomId).build()
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
	
	def static loadNote(String s) {
		def req = SoupMessage.LoadNoteReq.newBuilder()
				.setAid(s)
				.build()
		channel.writeAndFlush(GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_LOAD_NOTE, req))
	}
	
	def static addNote(p1, p2) {
		def builder = SoupMessage.AddNoteReq.newBuilder()
		if (p1) {
			builder.setMessageId(p1)
		}
		if (p2) {
			builder.setContent(p2)
		}
		def req = builder.build()
		channel.writeAndFlush(GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_ADD_NOTE, req))
	}
	
	def static deleteNote(String s) {
		def req = SoupMessage.DeleteNoteReq.newBuilder()
				.setId(s)
				.build()
		channel.writeAndFlush(GameUtils.reqMsg(ProtocolEnums.REQ_SOUP_DELETE_NOTE, req))
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
