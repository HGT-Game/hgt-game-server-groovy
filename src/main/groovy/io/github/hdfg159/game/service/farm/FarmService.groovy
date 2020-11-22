package io.github.hdfg159.game.service.farm

import groovy.util.logging.Slf4j
import io.github.hdfg159.game.domain.dto.GameMessage
import io.github.hdfg159.game.enumeration.ProtocolEnums
import io.github.hdfg159.game.service.AbstractService
import io.reactivex.Completable

/**
 * Project:starter
 * <p>
 * Package:io.github.hdfg159.game.service
 * <p>
 * 农场系统
 * @date 2020/7/16 17:13
 * @author zhangzhenyu
 */
@Slf4j
@Singleton
class FarmService extends AbstractService {
	@Override
	Completable init() {
		ProtocolEnums.REQ_TEST.handle(this, test)
		return Completable.complete()
	}
	
	@Override
	Completable destroy() {
		return Completable.complete()
	}
	
	def test = {headers, params ->
		// throw new RuntimeException("error test========================")
		def res = GameMessage.TestRes.newBuilder()
				.setStr("teststsadasdasdasd")
				.build()
		
		ProtocolEnums.RES_TEST.sucRes(res)
	}
}
