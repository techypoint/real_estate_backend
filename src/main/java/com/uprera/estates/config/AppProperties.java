package com.uprera.estates.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String cdnBaseUrl, List<String> corsOrigins) {}
