package com.articy.runtime.core;

import com.articy.runtime.model.ArticyObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArticyDatabase {
    private final Map<Long, ArticyObject> objectRegistry = new ConcurrentHashMap<>();

    public void registerObject(ArticyObject object) {
        objectRegistry.put(object.getId(), object);
    }

    public <T extends ArticyObject> T getObject(long id, Class<T> type) {
        ArticyObject obj = objectRegistry.get(id);
        if (obj != null && type.isInstance(obj)) {
            return type.cast(obj);
        }
        return null;
    }

    public <T extends ArticyObject> T getObject(String hexId, Class<T> type) {
        return getObject(parseHexId(hexId), type);
    }

    public static long parseHexId(String hexId) {
        if (hexId == null || hexId.isEmpty()) {
            return 0L;
        }
        if (hexId.startsWith("0x")) {
            return Long.parseUnsignedLong(hexId.substring(2), 16);
        }
        return Long.parseUnsignedLong(hexId, 16);
    }
}
