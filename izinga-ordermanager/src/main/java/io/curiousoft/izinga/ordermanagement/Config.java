package io.curiousoft.izinga.ordermanagement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.DateSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;

@Configuration
public class Config {

    @Primary
    @Bean
    public ObjectMapper mapper(@Value("${spring.jackson.date-format}") String dateFormat) {
        ObjectMapper mapper = new ObjectMapper();
        var timeModule = new JavaTimeModule();
        timeModule.addSerializer(ZonedDateTime.class, ZonedDateTimeSerializer.INSTANCE);
        timeModule.addSerializer(OffsetDateTime.class, OffsetDateTimeSerializer.INSTANCE);
        timeModule.addSerializer(Date.class, DateSerializer.instance);
        mapper.registerModule(timeModule);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}
