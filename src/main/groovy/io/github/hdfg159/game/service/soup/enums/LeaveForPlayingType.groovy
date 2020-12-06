package io.github.hdfg159.game.service.soup.enums

/**
 * Project:hgt-game-server
 * <p>
 * Package:io.github.hdfg159.game.service.soup.enums
 * <p>
 *
 * @date 2020/11/20 11:24
 * @author zhangzhenyu
 */
enum LeaveForPlayingType {
    NO(1),
    YES(2),

    private int type

    LeaveForPlayingType(int type) {
        this.type = type
    }

    int getType() {
        return type
    }
}
