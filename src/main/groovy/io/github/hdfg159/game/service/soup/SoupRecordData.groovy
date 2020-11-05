package io.github.hdfg159.game.service.soup

import io.github.hdfg159.game.data.AbstractDataManager

/**
 * 海龟汤 场次记录管理
 */
@Singleton
class SoupRecordData extends AbstractDataManager<SoupRecord> {
	@Override
	Class<SoupRecord> clazz() {
		SoupRecord.class
	}
}
