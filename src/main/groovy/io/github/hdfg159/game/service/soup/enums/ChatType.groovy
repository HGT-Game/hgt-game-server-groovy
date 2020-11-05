package io.github.hdfg159.game.service.soup.enums

/**
 * 聊天类型
 */
enum ChatType {
	CHAT(1),
	QUESTION(2)
	
	int type
	
	ChatType(int type) {
		this.type = type
	}
	
	int getType() {
		return type
	}
}