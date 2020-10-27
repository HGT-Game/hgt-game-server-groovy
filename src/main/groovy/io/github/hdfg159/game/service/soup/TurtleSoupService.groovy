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
		handleEvent(EventEnums.SOUP_CREATE_ROOM, createRoomEvent)
		
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
			def createRoomEvt = SoupEvent.CreateRoom.newBuilder()
					.setRoomId(room.id)
					.build()
			publishEvent(EventEnums.SOUP_CREATE_ROOM, createRoomEvt)
			return GameUtils.sucResMsg(ProtocolEnums.RES_SOUP_CREATE_ROOM, roomRes.build())
		}
		
		return GameUtils.resMsg(ProtocolEnums.RES_SOUP_CREATE_ROOM, CodeEnums.SOUP_ROOM_CREATE_FAIL)
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
				// 推送消息
				def roomPush = SoupMessage.RoomPush.newBuilder()
						.addSeatsChange(buildSeatRes(aid, avaIndex, room.owner))
						.build()
				def msg = GameUtils.resMsg(ProtocolEnums.RES_SOUP_ROOM_PUSH, CodeEnums.SOUP_ROOM_PUSH_NEW_JOIN, roomPush)
				avatarService.pushAllMsg(room.roomMemberMap.keySet(), [aid].toSet(), msg)
				
				def allSeatRes = room.roomMemberMap.collect {
					buildSeatRes(it.key, it.value, room.owner)
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
						room.status = 2
						
						// 更改其他玩家状态数据
						room.memberIds.findAll {it != aid}
								.each {
									memberData.getById(it)?.status?.compareAndSet(2, 3)
								}
						
						// 开始游戏记录
						def record = SoupRecord.createRecord(room)
						def cache = recordData.saveCache(record)
						room.recordMap.put(cache.id, cache)
						
						return sucResMsg
					} else {
						return errRes
					}
				} else {
					if (member.status.compareAndSet(1, 2)) {
						room.prepare.add(aid)
						return sucResMsg
					} else {
						return errRes
					}
				}
			} else {
				// 取消准备
				if (room.owner != aid && member.status.compareAndSet(2, 1)) {
					room.prepare.remove(aid)
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
		
		// 主动踢人成员信息
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
			
			if (roomData.kick(aid, kickMember, room)) {
				return GameUtils.sucResMsg(ProtocolEnums.REQ_SOUP_KICK, SoupMessage.KickRes.newBuilder().build())
			} else {
				return errRes
			}
		}
	}
	
	def exchangeSeat = {headers, params ->
		def req = params as SoupMessage.ExchangeSeatReq
	}
	
	def chat = {headers, params ->
		def req = params as SoupMessage.ChatReq
	}
	
	def answer = {headers, params ->
		def req = params as SoupMessage.AnswerReq
	}
	
	def end = {headers, params ->
		def req = params as SoupMessage.EndReq
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
	
	def createRoomEvent = {headers, params ->
		def event = params as SoupEvent.CreateRoom
		
		roomData.removeAvaFromHall(event.aid)
	}
	
	// Private
	
	private SoupMessage.RoomMemberSeatRes buildSeatRes(String aid, int avaIndex, String owner) {
		def avatar = avatarService.getAvatarData()?.getById(aid)
		def seatRes = SoupMessage.RoomMemberSeatRes.newBuilder()
				.setAid(avatar?.id)
				.setAvaName(avatar?.username)
				.setAvaHead("")
				.setIndex(avaIndex)
				.setOwner(owner == aid)
				.build()
		seatRes
	}
}
