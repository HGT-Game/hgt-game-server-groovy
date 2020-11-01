package io.github.hdfg159.game.service.soup

import groovy.util.logging.Slf4j
import io.github.hdfg159.common.util.IdUtils
import io.github.hdfg159.game.domain.dto.EventMessage
import io.github.hdfg159.game.domain.dto.SoupEvent
import io.github.hdfg159.game.domain.dto.SoupMessage
import io.github.hdfg159.game.domain.dto.SoupMessage.RoomPush
import io.github.hdfg159.game.enumeration.CodeEnums
import io.github.hdfg159.game.enumeration.EventEnums
import io.github.hdfg159.game.service.AbstractService
import io.github.hdfg159.game.service.avatar.AvatarService
import io.github.hdfg159.game.service.soup.enums.AnswerType
import io.github.hdfg159.game.service.soup.enums.MemberStatus
import io.github.hdfg159.game.service.soup.enums.RoomStatus
import io.github.hdfg159.game.util.GameUtils
import io.github.hdfg159.scheduler.SchedulerManager
import io.github.hdfg159.scheduler.factory.Triggers
import io.reactivex.Completable

import java.time.LocalDateTime
import java.util.function.Function

import static io.github.hdfg159.game.enumeration.ProtocolEnums.*

/**
 * Project:starter
 * <p>
 * Package:io.github.hdfg159.game.service.soup
 * <p>
 *
 * @date 2020/10/23 14:18
 * @author zhangzhenyu
 */
@Slf4j
@Singleton
class TurtleSoupService extends AbstractService {
	def avatarService = AvatarService.getInstance()
	
	def memberData = SoupMemberData.getInstance()
	def recordData = SoupRecordData.getInstance()
	
	def roomData = SoupRoomData.getInstance()
	
	def scheduler = SchedulerManager.INSTANCE
	
	@Override
	Completable init() {
		response(REQ_SOUP_ROOM_HALL, roomHall)
		response(REQ_SOUP_CREATE_ROOM, createRoom)
		response(REQ_SOUP_JOIN_ROOM, joinRoom)
		response(REQ_SOUP_LEAVE_ROOM, leaveRoom)
		response(REQ_SOUP_PREPARE, prepare)
		response(REQ_SOUP_KICK, kick)
		response(REQ_SOUP_EXCHANGE_SEAT, exchangeSeat)
		response(REQ_SOUP_CHAT, chat)
		response(REQ_SOUP_ANSWER, answer)
		response(REQ_SOUP_END, end)
		response(REQ_SOUP_SELECT_QUESTION, selectQuestion)
		
		handleEvent(EventEnums.OFFLINE, offlineEvent)
		handleEvent(EventEnums.ONLINE, onlineEvent)
		handleEvent(EventEnums.SOUP_SEAT_CHANGE, seatChangeEvent)
		
		this.@vertx.rxDeployVerticle(memberData).ignoreElement()
				.mergeWith(this.@vertx.rxDeployVerticle(recordData).ignoreElement())
	}
	
	@Override
	Completable destroy() {
		Completable.complete()
	}
	
	// Request
	
	def roomHall = {headers, params ->
		// def aid = getHeaderAvatarId(headers)
		// def req = params as SoupMessage.RoomHallReq
		
		def builder = SoupMessage.RoomHallRes.newBuilder()
		roomData.getRooms().collect {
			def roomRes = SoupMessage.RoomHallRes.RoomRes.newBuilder()
					.setId(it.id)
					.setName(it.name)
					.build()
			builder.addRooms(roomRes)
		}
		
		GameUtils.sucResMsg(RES_SOUP_ROOM_HALL, builder.build())
	}
	
	def createRoom = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.CreateRoomReq
		def roomRes = SoupMessage.CreateRoomRes.newBuilder()
		
		if (!req.name || req.name.length() > 5) {
			return GameUtils.resMsg(RES_SOUP_CREATE_ROOM, CodeEnums.SOUP_ROOM_NAME_ILLEGAL)
		}
		
		if (req.max <= 0 || req.max > 10) {
			return GameUtils.resMsg(RES_SOUP_CREATE_ROOM, CodeEnums.SOUP_ROOM_MAX_ILLEGAL)
		}
		
		def pair = roomData.create(aid, req.name, req.max, req.password)
		def room = pair.item2
		def resultCode = pair.item1
		
		if (!resultCode.success()) {
			return GameUtils.resMsg(RES_SOUP_CREATE_ROOM, resultCode)
		} else {
			publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
			
			if (room.recordId) {
				def record = room.recordMap.get(room.recordId)
				record.mcId
			}
			
			def res = roomRes.setRoom(buildRoomPush([aid], room.id, {it})).build()
			return GameUtils.sucResMsg(RES_SOUP_CREATE_ROOM, res)
		}
	}
	
	def joinRoom = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.JoinRoomReq
		
		def member = memberData.getById(aid)
		
		def roomId = req.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_JOIN_ROOM, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		if (room.password != req.password) {
			return GameUtils.resMsg(RES_SOUP_JOIN_ROOM, CodeEnums.SOUP_ROOM_JOIN_FAIL)
		}
		
		synchronized (room) {
			def joinRoomResCode = room.joinRoom(aid)
			if (!joinRoomResCode.success()) {
				return GameUtils.resMsg(RES_SOUP_JOIN_ROOM, joinRoomResCode)
			}
			
			// 改变成员状态
			def avaIndex = room.getAvaIndex(aid)
			if (!avaIndex) {
				return GameUtils.resMsg(RES_SOUP_JOIN_ROOM, CodeEnums.SOUP_ROOM_MEMBER_NOT_EXIST)
			}
			
			def memberJoinResCode = member.joinRoom(avaIndex, roomId)
			if (!memberJoinResCode.success()) {
				return GameUtils.resMsg(RES_SOUP_JOIN_ROOM, memberJoinResCode)
			}
			
			publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
			roomPush(CodeEnums.SOUP_ROOM_PUSH, [aid], [aid], roomId, {it})
			
			def sucRes = SoupMessage.JoinRoomRes.newBuilder()
					.setRoom(buildRoomPush(room.getAllMemberIds(), roomId, {it}))
					.build()
			return GameUtils.sucResMsg(RES_SOUP_JOIN_ROOM, sucRes)
		}
	}
	
	def leaveRoom = {headers, params ->
		// def req = params as SoupMessage.LeaveRoomReq
		def aid = getHeaderAvatarId(headers)
		
		def member = memberData.getById(aid)
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_LEAVE_ROOM, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			def resultCode = roomData.leaveRoom(member, room)
			if (resultCode.success()) {
				
				publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(roomId).build())
				roomPush(CodeEnums.SOUP_ROOM_PUSH, [aid], [], roomId, {it})
				
				return GameUtils.sucResMsg(RES_SOUP_LEAVE_ROOM, SoupMessage.LeaveRoomRes.newBuilder().build())
			} else {
				return GameUtils.resMsg(RES_SOUP_LEAVE_ROOM, resultCode)
			}
		}
	}
	
	def prepare = {headers, params ->
		def req = params as SoupMessage.PrepareReq
		def aid = getHeaderAvatarId(headers)
		
		def sucResMsg = GameUtils.sucResMsg(RES_SOUP_PREPARE, SoupMessage.PrepareRes.newBuilder().build())
		def errRes = GameUtils.resMsg(RES_SOUP_PREPARE, CodeEnums.SOUP_PREPARE_FAIL)
		
		def member = memberData.getById(aid)
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_PREPARE, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			if (req.ok) {
				// 准备或开始
				if (room.owner == aid) {
					// 更改玩家状态成功 && 准备人数足够
					if (room.max > room.prepare.size()) {
						return GameUtils.resMsg(RES_SOUP_PREPARE, CodeEnums.SOUP_PREPARE_MAX_NOT_REACH)
					}
					
					if (member.status.compareAndSet(MemberStatus.ROOM.status, MemberStatus.PLAYING.status)) {
						// 更改房间状态
						room.status = RoomStatus.SELECT.status
						
						// todo 获取问题
						def selectQuestions = (1..10).collect {
							def idStr = IdUtils.idStr
							new Tuple3<>(idStr, "Q-${idStr}", "A-${idStr}")
						}
						
						def questionRes = selectQuestions.collect {
							SoupMessage.QuestionRes.newBuilder()
									.setId(it.getV1())
									.setQuestion(it.getV2())
									.setContent(it.getV3())
									.build()
						}
						
						// 开始游戏记录
						def record = SoupRecord.createRecord(room, selectQuestions.collect {it.getV1()})
						def cache = recordData.saveCache(record)
						room.recordMap.put(cache.id, cache)
						room.recordId = cache.id
						
						// 更改其他玩家状态数据
						room.memberIds
								.each {
									def m = memberData.getById(it)
									def push = RoomPush.newBuilder().setRoomId(room.id).setStatus(room.status)
									if (it == room.owner) {
										m.status.compareAndSet(MemberStatus.ROOM.status, MemberStatus.PLAYING.status)
										m.mcTimes += 1
										
										push.addAllSelectQuestions(questionRes)
									} else {
										m.status.compareAndSet(MemberStatus.PREPARE.status, MemberStatus.PLAYING.status)
									}
									m.recordIds.add(cache.id)
									
									// 消息推送
									def msg = GameUtils.resMsg(RES_SOUP_ROOM_PUSH, CodeEnums.SOUP_ROOM_PUSH, push.build())
									avatarService.pushMsg(it, msg)
								}
						
						// 定时任务
						Triggers.once("${roomId}::SELECT", LocalDateTime.now().plusSeconds(10), {
							// 自动选择一题并推送
							autoSelectQuestion(roomId)
						}).schedule()
						
						return sucResMsg
					} else {
						return errRes
					}
				} else {
					if (member.status.compareAndSet(MemberStatus.ROOM.status, MemberStatus.PREPARE.status)) {
						room.prepare.add(aid)
						
						roomPush(CodeEnums.SOUP_ROOM_PUSH, [aid], [], roomId, {it})
						return sucResMsg
					} else {
						return errRes
					}
				}
			} else {
				// 取消准备
				if (room.owner != aid && member.status.compareAndSet(MemberStatus.PREPARE.status, MemberStatus.ROOM.status)) {
					room.prepare.remove(aid)
					
					roomPush(CodeEnums.SOUP_ROOM_PUSH, [aid], [], roomId, {it})
					return sucResMsg
				} else {
					return errRes
				}
			}
		}
	}
	
	def kick = {headers, params ->
		def req = params as SoupMessage.KickReq
		def aid = getHeaderAvatarId(headers)
		
		
		def kickAid = req.aid
		def kickIndex = req.index
		if (!kickAid || !kickIndex) {
			return GameUtils.resMsg(RES_SOUP_KICK, CodeEnums.SOUP_KICK_PARAM_ERROR)
		}
		
		// 主动踢人 成员信息
		def member = memberData.getById(aid)
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_KICK, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			SoupMember kickMember = null
			if (kickAid) {
				kickMember = memberData.getById(kickAid)
			}
			def kickMid = room.memberIds[kickIndex]
			if (kickIndex && kickMid) {
				kickMember = memberData.getById(kickMid)
			}
			
			if (!kickMember) {
				return GameUtils.resMsg(RES_SOUP_KICK, CodeEnums.SOUP_KICK_MEMBER_NOT_EXIST)
			}
			
			def kickResult = roomData.kick(aid, kickMember, room)
			if (kickResult.success()) {
				
				publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
				roomPush(CodeEnums.SOUP_ROOM_PUSH, [kickMember.id], [], roomId, {it})
				
				return GameUtils.sucResMsg(RES_SOUP_KICK, SoupMessage.KickRes.newBuilder().build())
			} else {
				return GameUtils.resMsg(RES_SOUP_KICK, kickResult)
			}
		}
	}
	
	def exchangeSeat = {headers, params ->
		def req = params as SoupMessage.ExchangeSeatReq
		def aid = getHeaderAvatarId(headers)
		def index = req.index
		
		def member = memberData.getById(aid)
		if (member.seat == index) {
			return GameUtils.resMsg(RES_SOUP_EXCHANGE_SEAT, CodeEnums.SOUP_SEAT_EXIST)
		}
		
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_EXCHANGE_SEAT, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			def result = roomData.exchangeSeat(room, member, index)
			if (result.success()) {
				
				publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
				roomPush(CodeEnums.SOUP_ROOM_PUSH, [aid], [], roomId, {it})
				
				return GameUtils.sucResMsg(RES_SOUP_EXCHANGE_SEAT, SoupMessage.ExchangeSeatRes.newBuilder().build())
			} else {
				return GameUtils.resMsg(RES_SOUP_EXCHANGE_SEAT, result)
			}
		}
	}
	
	def chat = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.ChatReq
		def builder = SoupMessage.ChatRes.newBuilder()
		
		def content = req.content
		if (!content || content.length() > 20) {
			return GameUtils.resMsg(RES_SOUP_CHAT, CodeEnums.SOUP_CHAT_CONTENT_ILLEGAL)
		}
		
		def member = memberData.getById(aid)
		def speak = member.speak()
		if (speak > 0) {
			return GameUtils.resMsg(RES_SOUP_CHAT, CodeEnums.SOUP_CHAT_LIMIT, builder.setSeconds(speak).build())
		}
		
		def room = roomData.getRoom(member.roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_CHAT, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		if (room.status != RoomStatus.PLAYING.status) {
			return GameUtils.resMsg(RES_SOUP_CHAT, CodeEnums.SOUP_ROOM_STATUS_NOT_PLAYING)
		}
		
		def record = room.getRecord()
		if (!record) {
			return GameUtils.resMsg(RES_SOUP_CHAT, CodeEnums.SOUP_RECORD_NOT_EXIST)
		}
		
		SoupChatRecord chat = new SoupChatRecord(
				id: IdUtils.idStr,
				mid: aid,
				answer: AnswerType.NON.type,
				content: content,
				createTime: LocalDateTime.now()
		)
		record.chatRecordIds.add(chat.id)
		record.chatRecordMap.put(chat.id, chat)
		record.memberMsgMap.get(aid).add(chat.id)
		
		roomPush(CodeEnums.SOUP_ROOM_PUSH, [], [], room.id, {
			def res = buildMessageRes(chat, aid, record.mcId)
			if (res) {
				it.addChangedMsg(res)
			}
		})
		return GameUtils.sucResMsg(RES_SOUP_CHAT, builder.build())
	}
	
	def answer = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.AnswerReq
		
		def member = memberData.getById(aid)
		def room = roomData.getRoom(member.roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_ANSWER, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		if (room.status != RoomStatus.PLAYING.status) {
			return GameUtils.resMsg(RES_SOUP_ANSWER, CodeEnums.SOUP_ROOM_STATUS_NOT_PLAYING)
		}
		
		def record = room.getRecord()
		if (!record) {
			return GameUtils.resMsg(RES_SOUP_ANSWER, CodeEnums.SOUP_RECORD_NOT_EXIST)
		}
		
		if (record.mcId != aid) {
			return GameUtils.resMsg(RES_SOUP_ANSWER, CodeEnums.SOUP_MEMBER_NOT_MC)
		}
		
		def chat = record.getMsg(req.id)
		if (!chat) {
			return GameUtils.resMsg(RES_SOUP_ANSWER, CodeEnums.SOUP_MESSAGE_NOT_EXIST)
		}
		
		def answerType = AnswerType.valOf(req.answer)
		if (!answerType) {
			return GameUtils.resMsg(RES_SOUP_ANSWER, CodeEnums.SOUP_ANSWER_TYPE_NOT_EXIST)
		}
		
		// 改变数据设置答案
		chat.setAnswer(answerType.type)
		
		roomPush(CodeEnums.SOUP_ROOM_PUSH, [], [], room.id, {
			def res = buildMessageRes(chat, aid, record.mcId)
			if (res) {
				it.addChangedMsg(res)
			}
		})
		
		GameUtils.sucResMsg(RES_SOUP_ANSWER, SoupMessage.AnswerRes.newBuilder().build())
	}
	
	def end = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		// def req = params as SoupMessage.EndReq
		
		def member = memberData.getById(aid)
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_END, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			if (room.status != RoomStatus.PLAYING.status) {
				return GameUtils.resMsg(RES_SOUP_END, CodeEnums.SOUP_ROOM_STATUS_NOT_PLAYING)
			}
			
			def recordId = room.recordId
			def record = room.recordMap.get(recordId)
			if (!record) {
				return GameUtils.resMsg(RES_SOUP_END, CodeEnums.SOUP_RECORD_NOT_EXIST)
			}
			
			if (record.mcId != aid) {
				return GameUtils.resMsg(RES_SOUP_END, CodeEnums.SOUP_MEMBER_NOT_MC)
			}
			
			// 记录结束时间
			record.endTime = LocalDateTime.now()
			// 更改房间状态
			room.recordId = null
			room.status = RoomStatus.WAIT.status
			
			// 重置玩家状态
			room.getAllMemberIds().each {
				def m = memberData.getById(it)
				// 改成在房间
				m.status.compareAndSet(MemberStatus.PLAYING.status, MemberStatus.ROOM.status)
				// 非房主移除准备数据
				if (m.id != room.owner) {
					room.prepare.remove(m.id)
				}
			}
			
			// todo 推送汤底答案和所有位置信息
			roomPush(CodeEnums.SOUP_ROOM_PUSH, room.getAllMemberIds(), [], roomId, {
				def questionRes = SoupMessage.QuestionRes.newBuilder()
						.setContent("A-${record.questionId}")
						.build()
				it.setQuestion(questionRes)
			})
			
			// 强制刷缓存
			recordData.updateForceById(recordId)
			
			return GameUtils.sucResMsg(RES_SOUP_END, SoupMessage.EndRes.newBuilder().build())
		}
	}
	
	def selectQuestion = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.SelectQuestionReq
		
		if (!req.id) {
			return GameUtils.resMsg(RES_SOUP_SELECT_QUESTION, CodeEnums.PARAM_ERROR)
		}
		
		def member = memberData.getById(aid)
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_SELECT_QUESTION, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			if (room.status != RoomStatus.SELECT.status) {
				return GameUtils.resMsg(RES_SOUP_SELECT_QUESTION, CodeEnums.SOUP_ROOM_STATUS_NOT_SELECT)
			}
			
			def record = room.getRecord()
			if (!record) {
				return GameUtils.resMsg(RES_SOUP_SELECT_QUESTION, CodeEnums.SOUP_RECORD_NOT_EXIST)
			}
			
			if (record.mcId != aid) {
				return GameUtils.resMsg(RES_SOUP_SELECT_QUESTION, CodeEnums.SOUP_MEMBER_NOT_MC)
			}
			
			// 取消定时器
			scheduler.cancel("${roomId}::SELECT")
			
			// 选题推送
			room.status = RoomStatus.PLAYING.status
			record.questionId = req.id
			record.memberIds.each {
				memberData.getById(it).questionIds.add(req.id)
			}
			
			// 推送房间状态和题目
			roomPush(CodeEnums.SOUP_ROOM_PUSH, [], [], roomId, {
				it.setQuestion(SoupMessage.QuestionRes.newBuilder().setId(req.id).setQuestion("A-${req.id}").build())
			})
			
			return GameUtils.sucResMsg(RES_SOUP_SELECT_QUESTION, SoupMessage.SelectQuestionRes.newBuilder().build())
		}
	}
	
	// Event
	
	def onlineEvent = {headers, params ->
		def event = params as EventMessage.Online
		log.info "[${event.userId}][${event.username}] online"
		
		def aid = event.userId
		if (!memberData.getById(aid)) {
			memberData.saveCache(new SoupMember(aid))
		}
		
		def member = memberData.getById(aid)
		member.online()
		
		// 断线重连修正数据
		if (!roomData.existRoom(member.roomId)) {
			member.resetRoomInfo()
		}
	}
	
	def offlineEvent = {headers, params ->
		def event = params as EventMessage.Offline
		log.info "[${event.userId}][${event.username}] offline"
		
		def aid = event.userId
		def member = memberData.getById(aid)
		member.offline()
		
		memberData.updateForceById(aid)
	}
	
	def seatChangeEvent = {headers, params ->
		def event = params as SoupEvent.SeatChange
		
		def aid = event.aid
		log.info "[${event.roomId}]${aid} seat change"
		
		// 大厅在线玩家数据修改
		def member = memberData.getById(aid)
		if (member.status.get() == MemberStatus.FREE.status) {
			roomData.addAvaIntoHall(aid)
		} else {
			roomData.removeAvaFromHall(aid)
		}
	}
	
	// Private
	
	def buildSeatRes(SoupMember member, String owner, String mc) {
		if (!member) {
			return null
		}
		
		def avatar = avatarService.getAvatarById(member.id)
		
		SoupMessage.RoomMemberSeatRes.newBuilder()
				.setAid(avatar?.id)
				.setAvaName(avatar?.username)
				.setAvaHead("")
				.setStatus(member?.status?.get())
				.setIndex(member?.seat)
				.setOwner(owner == member?.id)
				.setMc(mc == member?.id)
				.setLeave(member?.roomId == null)
				.build()
	}
	
	def roomPush(CodeEnums code,
				 Collection<String> changeMemberIds,
				 Collection<String> excludePushMemberIds,
				 String roomId,
				 Function<RoomPush.Builder, RoomPush.Builder> mapping) {
		
		def push = buildRoomPush(changeMemberIds, roomId, mapping)
		if (push) {
			def msg = GameUtils.resMsg(RES_SOUP_ROOM_PUSH, code, push)
			avatarService.pushAllMsg(roomData.getRoom(roomId).getAllMemberIds(), excludePushMemberIds.toSet(), msg)
		}
	}
	
	def buildRoomPush(Collection<String> changeMemberIds,
					  String roomId,
					  Function<RoomPush.Builder, RoomPush.Builder> mapping) {
		def room = roomData.getRoom(roomId)
		if (!room) {
			return null
		}
		
		def seatRes = changeMemberIds ? changeMemberIds.collect {
			def member = memberData.getById(it)
			buildSeatRes(member, room.owner, room.owner)
		} : []
		
		def builder = RoomPush.newBuilder()
				.setRoomId(room.id)
				.setStatus(room.status)
				.addAllSeatsChange(seatRes)
		
		mapping.apply(builder).build()
	}
	
	def buildMessageRes(SoupChatRecord chat, String aid, String mcId) {
		if (!chat) {
			return null
		}
		
		def msgResBuilder = SoupMessage.ChatMessageRes.newBuilder()
				.setId(chat.id)
				.setContent(chat.content)
				.setAnswer(chat.answer)
		
		def avatar = avatarService.getAvatarById(aid)
		if (avatar) {
			msgResBuilder
					.setAid(avatar.id)
					.setAvaName(avatar.username)
					.setAvaHead("")
					.setMc(mcId == avatar.id)
		}
		msgResBuilder.build()
	}
	
	def autoSelectQuestion(String roomId) {
		def room = roomData.getRoom(roomId)
		if (!room) {
			return
		}
		
		synchronized (room) {
			if (room.status != RoomStatus.SELECT.status) {
				return
			}
			
			def record = room.getRecord()
			if (!record) {
				return
			}
			
			// 选题推送
			room.status = RoomStatus.PLAYING.status
			
			def questionId = record.selectQuestionIds.shuffled()[0]
			record.questionId = questionId
			record.memberIds.each {
				memberData.getById(it).questionIds.add(questionId)
			}
			
			// 推送房间状态和题目
			roomPush(CodeEnums.SOUP_ROOM_PUSH, [], [], roomId, {
				it.setQuestion(SoupMessage.QuestionRes.newBuilder().setId(questionId).setQuestion("A-${questionId}").build())
			})
		}
	}
}
