package com.alquimia.infrastructure.config;

import com.alquimia.application.IntelligenceService;
import com.alquimia.application.port.in.IntelligenceUseCases;
import com.alquimia.application.port.out.ReadingRepository;
import com.alquimia.domain.IntelligenceProcessor;
import com.alquimia.infrastructure.adapter.out.InMemoryReadingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class DomainConfiguration {
    @Bean
    ReadingRepository readingRepository() { return new InMemoryReadingRepository(); }

    @Bean
    IntelligenceUseCases intelligenceUseCases(ReadingRepository repository,
            @Value("${alquimia.distance-tolerance:0.01}") double distanceTolerance) {
        return new IntelligenceService(repository, new IntelligenceProcessor(distanceTolerance));
    }
}
