package io.github.hdfg159.game.service.soup

import groovy.transform.Canonical
import io.github.hdfg159.game.data.TData
import io.github.hdfg159.game.enumeration.CodeEnums
import io.github.hdfg159.game.service.soup.enums.LeaveEnum
import io.github.hdfg159.game.service.soup.enums.MemberStatus

import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * 海龟汤 玩家数据
 *
 * @date 2020/10/23 14:32
 * @author zhangzhenyu
 */
@Canonical
class SoupMember implements TData<String> {
	/**
	 * 发言间隔时间
	 */
	private static final SPEAK_INTERVAL_SECOND = 2
	
	/**
	 * io.github.hdfg159.game.service.soup.enums.MemberStatus
	 *
	 * 目前状态 0:闲置 1:在房间 2:准备中 3:游戏中
	 */
	AtomicInteger status
	/**
	 * 房间座位号
	 */
	volatile Integer seat
	/**
	 * 当前房间 ID
	 */
	volatile String roomId
	/**
	 * {@link io.github.hdfg159.game.service.soup.enums.LeaveEnum}
	 * 离开方式
	 */
	volatile int leave
	/**
	 * 最后发言时间
	 */
	volatile LocalDateTime lastSpeakTime
	
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
		status = new AtomicInteger(MemberStatus.FREE.status)
		
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
		// 更改状态
		this.@status.getAndSet(MemberStatus.FREE.status)
		this.@offlineTime = LocalDateTime.now()
	}
	
	/**
	 * 加入房间
	 * @param seat 座位号
	 * @param roomId 房间 ID
	 * @return 加入结果
	 */
	def joinRoom(int seat, String roomId, boolean playing) {
		if (status.get() != MemberStatus.FREE.status) {
			// 非闲置状态不加入
			return CodeEnums.SOUP_MEMBER_NOT_FREE
		}
		
		if (this.@roomId && roomId != this.@roomId) {
			// roomId不为空肯定是重连，房间ID不同不允许加入
			return CodeEnums.SOUP_ROOM_JOIN_FAIL
		}
		
		// 走正常逻辑
		status.getAndSet(playing ? MemberStatus.PLAYING.status : MemberStatus.ROOM.status)
		this.@seat = seat
		this.@roomId = roomId
		
		CodeEnums.SUCCESS
	}
	
	/**
	 * 是否可以发言
	 * @return long 剩余时间(秒)
	 */
	def speak() {
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
	
	def leaveRoom(boolean kick, boolean allowLeaveForPlaying) {
		if (!roomId) {
			return false
		}
		
		if (status.get() == MemberStatus.PLAYING.status && allowLeaveForPlaying) {
			// 游戏中允许退出才能退出
			leave(kick)
			return true
		} else if (status.get() == MemberStatus.ROOM.status) {
			// 在房间状态可以退出
			leave(kick)
			return true
		} else {
			// 其他都不行
			return false
		}
	}
	
	def leave(kick) {
		this.@status.getAndSet(MemberStatus.FREE.status)
		this.@roomId = null
		this.@leave = kick ? LeaveEnum.PASSIVE.type : LeaveEnum.INITIATIVE.type
	}
	
	def resetRoomInfo() {
		status.getAndSet(MemberStatus.FREE.status)
		seat = null
		roomId = null
	}
	
	def addQuestion(String questionId) {
		this.@questionIds.add(questionId)
	}
}
