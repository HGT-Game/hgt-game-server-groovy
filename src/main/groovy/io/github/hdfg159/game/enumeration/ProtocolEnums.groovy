package io.github.hdfg159.game.enumeration

import com.google.protobuf.Message
import io.github.hdfg159.game.constant.GameConsts
import io.github.hdfg159.game.domain.dto.GameMessage

/**
 * 接口协议枚举
 * Project:starter
 * Package:io.github.hdfg159.game.enumeration
 * Created by hdfg159 on 2020/7/15 22:48.
 */
enum ProtocolEnums {
	// 1-1000 系统预留
	/**
	 * 1~1000预留编码
	 */
	REQ_PUSH(1, Object.class),
	RES_PUSH(-1, Object.class),
	/**
	 * 心跳
	 */
	REQ_HEART_BEAT(2, GameMessage.HeartBeatReq.class),
	RES_HEART_BEAT(-2, GameMessage.HeartBeatRes.class),
	
	// 1001-2000 玩家
	/**
	 * 下线
	 */
	REQ_OFFLINE(1001, GameMessage.OfflineReq.class),
	RES_OFFLINE(-1001, GameMessage.OfflineRes.class),
	/**
	 * 登录
	 */
	REQ_LOGIN(1002, GameMessage.LoginReq.class),
	RES_LOGIN(-1002, GameMessage.LoginRes.class),
	/**
	 * 注册
	 */
	REQ_REGISTER(1003, GameMessage.RegisterReq.class),
	RES_REGISTER(-1003, GameMessage.RegisterReq.class),
	
	// 2001-3000 海龟汤
	/**
	 * 查询大厅房间
	 */
	REQ_SOUP_ROOM_HALL(2001, null),
	RES_SOUP_ROOM_HALL(-2001, null),
	/**
	 * 创建房间
	 */
	REQ_SOUP_CREATE_ROOM(2002, null),
	RES_SOUP_CREATE_ROOM(-2002, null),
	/**
	 * 加入房间
	 */
	REQ_SOUP_JOIN_ROOM(2003, null),
	RES_SOUP_JOIN_ROOM(-2003, null),
	/**
	 * 离开房间
	 */
	REQ_SOUP_LEAVE_ROOM(2004, null),
	RES_SOUP_LEAVE_ROOM(-2004, null),
	/**
	 * 准备/开始游戏
	 */
	REQ_SOUP_PREPARE(2005, null),
	RES_SOUP_PREPARE(-2005, null),
	/**
	 * 踢人
	 */
	REQ_SOUP_KICK(2006, null),
	RES_SOUP_KICK(-2006, null),
	/**
	 * 换位置
	 */
	REQ_SOUP_EXCHANGE_SEAT(2007, null),
	RES_SOUP_EXCHANGE_SEAT(-2007, null),
	/**
	 * 聊天或提问
	 */
	REQ_SOUP_CHAT(2008, null),
	RES_SOUP_CHAT(-2008, null),
	/**
	 * 回答
	 */
	REQ_SOUP_ANSWER(2009, null),
	RES_SOUP_ANSWER(-2009, null),
	/**
	 * 公布汤底(结束游戏)
	 */
	REQ_SOUP_END(2010, null),
	RES_SOUP_END(-2010, null),
	
	/**
	 * 大厅消息推送
	 */
	REQ_SOUP_HALL_PUSH(2900, null),
	RES_SOUP_HALL_PUSH(-2900, null),
	/**
	 * 房间消息推送
	 */
	REQ_SOUP_ROOM_PUSH(2901, null),
	RES_SOUP_ROOM_PUSH(-2901, null),
	
	// =====================================================
	/**
	 * 1000~1099 预留编码
	 */
	REQ_TEST(9999999, GameMessage.TestReq.class),
	RES_TEST(-9999999, GameMessage.TestRes.class),
	
	long protocol
	Class<? extends Message> requestClass
	
	ProtocolEnums(long protocol, Class<? extends Message> requestClass) {
		this.protocol = protocol
		this.requestClass = requestClass
	}
	
	String address() {
		"${GameConsts.ADDRESS_PROTOCOL}${protocol}"
	}
}