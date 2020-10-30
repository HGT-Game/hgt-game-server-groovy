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
	AvatarService avatarService = AvatarService.getInstance()
	
	SoupMemberData memberData = SoupMemberData.getInstance()
	SoupRecordData recordData = SoupRecordData.getInstance()
	
	SoupRoomData roomData = SoupRoomData.getInstance()
	
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
		
		def tuple2 = roomData.create(aid, req.name, req.max, req.password)
		def room = tuple2.item2
		def resultCode = tuple2.item1
		
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
			roomPush([aid], [aid], roomId, {it})
			
			def sucRes = SoupMessage.JoinRoomRes.newBuilder()
					.setRoom(buildRoomPush([aid], roomId, {it}))
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
				roomPush([aid], [], roomId, {it})
				
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
					if ((room.max <= room.prepare.size() + 1)
							&& member.status.compareAndSet(MemberStatus.ROOM.status, MemberStatus.PLAYING.status)) {
						// 更改房间状态
						room.status = RoomStatus.PLAYING.status
						
						// todo 获取问题
						def questionId = "111"
						def question = "问题"
						
						// 开始游戏记录
						def record = SoupRecord.createRecord(room, questionId)
						def cache = recordData.saveCache(record)
						room.recordMap.put(cache.id, cache)
						room.recordId = cache.id
						
						// 更改其他玩家状态数据
						room.memberIds
								.findAll {it != aid}
								.each {
									def m = memberData.getById(it)
									
									m.status.compareAndSet(MemberStatus.PREPARE.status, MemberStatus.PLAYING.status)
									m.questionIds.add(questionId)
									m.recordIds.add(cache.id)
									
									// todo 现在默认房主就是mc
									if (it == room.owner) {
										m.mcTimes += 1
									}
								}
						
						roomPush([room.owner], [], roomId, {
							def questionRes = SoupMessage.QuestionRes.newBuilder()
									.setId(questionId)
									.setQuestion(question)
									.build()
							it.setQuestion(questionRes)
						})
						return sucResMsg
					} else {
						return errRes
					}
				} else {
					if (member.status.compareAndSet(MemberStatus.ROOM.status, MemberStatus.PREPARE.status)) {
						room.prepare.add(aid)
						
						roomPush([aid], [], roomId, {it})
						return sucResMsg
					} else {
						return errRes
					}
				}
			} else {
				// 取消准备
				if (room.owner != aid && member.status.compareAndSet(MemberStatus.PREPARE.status, MemberStatus.ROOM.status)) {
					room.prepare.remove(aid)
					
					roomPush([aid], [], roomId, {it})
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
				roomPush([kickMember.id], [], roomId, {it})
				
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
				roomPush([aid], [], roomId, {it})
				
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
			return GameUtils.resMsg(RES_SOUP_CHAT, CodeEnums.SOUP_ROOM_NOT_PLAYING)
		}
		
		def recordId = room.recordId
		if (!recordId) {
			return GameUtils.resMsg(RES_SOUP_CHAT, CodeEnums.SOUP_RECORD_NOT_EXIST)
		}
		
		def record = room.getRecord(recordId)
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
		
		// todo 推送
		return GameUtils.sucResMsg(RES_SOUP_CHAT, builder.build())
	}
	
	def answer = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.AnswerReq
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
				return GameUtils.resMsg(RES_SOUP_END, CodeEnums.SOUP_ROOM_NOT_PLAYING)
			}
			
			def recordId = room.recordId
			def record = room.recordMap.get(recordId)
			if (!record) {
				return GameUtils.resMsg(RES_SOUP_END, CodeEnums.SOUP_RECORD_NOT_EXIST)
			}
			
			// 记录结束时间
			record.endTime = LocalDateTime.now()
			// 更改房间状态
			room.recordId = null
			room.status = RoomStatus.WAIT.status
			
			// 重置玩家状态
			room.roomMemberMap.keySet().each {
				def m = memberData.getById(it)
				if (m) {
					m.status.compareAndSet(MemberStatus.PLAYING.status, MemberStatus.PREPARE.status)
				}
			}
			
			// todo 推送汤底答案和所有位置信息
			roomPush(room.roomMemberMap.keySet(), [], roomId, {
				def questionRes = SoupMessage.QuestionRes.newBuilder()
						.setContent("我是答案")
						.build()
				it.setQuestion(questionRes)
			})
			
			// 强制刷缓存
			recordData.updateForceById(recordId)
			
			return GameUtils.sucResMsg(RES_SOUP_END, SoupMessage.EndRes.newBuilder().build())
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
	
	private SoupMessage.RoomMemberSeatRes buildSeatRes(SoupMember member, String owner, String mc) {
		if (!member) {
			return null
		}
		
		def avatar = avatarService.getAvatarData()?.getById(member.id)
		
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
	
	private void roomPush(Collection<String> changeMemberIds,
						  Collection<String> excludePushMemberIds,
						  String roomId,
						  Function<RoomPush.Builder, RoomPush.Builder> mapping) {
		
		def push = buildRoomPush(changeMemberIds, roomId, mapping)
		if (push) {
			def msg = GameUtils.resMsg(RES_SOUP_ROOM_PUSH, CodeEnums.SOUP_ROOM_PUSH, push)
			avatarService.pushAllMsg(roomData.getRoom(roomId).roomMemberMap.keySet(), excludePushMemberIds.toSet(), msg)
		}
	}
	
	private RoomPush buildRoomPush(Collection<String> changeMemberIds,
								   String roomId,
								   Function<RoomPush.Builder, RoomPush.Builder> mapping) {
		def room = roomData.getRoom(roomId)
		if (!room) {
			return null
		}
		
		def seatRes = changeMemberIds ? changeMemberIds.collect {
			def member = memberData.getById(it)
			buildSeatRes(member, room.owner, room.owner)
		} : room.roomMemberMap.keySet().collect {
			def member = memberData.getById(it)
			buildSeatRes(member, room.owner, room.owner)
		}
		
		def builder = RoomPush.newBuilder()
				.setRoomId(room.id)
				.setStatus(room.status)
				.addAllSeatsChange(seatRes)
		
		mapping.apply(builder).build()
	}
}
