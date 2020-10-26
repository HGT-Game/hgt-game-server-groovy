package io.github.hdfg159.game.service.soup


import io.vertx.core.impl.ConcurrentHashSet

import java.util.concurrent.ConcurrentHashMap

/**
 * Project:starter
 * Package:io.github.hdfg159.game.service.soup
 * Created by hdfg159 on 2020/10/23 23:07.
 */
@Singleton
class SoupRoomData {
	SoupMemberData memberData = SoupMemberData.getInstance()
	/**
	 * 在大厅玩家ID
	 */
	ConcurrentHashSet<String> hallOnlineAvatars = []
	/**
	 * [房间id:房间]
	 */
	ConcurrentHashMap<String, SoupRoom> roomMap = new ConcurrentHashMap<>()
	
	SoupRoom create(String aid, String name, int max, String password) {
		def room = SoupRoom.createRoom(aid, name, max, password)
		
		def member = memberData.getById(aid)
		def joinRoomSuc = member.joinRoom(0, room.id)
		if (!member || !joinRoomSuc) {
			return null
		}
		
		return roomMap.put(room.id, room)
	}
	
	TreeSet<SoupRoom> getRooms() {
		def rooms = new TreeSet<SoupRoom>()
		rooms.addAll(roomMap.values())
		rooms
	}
	
	boolean removeAvaFromHall(String aid) {
		hallOnlineAvatars.remove(aid)
	}
	
	boolean addAvaIntoHall(String aid) {
		hallOnlineAvatars.add(aid)
	}
	
	boolean leaveRoom(String aid, String roomId) {
		def room = roomMap.get(roomId)
		// 房间不存在
		if (!room) {
			return false
		}
		
		synchronized (room) {
			// 不存在用户
			if (!room.roomMemberMap.containsKey(aid)) {
				return false
			}
			
			// 最后一个人
			if (room.roomMemberMap.size() == 1) {
				roomMap.remove(roomId)
				return true
			}
			
			// 移除
			def removeIndex = room.roomMemberMap.remove(aid)
			room.memberIds.set(removeIndex, null)
			
			// 是房主
			if (room.owner == aid) {
				// 随机一个做房主
				def memberIds = room.roomMemberMap.keySet()
				room.owner = memberIds[0]
			}
			
			return true
		}
	}
}
