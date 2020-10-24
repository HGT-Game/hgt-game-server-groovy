package io.github.hdfg159.game.service.soup

import io.github.hdfg159.game.data.AbstractDataManager

/**
 * Project:starter
 * Package:io.github.hdfg159.game.service.soup
 * Created by hdfg159 on 2020/10/23 22:55.
 */
@Singleton
class SoupRecordData extends AbstractDataManager<SoupRecord> {
	@Override
	Class<SoupRecord> clazz() {
		SoupRecord.class
	}
}
