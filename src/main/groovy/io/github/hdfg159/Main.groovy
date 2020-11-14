package io.github.hdfg159

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import groovy.jmx.builder.JmxBuilder
import groovy.util.logging.Slf4j
import groovy.yaml.YamlSlurper
import io.github.hdfg159.game.GameVerticle
import io.github.hdfg159.game.config.ServerConfig
import io.github.hdfg159.game.constant.GameConsts
import io.github.hdfg159.game.server.GameServer
import io.github.hdfg159.game.util.GroovyUtils
import io.github.hdfg159.web.WebVerticle
import io.reactivex.Completable
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.reactivex.core.Vertx

import java.lang.management.ManagementFactory

import static io.reactivex.schedulers.Schedulers.io

/**
 * Project:starter
 * <p>
 * Package:io.github.hdfg159
 * <p>
 *
 * @date 2020/7/20 18:00
 * @author zhangzhenyu
 */
@Slf4j
class Main {
	static main(args) {
		// vert.x 序列化 json 忽略 null
		DatabindCodec.mapper()
				.setSerializationInclusion(JsonInclude.Include.NON_NULL)
				.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
				.registerModule(new JavaTimeModule())
		
		writePidFile()
		
		Vertx vx = Vertx.vertx()
		
		jmx()
		
		Completable.mergeArrayDelayError(
				vx.rxDeployVerticle(WebVerticle.instance).ignoreElement(),
				vx.rxDeployVerticle(GameVerticle.instance).ignoreElement()
		).concatWith(gameServer(vx)).subscribe({
			log.info "deploy verticle success"
		}, {
			log.error "deploy verticle error:${it.message}", it
			new Thread({System.exit(0)}).start()
		})
		
		Runtime.addShutdownHook {
			log.info("shutdown application,exist verticle count:${vx.deploymentIDs().size()},closing  ...")
			
			log.info("shutdown game server")
			GameServer.instance.stop()
			log.info("shutdown game server success")
			
			def throwable = vx.rxClose().blockingGet()
			log.info "vert.x close ${throwable ? "fail" : "success"} ${throwable ? throwable.message : ""}"
			log.info "shutdown application success"
		}
	}
	
	private static void writePidFile() {
		def pid = ManagementFactory.getRuntimeMXBean().getPid()
		def pidFile = new File(GameConsts.PID_FILE_NAME)
		pidFile.delete()
		pidFile << pid
		log.info "current application pid:[{}]", pid
	}
	
	private static Completable gameServer(Vertx vx) {
		vx.fileSystem()
				.rxReadFile(GameConsts.CONFIG_PATH)
				.map({buffer ->
					def gameConfigMap = new YamlSlurper().parseText(buffer.toString()).server.game
					new JsonObject(gameConfigMap).mapTo(ServerConfig.class)
				})
				.flatMapCompletable({config ->
					Completable.fromCallable({
						GameServer.instance.start(vx, config)
					}).subscribeOn(io())
				})
	}
	
	/**
	 * jmx
	 */
	private static void jmx() {
		def jmx = new JmxBuilder()
		def beans = jmx.export {
			bean(
					target: new GroovyUtils() {},
					attributes: [],
					operations: "*"
			)
		}
	}
}
