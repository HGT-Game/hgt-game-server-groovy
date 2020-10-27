package io.github.hdfg159.game.service.soup.enums

/**
 * Project:hgt-game-server
 * Package:io.github.hdfg159.game.service.soup.enums
 * Created by hdfg159 on 2020/10/27 22:37.
 */
enum MemberStatus {
	FREE(0),
	ROOM(1),
	PREPARE(2),
	PLAYING(3)
	
	private int status
	
	MemberStatus(int status) {
		this.status = status
	}
	
	int getStatus() {
		return status
	}
}