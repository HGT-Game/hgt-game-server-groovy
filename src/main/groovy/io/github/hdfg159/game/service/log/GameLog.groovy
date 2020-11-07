package io.github.hdfg159.game.service.log

import groovy.transform.Canonical
import io.github.hdfg159.game.data.TData
import io.github.hdfg159.game.enumeration.LogEnums
import io.vertx.core.json.JsonObject

import java.time.LocalDateTime

/**
 * Project:hgt-game-server
 * Package:io.github.hdfg159.game.service.log
 * Created by hdfg159 on 2020/11/7 9:54.
 */
@Canonical
class GameLog implements TData<String> {
	// 玩家ID
	String aid
	// 玩家名字
	String name
	// 日志类型
	LogEnums opt
	// 日志附加参数
	JsonObject param
	// 创建时间
	LocalDateTime createTime
}
