package io.github.hdfg159.game.service.soup

import groovy.util.logging.Slf4j
import io.github.hdfg159.game.data.AbstractDataManager

/**
 * Project:starter
 * <p>
 * Package:io.github.hdfg159.game.service.soup
 * <p>
 *
 * @date 2020/10/23 14:26
 * @author zhangzhenyu
 */
@Slf4j
@Singleton
class SoupRoomData extends AbstractDataManager<SoupRoom> {
	@Override
	Class<SoupRoom> clazz() {
		SoupRoom.class
	}
}
