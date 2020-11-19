package io.github.hdfg159.game.service.soup.config

import groovy.util.logging.Slf4j
import io.github.hdfg159.game.config.AbstractConfigLoader
import io.reactivex.Completable
import io.vertx.reactivex.sqlclient.templates.SqlTemplate

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
class QuestionConfig extends AbstractConfigLoader {
    Map<String, Question> questionMap = [:]
    
    @Override
    Completable load() {
        SqlTemplate.forQuery(client, "SELECT question_id AS id, title, description AS question, content FROM question WHERE status = 1")
                .mapTo(Question.class)
                .rxExecute([:])
                .doOnSuccess({
                    it.each {question ->
                        questionMap.put(question.id, question)
                    }
                    log.info("load question config size:[{}]", questionMap.size())
                }).ignoreElement()
    }
    
    @Override
    Completable reload() {
        load()
    }
    
    def getQuestion(String id) {
        questionMap[id]
    }
    
    Set<String> getQuestionIds() {
        questionMap.keySet().asUnmodifiable()
    }
}
