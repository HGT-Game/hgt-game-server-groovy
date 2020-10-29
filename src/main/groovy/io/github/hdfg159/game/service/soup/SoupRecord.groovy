package io.github.hdfg159.game.service.soup

import groovy.transform.Canonical
import io.github.hdfg159.game.data.TData

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Project:starter
 * Package:io.github.hdfg159.game.service.soup
 * Created by hdfg159 on 2020/10/23 22:15.
 */
@Canonical
class SoupRecord implements TData<String> {
	/**
	 * 所属房间 ID
	 */
	String roomId
	
	/**
	 * MC ID
	 */
	String mcId
	
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
	
	ConcurrentLinkedQueue<String> chatRecordIds
	/**
	 * 聊天记录
	 */
	ConcurrentHashMap<String, SoupChatRecord> chatRecordMap
	/**
	 * 成员聊天记录 [用户ID:[聊天ID]]
	 */
	Map<String, ConcurrentLinkedQueue<String>> memberMsgMap
	
	/**
	 * 笔记本数据 [笔记ID:笔记]
	 */
	Map<String, SoupNote> noteMap
	/**
	 * [用户ID:[笔记ID]]
	 */
	Map<String, ConcurrentLinkedQueue<String>> memberNoteMap
	
	static SoupRecord createRecord(SoupRoom room, String questionId) {
		def record = new SoupRecord()
		record.roomId = room.id
		record.startTime = LocalDateTime.now()
		
		record.memberIds = new ArrayList<>(room.memberIds)
		
		record.mcId = room.owner
		record.questionId = questionId
		
		record.chatRecordIds = new ConcurrentLinkedQueue<>()
		record.chatRecordMap = new ConcurrentHashMap<>()
		record.memberMsgMap = [:]
		
		record.noteMap = [:]
		record.memberNoteMap = [:]
		
		record.memberIds.each {
			record.memberMsgMap.put(it, new ConcurrentLinkedQueue<>())
			
			record.memberNoteMap.put(it, new ConcurrentLinkedQueue<String>())
		}
		
		record
	}
}
