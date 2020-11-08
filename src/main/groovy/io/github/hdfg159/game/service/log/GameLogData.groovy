package io.github.hdfg159.game.service.log

import groovy.util.logging.Slf4j
import io.github.hdfg159.common.util.IdUtils
import io.reactivex.*
import io.vertx.core.json.JsonObject
import io.vertx.ext.mongo.BulkOperation
import io.vertx.ext.mongo.MongoClientBulkWriteResult
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.ext.mongo.MongoClient

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

import static io.github.hdfg159.game.constant.GameConsts.LOG_MONGO_CONFIG
import static io.github.hdfg159.game.constant.GameConsts.LOG_MONGO_DATA_SOURCE
import static io.reactivex.schedulers.Schedulers.single

/**
 * Project:hgt-game-server
 * Package:io.github.hdfg159.game.service.log
 * Created by hdfg159 on 2020/11/7 10:03.
 */
@Slf4j
@Singleton
class GameLogData extends AbstractVerticle {
	private static final int BATCH_LOG_LIMIT = 20
	
	def successCount = new LongAdder()
	def errorCount = new LongAdder()
	
	def client
	def volatile shutdown = false
	def logQueue = new LinkedBlockingQueue<GameLog>()
	
	@Override
	Completable rxStart() {
		log.info "deploy data manager ${this.class.simpleName}"
		
		this.@vertx.fileSystem()
				.rxReadFile(LOG_MONGO_CONFIG)
				.map({buffer ->
					def config = new JsonObject(buffer.delegate)
					this.client = MongoClient.createShared(this.@vertx, config, LOG_MONGO_DATA_SOURCE)
					log.info "create db log mongo client,config:${config},result:[${client == null}]"
					this.client
				})
				.ignoreElement()
				.concatWith(Completable.fromRunnable({saveLogTask()}))
	}
	
	@Override
	Completable rxStop() {
		Completable.fromRunnable({
			this.@shutdown = true
		}).concatWith(
				Single.just(logQueue)
						.filter({!it.isEmpty()})
						.map({Tuple.tuple(it.size(), packLogs(it.size()))})
						.flatMapCompletable({tuple ->
							rxBatchSaveDB(tuple.v2).doOnSuccess({
								successCount.add(tuple.v1)
								log.info "stopping --> batch save log success:${it.toJson()}"
							}).doOnError({
								errorCount.add(tuple.v1)
								log.error "stopping --> batch save log error", it
							}).onErrorComplete().ignoreElement()
						})
		).doOnComplete({
			log.info "log data manager close success,success:[{}],error:[{}]", successCount.sum(), errorCount.sum()
		})
	}
	
	def saveLogTask() {
		Flowable.<GameLog> create({
			while (!shutdown) {
				try {
					it.onNext(logQueue.take())
				} catch (InterruptedException e) {
					log.error "interrupted emmit game log,next on complete:{}", e.message
					it.onComplete()
				}
			}
			
			it.onComplete()
		}, BackpressureStrategy.BUFFER)
				.subscribeOn(single())
				.buffer(10, TimeUnit.SECONDS, BATCH_LOG_LIMIT)
				.flatMapMaybe({logs ->
					rxBatchSaveDB(logs).doOnSuccess({
						successCount.add(logs.size())
					}).doOnError({
						errorCount.add(logs.size())
						log.error "batch save log error", it
					}).onErrorComplete()
				})
				.subscribe({
					log.info "batch save log success:${it.toJson()}"
				}, {
					log.error "batch save log error", it
				}, {
					log.debug "batch save log complete"
				})
	}
	
	def packLogs(int limit) {
		(0..<limit).collect {logQueue.poll()}.findAll {it != null}
	}
	
	static def collectionName() {
		// 覆盖集合名称，使用表名+日期
		"${GameLog.class.simpleName}-${LocalDate.now().format("yyyyMMdd")}"
	}
	
	Maybe<MongoClientBulkWriteResult> rxBatchSaveDB(Collection<GameLog> data) {
		data ? client.rxBulkWrite(collectionName(), data.collect {
			BulkOperation.createInsert(JsonObject.mapFrom(it))
		}) : Maybe.<MongoClientBulkWriteResult> empty()
	}
	
	def log(GameLog log) {
		if (!shutdown && log) {
			log.id = IdUtils.idStr
			log.createTime = LocalDateTime.now()
			logQueue.add(log)
		}
	}
}
