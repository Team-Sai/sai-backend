package org.teamsai.saibackend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebViewConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/accounts/link").setViewName("accounts/link");
        registry.addViewController("/accounts/link/select").setViewName("accounts/link-select");
        registry.addViewController("/contracts/complete").setViewName("contract/contract-complete");
        registry.addViewController("/contracts/edit-complete").setViewName("contract/contract-edit-complete");
    }
}
