package io.github.hdfg159.game.service.soup.enums
/**
 * Project:hgt-game-server
 * Package:io.github.hdfg159.game.service.soup.enums
 * Created by hdfg159 on 2020/10/29 22:15.
 */
enum AnswerType {
	NON(1),
	UNRELATED(2),
	YES(3),
	NO(4),
	HALF(5),
	
	int type
	
	AnswerType(int type) {
		this.type = type
	}
	
	int getType() {
		return type
	}
	
	static AnswerType valOf(Integer answer) {
		if (!answer) {
			return null
		}
		
		for (AnswerType a in values()) {
			if (a.type == answer) {
				return a
			}
		}
		
		return null
	}
}