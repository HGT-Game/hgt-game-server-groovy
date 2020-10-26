package io.github.hdfg159.game.service.soup

import groovy.transform.Canonical
import io.github.hdfg159.game.data.TData

import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

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
class SoupMember implements TData<String> {
	/**
	 * 发言间隔时间
	 */
	private static final SPEAK_INTERVAL_SECOND = 6
	
	/**
	 * 目前状态 0:闲置 1:在房间 2:准备中 3:游戏中
	 */
	AtomicInteger status
	/**
	 * 房间座位号
	 */
	int seat
	/**
	 * 当前房间 ID
	 */
	String roomId
	/**
	 * 最后发言时间
	 */
	LocalDateTime lastSpeakTime
	
	/**
	 * 参与记录场次
	 */
	Set<String> recordIds
	/**
	 * 参与问题ID
	 */
	Set<String> questionIds
	/**
	 * mc次数
	 */
	int mcTimes
	
	/**
	 * 创建时间
	 */
	LocalDateTime createTime
	/**
	 * 下线时间
	 */
	LocalDateTime offlineTime
	/**
	 * 上线时间
	 */
	LocalDateTime loginTime
	
	SoupMember() {
	}
	
	SoupMember(String aid) {
		id = aid
		status = new AtomicInteger(0)
		
		recordIds = []
		questionIds = []
		
		createTime = LocalDateTime.now()
	}
	
	/**
	 * 上线
	 */
	def online() {
		this.@loginTime = LocalDateTime.now()
	}
	
	/**
	 * 离线
	 */
	def offline() {
		this.@offlineTime = LocalDateTime.now()
	}
	
	/**
	 * 加入房间
	 * @param seat 座位号
	 * @param roomId 房间 ID
	 * @return 加入结果
	 */
	boolean joinRoom(int seat, String roomId) {
		if (this.@status.get() != 0) {
			// 非闲置状态不加入
			return false
		}
		
		this.@status.getAndSet(1)
		this.@seat = seat
		this.@roomId = roomId
		return true
	}
	
	/**
	 * 是否可以发言
	 * @return long 剩余时间(秒)
	 */
	long speak() {
		if (!lastSpeakTime) {
			this.@lastSpeakTime = LocalDateTime.now()
			return 0L
		}
		
		def duration = lastSpeakTime >> LocalDateTime.now() as Duration
		def seconds = Math.max(0, SPEAK_INTERVAL_SECOND - duration.seconds)
		if (!seconds) {
			this.@lastSpeakTime = LocalDateTime.now()
		}
		
		seconds
	}
	
	boolean leaveRoom() {
		if (!roomId) {
			return false
		}
		
		// 只有在房间状态才能退出
		if (status.get() != 1) {
			return false
		}
		
		this.@status.getAndSet(0)
		this.@roomId = null
		
		true
	}
}
