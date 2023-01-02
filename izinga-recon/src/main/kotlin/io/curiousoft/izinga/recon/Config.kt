package io.curiousoft.izinga.recon

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.databind.util.Converter
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.collections.ArrayList


@Configuration
open class ReconConfig {

    @Bean
    open fun provideObjectMapper() = ObjectMapper().also {
        it.registerKotlinModule()
        it.registerModule(
            JavaTimeModule().also {
                it.addSerializer(ZonedDateTime::class.java, ZonedDateTimeSerializer.INSTANCE)
                it.addSerializer(OffsetDateTime::class.java, OffsetDateTimeSerializer.INSTANCE)
            }
        )
        it.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        it.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

/*    @Bean
    open fun customConversions(): MongoCustomConversions? {
        val converters = mutableListOf(DateToZonedDateTimeConverter(), ZonedDateTimeToDateConverter())
        return MongoCustomConversions(converters)
    }*/

    @ReadingConverter
    class DateToZonedDateTimeConverter : DateConverter<Date, ZonedDateTime> {
        override fun convert(source: Date): ZonedDateTime {
            return ZonedDateTime.ofInstant(source.toInstant(), ZoneId.systemDefault())
        }
    }

    @WritingConverter
    class ZonedDateTimeToDateConverter : DateConverter<ZonedDateTime, Date> {
        override fun convert(source: ZonedDateTime): Date {
            return Date.from(source.toInstant())
        }
    }

    interface DateConverter<A, B> : Converter<A, B> {
        override fun getInputType(p0: TypeFactory?): JavaType {
            TODO("Not yet implemented")
        }
        override fun getOutputType(p0: TypeFactory?): JavaType {
            TODO("Not yet implemented")
        }
    }
}