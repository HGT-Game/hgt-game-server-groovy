package io.github.hdfg159.game.service.soup.enums

/**
 * 成员状态 枚举
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