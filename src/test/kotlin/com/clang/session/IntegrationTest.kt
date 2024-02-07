package com.clang.session

import com.clang.session.config.AsyncSyncConfiguration
import com.clang.session.config.EmbeddedElasticsearch
import com.clang.session.config.EmbeddedKafka
import com.clang.session.config.EmbeddedRedis
import com.clang.session.config.EmbeddedSQL
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext

/**
 * Base composite annotation for integration tests.
 */
@kotlin.annotation.Target(AnnotationTarget.CLASS)
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@SpringBootTest(classes = [KhSessionApp::class, AsyncSyncConfiguration::class])
@EmbeddedRedis
@EmbeddedElasticsearch
@EmbeddedKafka
@EmbeddedSQL
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
annotation class IntegrationTest
