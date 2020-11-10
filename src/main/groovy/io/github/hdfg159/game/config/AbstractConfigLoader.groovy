package io.github.hdfg159.game.config

import groovy.util.logging.Slf4j
import io.github.hdfg159.game.constant.GameConsts
import io.reactivex.Completable
import io.vertx.core.json.JsonObject
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.mysqlclient.MySQLPool
import io.vertx.sqlclient.PoolOptions

/**
 * Project:hgt-game-server
 * Package:io.github.hdfg159.game.config
 * Created by hdfg159 on 2020/11/10 22:52.
 */
@Slf4j
abstract class AbstractConfigLoader extends AbstractVerticle {
	MySQLPool client
	
	@Override
	Completable rxStart() {
		def createClient = this.@vertx.fileSystem()
				.rxReadFile(GameConsts.CONFIG_MYSQL)
				.map({new JsonObject(it.delegate)})
				.doOnSuccess({
					client = MySQLPool.pool(this.@vertx, new MySQLConnectOptions(it), new PoolOptions())
				}).ignoreElement()
		
		createClient.concatWith(Completable.defer({load()}))
				.doOnComplete({
					log.info "deploy config complete : ${this.class.simpleName}"
				})
	}
	
	abstract Completable load()
	
	abstract Completable reload()
}
