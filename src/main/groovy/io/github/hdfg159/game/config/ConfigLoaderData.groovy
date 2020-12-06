package io.github.hdfg159.game.config

import groovy.util.logging.Slf4j
import io.reactivex.Flowable

import java.util.concurrent.ConcurrentHashMap

/**
 * Project:hgt-game-server
 * <p>
 * Package:io.github.hdfg159.game.config
 * <p>
 *
 * @date 2020/11/11 16:26
 * @author zhangzhenyu
 */
@Slf4j
@Singleton
class ConfigLoaderData {
    ConcurrentHashMap<String, AbstractConfigLoader> configLoaderMap = new ConcurrentHashMap<>()

    def getConfigLoader(String instanceName) {
        configLoaderMap[instanceName]
    }

    def addConfigLoader(AbstractConfigLoader configLoader) {
        if (configLoader) {
            configLoaderMap.put(configLoader.class.simpleName, configLoader)
        }
    }

    def reloadConfig(Collection<String> filterInstances) {
        def loaders = configLoaderMap.findAll {!filterInstances.contains(it.key)}.values()
        Flowable.fromIterable(loaders)
                .flatMapCompletable({loader ->
                    loader.reload().doOnError({
                        log.error "reload config error:[${loader.class.simpleName}]", it
                    }).doOnComplete({
                        log.info "reload config success:[${loader.class.simpleName}]"
                    }).onErrorComplete()
                })
    }
}
