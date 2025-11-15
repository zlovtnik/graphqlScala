package com.rcs.ssf.config;

import com.rcs.ssf.dynamic.OracleArrayChunkingProperties;
import com.rcs.ssf.dynamic.streaming.QueryStreamingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({OracleArrayChunkingProperties.class, QueryStreamingProperties.class})
public class DatabaseOptimizationConfig {
}
