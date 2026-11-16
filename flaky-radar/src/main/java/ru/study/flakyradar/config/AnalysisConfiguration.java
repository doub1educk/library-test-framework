package ru.study.flakyradar.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.study.flakyradar.analysis.DetectionPolicy;
import ru.study.flakyradar.analysis.FlakinessDetector;
import ru.study.flakyradar.analysis.GateEvaluator;
import ru.study.flakyradar.analysis.SignatureNormalizer;
import ru.study.flakyradar.analysis.VerdictCalculator;
import ru.study.flakyradar.report.JUnitXmlParser;

import java.time.Clock;

/**
 * Пакеты {@code report} и {@code analysis} написаны на чистой Java и о Spring не знают.
 * Здесь они собираются в бины с порогами из настроек.
 */
@Configuration(proxyBeanMethods = false)
public class AnalysisConfiguration {

    @Bean
    DetectionPolicy detectionPolicy(FlakyRadarProperties properties) {
        return properties.detection().toPolicy();
    }

    @Bean
    JUnitXmlParser jUnitXmlParser() {
        return new JUnitXmlParser();
    }

    @Bean
    SignatureNormalizer signatureNormalizer() {
        return new SignatureNormalizer();
    }

    @Bean
    FlakinessDetector flakinessDetector(DetectionPolicy policy) {
        return new FlakinessDetector(policy);
    }

    @Bean
    VerdictCalculator verdictCalculator(DetectionPolicy policy) {
        return new VerdictCalculator(policy);
    }

    @Bean
    GateEvaluator gateEvaluator() {
        return new GateEvaluator();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
