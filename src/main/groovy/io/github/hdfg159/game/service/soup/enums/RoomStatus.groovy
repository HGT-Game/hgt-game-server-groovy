package io.github.hdfg159.game.service.soup.enums

/**
 * Project:hgt-game-server
 * Package:io.github.hdfg159.game.service.soup.enums
 * Created by hdfg159 on 2020/10/27 22:46.
 */
enum RoomStatus {
	WAIT(1),
	PLAYING(2)
	
	private int status
	
	RoomStatus(int status) {
		this.status = status
	}
	
	int getStatus() {
		return status
	}
}