package io.github.hdfg159.game.service.log

import groovy.util.logging.Slf4j
import io.github.hdfg159.game.service.AbstractService
import io.reactivex.Completable

/**
 * Project:hgt-game-server
 * Package:io.github.hdfg159.game.service.log
 * Created by hdfg159 on 2020/11/7 9:53.
 */
@Slf4j
@Singleton
class LogService extends AbstractService {
	def logData = GameLogData.getInstance()
	
	@Override
	Completable init() {
		this.@vertx.rxDeployVerticle(logData).ignoreElement()
	}
	
	@Override
	Completable destroy() {
		Completable.complete()
	}
	
	def log(GameLog log) {
		logData.log(log)
	}
}
