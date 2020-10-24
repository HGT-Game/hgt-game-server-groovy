package io.github.hdfg159.game.service.soup

import groovy.util.logging.Slf4j
import io.github.hdfg159.game.domain.dto.EventMessage
import io.github.hdfg159.game.enumeration.EventEnums
import io.github.hdfg159.game.service.AbstractService
import io.reactivex.Completable

/**
 * Project:starter
 * <p>
 * Package:io.github.hdfg159.game.service.soup
 * <p>
 *
 * @date 2020/10/23 14:18
 * @author zhangzhenyu
 */
@Slf4j
@Singleton
class TurtleSoupService extends AbstractService {
	@Override
	Completable init() {
		
		handleEvent(EventEnums.OFFLINE, offlineEvent)
		handleEvent(EventEnums.ONLINE, onlineEvent)
		Completable.complete()
	}
	
	@Override
	Completable destroy() {
		Completable.complete()
	}
	
	def onlineEvent = {headers, params ->
		def event = params as EventMessage.Online
		log.info "[海龟汤系统]${this.class.name} 收到上线通知:${event.username}"
	}
	
	def offlineEvent = {headers, params ->
		def event = params as EventMessage.Offline
		def username = event.username
		log.info "[海龟汤系统]${this.class.name} 收到下线通知:${username}"
	}
}
