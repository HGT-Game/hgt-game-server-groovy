package io.github.hdfg159.game.service.avatar


import groovy.transform.Canonical
import io.github.hdfg159.game.data.TData

import java.time.LocalDateTime

/**
 * Project:starter
 * <p>
 * Package:io.github.hdfg159.game.domain
 * <p>
 *
 * @date 2020/7/16 9:48
 * @author zhangzhenyu
 */
@Canonical
class Avatar implements TData<String> {
    /**
     * 用户名
     */
    String username
    /**
     * 密码
     */
    String password
    /**
     * 最后登录时间
     */
    LocalDateTime loginTime
    /**
     * 最后下线时间
     */
    LocalDateTime offlineTime
}
