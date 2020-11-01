package io.github.hdfg159.game.service.soup

import groovy.transform.Canonical
import io.github.hdfg159.common.util.IdUtils
import io.github.hdfg159.game.data.TData
import io.github.hdfg159.game.enumeration.CodeEnums
import io.github.hdfg159.game.service.soup.enums.RoomStatus

import java.time.LocalDateTime

/**
 * Project:starter
 * <p>
 * Package:io.github.hdfg159.game.service.soup
 * <p>
 *
 * @date 2020/10/23 14:32
 * @author zhangzhenyu
 */
@Canonical
class SoupRoom implements TData<String>, Comparable<SoupRoom> {
	/**
	 * io.github.hdfg159.game.service.soup.enums.RoomStatus
	 *
	 * 目前状态 0:等待加入 1:游戏中
	 */
	Integer status
	/**
	 * 房间名称
	 */
	String name
	/**
	 * 房间密码 null:无密码
	 */
	String password
	/**
	 * 最大人数
	 */
	int max
	/**
	 * 已经准备的玩家ID
	 */
	Set<String> prepare
	/**
	 * 房主
	 */
	String owner
	/**
	 * 目前在房间玩家ID
	 */
	List<String> memberIds
	/**
	 * [玩家ID:位置]
	 */
	Map<String, Integer> roomMemberMap
	/**
	 * 创建者ID
	 */
	String creator
	/**
	 * 房间创建时间
	 */
	LocalDateTime createTime
	/**
	 * 当前开局记录 ID
	 */
	String recordId
	/**
	 * 开局记录 [id:记录]
	 */
	LinkedHashMap<String, SoupRecord> recordMap
	
	@Override
	int compareTo(SoupRoom o) {
		def cct = this.@createTime <=> o.createTime
		return (!cct) ? (this.@status <=> o.status) : cct
	}
	
	static def createRoom(String aid, String name, int max, String password) {
		def room = new SoupRoom(
				id: IdUtils.idStr,
				status: RoomStatus.WAIT.status,
				name: name,
				password: password,
				max: max,
				prepare: [aid],
				owner: aid,
				creator: aid,
				createTime: LocalDateTime.now(),
				recordMap: new LinkedHashMap<String, SoupRecord>()
		)
		
		// 初始化用户位置相关信息
		room.memberIds = new ArrayList<>(max)
		(0..<max).each {
			if (it) {
				room.memberIds.add(null)
			} else {
				room.memberIds.add(aid)
			}
		}
		
		room.roomMemberMap = [:]
		room.roomMemberMap.put(aid, 0)
		room
	}
	
	def joinRoom(String aid) {
		// 如果是房间成员，允许重复加入
		if (roomMemberMap.containsKey(aid)) {
			return CodeEnums.SUCCESS
		}
		
		if (roomMemberMap.size() >= max) {
			return CodeEnums.SOUP_ROOM_JOIN_MAX_LIMIT
		}
		
		for (it in (0..max - 1)) {
			def mid = memberIds[it]
			if (!mid) {
				memberIds[it] = aid
				roomMemberMap.put(aid, it)
				return CodeEnums.SUCCESS
			}
		}
		
		CodeEnums.SOUP_ROOM_JOIN_FAIL
	}
	
	def getAvaIndex(String aid) {
		roomMemberMap[aid]
	}
	
	def getRecord() {
		recordMap[recordId]
	}
	
	def getAllMemberIds() {
		roomMemberMap.keySet()
	}
}
