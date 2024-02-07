package com.clang.session.web.rest

import com.clang.session.IntegrationTest
import com.clang.session.config.EmbeddedKafka
import com.clang.session.config.KafkaSseConsumer
import com.clang.session.config.KafkaSseProducer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.cloud.stream.test.binder.MessageCollector
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageHeaders
import org.springframework.messaging.support.GenericMessage
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.util.MimeTypeUtils

@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
@EmbeddedKafka
class KhSessionKafkaResourceIT {

    @Autowired
    private lateinit var collector: MessageCollector

    @Autowired
    private lateinit var restMockMvc: MockMvc

    @Autowired
    @Qualifier(KafkaSseProducer.CHANNELNAME)
    private lateinit var output: MessageChannel

    @Autowired
    @Qualifier(KafkaSseConsumer.CHANNELNAME)
    private lateinit var input: MessageChannel

    @Test
    @Throws(Exception::class)
    fun producesMessages() {

        restMockMvc.perform(post("/api/kh-session-kafka/publish?message=value-produce"))
            .andExpect(status().isOk)

        val messages = collector.forChannel(output)
        val payload = messages.take() as GenericMessage<String>
        assertThat(payload.payload).isEqualTo("value-produce")
    }

    @Test
    @Throws(Exception::class)
    fun consumesMessages() {
        val map = mutableMapOf<String, Any>()
        map[MessageHeaders.CONTENT_TYPE] = MimeTypeUtils.TEXT_PLAIN_VALUE
        val headers = MessageHeaders(map)
        val testMessage = GenericMessage<String>("value-consume", headers)
        val mvcResult = restMockMvc
            .perform(get("/api/kh-session-kafka/register"))
            .andExpect(status().isOk)
            .andExpect(request().asyncStarted())
            .andReturn()
        for (i in 0 until 100) {
            input.send(testMessage)
            Thread.sleep(100)
            val content = mvcResult.response.contentAsString
            if (content.contains("data:value-consume")) {
                restMockMvc
                    .perform(get("/api/kh-session-kafka/unregister"))
                return
            }
        }
        fail<String>("Expected content data:value-consume not received")
    }
}
