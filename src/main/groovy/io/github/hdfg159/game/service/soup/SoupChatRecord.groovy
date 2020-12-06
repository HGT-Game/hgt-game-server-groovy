package io.github.hdfg159.game.service.soup

import groovy.transform.Canonical
import io.github.hdfg159.game.data.TData

import java.time.LocalDateTime

/**
 * 海龟汤 聊天记录
 */
@Canonical
class SoupChatRecord implements TData<String> {
    /**
     * 成员 ID
     */
    String mid
    /**
     * 消息类型 0:普通聊天
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
     * 创建时间
     */
    LocalDateTime createTime
}
