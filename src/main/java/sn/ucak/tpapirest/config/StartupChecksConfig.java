package sn.ucak.tpapirest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StartupChecksConfig {

    /** Déclaré {@code static} : un BeanFactoryPostProcessor doit être créé avant les autres beans. */
    @Bean
    static RequiredConfigurationValidator requiredConfigurationValidator() {
        return new RequiredConfigurationValidator();
    }
}
