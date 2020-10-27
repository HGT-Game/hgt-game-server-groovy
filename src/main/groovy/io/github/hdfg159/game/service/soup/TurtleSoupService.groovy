package io.github.hdfg159.game.service.soup

import groovy.util.logging.Slf4j
import io.github.hdfg159.game.domain.dto.EventMessage
import io.github.hdfg159.game.domain.dto.SoupEvent
import io.github.hdfg159.game.domain.dto.SoupMessage
import io.github.hdfg159.game.enumeration.CodeEnums
import io.github.hdfg159.game.enumeration.EventEnums
import io.github.hdfg159.game.enumeration.ProtocolEnums
import io.github.hdfg159.game.service.AbstractService
import io.github.hdfg159.game.service.avatar.AvatarService
import io.github.hdfg159.game.util.GameUtils
import io.reactivex.Completable

import java.time.LocalDateTime

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
		response(ProtocolEnums.REQ_SOUP_ROOM_HALL, roomHall)
		response(ProtocolEnums.REQ_SOUP_CREATE_ROOM, createRoom)
		response(ProtocolEnums.REQ_SOUP_JOIN_ROOM, joinRoom)
		response(ProtocolEnums.REQ_SOUP_LEAVE_ROOM, leaveRoom)
		response(ProtocolEnums.REQ_SOUP_PREPARE, prepare)
		response(ProtocolEnums.REQ_SOUP_KICK, kick)
		response(ProtocolEnums.REQ_SOUP_EXCHANGE_SEAT, exchangeSeat)
		response(ProtocolEnums.REQ_SOUP_CHAT, chat)
		response(ProtocolEnums.REQ_SOUP_ANSWER, answer)
		response(ProtocolEnums.REQ_SOUP_END, end)
		
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
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.RoomHallReq
		def builder = SoupMessage.RoomHallRes.newBuilder()
		
		roomData.getRooms().collect {
			def roomRes = SoupMessage.RoomHallRes.RoomRes.newBuilder()
					.setId(it.id)
					.setName(it.name)
					.build()
			builder.addRooms(roomRes)
		}
		
		// 没事别刷大厅
		roomData.addAvaIntoHall(aid)
		
		GameUtils.sucResMsg(ProtocolEnums.RES_SOUP_ROOM_HALL, builder.build())
	}
	
	def createRoom = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.CreateRoomReq
		def roomRes = SoupMessage.CreateRoomRes.newBuilder()
		
		if (!req.name || req.name.length() > 5) {
			return GameUtils.resMsg(ProtocolEnums.RES_SOUP_CREATE_ROOM, CodeEnums.SOUP_ROOM_NAME_ILLEGAL)
		}
		
		if (req.max == 0 || req.max > 10) {
			return GameUtils.resMsg(ProtocolEnums.RES_SOUP_CREATE_ROOM, CodeEnums.SOUP_ROOM_MAX_ILLEGAL)
		}
		
		def room = roomData.create(aid, req.name, req.max, req.password)
		
		if (room) {
			roomRes.setId(room.id)
			
			publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
			return GameUtils.sucResMsg(ProtocolEnums.RES_SOUP_CREATE_ROOM, roomRes.build())
		}
		
		GameUtils.resMsg(ProtocolEnums.RES_SOUP_CREATE_ROOM, CodeEnums.SOUP_ROOM_CREATE_FAIL)
	}
	
	def joinRoom = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.JoinRoomReq
		
		def member = memberData.getById(aid)
		
		def roomId = req.roomId
		def room = roomData.roomMap.get(roomId)
		if (!room) {
			return GameUtils.resMsg(ProtocolEnums.RES_SOUP_JOIN_ROOM, CodeEnums.SOUP_ROOM_NOT_EXIST)
		}
		
		synchronized (room) {
			def joinRoomSuc = room.joinRoom(aid)
			def avaIndex = room.getAvaIndex(aid)
			// 改变成员状态
			def memberJoinRoomSuc = member.joinRoom(avaIndex, roomId)
			if (joinRoomSuc && avaIndex && memberJoinRoomSuc) {
				publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
				
				roomPush([aid], [], roomId, null)
				
				def allSeatRes = room.roomMemberMap.collect {
					buildSeatRes(memberData.getById(it.key), room.owner)
				}
				def sucRes = SoupMessage.JoinRoomRes.newBuilder()
						.addAllSeatsChange(allSeatRes)
						.build()
				return GameUtils.sucResMsg(ProtocolEnums.RES_SOUP_JOIN_ROOM, sucRes)
			} else {
				return GameUtils.resMsg(ProtocolEnums.RES_SOUP_JOIN_ROOM, CodeEnums.SOUP_ROOM_JOIN_FAIL)
			}
		}
	}
	
	def leaveRoom = {headers, params ->
		def req = params as SoupMessage.LeaveRoomReq
		def aid = getHeaderAvatarId(headers)
		
		def member = memberData.getById(aid)
		def roomId = member.roomId
		if (roomData.leaveRoom(member, roomId)) {
			publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(roomId).build())
			roomPush([aid], [], roomId, null)
			return GameUtils.sucResMsg(ProtocolEnums.RES_SOUP_LEAVE_ROOM, SoupMessage.LeaveRoomRes.newBuilder().build())
		} else {
			return GameUtils.resMsg(ProtocolEnums.RES_SOUP_LEAVE_ROOM, CodeEnums.SOUP_ROOM_LEAVE_ROOM_FAIL)
		}
	}
	
	def prepare = {headers, params ->
		def req = params as SoupMessage.PrepareReq
		def aid = getHeaderAvatarId(headers)
		
		def sucResMsg = GameUtils.sucResMsg(ProtocolEnums.RES_SOUP_PREPARE, SoupMessage.PrepareRes.newBuilder().build())
		def errRes = GameUtils.resMsg(ProtocolEnums.RES_SOUP_PREPARE, CodeEnums.SOUP_PREPARE_FAIL)
		
		def member = memberData.getById(aid)
		def roomId = member.roomId
		if (!roomId) {
			return errRes
		}
		
		def room = roomData.roomMap.get(roomId)
		synchronized (room) {
			if (req.ok) {
				// 准备或开始
				if (room.owner == aid) {
					// 更改玩家状态成功 && 准备人数足够
					if (room.max == room.prepare.size() + 1
							&& member.status.compareAndSet(1, 3)) {
						// 更改房间状态
						room.status = 1
						
						// 开始游戏记录
						def record = SoupRecord.createRecord(room)
						def cache = recordData.saveCache(record)
						room.recordMap.put(cache.id, cache)
						room.recordId = cache.id
						
						// 更改其他玩家状态数据
						room.memberIds
								.findAll {it != aid}
								.each {
									def m = memberData.getById(it)
									if (m) {
										m.status.compareAndSet(2, 3)
										m.recordIds.add(cache.id)
										// todo 选题 mc time
									}
								}
						// todo 推送游戏开始 初始化相关定时器
						roomPush([], [], roomId, null)
						
						return sucResMsg
					} else {
						return errRes
					}
				} else {
					if (member.status.compareAndSet(1, 2)) {
						room.prepare.add(aid)
						
						roomPush([aid], [], roomId, null)
						return sucResMsg
					} else {
						return errRes
					}
				}
			} else {
				// 取消准备
				if (room.owner != aid && member.status.compareAndSet(2, 1)) {
					room.prepare.remove(aid)
					
					roomPush([aid], [], roomId, null)
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
		
		def errRes = GameUtils.resMsg(ProtocolEnums.REQ_SOUP_KICK, CodeEnums.SOUP_KICK_FAIL)
		
		def kickAid = req.aid
		def kickIndex = req.index
		if (!kickAid || !kickIndex) {
			return errRes
		}
		
		// 主动踢人 成员信息
		def member = memberData.getById(aid)
		def roomId = member.roomId
		if (!roomId) {
			return errRes
		}
		
		def room = roomData.roomMap.get(roomId)
		if (!room) {
			return errRes
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
				return errRes
			}
			
			if (roomData.kick(aid, kickMember, room)) {
				publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
				roomPush([kickMember.id], [], roomId, null)
				return GameUtils.sucResMsg(ProtocolEnums.REQ_SOUP_KICK, SoupMessage.KickRes.newBuilder().build())
			} else {
				return errRes
			}
		}
	}
	
	def exchangeSeat = {headers, params ->
		def req = params as SoupMessage.ExchangeSeatReq
		def aid = getHeaderAvatarId(headers)
		def index = req.index
		
		def errRes = GameUtils.resMsg(ProtocolEnums.REQ_SOUP_EXCHANGE_SEAT, CodeEnums.SOUP_EXCHANGE_SEAT_FAIL)
		
		def member = memberData.getById(aid)
		if (member.seat == index) {
			return errRes
		}
		
		def roomId = member.roomId
		if (!roomId) {
			return errRes
		}
		
		def room = roomData.roomMap.get(roomId)
		if (!room) {
			return errRes
		}
		
		synchronized (room) {
			if (roomData.exchangeSeat(room, member, index)) {
				publishEvent(EventEnums.SOUP_SEAT_CHANGE, SoupEvent.SeatChange.newBuilder().setAid(aid).setRoomId(room.id).build())
				roomPush([aid], [], roomId, null)
				return GameUtils.sucResMsg(ProtocolEnums.REQ_SOUP_EXCHANGE_SEAT, SoupMessage.ExchangeSeatRes.newBuilder().build())
			} else {
				return errRes
			}
		}
	}
	
	def chat = {headers, params ->
		def req = params as SoupMessage.ChatReq
	}
	
	def answer = {headers, params ->
		def req = params as SoupMessage.AnswerReq
	}
	
	def end = {headers, params ->
		def aid = getHeaderAvatarId(headers)
		def req = params as SoupMessage.EndReq
		
		def errRes = GameUtils.resMsg(ProtocolEnums.REQ_SOUP_END, CodeEnums.SOUP_END_FAIL)
		
		def member = memberData.getById(aid)
		def roomId = member.roomId
		if (!roomId) {
			return errRes
		}
		
		def room = roomData.roomMap.get(roomId)
		if (!room) {
			return errRes
		}
		
		synchronized (room) {
			if (room.status != 1) {
				return errRes
			}
			
			def recordId = room.recordId
			def record = room.recordMap.get(recordId)
			if (!record) {
				return errRes
			}
			
			// 记录结束时间
			record.endTime = LocalDateTime.now()
			room.recordId = null
			room.status = 0
			
			// 重置玩家状态
			room.roomMemberMap.keySet().each {
				def m = memberData.getById(it)
				if (m) {
					m.status.compareAndSet(3, 2)
				}
			}
			
			// 推送汤底答案和所有位置信息
			roomPush(room.roomMemberMap.keySet(), [], roomId, "汤底")
			
			return GameUtils.sucResMsg(ProtocolEnums.REQ_SOUP_END, SoupMessage.EndRes.newBuilder().build())
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
		if (member.status.get() == 0) {
			roomData.addAvaIntoHall(aid)
		} else {
			roomData.removeAvaFromHall(aid)
		}
	}
	
	// Private
	
	private SoupMessage.RoomMemberSeatRes buildSeatRes(SoupMember member, String owner) {
		def avatar = avatarService.getAvatarData()?.getById(member.id)
		
		SoupMessage.RoomMemberSeatRes.newBuilder()
				.setAid(avatar?.id)
				.setAvaName(avatar?.username)
				.setAvaHead("")
				.setStatus(member?.status?.get())
				.setIndex(member?.seat)
				.setOwner(owner == member?.id)
				.build()
	}
	
	private void roomPush(Collection<String> changeMemberIds, Collection<String> excludePushMemberIds, String roomId, String content) {
		def room = roomData.roomMap.get(roomId)
		if (!room) {
			return
		}
		
		def seatRes = changeMemberIds ? [] : room.roomMemberMap.keySet().collect {
			def member = memberData.getById(it)
			buildSeatRes(member, room.owner)
		}
		
		def push = SoupMessage.RoomPush.newBuilder()
				.addAllSeatsChange(seatRes)
				.setStatus(room.status)
				.setContent(content)
				.build()
		def msg = GameUtils.resMsg(ProtocolEnums.RES_SOUP_ROOM_PUSH, CodeEnums.SOUP_ROOM_PUSH_SEAT_CHANGE, push)
		
		avatarService.pushAllMsg(room.roomMemberMap.keySet(), excludePushMemberIds, msg)
	}
}
