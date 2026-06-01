package com.silver.ai.fluxmcp.infrastructure.config;

import com.silver.ai.fluxmcp.domain.model.AuthType;
import com.silver.ai.fluxmcp.domain.model.ProtocolType;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.util.List;

@Configuration
@EnableR2dbcRepositories(basePackages = "com.silver.ai.fluxmcp.infrastructure.persistence")
public class R2dbcConfig extends AbstractR2dbcConfiguration {

    private final ConnectionFactory connectionFactory;

    public R2dbcConfig(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public ConnectionFactory connectionFactory() {
        return connectionFactory;
    }

    @Override
    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        var dialect = DialectResolver.getDialect(connectionFactory);
        var converters = List.of(
                new AuthTypeWriteConverter(),
                new AuthTypeReadConverter(),
                new ProtocolTypeWriteConverter(),
                new ProtocolTypeReadConverter()
        );
        return R2dbcCustomConversions.of(dialect, converters);
    }

    @WritingConverter
    static class AuthTypeWriteConverter implements Converter<AuthType, String> {
        @Override
        public String convert(AuthType source) {
            return source.name();
        }
    }

    @ReadingConverter
    static class AuthTypeReadConverter implements Converter<String, AuthType> {
        @Override
        public AuthType convert(String source) {
            return AuthType.valueOf(source);
        }
    }

    @WritingConverter
    static class ProtocolTypeWriteConverter implements Converter<ProtocolType, String> {
        @Override
        public String convert(ProtocolType source) {
            return source.name();
        }
    }

    @ReadingConverter
    static class ProtocolTypeReadConverter implements Converter<String, ProtocolType> {
        @Override
        public ProtocolType convert(String source) {
            return ProtocolType.valueOf(source);
        }
    }
}
