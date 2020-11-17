package io.github.hdfg159.game.service.soup

import groovy.transform.Canonical
import io.github.hdfg159.common.util.IdUtils
import io.github.hdfg159.game.data.TData
import io.github.hdfg159.game.domain.dto.SoupMessage
import io.github.hdfg159.game.service.soup.enums.NoteType

import java.time.LocalDateTime

/**
 * 海龟汤 笔记
 */
@Canonical
class SoupNote implements TData<String> {
	/**
	 * {@link io.github.hdfg159.game.service.soup.enums.NoteType}
	 * 笔记类型 1:引用对话 2:自定义笔记内容
	 */
	int type
	/**
	 * 用户 ID
	 */
	String aid
	/**
	 * 引用聊天 ID
	 */
	String referChatId
	/**
	 * 自定义笔记内容
	 */
	String content
	/**
	 * 创建时间
	 */
	LocalDateTime createTime
	
	static def createChatNote(String aid, String chatId) {
		new SoupNote(
				id: IdUtils.idStr,
				type: NoteType.CHAT.type,
				aid: aid,
				referChatId: chatId,
				createTime: LocalDateTime.now()
		)
	}
	
	static def createCustomNote(String aid, String content) {
		new SoupNote(
				id: IdUtils.idStr,
				type: NoteType.CUSTOM.type,
				aid: aid,
				content: content,
				createTime: LocalDateTime.now()
		)
	}
	
	def covertNoteRes(SoupChatRecord chat) {
		def builder = SoupMessage.NoteRes.newBuilder()
				.setId(id)
				.setType(type)
		
		if (NoteType.CHAT.type == type) {
			builder.setChatMessage(covertChatToNoteRes(chat))
		}
		if (NoteType.CUSTOM.type == type) {
			builder.setContent(content)
		}
		
		builder.build()
	}
	
	static def covertChatToNoteRes(SoupChatRecord chat) {
		SoupMessage.ChatMessageRes.newBuilder()
				.setContent(chat.content)
				.setAnswer(chat.answer)
				.build()
	}
}
