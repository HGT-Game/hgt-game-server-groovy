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
import io.github.hdfg159.game.service.soup.config.QuestionConfig
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
		Completable.defer({
			def configs = Completable.mergeArray(
					// 海龟汤问题配置表
					this.@vertx.rxDeployVerticle(QuestionConfig.instance).ignoreElement()
			)
			
			def dataManagers = Completable.mergeArray(
					// 日志数据
					this.@vertx.rxDeployVerticle(GameLogData.instance).ignoreElement(),
					
					// 玩家数据
					this.@vertx.rxDeployVerticle(AvatarData.instance).ignoreElement(),
					
					// 海龟汤数据
					this.@vertx.rxDeployVerticle(SoupMemberData.instance).ignoreElement(),
					this.@vertx.rxDeployVerticle(SoupRecordData.instance).ignoreElement(),
			)
			
			def services = this.@vertx.rxDeployVerticle(LogService.getInstance()).ignoreElement()
					.concatWith(
							Completable.mergeArray(
									this.@vertx.rxDeployVerticle(AvatarService.getInstance()).ignoreElement(),
									this.@vertx.rxDeployVerticle(TurtleSoupService.getInstance()).ignoreElement(),
									this.@vertx.rxDeployVerticle(FarmService.getInstance()).ignoreElement()
							)
					)
			
			Completable.concatArray(
					configs,
					dataManagers,
					services
			)
		}).doOnComplete({
			log.info "deploy ${this.class.simpleName} complete"
		})
	}
	
	@Override
	Completable rxStop() {
		Completable.fromRunnable({
			SchedulerManager.INSTANCE.shutdown()
			log.info "shutdown scheduler manager"
		})
	}
}
