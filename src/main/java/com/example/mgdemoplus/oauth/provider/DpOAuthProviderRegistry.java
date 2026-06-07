package com.example.mgdemoplus.oauth.provider;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DpOAuthProviderRegistry {

    private final Map<String, DpOAuthProvider> providers;

    public DpOAuthProviderRegistry(List<DpOAuthProvider> providerList) {
        Map<String, DpOAuthProvider> map = new LinkedHashMap<>();
        for (DpOAuthProvider provider : providerList) {
            map.put(provider.id(), provider);
        }
        this.providers = Map.copyOf(map);
    }

    public DpOAuthProvider get(String providerId) {
        return providers.get(providerId);
    }

    public DpOAuthProvider require(String providerId) {
        DpOAuthProvider provider = get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("未知 OAuth 提供商: " + providerId);
        }
        return provider;
    }

    public List<DpOAuthProvider> enabledProviders() {
        return providers.values().stream()
                .filter(DpOAuthProvider::enabled)
                .collect(Collectors.toList());
    }

    public Collection<DpOAuthProvider> all() {
        return providers.values();
    }
}
