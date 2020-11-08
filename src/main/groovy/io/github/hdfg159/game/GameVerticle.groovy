package io.github.hdfg159.game

import groovy.util.logging.Slf4j
import io.github.hdfg159.game.service.avatar.AvatarData
import io.github.hdfg159.game.service.avatar.AvatarService
import io.github.hdfg159.game.service.farm.FarmService
import io.github.hdfg159.game.service.log.GameLogData
import io.github.hdfg159.game.service.log.LogService
import io.github.hdfg159.game.service.soup.SoupMemberData
import io.github.hdfg159.game.service.soup.SoupRecordData
import io.github.hdfg159.game.service.soup.TurtleSoupService
import io.github.hdfg159.scheduler.SchedulerManager
import io.reactivex.Completable
import io.vertx.reactivex.core.AbstractVerticle

/**
 * 游戏 verticle
 * Project:starter
 * Package:io.github.hdfg159.game
 * Created by hdfg159 on 2020/7/14 23:10.
 */
@Slf4j
@Singleton
class GameVerticle extends AbstractVerticle {
	@Override
	Completable rxStart() {
		log.info "deploy ${this.class.simpleName}"
		
		def dataManagers = Completable.mergeArray(
				this.@vertx.rxDeployVerticle(GameLogData.instance).ignoreElement(),
				
				this.@vertx.rxDeployVerticle(AvatarData.instance).ignoreElement(),
				
				this.@vertx.rxDeployVerticle(SoupMemberData.instance).ignoreElement(),
				this.@vertx.rxDeployVerticle(SoupRecordData.instance).ignoreElement(),
		)
		
		def services = this.@vertx.rxDeployVerticle(LogService.getInstance()).ignoreElement()
				.concatWith(this.@vertx.rxDeployVerticle(AvatarService.getInstance()).ignoreElement())
				.mergeWith(this.@vertx.rxDeployVerticle(TurtleSoupService.getInstance()).ignoreElement())
				.mergeWith(this.@vertx.rxDeployVerticle(FarmService.getInstance()).ignoreElement())
		
		dataManagers.concatWith(services)
	}
	
	@Override
	Completable rxStop() {
		Completable.fromRunnable({
			SchedulerManager.INSTANCE.shutdown()
			log.info "shutdown scheduler manager"
		})
	}
}
