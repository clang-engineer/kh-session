package com.clang.session.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.elasticsearch.config.ElasticsearchConfigurationSupport
import org.springframework.data.elasticsearch.core.convert.ElasticsearchCustomConversions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Configuration
class ElasticsearchConfiguration : ElasticsearchConfigurationSupport() {

    @Bean
    override fun elasticsearchCustomConversions() = ElasticsearchCustomConversions(
        listOf(
            ZonedDateTimeWritingConverter(),
            ZonedDateTimeReadingConverter(),
            InstantWritingConverter(),
            InstantReadingConverter(),
            LocalDateWritingConverter(),
            LocalDateReadingConverter()
        )
    )

    @WritingConverter
    class ZonedDateTimeWritingConverter : Converter<ZonedDateTime, String> {
        override fun convert(source: ZonedDateTime?): String? {
            if (source == null) {
                return null
            }
            return source.toInstant().toString()
        }
    }

    @ReadingConverter
    class ZonedDateTimeReadingConverter : Converter<String, ZonedDateTime> {
        override fun convert(source: String?): ZonedDateTime? {
            if (source == null) {
                return null
            }
            return Instant.parse(source).atZone(ZoneId.systemDefault())
        }
    }

    @WritingConverter
    class InstantWritingConverter : Converter<Instant, String> {
        override fun convert(source: Instant?): String? {
            if (source == null) {
                return null
            }
            return source.toString()
        }
    }

    @ReadingConverter
    class InstantReadingConverter : Converter<String, Instant> {
        override fun convert(source: String?): Instant? {
            if (source == null) {
                return null
            }
            return Instant.parse(source)
        }
    }

    @WritingConverter
    class LocalDateWritingConverter : Converter<LocalDate, String> {
        override fun convert(source: LocalDate?): String? {
            if (source == null) {
                return null
            }
            return source.toString()
        }
    }

    @ReadingConverter
    class LocalDateReadingConverter : Converter<String, LocalDate> {
        override fun convert(source: String?): LocalDate? {
            if (source == null) {
                return null
            }
            return LocalDate.parse(source)
        }
    }
}
