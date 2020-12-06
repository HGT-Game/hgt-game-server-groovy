package io.github.hdfg159.game.service.soup.enums

/**
 * Project:hgt-game-server
 * Package:io.github.hdfg159.game.service.soup.enums
 * Created by hdfg159 on 2020/11/19 22:44.
 */
enum LeaveEnum {
    NONE(0),
    INITIATIVE(1),
    PASSIVE(2),

    private int type

    LeaveEnum(int type) {
        this.type = type
    }

    int getType() {
        return type
    }
}