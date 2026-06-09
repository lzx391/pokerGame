package com.example.mgdemoplus.oauth.provider;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DpOAuthProviderRegistry {
/**
 * 这里的细节是接口是统一的，但是实现可以是不同的，然后通过Map存储，根据providerId获取具体的实现类型的对象
 * 举个例子，比如Map<String,Animal>是存动物的，Animal是接口，然后有Cat和Dog实现了这个接口
 * 然后当输入的K是cat时，return的实际上是 Cat cat,当输入的K是dog时，return的实际上是 Dog dog
 */
    private final Map<String, DpOAuthProvider> providers;
/**
 * 知识点，没有显示注册，是靠Bean扫到实现接口的类并打包成list并注入到构造器的map中
 * @param providerList
 */
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
