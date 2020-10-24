package io.github.hdfg159.game.service.soup

import groovy.transform.Canonical
import io.github.hdfg159.game.data.TData

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
	 * 目前状态 0:闲置 1:加入房间 2:准备中 3:游戏中
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
}
