package io.github.hdfg159.game.service.soup.enums

/**
 * Project:hgt-game-server
 * Package:io.github.hdfg159.game.service.soup.enums
 * Created by hdfg159 on 2020/10/29 22:14.
 */
enum ChatType {
	CHAT(0),
	QUESTION(1)
	
	int type
	
	ChatType(int type) {
		this.type = type
	}
	
	int getType() {
		return type
	}
}