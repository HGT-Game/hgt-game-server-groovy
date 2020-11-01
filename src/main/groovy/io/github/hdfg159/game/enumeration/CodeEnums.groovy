package io.github.hdfg159.game.enumeration

/**
 * 响应码枚举
 * Project:starter
 * Package:io.github.hdfg159.game.enumeration
 * Created by hdfg159 on 2020/7/15 22:53.
 */
enum CodeEnums {
	// 1~10000 预留编码
	SUCCESS(200),
	ERROR(500),
	REQUEST(1),
	MAX_CONNECTION_LIMIT(2),
	HEART_BEAT(3),
	PARAM_ERROR(4),
	
	// 10001-20000 玩家
	/**
	 * 注册信息不合法
	 */
	REGISTER_INFO_ILLEGAL(10001),
	/**
	 * 已经有账号登录成功
	 */
	EXIST_LOGIN(10002),
	/**
	 * 登录失败
	 */
	LOGIN_FAIL(10003),
	/**
	 * 强制下线
	 */
	FORCE_OFFLINE(10004),
	
	// 20001-30000 海龟汤
	/**
	 * 房间名字不合法
	 */
	SOUP_ROOM_NAME_ILLEGAL(20001),
	/**
	 * 房间最大人数不合法
	 */
	SOUP_ROOM_MAX_ILLEGAL(20002),
	/**
	 * 创建房间失败
	 */
	SOUP_ROOM_CREATE_FAIL(20003),
	
	/**
	 * 房间不存在
	 */
	SOUP_ROOM_NOT_EXIST(20101),
	/**
	 * 房间不在游戏中
	 */
	SOUP_ROOM_STATUS_NOT_PLAYING(20102),
	/**
	 * 房间在游戏中
	 */
	SOUP_ROOM_STATUS_PLAYING(20103),
	/**
	 * 房间成员不存在
	 */
	SOUP_ROOM_MEMBER_NOT_EXIST(20104),
	/**
	 * 房间不在选题状态
	 */
	SOUP_ROOM_STATUS_NOT_SELECT(20105),
	
	/**
	 * 加入房间失败
	 */
	SOUP_ROOM_JOIN_FAIL(20200),
	/**
	 * 房间已经满人
	 */
	SOUP_ROOM_JOIN_MAX_LIMIT(20201),
	/**
	 * 已经加入房间
	 */
	SOUP_ROOM_JOINED(20202),
	
	
	/**
	 * 房间推送
	 */
	SOUP_ROOM_PUSH(20300),
	
	/**
	 * 离开房间失败
	 */
	SOUP_ROOM_LEAVE_ROOM_FAIL(20400),
	
	/**
	 * 准备失败
	 */
	SOUP_PREPARE_FAIL(20500),
	/**
	 * 未达到最大人数，不能开始游戏
	 */
	SOUP_PREPARE_MAX_NOT_REACH(20501),
	
	/**
	 * 踢人失败
	 */
	SOUP_KICK_FAIL(20600),
	/**
	 * 踢人参数错误
	 */
	SOUP_KICK_PARAM_ERROR(20601),
	/**
	 * 此人不存在
	 */
	SOUP_KICK_MEMBER_NOT_EXIST(20602),
	
	
	/**
	 * 交换位置失败
	 */
	SOUP_SEAT_EXCHANGE_FAIL(20700),
	/**
	 * 当前座位已经有人
	 */
	SOUP_SEAT_EXIST(20701),
	
	/**
	 * 结束游戏失败
	 */
	SOUP_END_FAIL(20800),
	
	/**
	 * 聊天被限制
	 */
	SOUP_CHAT_LIMIT(20900),
	/**
	 * 聊天内容不合法
	 */
	SOUP_CHAT_CONTENT_ILLEGAL(20901),
	/**
	 * 场次记录不存在
	 */
	SOUP_RECORD_NOT_EXIST(20902),
	/**
	 * 聊天记录不存在
	 */
	SOUP_MESSAGE_NOT_EXIST(20903),
	
	/**
	 * 成员不是闲置状态
	 */
	SOUP_MEMBER_NOT_FREE(21000),
	/**
	 * 成员不是MC
	 */
	SOUP_MEMBER_NOT_MC(21001),
	/**
	 * 不是当前房间房主
	 */
	SOUP_MEMBER_NOT_OWNER(21002),
	
	/**
	 * 答案类型不存在
	 */
	SOUP_ANSWER_TYPE_NOT_EXIST(21100),
	
	
	long code
	
	CodeEnums(long code) {
		this.code = code
	}
	
	boolean success() {
		code == SUCCESS.code
	}
	
	static CodeEnums valOf(Long code) {
		if (!code) {
			return null
		}
		
		for (CodeEnums c in values()) {
			if (c.code == code) {
				return c
			}
		}
		
		return null
	}
}