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
import io.github.hdfg159.game.util.GameUtils
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
		response(REQ_SOUP_LOAD, load)
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
		
		response(REQ_SOUP_ADD_NOTE, addNote)
		response(REQ_SOUP_DELETE_NOTE, deleteNote)
		response(REQ_SOUP_LOAD_NOTE, loadNote)
		
		handleEvent(EventEnums.OFFLINE, offlineEvent)
		handleEvent(EventEnums.ONLINE, onlineEvent)
		handleEvent(EventEnums.SOUP_SEAT_CHANGE, seatChangeEvent)
		
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
			return GameUtils.sucResMsg(RES_SOUP_LOAD, SoupMessage.LoadRes.newBuilder().build())
		} else {
			def loadRes = SoupMessage.LoadRes.newBuilder()
					.setReconnect(true)
					.setRoomId(room.id)
					.setPassword(room.password)
					.build()
			return GameUtils.sucResMsg(RES_SOUP_LOAD, loadRes)
		}
	}
	
	def roomHall = {headers, params ->
		// def aid = getHeaderAvatarId(headers)
		// def req = params as SoupMessage.RoomHallReq
		
		def roomPushes = roomData.getRooms()
				.findAll {
					it.status == RoomStatus.WAIT.status
				}
				.collect {
					buildRoomPush([], it.id, {
						def v1 = it.v1
						def room = it.v2
						v1.setRoomMemberNum(room.getAllMemberIds().size()).setHasPassword(room.password ? true : false)
					})
				}
		
		GameUtils.sucResMsg(RES_SOUP_ROOM_HALL, SoupMessage.RoomHallRes.newBuilder().addAllRooms(roomPushes).build())
	}
	
	def createRoom = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.CreateRoomReq
		def roomRes = SoupMessage.CreateRoomRes.newBuilder()
		
		def roomName = req.name
		if (!roomName || roomName.length() > 8) {
			return GameUtils.resMsg(RES_SOUP_CREATE_ROOM, CodeEnums.SOUP_ROOM_NAME_ILLEGAL)
		}
		
		def max = req.max
		if (max <= 0 || max > 10) {
			return GameUtils.resMsg(RES_SOUP_CREATE_ROOM, CodeEnums.SOUP_ROOM_MAX_ILLEGAL)
		}
		
		def member = memberData.getById(aid)
		if (!member) {
			return GameUtils.resMsg(RES_SOUP_CREATE_ROOM, CodeEnums.SOUP_ROOM_MEMBER_NOT_EXIST)
		}
		
		def pair = roomData.create(member, roomName, max, req.password)
		def room = pair.v2
		def resultCode = pair.v1
		
		if (!resultCode.success()) {
			return GameUtils.resMsg(RES_SOUP_CREATE_ROOM, resultCode)
		} else {
			publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
			
			def res = roomRes.setRoom(buildRoomPush([aid], room.id, {it.v1})).build()
			
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
			
			return GameUtils.sucResMsg(RES_SOUP_CREATE_ROOM, res)
		}
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
			if (avaIndex == null) {
				return GameUtils.resMsg(RES_SOUP_JOIN_ROOM, CodeEnums.SOUP_ROOM_MEMBER_NOT_EXIST)
			}
			
			def memberJoinResCode = member.joinRoom(avaIndex, roomId, room.status == RoomStatus.PLAYING.status)
			if (!memberJoinResCode.success()) {
				return GameUtils.resMsg(RES_SOUP_JOIN_ROOM, memberJoinResCode)
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
						.findAll {it != null}
						.collect {buildMessageRes(it, record.mcId)}
				
				// 题目
				def questionRes = buildQuestion(aid == record.mcId, record.questionId)
				it.v1.setQuestion(questionRes)
				
				it.v1.addAllMsg(chatRecords)
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
			
			return GameUtils.sucResMsg(RES_SOUP_JOIN_ROOM, SoupMessage.JoinRoomRes.newBuilder().setRoom(roomPush).build())
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
			def result = roomData.leaveRoom(member, room)
			if (result.v1.success()) {
				
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
				
				return GameUtils.sucResMsg(RES_SOUP_LEAVE_ROOM, SoupMessage.LeaveRoomRes.newBuilder().build())
			} else {
				return GameUtils.resMsg(RES_SOUP_LEAVE_ROOM, result.v1)
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
					if (room.prepare.size() != room.allMemberIds.size()) {
						return GameUtils.resMsg(RES_SOUP_PREPARE, CodeEnums.SOUP_PREPARE_MAX_NOT_REACH)
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
							def msg = GameUtils.resMsg(RES_SOUP_ROOM_PUSH, CodeEnums.SOUP_ROOM_PUSH, push.build())
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
						
						roomPush([aid], [], roomId, {
							it.v1
						})
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
				roomPush([kickMember.id], [], roomId, {
					it.v1
				})
				
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
				roomPush([aid], [], roomId, {
					it.v1
				})
				
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
		if (member.status.get() != MemberStatus.PLAYING.status) {
			// 还没进对局拒绝请求
			return GameUtils.resMsg(RES_SOUP_CHAT, CodeEnums.SOUP_MEMBER_NOT_PLAYING)
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
		
		def speak = member.speak()
		if (speak > 0) {
			return GameUtils.resMsg(RES_SOUP_CHAT, CodeEnums.SOUP_CHAT_LIMIT, builder.setSeconds(speak).build())
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
		return GameUtils.sucResMsg(RES_SOUP_CHAT, builder.build())
	}
	
	def answer = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.AnswerReq
		
		def member = memberData.getById(aid)
		if (member.status.get() != MemberStatus.PLAYING.status) {
			// 还没进对局拒绝请求
			return GameUtils.resMsg(RES_SOUP_ANSWER, CodeEnums.SOUP_MEMBER_NOT_PLAYING)
		}
		
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
		
		if (chat.mid == aid) {
			// 这种属于 mc 自己说自己回答，不允许
			return GameUtils.resMsg(RES_SOUP_ANSWER, CodeEnums.SOUP_ANSWER_NOT_ALLOW_MC)
		}
		
		def answerType = AnswerType.valOf(req.answer)
		if (!answerType) {
			return GameUtils.resMsg(RES_SOUP_ANSWER, CodeEnums.SOUP_ANSWER_TYPE_NOT_EXIST)
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
		
		GameUtils.sucResMsg(RES_SOUP_ANSWER, SoupMessage.AnswerRes.newBuilder().build())
	}
	
	def end = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		// def req = params as SoupMessage.EndReq
		
		def member = memberData.getById(aid)
		if (member.status.get() != MemberStatus.PLAYING.status) {
			// 还没进对局拒绝请求
			return GameUtils.resMsg(RES_SOUP_END, CodeEnums.SOUP_MEMBER_NOT_PLAYING)
		}
		
		def roomId = member.roomId
		def room = roomData.getRoom(roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_END, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			if (room.status != RoomStatus.PLAYING.status) {
				return GameUtils.resMsg(RES_SOUP_END, CodeEnums.SOUP_ROOM_STATUS_NOT_PLAYING)
			}
			
			def record = room.getRecord()
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
			
			return GameUtils.sucResMsg(RES_SOUP_END, SoupMessage.EndRes.newBuilder().build())
		}
	}
	
	def selectQuestion = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.SelectQuestionReq
		
		if (!req.id) {
			return GameUtils.resMsg(RES_SOUP_SELECT_QUESTION, CodeEnums.PARAM_ERROR)
		}
		
		def question = questionConfig.getQuestion(req.id)
		if (!question) {
			return GameUtils.resMsg(RES_SOUP_SELECT_QUESTION, CodeEnums.SOUP_QUESTION_NOT_EXIST)
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
			
			// 选题推送
			record.questionId = req.id
			room.status = RoomStatus.PLAYING.status
			
			// 取消定时器
			scheduler.cancel("${roomId}::SELECT")
			
			// 推送房间状态和题目
			roomPush([], [], roomId, {
				// 这个位置不允许空指针，肯定有数据
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
			return GameUtils.resMsg(RES_SOUP_ADD_NOTE, CodeEnums.SOUP_NOTE_ILLEGAL)
		}
		
		def member = memberData.getById(aid)
		if (member.status.get() != MemberStatus.PLAYING.status) {
			// 还没进对局拒绝请求
			return GameUtils.resMsg(RES_SOUP_ADD_NOTE, CodeEnums.SOUP_MEMBER_NOT_PLAYING)
		}
		
		def room = roomData.getRoom(member.roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_ADD_NOTE, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		def record = room.getRecord()
		if (!record) {
			return GameUtils.resMsg(RES_SOUP_ADD_NOTE, CodeEnums.SOUP_RECORD_NOT_EXIST)
		}
		
		if (record.mcId == aid) {
			return GameUtils.resMsg(RES_SOUP_ADD_NOTE, CodeEnums.SOUP_ADD_NOTE_NOT_ALLOW_MC)
		}
		
		if (messageId) {
			def msg = record.getMsg(messageId)
			if (!msg) {
				return GameUtils.resMsg(RES_SOUP_ADD_NOTE, CodeEnums.SOUP_MESSAGE_NOT_EXIST)
			}
			
			if (msg.answer == AnswerType.NON.type) {
				return GameUtils.resMsg(RES_SOUP_ADD_NOTE, CodeEnums.SOUP_MESSAGE_NOT_ANSWER)
			}
			
			def note = SoupNote.createChatNote(aid, messageId)
			record.addNote(note)
			return GameUtils.sucResMsg(RES_SOUP_ADD_NOTE, SoupMessage.AddNoteRes.newBuilder().setNote(note.covertNoteRes(msg)).build())
		}
		
		if (!messageId && content) {
			if (content.length() > 20) {
				return GameUtils.resMsg(RES_SOUP_ADD_NOTE, CodeEnums.SOUP_NOTE_ILLEGAL)
			} else {
				def note = SoupNote.createCustomNote(aid, content)
				record.addNote(note)
				return GameUtils.sucResMsg(RES_SOUP_ADD_NOTE, SoupMessage.AddNoteRes.newBuilder().setNote(note.covertNoteRes(null)).build())
			}
		}
		
		GameUtils.resMsg(RES_SOUP_ADD_NOTE, CodeEnums.SOUP_NOTE_ILLEGAL)
	}
	
	def loadNote = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.LoadNoteReq
		
		def loadAid = req.aid ?: aid
		
		def member = memberData.getById(aid)
		def room = roomData.getRoom(member.roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_LOAD_NOTE, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		def record = room.getRecord()
		if (!record) {
			return GameUtils.resMsg(RES_SOUP_LOAD_NOTE, CodeEnums.SOUP_RECORD_NOT_EXIST)
		}
		
		def res = SoupMessage.LoadNoteRes.newBuilder()
				.addAllNotes(record.getAidAllNoteRes(loadAid))
				.build()
		GameUtils.sucResMsg(RES_SOUP_LOAD_NOTE, res)
	}
	
	def deleteNote = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.DeleteNoteReq
		
		def id = req.id
		
		def member = memberData.getById(aid)
		if (member.status.get() != MemberStatus.PLAYING.status) {
			// 还没进对局拒绝请求
			return GameUtils.resMsg(RES_SOUP_DELETE_NOTE, CodeEnums.SOUP_MEMBER_NOT_PLAYING)
		}
		
		def room = roomData.getRoom(member.roomId)
		if (!room) {
			return GameUtils.resMsg(RES_SOUP_DELETE_NOTE, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		def record = room.getRecord()
		if (!record) {
			return GameUtils.resMsg(RES_SOUP_DELETE_NOTE, CodeEnums.SOUP_RECORD_NOT_EXIST)
		}
		
		def note = record.getNote(id)
		if (!note) {
			return GameUtils.resMsg(RES_SOUP_DELETE_NOTE, CodeEnums.SOUP_NOTE_NOT_EXIST)
		}
		
		if (note.aid != aid) {
			return GameUtils.resMsg(RES_SOUP_DELETE_NOTE, CodeEnums.SOUP_NOTE_DELETE_LIMIT)
		}
		
		record.deleteNote(note)
		
		GameUtils.sucResMsg(RES_SOUP_DELETE_NOTE, SoupMessage.DeleteNoteRes.newBuilder().build())
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
			def msg = GameUtils.resMsg(RES_SOUP_ROOM_PUSH, CodeEnums.SOUP_ROOM_PUSH, push)
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
				// 这个位置不允许空指针，肯定有数据
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
