package io.github.hdfg159.game.service.soup

import groovy.transform.Canonical
import io.github.hdfg159.game.data.TData

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 海龟汤 场次记录
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
	 * 提供选择的问题 ID
	 */
	List<String> selectQuestionIds
	
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
	
	static def createRecord(SoupRoom room, List<String> selectQuestionIds) {
		def record = new SoupRecord()
		record.roomId = room.id
		record.startTime = LocalDateTime.now()
		
		record.memberIds = new ArrayList<>(room.memberIds)
		
		record.mcId = room.owner
		record.selectQuestionIds = selectQuestionIds
		
		record.chatRecordIds = new ConcurrentLinkedQueue<>()
		record.chatRecordMap = new ConcurrentHashMap<>()
		record.memberMsgMap = [:]
		
		record.noteMap = [:]
		record.memberNoteMap = [:]
		
		record.getRecordMemberIds().each {
			record.memberMsgMap.put(it, new ConcurrentLinkedQueue<>())
			
			record.memberNoteMap.put(it, new ConcurrentLinkedQueue<String>())
		}
		
		record
	}
	
	def getMsg(String msgId) {
		!msgId ? null : chatRecordMap[msgId]
	}
	
	def getRecordMemberIds() {
		memberIds.findAll {it != null}
	}
	
	def getNote(String id) {
		noteMap[id]
	}
	
	def addNote(SoupNote note) {
		if (!note) {
			return
		}
		
		def id = note.id
		noteMap.put(id, note)
		
		def aid = note.aid
		memberNoteMap[aid].add(id)
	}
	
	def deleteNote(SoupNote note) {
		def aid = note.aid
		def id = note.id
		
		noteMap.remove(id)
		memberNoteMap[aid].remove(id)
	}
	
	def getAidAllNoteRes(String aid) {
		def noteIds = memberNoteMap[aid]
		if (noteIds) {
			return []
		}
		
		noteIds.collect {getNote(it)}
				.findAll {it != null}
				.collect {
					it.covertNoteRes(getMsg(it.referChatId))
				}
	}
}
