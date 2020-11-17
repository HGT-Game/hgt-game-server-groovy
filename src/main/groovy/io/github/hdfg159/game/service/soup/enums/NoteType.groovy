package io.github.hdfg159.game.service.soup.enums

/**
 * Project:hgt-game-server
 * <p>
 * Package:io.github.hdfg159.game.service.soup.enums
 * <p>
 *
 * @date 2020/11/17 17:03
 * @author zhangzhenyu
 */
enum NoteType {
	NONE(0),
	CHAT(1),
	CUSTOM(2),
	
	private int type
	
	NoteType(int type) {
		this.type = type
	}
	
	int getType() {
		return type
	}
}