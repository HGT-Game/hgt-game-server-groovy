package io.github.hdfg159.game.service.soup

import groovy.util.logging.Slf4j
import io.github.hdfg159.game.data.AbstractDataManager

/**
 * Project:starter
 * <p>
 * Package:io.github.hdfg159.game.service.soup
 * <p>
 *
 * @date 2020/10/23 14:25
 * @author zhangzhenyu
 */
@Slf4j
@Singleton
class SoupMemberData extends AbstractDataManager<SoupMember> {
	@Override
	Class<SoupMember> clazz() {
		SoupMember.class
	}
}
