package io.github.hdfg159.game.service.soup

import io.github.hdfg159.common.util.IdUtils

import java.time.LocalDateTime
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
	 * [房间id:房间]
	 */
	ConcurrentHashMap<String, SoupRoom> roomMap
	
	SoupRoom create(String aid, int max, String password) {
		def room = new SoupRoom(
				id: IdUtils.idStr,
				status: 0,
				password: password,
				max: max,
				owner: aid,
				memberIds: [aid],
				roomMemberMap: [0: aid],
				creator: aid,
				createTime: LocalDateTime.now(),
				recordMap: [:] as LinkedHashMap
		)
		
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
}
