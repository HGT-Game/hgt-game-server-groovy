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
class SoupRoom implements TData<String> {
	/**
	 * 房主
	 */
	String owner
	/**
	 * 房间玩家ID
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
	 * 记录 map
	 */
	LinkedHashMap<String, SoupRecord> recordMap
	
	static class SoupRecord implements TData<String> {
		/**
		 * MC ID
		 */
		String mc
		/**
		 * 问题 ID
		 */
		String questionId
		/**
		 * 游戏开始时间
		 */
		LocalDateTime startTime
		/**
		 * 游戏结束时间
		 */
		LocalDateTime endTime
		/**
		 * 本次房间玩家ID
		 */
		List<String> memberIds
		/**
		 * 成员聊天记录
		 */
		HashMap<String, LinkedList<String>> memberMsgMap
		/**
		 * 聊天记录
		 */
		LinkedHashMap<String, SoupChatRecord> chatRecordMap
	}
	
	static class SoupChatRecord implements TData<String> {
		/**
		 * 成员 ID
		 */
		String mid
		/**
		 * 消息类型 0:普通聊天 1:问题
		 */
		int type
		/**
		 * MC回答 1:无关 2:是 3:不是 4:半对
		 */
		int answer
		/**
		 * 内容
		 */
		String content
		/**
		 * 房间创建时间
		 */
		LocalDateTime createTime
	}
}
