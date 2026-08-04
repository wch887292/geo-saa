package com.geosaa.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AiAdapterFactory {
    private final Map<String, AiAdapter> adapterMap = new ConcurrentHashMap<>();

    public AiAdapterFactory(List<AiAdapter> adapters) {
        adapters.forEach(adapter -> adapterMap.put(adapter.getType(), adapter));
    }

    public AiAdapter getAdapter(String type) {
        AiAdapter adapter = adapterMap.get(type);
        if (adapter == null) {
            adapter = adapterMap.get("openai"); // 默认
        }
        return adapter;
    }
}