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
}
