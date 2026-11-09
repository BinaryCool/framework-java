package com.dodosupermarket.im.config;

import com.dodosupermarket.im.client.WuKongProductClient;
import com.dodosupermarket.im.dao.ImStore;
import com.dodosupermarket.im.service.ImAccountService;
import com.dodosupermarket.im.service.ImBusinessCatalog;
import com.dodosupermarket.im.service.ImImageService;
import com.dodosupermarket.im.service.ImMessageContent;
import com.dodosupermarket.im.service.ImSendService;
import com.dodosupermarket.im.service.ImIdentityProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

@Configuration
@ConditionalOnProperty(name = "dudu.im.enabled", havingValue = "true")
@EnableConfigurationProperties(ImProperties.class)
public class ImConfiguration {
    @Bean
    public ImStore imStore(ImProperties properties) {
        return new ImStore(properties);
    }

    @Bean
    public WuKongProductClient wuKongProductClient(ImProperties properties) {
        return new WuKongProductClient(properties);
    }

    @Bean
    public ImAccountService imAccountService(ImStore store, WuKongProductClient client, SessionRepository<? extends Session> sessions, ImProperties config, ImIdentityProvider identity) {
        return new ImAccountService(store, client, sessions, config, identity);
    }

    @Bean
    public ImImageService imImageService(ImStore store, ImAccountService accounts, ImProperties config) {
        return new ImImageService(store, accounts, config);
    }

    @Bean
    public ImMessageContent imMessageContent(ImStore store, ImAccountService accounts, org.springframework.beans.factory.ObjectProvider<ImBusinessCatalog> catalog, ImProperties config) {
        return new ImMessageContent(store, accounts, config.isCardsEnabled() ? catalog.getIfAvailable() : null);
    }

    @Bean
    public ImSendService imSendService(ImStore store, WuKongProductClient client, ImAccountService accounts, ImImageService images, ImMessageContent content) {
        return new ImSendService(store, client, accounts, images, content);
    }
}
