package io.github.hdfg159.game.service.soup

import groovy.transform.Canonical
import io.github.hdfg159.game.data.TData

import java.time.LocalDateTime

/**
 * Project:starter
 * Package:io.github.hdfg159.game.service.soup
 * Created by hdfg159 on 2020/10/23 22:42.
 */
@Canonical
class SoupChatRecord implements TData<String> {
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