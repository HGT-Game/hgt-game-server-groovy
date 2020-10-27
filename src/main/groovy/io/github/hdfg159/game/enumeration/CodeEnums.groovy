package io.github.hdfg159.game.enumeration

/**
 * 响应码枚举
 * Project:starter
 * Package:io.github.hdfg159.game.enumeration
 * Created by hdfg159 on 2020/7/15 22:53.
 */
enum CodeEnums {
	// 1~1000 预留编码
	SUCCESS(200),
	ERROR(500),
	REQUEST(1),
	MAX_CONNECTION_LIMIT(2),
	HEART_BEAT(3),
	
	// 1001-2000 玩家
	/**
	 * 注册信息不合法
	 */
	REGISTER_INFO_ILLEGAL(1001),
	/**
	 * 已经有账号登录成功
	 */
	EXIST_LOGIN(1002),
	/**
	 * 登录失败
	 */
	LOGIN_FAIL(1003),
	/**
	 * 强制下线
	 */
	FORCE_OFFLINE(1004),
	
	// 2001-3000 海龟汤
	SOUP_ROOM_NAME_ILLEGAL(2001),
	SOUP_ROOM_MAX_ILLEGAL(2002),
	SOUP_ROOM_CREATE_FAIL(2003),
	SOUP_ROOM_NOT_EXIST(2004),
	SOUP_ROOM_JOIN_FAIL(2005),
	SOUP_ROOM_PUSH_NEW_JOIN(2006),
	SOUP_ROOM_LEAVE_ROOM_FAIL(2007),
	SOUP_PREPARE_FAIL(2008),
	SOUP_KICK_FAIL(2009),
	
	
	long code
	
	CodeEnums(long code) {
		this.code = code
	}
}