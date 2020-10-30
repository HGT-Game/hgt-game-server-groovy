package io.github.hdfg159.game.service.soup.enums

/**
 * Project:hgt-game-server
 * Package:io.github.hdfg159.game.service.soup.enums
 * Created by hdfg159 on 2020/10/27 22:37.
 */
enum MemberStatus {
	FREE(1),
	ROOM(2),
	PREPARE(3),
	PLAYING(4)
	
	private int status
	
	MemberStatus(int status) {
		this.status = status
	}
	
	int getStatus() {
		return status
	}
}