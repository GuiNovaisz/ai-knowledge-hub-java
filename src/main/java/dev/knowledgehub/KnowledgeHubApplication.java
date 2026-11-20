package dev.knowledgehub;

import dev.knowledgehub.shared.HubSettings;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(HubSettings.class)
public class KnowledgeHubApplication {
    public static void main(String[] args) {
        var context=SpringApplication.run(KnowledgeHubApplication.class,args);
        if(java.util.Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("evaluation")) context.close();
    }
}
