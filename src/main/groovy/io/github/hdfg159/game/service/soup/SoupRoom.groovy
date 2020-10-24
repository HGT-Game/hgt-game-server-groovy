package io.github.hdfg159.game.service.soup

import groovy.transform.Canonical
import io.github.hdfg159.game.data.TData

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
	 * 目前状态 0:等待加入 1:满人 2:准备中 3:游戏中
	 */
	Integer status
	/**
	 * 房间密码 null:无密码
	 */
	String password
	/**
	 * 最大人数
	 */
	int max
	/**
	 * 房主
	 */
	volatile String owner
	/**
	 * 目前在房间玩家ID
	 */
	List<String> memberIds
	/**
	 * [位置:玩家ID]
	 */
	Map<Integer, String> roomMemberMap
	/**
	 * 创建者ID
	 */
	String creator
	/**
	 * 房间创建时间
	 */
	LocalDateTime createTime
	/**
	 * 开局记录 [id:记录]
	 */
	LinkedHashMap<String, SoupRecord> recordMap
	
	@Override
	int compareTo(SoupRoom o) {
		def cct = this.@createTime <=> o.createTime
		return (!cct) ? (this.@status <=> o.status) : cct
	}
}
