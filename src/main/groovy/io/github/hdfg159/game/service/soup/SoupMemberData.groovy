package io.github.hdfg159.game.service.soup

import io.github.hdfg159.game.data.AbstractDataManager

/**
 * 海龟汤 玩家数据管理
 */
@Singleton
class SoupMemberData extends AbstractDataManager<SoupMember> {
	@Override
	Class<SoupMember> clazz() {
		SoupMember.class
	}
}
