package io.github.hdfg159.web

import groovy.util.logging.Slf4j
import groovy.yaml.YamlSlurper
import io.github.hdfg159.common.util.IdUtils
import io.github.hdfg159.game.config.ConfigLoaderData
import io.github.hdfg159.game.constant.GameConsts
import io.github.hdfg159.web.config.WebServerConfig
import io.github.hdfg159.web.domain.dto.BaseResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.reactivex.Completable
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.core.http.HttpServer
import io.vertx.reactivex.ext.auth.jwt.JWTAuth
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.handler.*

/**
 * Project:starter
 * Package:io.github.hdfg159.web
 * Created by hdfg159 on 2020/7/14 23:09.
 */
@Slf4j
@Singleton
class WebVerticle extends AbstractVerticle {
	@Override
	Completable rxStart() {
		Single.just(new WebServerConfig())
				.flatMap({
					this.@vertx.fileSystem()
							.rxReadFile(GameConsts.CONFIG_PATH)
							.map({
								new JsonObject(new YamlSlurper().parseText(it.toString()).server.web).mapTo(WebServerConfig.class)
							})
							.map({
								it.setJwtConfig(new WebServerConfig.JwtConfig())
								it
							})
				})
				.flatMap({config ->
					this.@vertx.fileSystem()
							.rxReadFile("config/jwt/public.pem")
							.map({it.toString()})
							.map({
								config.jwtConfig.setPublicKey(it)
								config
							})
				})
				
				.flatMap({config ->
					this.@vertx.fileSystem()
							.rxReadFile("config/jwt/private_key.pem")
							.map({it.toString()})
							.map({
								config.jwtConfig.setPrivateKey(it)
								config
							})
				})
				.flatMap({deployServer(it)}).ignoreElement()
				.doOnComplete({
					log.info "deploy ${this.class.simpleName} complete"
				})
	}
	
	@Override
	Completable rxStop() {
		Completable.fromCallable({
			log.info "undeploy ${this.class.simpleName}"
		})
	}
	
	Single<HttpServer> deployServer(WebServerConfig config) {
		Router router = Router.router(this.@vertx)
		router.route()
				.handler(FaviconHandler.create(this.@vertx, "static/favicon.ico"))
				.handler(LoggerHandler.create())
				.handler(BodyHandler.create())
		// .handler(SessionHandler.create(LocalSessionStore.create(this.@vertx)))
				.handler(CorsHandler.create("*"))
				.handler(ResponseTimeHandler.create())
		
		// 一定不要用create("/static")，是相对路径
		def staticHandler = StaticHandler.create("static")
		router.route("/static/*").handler(staticHandler)
		// 因为设置了访问目录，访问目录时候要地址后面加 /
		def shareStaticHandler = StaticHandler.create("logs").setDirectoryListing(true).setCachingEnabled(false)
		router.route("/share/*").handler(shareStaticHandler)
		
		def algorithm = "RS256"
		def jWTAuthOptions = new JWTAuthOptions()
				.addPubSecKey(
						new PubSecKeyOptions()
								.setAlgorithm(algorithm)
								.setBuffer(config.jwtConfig.getPublicKey())
				)
				.addPubSecKey(
						new PubSecKeyOptions()
								.setAlgorithm(algorithm)
								.setBuffer(config.jwtConfig.getPrivateKey())
				)
		JWTAuth jwt = JWTAuth.create(this.@vertx, jWTAuthOptions)
		def chainAuthHandler = ChainAuthHandler.any()
		chainAuthHandler.add(JWTAuthHandler.create(jwt))
		router.route("/api/*").handler(chainAuthHandler)
		
		router.get("/").handler({
			it.response().end(IdUtils.idStr)
		})
		router.post("/login").handler({ctx ->
			def params = ctx.bodyAsJson
			
			def username = params.getString("username")
			def password = params.getString("password")
			if (username == 'admin' && password == 'admin') {
				def object = new JsonObject()
				object.put("uuid", UUID.randomUUID().toString())
				// 根据额外信息生成token
				def token = jwt.generateToken(object, new JWTOptions(
						algorithm: algorithm,
						expiresInSeconds: 20 * 60,
						permissions: ["ADMIN"]
				))
				BaseResponse.success(token).responseOk(ctx)
				return
			}
			
			BaseResponse.fail("登录失败").response(ctx, HttpResponseStatus.UNAUTHORIZED.code())
		})
		
		router.mountSubRouter("/api/config", configRouter())
		
		router.errorHandler(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), {context ->
			def throwable = context.failure()
			log.error "router error:${throwable?.message}", throwable
			BaseResponse.fail(throwable?.message, throwable?.class?.name)
					.response(context, HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
		})
		
		this.@vertx.createHttpServer()
				.requestHandler(router)
				.exceptionHandler({exception -> log.error exception.message, exception})
				.rxListen(config.port)
	}
	
	def configRouter() {
		def router = Router.router(this.@vertx)
		
		router.post("/reload/all").handler({ctx ->
			ConfigLoaderData.instance.reloadConfig([])
					.doOnComplete({
						BaseResponse.success().response(ctx, HttpResponseStatus.OK.code())
					})
					.subscribe()
		})
		
		router
	}
}
