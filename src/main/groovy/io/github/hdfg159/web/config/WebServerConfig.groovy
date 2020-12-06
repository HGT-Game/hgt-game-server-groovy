package io.github.hdfg159.web.config

/**
 * Project:hgt-game-server
 * <p>
 * Package:io.github.hdfg159.web.config
 * <p>
 *
 * @date 2020/11/10 15:46
 * @author zhangzhenyu
 */
class WebServerConfig {
    int port
    JwtConfig jwtConfig

    static class JwtConfig {
        String privateKey
        String publicKey
    }
}
