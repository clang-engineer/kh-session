package com.clang.session.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Base class for starting/stopping ElasticSearch during tests.
 */
class ElasticsearchTestContainer : InitializingBean, DisposableBean {

    private val log = LoggerFactory.getLogger(javaClass)
    private val CONTAINER_STARTUP_TIMEOUT_MINUTES = 10.toLong()
    private var elasticsearchContainer: ElasticsearchContainer? = null

    override fun destroy() {
        if (null != elasticsearchContainer && elasticsearchContainer?.isRunning == true) {
            elasticsearchContainer?.close()
        }
    }

    override fun afterPropertiesSet() {
        if (null == elasticsearchContainer) {
            elasticsearchContainer =
                ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch").withTag("7.17.4"))
                    .withStartupTimeout(Duration.of(CONTAINER_STARTUP_TIMEOUT_MINUTES, ChronoUnit.MINUTES))
                    .withSharedMemorySize(256000000L)
                    .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
                    .withEnv("xpack.security.enabled", "false")
                    .withLogConsumer(Slf4jLogConsumer(log))
                    .withReuse(true)
        }
        if (elasticsearchContainer?.isRunning != true) {
            elasticsearchContainer?.start()
        }
    }

    fun getElasticsearchContainer() = elasticsearchContainer
}
