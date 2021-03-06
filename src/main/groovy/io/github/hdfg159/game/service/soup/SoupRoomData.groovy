package io.github.hdfg159.game.service.soup

import io.github.hdfg159.game.enumeration.CodeEnums
import io.github.hdfg159.game.service.soup.enums.RoomStatus
import io.vertx.core.impl.ConcurrentHashSet

import java.util.concurrent.ConcurrentHashMap

/**
 * 海龟汤 房间数据管理
 */
@Singleton
class SoupRoomData {

    /**
     * 在大厅玩家ID
     */
    ConcurrentHashSet<String> hallOnlineAvatars = []
    /**
     * [房间id:房间]
     */
    ConcurrentHashMap<String, SoupRoom> roomMap = new ConcurrentHashMap<>()

    Tuple2<CodeEnums, SoupRoom> create(SoupMember member, String name, int max, String password) {
        def room = SoupRoom.createRoom(member.id, name, max, password)

        def joinRoomSuc = member.joinRoom(0, room.id, false)
        if (joinRoomSuc.fail()) {
            return Tuple.tuple(joinRoomSuc, null)
        }

        roomMap.put(room.id, room)

        Tuple.tuple(CodeEnums.SUCCESS, room)
    }

    def getRooms() {
        def rooms = new TreeSet<SoupRoom>()
        rooms.addAll(roomMap.values())

        rooms
    }

    def removeAvaFromHall(String aid) {
        hallOnlineAvatars.remove(aid)
    }

    def addAvaIntoHall(String aid) {
        hallOnlineAvatars.add(aid)
    }

    Tuple2<CodeEnums, List<String>> leaveRoom(SoupMember member, SoupRoom room) {
        // 游戏中不能退出
        if (room.status == RoomStatus.PLAYING.status) {
            def record = room.getRecord()
            // 如果是不允许退出 或者 mc掉线想趁机退出，不给
            if (!record.leaveForPlaying || member.id == record.mcId) {
                return Tuple.tuple(CodeEnums.SOUP_ROOM_STATUS_PLAYING, [])
            }
        }

        // 不存在用户
        def mid = member.id
        if (!room.roomMemberMap.containsKey(mid)) {
            return Tuple.tuple(CodeEnums.SOUP_ROOM_MEMBER_NOT_EXIST, [])
        }

        // 最后一个人
        List<String> changeAva = [mid]
        if (room.roomMemberMap.size() == 1) {
            roomMap.remove(room.id)
        } else {
            // 移除准备玩家数据
            room.prepare.remove(mid)
            // 移除玩家位置和玩家信息存储
            def removeIndex = room.roomMemberMap.remove(mid)
            room.memberIds.set(removeIndex, null)

            // 是房主
            if (room.owner == mid) {
                // 随机选第一个做房主
                room.owner = (room.roomMemberMap.keySet().toList().shuffled())[0]
                changeAva += room.owner
            }
        }

        // 无脑离开
        member.leaveRoom(false, room.getRecord()?.leaveForPlaying ?: false)
        return Tuple.tuple(CodeEnums.SUCCESS, changeAva)
    }

    static def kick(String aid, SoupMember member, SoupRoom room) {
        // 不能踢自己
        if (aid == member.id) {
            return CodeEnums.SOUP_KICK_ME
        }

        // 不是房主不能踢人
        if (aid != room.owner) {
            return CodeEnums.SOUP_MEMBER_NOT_OWNER
        }

        // 游戏中不能踢人
        if (room.status == RoomStatus.PLAYING.status) {
            return CodeEnums.SOUP_ROOM_STATUS_PLAYING
        }

        // 不存在用户
        if (!room.roomMemberMap.containsKey(member.id)) {
            return CodeEnums.SOUP_ROOM_MEMBER_NOT_EXIST
        }

        // 移除位置相关数据
        def index = room.roomMemberMap.remove(member.id)
        room.memberIds.set(index, null)
        // 移除准备玩家数据
        room.prepare.remove(member.id)

        member.leaveRoom(true, false)

        return CodeEnums.SUCCESS
    }

    static def exchangeSeat(SoupRoom room, SoupMember member, int index) {
        // 游戏中不能换位置
        if (room.status == RoomStatus.PLAYING.status) {
            return CodeEnums.SOUP_ROOM_STATUS_PLAYING
        }

        // 座位有人不换
        if (room.memberIds[index]) {
            return CodeEnums.SOUP_SEAT_EXIST
        }

        // 换位置
        def removeIndex = room.roomMemberMap.remove(member.id)
        room.memberIds.set(removeIndex, null)

        room.roomMemberMap.put(member.id, index)
        room.memberIds.set(index, member.id)

        // 记录个人数据
        member.seat = index

        return CodeEnums.SUCCESS
    }

    def getRoom(String roomId) {
        !roomId ? null : roomMap[roomId]
    }

    def existRoom(String roomId) {
        !roomId ? false : roomMap.containsKey(roomId)
    }
}
