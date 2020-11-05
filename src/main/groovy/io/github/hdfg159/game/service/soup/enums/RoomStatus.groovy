package io.github.hdfg159.game.service.soup.enums

/**
 * 房间状态 枚举
 */
enum RoomStatus {
	WAIT(1),
	SELECT(2),
	PLAYING(3)
	
	private int status
	
	RoomStatus(int status) {
		this.status = status
	}
	
	int getStatus() {
		return status
	}
}