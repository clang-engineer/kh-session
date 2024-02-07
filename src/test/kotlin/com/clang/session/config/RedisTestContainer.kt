package com.clang.session.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer

class RedisTestContainer : InitializingBean, DisposableBean {

    private var redisContainer: GenericContainer<*>? = null
    private val log = LoggerFactory.getLogger(javaClass)

    override fun destroy() {
        if (null != redisContainer && redisContainer?.isRunning == true) {
            redisContainer?.close()
        }
    }

    override fun afterPropertiesSet() {
        if (null == redisContainer) {
            redisContainer = GenericContainer("redis:6.2.7")
                .withExposedPorts(6379)
                .withLogConsumer(Slf4jLogConsumer(log))
                .withReuse(true)
        }
        if (redisContainer?.isRunning != true) {
            redisContainer?.start()
        }
    }

    fun getRedisContainer() = redisContainer
}
