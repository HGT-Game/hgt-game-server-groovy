package io.github.hdfg159.game.service.soup.config

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import groovy.util.logging.Slf4j
import io.reactivex.Completable
import io.vertx.core.json.Json
import io.vertx.reactivex.core.AbstractVerticle

/**
 * Project:hgt-game-server
 * <p>
 * Package:io.github.hdfg159.game.service.soup.config
 * <p>
 *
 * @author zhangzhenyu* @date 2020/11/2 17:25
 */
@Slf4j
@Singleton
class QuestionConfig extends AbstractVerticle {
    Map<String, Question> questionMap
    
    @Override
    Completable rxStart() {
        this.@vertx.fileSystem()
                .rxReadFile('config/questions.json')
                .map({buffer ->
                    log.info "server config:${Json.decodeValue(buffer.delegate)}"
                    new ObjectMapper().readValue(buffer.toString(), new TypeReference<List<Question>>() {})
                })
                .doOnSuccess({
                    Map<String, Question> qm = [:]
                    it.collect {
                        qm.put(it.id, it)
                    }
                    questionMap = qm
                })
                .ignoreElement()
    }
}
