package io.github.hdfg159.game.service.soup

import groovy.util.logging.Slf4j
import io.github.hdfg159.common.util.IdUtils
import io.github.hdfg159.game.domain.dto.EventMessage
import io.github.hdfg159.game.domain.dto.SoupEvent
import io.github.hdfg159.game.domain.dto.SoupMessage
import io.github.hdfg159.game.domain.dto.SoupMessage.RoomPush
import io.github.hdfg159.game.enumeration.CodeEnums
import io.github.hdfg159.game.enumeration.EventEnums
import io.github.hdfg159.game.enumeration.LogEnums
import io.github.hdfg159.game.service.AbstractService
import io.github.hdfg159.game.service.avatar.AvatarService
import io.github.hdfg159.game.service.log.GameLog
import io.github.hdfg159.game.service.log.LogService
import io.github.hdfg159.game.service.soup.config.QuestionConfig
import io.github.hdfg159.game.service.soup.enums.*
import io.github.hdfg159.scheduler.factory.Triggers
import io.reactivex.Completable
import io.vertx.core.json.JsonObject

import java.time.LocalDateTime
import java.util.function.Function

import static io.github.hdfg159.game.enumeration.ProtocolEnums.*

/**
 * 海龟汤系统
 *
 * @date 2020/10/23 14:18
 * @author zhangzhenyu
 */
@Slf4j
@Singleton
class TurtleSoupService extends AbstractService {
	def avatarService = AvatarService.getInstance()
	def logService = LogService.getInstance()
	
	def memberData = SoupMemberData.getInstance()
	def recordData = SoupRecordData.getInstance()
	def questionConfig = QuestionConfig.getInstance()
	
	def roomData = SoupRoomData.getInstance()
	
	@Override
	Completable init() {
		REQ_SOUP_LOAD.handle(this, load)
		REQ_SOUP_ROOM_HALL.handle(this, roomHall)
		REQ_SOUP_CREATE_ROOM.handle(this, createRoom)
		REQ_SOUP_JOIN_ROOM.handle(this, joinRoom)
		REQ_SOUP_LEAVE_ROOM.handle(this, leaveRoom)
		REQ_SOUP_PREPARE.handle(this, prepare)
		REQ_SOUP_KICK.handle(this, kick)
		REQ_SOUP_EXCHANGE_SEAT.handle(this, exchangeSeat)
		REQ_SOUP_CHAT.handle(this, chat)
		REQ_SOUP_ANSWER.handle(this, answer)
		REQ_SOUP_END.handle(this, end)
		REQ_SOUP_SELECT_QUESTION.handle(this, selectQuestion)
		
		REQ_SOUP_ADD_NOTE.handle(this, addNote)
		REQ_SOUP_DELETE_NOTE.handle(this, deleteNote)
		REQ_SOUP_LOAD_NOTE.handle(this, loadNote)
		
		EventEnums.OFFLINE.handle(this, offlineEvent)
		EventEnums.ONLINE.handle(this, onlineEvent)
		EventEnums.SOUP_SEAT_CHANGE.handle(this, seatChangeEvent)
		
		Completable.complete()
	}
	
	@Override
	Completable destroy() {
		Completable.complete()
	}
	
	// Request
	
	def load = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		
		def member = memberData.getById(aid)
		def room = roomData.getRoom(member.roomId)
		if (!room) {
			// 房间不存在，清除掉成员的roomId
			member.roomId = null
			return RES_SOUP_LOAD.sucRes(SoupMessage.LoadRes.newBuilder().build())
		}
		
		def loadRes = SoupMessage.LoadRes.newBuilder()
				.setReconnect(true)
				.setRoomId(room.id)
				.setPassword(room.password)
				.build()
		
		RES_SOUP_LOAD.sucRes(loadRes)
	}
	
	def roomHall = {headers, params ->
		// def aid = getHeaderAvatarId(headers)
		// def req = params as SoupMessage.RoomHallReq
		
		def pushes = roomData.getRooms()
				.findAll {it.status == RoomStatus.WAIT.status}
				.collect {
					buildRoomPush([], it.id, {
						def room = it.v2
						it.v1.setRoomMemberNum(room.getAllMemberIds().size())
								.setHasPassword(room.password ? true : false)
					})
				}
		
		def hallRes = SoupMessage.RoomHallRes.newBuilder()
				.addAllRooms(pushes)
				.build()
		
		RES_SOUP_ROOM_HALL.sucRes(hallRes)
	}
	
	def createRoom = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.CreateRoomReq
		def roomRes = SoupMessage.CreateRoomRes.newBuilder()
		
		def roomName = req.name
		if (!roomName || roomName.length() > 8) {
			return RES_SOUP_CREATE_ROOM.res(CodeEnums.SOUP_ROOM_NAME_ILLEGAL)
		}
		
		def max = req.max
		if (max <= 0 || max > 10) {
			return RES_SOUP_CREATE_ROOM.res(CodeEnums.SOUP_ROOM_MAX_ILLEGAL)
		}
		
		def member = memberData.getById(aid)
		if (!member) {
			return RES_SOUP_CREATE_ROOM.res(CodeEnums.SOUP_ROOM_MEMBER_NOT_EXIST)
		}
		
		def pair = roomData.create(member, roomName, max, req.password)
		def room = pair.v2
		def resultCode = pair.v1
		
		if (resultCode.fail()) {
			return RES_SOUP_CREATE_ROOM.res(resultCode)
		}
		
		publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
		
		def roomPush = buildRoomPush([aid], room.id, {it.v1})
		def res = roomRes.setRoom(roomPush).build()
		
		logService.log(new GameLog(
				aid: aid,
				name: avatarService.getAvatarById(aid).username,
				opt: LogEnums.SOUP_CREATE_ROOM,
				param: new JsonObject([
						"id"  : room.id,
						"name": room.name,
						"max" : room.max
				])
		))
		
		RES_SOUP_CREATE_ROOM.sucRes(res)
	}
	
	def joinRoom = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.JoinRoomReq
		
		def member = memberData.getById(aid)
		
		def roomId = req.roomId
		def memberRoom = roomData.getRoom(member.roomId)
		if (!memberRoom) {
			// 成员信息记录加入的房间未清除（断线重连...），且加入时候 房间不存在，直接清掉
			member.roomId = null
		}
		
		def room = roomData.getRoom(roomId)
		if (!room) {
			return RES_SOUP_JOIN_ROOM.res(CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		if (room.password != req.password) {
			return RES_SOUP_JOIN_ROOM.res(CodeEnums.SOUP_ROOM_JOIN_FAIL)
		}
		
		synchronized (room) {
			def joinRoomResCode = room.joinRoom(aid)
			if (joinRoomResCode.fail()) {
				return RES_SOUP_JOIN_ROOM.res(joinRoomResCode)
			}
			
			// 改变成员状态
			def avaIndex = room.getAvaIndex(aid)
			if (avaIndex == null) {
				return RES_SOUP_JOIN_ROOM.res(CodeEnums.SOUP_ROOM_MEMBER_NOT_EXIST)
			}
			
			def memberJoinResCode = member.joinRoom(avaIndex, roomId, room.status == RoomStatus.PLAYING.status)
			if (memberJoinResCode.fail()) {
				return RES_SOUP_JOIN_ROOM.res(memberJoinResCode)
			}
			
			if (room.status == RoomStatus.PLAYING.status) {
				def isMc = room.getRecord().mcId == aid
				if (isMc) {
					// 正在游戏中且是 mc 加入房间
					cancelChangeLeaveTrigger(memberData.getById(aid))
				}
			}
			
			publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
			roomPush([aid], [aid], roomId, {it.v1})
			
			def roomPush = buildRoomPush(room.getAllMemberIds(), roomId, {
				if (room.status != RoomStatus.PLAYING.status) {
					// 不是正在游戏直接返回
					return it.v1
				}
				
				// 包装聊天记录数据
				def record = it.v2.getRecord()
				def msgIds = record.chatRecordIds.toList()
				// 限制100条
				def limit = 100
				// 取最小记录数，防止越界
				def min = Math.min(msgIds.size(), limit)
				def chatRecords = (0..<min).collect {record.getMsg(msgIds[msgIds.size() - min + it])}
						.grep()
						.collect {buildMessageRes(it, record.mcId)}
				
				// 题目
				def questionRes = buildQuestion(aid == record.mcId, record.questionId)
				
				it.v1.setQuestion(questionRes).addAllMsg(chatRecords)
			})
			
			logService.log(new GameLog(
					aid: aid,
					name: avatarService.getAvatarById(aid).username,
					opt: LogEnums.SOUP_JOIN_ROOM,
					param: new JsonObject([
							"id"  : room.id,
							"name": room.name
					])
			))
			
			return RES_SOUP_JOIN_ROOM.sucRes(SoupMessage.JoinRoomRes.newBuilder().setRoom(roomPush).build())
		}
	}
	
	def leaveRoom = {headers, params ->
		// def req = params as SoupMessage.LeaveRoomReq
		def aid = getHeaderAvatarId(headers)
		
		def member = memberData.getById(aid)
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return RES_SOUP_LEAVE_ROOM.res(CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			def result = roomData.leaveRoom(member, room)
			if (result.v1.fail()) {
				return RES_SOUP_LEAVE_ROOM.res(result.v1)
			}
			
			publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(roomId).build())
			roomPush(result.v2, [], roomId, {it.v1})
			
			logService.log(new GameLog(
					aid: aid,
					name: avatarService.getAvatarById(aid).username,
					opt: LogEnums.SOUP_LEAVE_ROOM,
					param: new JsonObject([
							"id"  : room.id,
							"name": room.name
					])
			))
			
			return RES_SOUP_LEAVE_ROOM.sucRes(SoupMessage.LeaveRoomRes.newBuilder().build())
		}
	}
	
	def prepare = {headers, params ->
		def req = params as SoupMessage.PrepareReq
		def aid = getHeaderAvatarId(headers)
		
		def sucResMsg = RES_SOUP_PREPARE.sucRes(SoupMessage.PrepareRes.newBuilder().build())
		def errRes = RES_SOUP_PREPARE.res(CodeEnums.SOUP_PREPARE_FAIL)
		
		def member = memberData.getById(aid)
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return RES_SOUP_PREPARE.res(CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			if (req.ok) {
				// 准备或开始
				if (room.owner == aid) {
					// 更改玩家状态成功 && 准备人数足够
					if (room.prepare.size() != room.allMemberIds.size()) {
						return RES_SOUP_PREPARE.res(CodeEnums.SOUP_PREPARE_MAX_NOT_REACH)
					}
					
					if (member.status.compareAndSet(MemberStatus.ROOM.status, MemberStatus.PLAYING.status)) {
						// 更改房间状态
						room.status = RoomStatus.SELECT.status
						
						def limit = 10
						def takeQuestions = questionConfig.getQuestionIds().toList().shuffled().take(limit)
						def questionRes = takeQuestions.collect {buildQuestion(true, it)}
						
						// 开始游戏记录
						def record = SoupRecord.createRecord(room, takeQuestions)
						def cache = recordData.saveCache(record)
						room.recordMap.put(cache.id, cache)
						room.recordId = cache.id
						
						// 更改其他玩家状态数据
						room.getAllMemberIds().each {
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
							def msg = RES_SOUP_ROOM_PUSH.res(CodeEnums.SOUP_ROOM_PUSH, push.build())
							avatarService.pushMsg(it, msg)
						}
						
						// 定时任务
						Triggers.once("${roomId}::SELECT", LocalDateTime.now().plusSeconds(takeQuestions.size() * 2), {
							// 自动选择一题并推送
							autoSelectQuestion(roomId)
						}).schedule()
						
						logService.log(new GameLog(
								aid: aid,
								name: avatarService.getAvatarById(aid).username,
								opt: LogEnums.SOUP_START_RECORD,
								param: new JsonObject([
										"id"       : room.id,
										"name"     : room.name,
										"recordId" : record.id,
										"memberIds": record.getRecordMemberIds()
								])
						))
						
						return sucResMsg
					} else {
						return errRes
					}
				} else {
					if (member.status.compareAndSet(MemberStatus.ROOM.status, MemberStatus.PREPARE.status)) {
						room.prepare.add(aid)
						
						roomPush([aid], [], roomId, {it.v1})
						return sucResMsg
					} else {
						return errRes
					}
				}
			} else {
				// 取消准备
				if (room.owner != aid && member.status.compareAndSet(MemberStatus.PREPARE.status, MemberStatus.ROOM.status)) {
					room.prepare.remove(aid)
					
					roomPush([aid], [], roomId, {
						it.v1
					})
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
			return RES_SOUP_KICK.res(CodeEnums.SOUP_KICK_PARAM_ERROR)
		}
		
		// 主动踢人 成员信息
		def member = memberData.getById(aid)
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return RES_SOUP_KICK.res(CodeEnums.SOUP_ROOM_NOT_EXIST)
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
				return RES_SOUP_KICK.res(CodeEnums.SOUP_KICK_MEMBER_NOT_EXIST)
			}
			
			def kickResult = roomData.kick(aid, kickMember, room)
			if (kickResult.fail()) {
				return RES_SOUP_KICK.res(kickResult)
			}
			
			publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
			roomPush([kickMember.id], [], roomId, {it.v1})
			
			logService.log(new GameLog(
					aid: aid,
					name: avatarService.getAvatarById(aid).username,
					opt: LogEnums.SOUP_KICK,
					param: new JsonObject([
							"id"       : room.id,
							"name"     : room.name,
							"kickIndex": kickIndex,
							"kickId"   : kickAid
					])
			))
			
			logService.log(new GameLog(
					aid: kickMember.id,
					name: avatarService.getAvatarById(kickMember.id).username,
					opt: LogEnums.SOUP_LEAVE_ROOM,
					param: new JsonObject([
							"id"  : room.id,
							"name": room.name,
							"kick": true
					])
			))
			
			return RES_SOUP_KICK.sucRes(SoupMessage.KickRes.newBuilder().build())
		}
	}
	
	def exchangeSeat = {headers, params ->
		def req = params as SoupMessage.ExchangeSeatReq
		def aid = getHeaderAvatarId(headers)
		def index = req.index
		
		def member = memberData.getById(aid)
		if (member.seat == index) {
			return RES_SOUP_EXCHANGE_SEAT.res(CodeEnums.SOUP_SEAT_EXIST)
		}
		
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return RES_SOUP_EXCHANGE_SEAT.res(CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			def result = roomData.exchangeSeat(room, member, index)
			if (result.fail()) {
				return RES_SOUP_EXCHANGE_SEAT.res(result)
			}
			
			publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
			roomPush([aid], [], roomId, {it.v1})
			
			logService.log(new GameLog(
					aid: aid,
					name: avatarService.getAvatarById(aid).username,
					opt: LogEnums.SOUP_EXCHANGE_SEAT,
					param: new JsonObject([
							"id"      : room.id,
							"name"    : room.name,
							"srcIndex": member.seat,
							"index"   : index
					])
			))
			
			return RES_SOUP_EXCHANGE_SEAT.sucRes(SoupMessage.ExchangeSeatRes.newBuilder().build())
		}
	}
	
	def chat = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.ChatReq
		def builder = SoupMessage.ChatRes.newBuilder()
		
		def content = req.content
		if (!content || content.length() > 20) {
			return RES_SOUP_CHAT.res(CodeEnums.SOUP_CHAT_CONTENT_ILLEGAL)
		}
		
		def member = memberData.getById(aid)
		if (member.status.get() != MemberStatus.PLAYING.status) {
			// 还没进对局拒绝请求
			return RES_SOUP_CHAT.res(CodeEnums.SOUP_MEMBER_NOT_PLAYING)
		}
		
		def room = roomData.getRoom(member.roomId)
		if (!room) {
			return RES_SOUP_CHAT.res(CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		if (room.status != RoomStatus.PLAYING.status) {
			return RES_SOUP_CHAT.res(CodeEnums.SOUP_ROOM_STATUS_NOT_PLAYING)
		}
		
		def record = room.getRecord()
		if (!record) {
			return RES_SOUP_CHAT.res(CodeEnums.SOUP_RECORD_NOT_EXIST)
		}
		
		def speak = member.speak()
		if (speak > 0) {
			return RES_SOUP_CHAT.res(CodeEnums.SOUP_CHAT_LIMIT, builder.setSeconds(speak).build())
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
		
		roomPush([], [], room.id, {
			def res = buildMessageRes(chat, record.mcId)
			if (res) {
				it.v1.addChangedMsg(res)
			}
			
			it.v1
		})
		return RES_SOUP_CHAT.sucRes(builder.build())
	}
	
	def answer = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.AnswerReq
		
		def member = memberData.getById(aid)
		if (member.status.get() != MemberStatus.PLAYING.status) {
			// 还没进对局拒绝请求
			return RES_SOUP_ANSWER.res(CodeEnums.SOUP_MEMBER_NOT_PLAYING)
		}
		
		def room = roomData.getRoom(member.roomId)
		if (!room) {
			return RES_SOUP_ANSWER.res(CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		if (room.status != RoomStatus.PLAYING.status) {
			return RES_SOUP_ANSWER.res(CodeEnums.SOUP_ROOM_STATUS_NOT_PLAYING)
		}
		
		def record = room.getRecord()
		if (!record) {
			return RES_SOUP_ANSWER.res(CodeEnums.SOUP_RECORD_NOT_EXIST)
		}
		
		if (record.mcId != aid) {
			return RES_SOUP_ANSWER.res(CodeEnums.SOUP_MEMBER_NOT_MC)
		}
		
		def chat = record.getMsg(req.id)
		if (!chat) {
			return RES_SOUP_ANSWER.res(CodeEnums.SOUP_MESSAGE_NOT_EXIST)
		}
		
		if (chat.mid == aid) {
			// 这种属于 mc 自己说自己回答，不允许
			return RES_SOUP_ANSWER.res(CodeEnums.SOUP_ANSWER_NOT_ALLOW_MC)
		}
		
		def answerType = AnswerType.valOf(req.answer)
		if (!answerType) {
			return RES_SOUP_ANSWER.res(CodeEnums.SOUP_ANSWER_TYPE_NOT_EXIST)
		}
		
		// 改变数据设置答案
		chat.setAnswer(answerType.type)
		
		roomPush([], [], room.id, {
			def res = buildMessageRes(chat, record.mcId)
			if (res) {
				it.v1.addChangedMsg(res)
			}
			
			it.v1
		})
		
		RES_SOUP_ANSWER.sucRes(SoupMessage.AnswerRes.newBuilder().build())
	}
	
	def end = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		// def req = params as SoupMessage.EndReq
		
		def member = memberData.getById(aid)
		if (member.status.get() != MemberStatus.PLAYING.status) {
			// 还没进对局拒绝请求
			return RES_SOUP_END.res(CodeEnums.SOUP_MEMBER_NOT_PLAYING)
		}
		
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return RES_SOUP_END.res(CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			if (room.status != RoomStatus.PLAYING.status) {
				return RES_SOUP_END.res(CodeEnums.SOUP_ROOM_STATUS_NOT_PLAYING)
			}
			
			def record = room.getRecord()
			if (!record) {
				return RES_SOUP_END.res(CodeEnums.SOUP_RECORD_NOT_EXIST)
			}
			
			if (record.mcId != aid) {
				return RES_SOUP_END.res(CodeEnums.SOUP_MEMBER_NOT_MC)
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
				
				// 结束时候只有在房间 && 还没掉线 ，才记录玩过问题
				if (avatarService.isOnline(m.id)) {
					m.addQuestion(record.questionId)
				}
			}
			
			roomPush(room.getAllMemberIds(), [], roomId, {
				// 这个位置不允许空指针，肯定有数据
				it.v1.setQuestion(buildQuestion(true, record.questionId))
			})
			
			// 强制刷缓存
			def recordId = record.id
			recordData.updateForceById(recordId)
			
			logService.log(new GameLog(
					aid: aid,
					name: avatarService.getAvatarById(aid).username,
					opt: LogEnums.SOUP_END,
					param: new JsonObject([
							"id"      : room.id,
							"name"    : room.name,
							"recordId": recordId
					])
			))
			
			return RES_SOUP_END.sucRes(SoupMessage.EndRes.newBuilder().build())
		}
	}
	
	def selectQuestion = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.SelectQuestionReq
		
		if (!req.id) {
			return RES_SOUP_SELECT_QUESTION.res(CodeEnums.PARAM_ERROR)
		}
		
		def question = questionConfig.getQuestion(req.id)
		if (!question) {
			return RES_SOUP_SELECT_QUESTION.res(CodeEnums.SOUP_QUESTION_NOT_EXIST)
		}
		
		def member = memberData.getById(aid)
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return RES_SOUP_SELECT_QUESTION.res(CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			if (room.status != RoomStatus.SELECT.status) {
				return RES_SOUP_SELECT_QUESTION.res(CodeEnums.SOUP_ROOM_STATUS_NOT_SELECT)
			}
			
			def record = room.getRecord()
			if (!record) {
				return RES_SOUP_SELECT_QUESTION.res(CodeEnums.SOUP_RECORD_NOT_EXIST)
			}
			
			if (record.mcId != aid) {
				return RES_SOUP_SELECT_QUESTION.res(CodeEnums.SOUP_MEMBER_NOT_MC)
			}
			
			// 选题推送
			record.questionId = req.id
			room.status = RoomStatus.PLAYING.status
			
			// 取消定时器
			scheduler.cancel("${roomId}::SELECT")
			
			// 推送房间状态和题目
			roomPush([], [], roomId, {
				it.v1.setQuestion(buildQuestion(false, req.id))
			})
			
			logService.log(new GameLog(
					aid: aid,
					name: avatarService.getAvatarById(aid).username,
					opt: LogEnums.SOUP_SELECT_QUESTION,
					param: new JsonObject([
							"id"        : room.id,
							"name"      : room.name,
							"questionId": req.id
					])
			))
			
			return RES_SOUP_SELECT_QUESTION.sucRes(SoupMessage.SelectQuestionRes.newBuilder().build())
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
		
		startChangeLeaveTrigger(member)
		
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
	
	def addNote = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.AddNoteReq
		def messageId = req.messageId
		def content = req.content
		
		if (!messageId && !content) {
			return RES_SOUP_ADD_NOTE.res(CodeEnums.SOUP_NOTE_ILLEGAL)
		}
		
		def member = memberData.getById(aid)
		if (member.status.get() != MemberStatus.PLAYING.status) {
			// 还没进对局拒绝请求
			return RES_SOUP_ADD_NOTE.res(CodeEnums.SOUP_MEMBER_NOT_PLAYING)
		}
		
		def room = roomData.getRoom(member.roomId)
		if (!room) {
			return RES_SOUP_ADD_NOTE.res(CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		def record = room.getRecord()
		if (!record) {
			return RES_SOUP_ADD_NOTE.res(CodeEnums.SOUP_RECORD_NOT_EXIST)
		}
		
		if (record.mcId == aid) {
			return RES_SOUP_ADD_NOTE.res(CodeEnums.SOUP_ADD_NOTE_NOT_ALLOW_MC)
		}
		
		if (messageId) {
			def msg = record.getMsg(messageId)
			if (!msg) {
				return RES_SOUP_ADD_NOTE.res(CodeEnums.SOUP_MESSAGE_NOT_EXIST)
			}
			
			if (msg.answer == AnswerType.NON.type) {
				return RES_SOUP_ADD_NOTE.res(CodeEnums.SOUP_MESSAGE_NOT_ANSWER)
			}
			
			def note = SoupNote.createChatNote(aid, messageId)
			record.addNote(note)
			return RES_SOUP_ADD_NOTE.sucRes(SoupMessage.AddNoteRes.newBuilder().setNote(note.covertNoteRes(msg)).build())
		}
		
		if (!messageId && content) {
			if (content.length() > 20) {
				return RES_SOUP_ADD_NOTE.res(CodeEnums.SOUP_NOTE_ILLEGAL)
			} else {
				def note = SoupNote.createCustomNote(aid, content)
				record.addNote(note)
				return RES_SOUP_ADD_NOTE.sucRes(SoupMessage.AddNoteRes.newBuilder().setNote(note.covertNoteRes(null)).build())
			}
		}
		
		RES_SOUP_ADD_NOTE.res(CodeEnums.SOUP_NOTE_ILLEGAL)
	}
	
	def loadNote = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.LoadNoteReq
		
		def loadAid = req.aid ?: aid
		
		def member = memberData.getById(aid)
		def room = roomData.getRoom(member.roomId)
		if (!room) {
			return RES_SOUP_LOAD_NOTE.res(CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		def record = room.getRecord()
		if (!record) {
			return RES_SOUP_LOAD_NOTE.res(CodeEnums.SOUP_RECORD_NOT_EXIST)
		}
		
		def res = SoupMessage.LoadNoteRes.newBuilder()
				.addAllNotes(record.getAidAllNoteRes(loadAid))
				.build()
		
		RES_SOUP_LOAD_NOTE.sucRes(res)
	}
	
	def deleteNote = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.DeleteNoteReq
		
		def id = req.id
		
		def member = memberData.getById(aid)
		if (member.status.get() != MemberStatus.PLAYING.status) {
			// 还没进对局拒绝请求
			return RES_SOUP_DELETE_NOTE.res(CodeEnums.SOUP_MEMBER_NOT_PLAYING)
		}
		
		def room = roomData.getRoom(member.roomId)
		if (!room) {
			return RES_SOUP_DELETE_NOTE.res(CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		def record = room.getRecord()
		if (!record) {
			return RES_SOUP_DELETE_NOTE.res(CodeEnums.SOUP_RECORD_NOT_EXIST)
		}
		
		def note = record.getNote(id)
		if (!note) {
			return RES_SOUP_DELETE_NOTE.res(CodeEnums.SOUP_NOTE_NOT_EXIST)
		}
		
		if (note.aid != aid) {
			return RES_SOUP_DELETE_NOTE.res(CodeEnums.SOUP_NOTE_DELETE_LIMIT)
		}
		
		record.deleteNote(note)
		
		RES_SOUP_DELETE_NOTE.sucRes(SoupMessage.DeleteNoteRes.newBuilder().build())
	}
	
	// Private
	
	def buildSeatRes(SoupMember member, String owner, String mc) {
		if (!member) {
			return null
		}
		
		def avatar = avatarService.getAvatarById(member.id)
		
		SoupMessage.RoomMemberSeatRes.newBuilder()
				.setAid(avatar.id)
				.setAvaName(avatar.username)
				.setAvaHead("")
				.setStatus(member.status.get())
				.setIndex(member.seat)
				.setOwner(owner == member.id)
				.setMc(mc == member.id)
				.setLeave(member.roomId == null ? member.leave : LeaveEnum.NONE.type)
				.setOnline(avatarService.isOnline(member.id))
				.build()
	}
	
	/**
	 * 房间推送
	 */
	def roomPush(Collection<String> changeMemberIds,
				 Collection<String> excludePushMemberIds,
				 String roomId,
				 Function<Tuple2<RoomPush.Builder, SoupRoom>, RoomPush.Builder> mapping) {
		
		def push = buildRoomPush(changeMemberIds, roomId, mapping)
		if (push) {
			def msg = RES_SOUP_ROOM_PUSH.res(CodeEnums.SOUP_ROOM_PUSH, push)
			avatarService.pushAllMsg(roomData.getRoom(roomId).getAllMemberIds(), excludePushMemberIds.toSet(), msg)
		}
	}
	
	/**
	 * 构建房间推送
	 */
	def buildRoomPush(Collection<String> changeMemberIds,
					  String roomId,
					  Function<Tuple2<RoomPush.Builder, SoupRoom>, RoomPush.Builder> mapping) {
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
				.setRoomName(room.name)
				.setRoomMax(room.max)
				.setStatus(room.status)
				.addAllSeatsChange(seatRes)
		
		if (room.status == RoomStatus.PLAYING.status) {
			// 正在游戏中
			def leave = room.getRecord().leaveForPlaying ? LeaveForPlayingType.YES.type : LeaveForPlayingType.NO.type
			builder.setLeaveForPlaying(leave)
		}
		
		mapping.apply(Tuple.tuple(builder, room)).build()
	}
	
	/**
	 * 构建聊天消息
	 */
	def buildMessageRes(SoupChatRecord chat, String mcId) {
		if (!chat) {
			return null
		}
		
		def msgResBuilder = SoupMessage.ChatMessageRes.newBuilder()
				.setId(chat.id)
				.setContent(chat.content)
				.setAnswer(chat.answer)
		
		def avatar = avatarService.getAvatarById(chat.mid)
		if (avatar) {
			msgResBuilder
					.setAid(avatar.id)
					.setAvaName(avatar.username)
					.setAvaHead("")
					.setMc(mcId == avatar.id)
		}
		msgResBuilder.build()
	}
	
	/**
	 * 执行自动选题
	 * @param roomId 房间 ID
	 */
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
			
			// 推送房间状态和题目
			roomPush([], [], roomId, {
				it.v1.setQuestion(buildQuestion(false, questionId))
			})
			
			logService.log(new GameLog(
					aid: "",
					name: "",
					opt: LogEnums.SOUP_SELECT_QUESTION,
					param: new JsonObject([
							"id"      : room.id,
							"name"    : room.name,
							"auto"    : true,
							"recordId": record.id
					])
			))
		}
	}
	
	/**
	 * 构建问题
	 */
	def buildQuestion(boolean containContent, String questionId) {
		def question = questionConfig.getQuestion(questionId)
		if (!question) {
			return null
		}
		
		def res = SoupMessage.QuestionRes.newBuilder()
				.setId(question.id)
				.setTitle(question.title)
				.setQuestion(question.question)
		
		if (containContent) {
			res.setContent(question.content)
		}
		
		return res.build()
	}
	
	def startChangeLeaveTrigger(SoupMember member) {
		if (!member) {
			return
		}
		
		def record = roomData.getRoom(member.roomId)?.getRecord()
		if (!record) {
			return
		}
		
		// 推送离线状态
		roomPush([member.id], [member.id], member.roomId, {it.v1})
		
		// 如果是 MC
		if (member.id == record.mcId) {
			// 启动 MC 离线定时器 倒计时
			Triggers.once("${record.id}::MC::OFFLINE::${member.id}", LocalDateTime.now().plusMinutes(3), {
				changeRecordLeave(member.roomId)
			}).schedule()
		}
	}
	
	def changeRecordLeave(roomId) {
		def room = roomData.getRoom(roomId)
		def record = room?.getRecord()
		if (!record) {
			return
		}
		
		synchronized (room) {
			// 改变状态
			record.leaveForPlaying = true
			// 推送
			roomPush([], [], room.id, {it.v1})
		}
	}
	
	def cancelChangeLeaveTrigger(SoupMember member) {
		if (!member) {
			return
		}
		
		def record = roomData.getRoom(member.roomId)?.getRecord()
		if (!record) {
			return
		}
		
		if (member.id == record.mcId) {
			// 取消 MC 离线定时器 倒计时
			def name = "${record.id}::MC::OFFLINE::${member.id}"
			def cancel = scheduler.cancel(name)
			log.info("cancel change leave trigger [${name}]:${cancel}")
		}
	}
}
