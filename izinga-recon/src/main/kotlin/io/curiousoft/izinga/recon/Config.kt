package io.curiousoft.izinga.recon

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.ser.std.DateSerializer
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.databind.util.Converter
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import java.text.DateFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import kotlin.collections.ArrayList


@Configuration
open class ReconConfig {

    @Bean
    open fun provideObjectMapper(it: ObjectMapper) = it.apply {
            registerKotlinModule()
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