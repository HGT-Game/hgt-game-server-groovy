package io.github.hdfg159.game.service

import com.google.protobuf.Message
import com.google.protobuf.TextFormat
import groovy.jmx.builder.JmxBuilder
import groovy.util.logging.Slf4j
import io.github.hdfg159.game.domain.dto.EventMessage
import io.github.hdfg159.game.domain.dto.GameMessage
import io.github.hdfg159.game.enumeration.EventEnums
import io.github.hdfg159.game.enumeration.ProtocolEnums
import io.github.hdfg159.game.service.avatar.ChannelData
import io.github.hdfg159.game.util.GameUtils
import io.github.hdfg159.scheduler.SchedulerManager
import io.reactivex.Completable
import io.reactivex.Maybe
import io.vertx.reactivex.core.AbstractVerticle
import io.vertx.reactivex.core.MultiMap
import io.vertx.reactivex.core.eventbus.EventBus

import static io.github.hdfg159.game.constant.GameConsts.ATTR_NAME_AVATAR
import static io.github.hdfg159.game.constant.GameConsts.ATTR_NAME_CHANNEL_ID
import static io.github.hdfg159.game.enumeration.CodeEnums.ERROR
import static io.reactivex.schedulers.Schedulers.io

/**
 * Project:starter
 * <p>
 * Package:io.github.hdfg159.game.service
 * <p>
 * 抽务游戏系统服务
 *
 * @date 2020/7/16 17:12
 * @author zhangzhenyu
 */
@Slf4j
abstract class AbstractService extends AbstractVerticle {
    /**
     * 通道信息
     */
    private static final ChannelData CHANNEL_DATA = ChannelData.instance

    protected SchedulerManager scheduler
    protected EventBus eventBus

    /**
     * 初始化方法
     * @return Completable
     */
    abstract Completable init()

    /**
     * 销毁方法
     * @return Completable
     */
    abstract Completable destroy()

    @Override
    Completable rxStart() {
        this.eventBus = this.@vertx.eventBus()
        this.scheduler = SchedulerManager.INSTANCE
        init().doOnComplete({
            // 暴露 jmx
            new JmxBuilder().export {
                bean(
                        target: this,
                        name: "io.github.hdfg159.game.data.service:name=${this.class.simpleName}@${this.hashCode()}",
                        attributes: [],
                        operations: "*"
                )
            }
        }).doOnComplete({
            log.info "deploy game service complete : ${this.class.simpleName}"
        })
    }

    @Override
    Completable rxStop() {
        destroy().doOnComplete({
            log.info "undeploy game service complete : ${this.class.simpleName}"
        })
    }

    /**
     * 响应请求
     * @param protocol 协议
     * @param closure 闭包执行体
     */
    void response(ProtocolEnums protocol, Closure closure) {
        def address = protocol.address()
        eventBus.consumer(address)
                .toFlowable()
                .flatMapMaybe({message ->
                    def body = message.body()
                    def headers = message.headers()

                    def data = body ? protocol.requestClass.getDefaultInstance().parserForType.parseFrom(body as byte[]) : null
                    def channelId = (headers as MultiMap) ?[ATTR_NAME_CHANNEL_ID] as String

                    Maybe.fromCallable({closure.call(headers, data) as GameMessage.Message})
                            .subscribeOn(io())
                            .doOnSuccess({
                                flushMsg(channelId, it)
                            })
                            .doOnError({
                                flushMsg(channelId, GameUtils.resMsg(protocol, ERROR))
                                log.error "[${address}] invoke request error:${it.message}", it
                            })
                            .onErrorComplete()
                })
                .subscribe({
                    log.debug "[${address}] invoke request success"
                }, {
                    log.error "[${address}] invoke request error:${it.message}", it
                }, {
                    log.debug "[${address}] request complete"
                })
    }

    /**
     * 获取用户ID
     * @param headers 头信息
     * @return ID
     */
    protected static String getHeaderAvatarId(headers) {
        return headers ?[ATTR_NAME_AVATAR] as String
    }

    protected static void flushMsg(String channelId, Message it) {
        if (channelId && it) {
            def channel = CHANNEL_DATA?.channelMap?.get(channelId)
            if (channel && channel.isActive()) {
                channel.writeAndFlush(it)
            } else {
                log.warn("channel not active,don't response,channel:${channel}")
            }
        }
    }

    /**
     * 发布事件
     * @param address 地址
     * @param obj 事件数据闭包
     */
    protected void publishEvent(EventEnums enums, Message data) {
        GameUtils.publishEvent(eventBus, enums, data)
    }

    /**
     * 处理事件
     * @param eventEnums 事件枚举
     * @param run 闭包执行体
     */
    void handleEvent(EventEnums eventEnums, Closure run) {
        def address = eventEnums.address()
        eventBus.consumer(address)
                .toFlowable()
                .flatMapCompletable({message ->
                    Completable
                            .fromRunnable({
                                def bytes = message.body() as byte[]
                                def event = EventMessage.Event.parseFrom(bytes)
                                def data = eventEnums.clazz.getDefaultInstance().parserForType.parseFrom(event.data.toByteArray())
                                def headers = message.headers()

                                log.info "[${address}] handle event:\n${TextFormat.printer().escapingNonAscii(false).printToString(data)}"
                                run.call(headers, data)
                            })
                            .subscribeOn(io())
                            .doOnError({
                                log.error "[${address}] invoke event error:[${it.message}]", it
                            })
                            .onErrorComplete()
                })
                .subscribe({
                    log.debug "[${address}] event complete"
                }, {
                    log.error "[${address}] invoke event error:[${it.message}]", it
                })
    }
}
