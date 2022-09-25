package com.operator.dojo;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Configuration
public class DojoConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }

    @Bean
    public Operator operator(KubernetesClient client, List<ResourceController<?>> controllers) {
        Operator operator = new Operator(client, DefaultConfigurationService.instance());
        controllers.forEach(operator::register);
        log.info("Registered {} controller beans", controllers.size());
        return operator;
    }

    @Bean
    public KubernetesClient kubernetesClient(){
        Config config = new ConfigBuilder().withNamespace("sample").build();
        return new DefaultKubernetesClient(config);
    }
}
