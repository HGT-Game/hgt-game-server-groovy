package io.github.hdfg159.game.service.soup.enums

/**
 * 答案类型
 */
enum AnswerType {
    // 未作答
    NON(1),
    // 不相关
    UNRELATED(2),
    // 对
    YES(3),
    // 错
    NO(4),
    // 半对
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