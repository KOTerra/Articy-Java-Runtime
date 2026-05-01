package com.articy.runtime.core;

import com.articy.runtime.model.ArticyObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and lookup service for all Articy objects.
 */
public class ArticyDatabase {
    private final Map<Long, ArticyObject> objectRegistry = new ConcurrentHashMap<>();

    /**
     * Registers an object in the database.
     */
    public void registerObject(ArticyObject object) {
        objectRegistry.put(object.getId(), object);
    }

    /**
     * Retrieves an object by its numeric ID and casts it to the requested type.
     */
    public <T extends ArticyObject> T getObject(long id, Class<T> type) {
        ArticyObject obj = objectRegistry.get(id);
        if (obj != null && type.isInstance(obj)) {
            return type.cast(obj);
        }
        return null;
    }

    /**
     * Retrieves an object by its hexadecimal string ID.
     */
    public <T extends ArticyObject> T getObject(String hexId, Class<T> type) {
        return getObject(parseHexId(hexId), type);
    }

    /**
     * Retrieves an object by its technical name.
     */
    public <T extends ArticyObject> T getObjectByTechnicalName(String technicalName, Class<T> type) {
        for (ArticyObject obj : objectRegistry.values()) {
            if (technicalName.equals(obj.getTechnicalName()) && type.isInstance(obj)) {
                return type.cast(obj);
            }
        }
        return null;
    }

    /**
     * Parses a hexadecimal ID string into a long. Supports '0x' prefix.
     */
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
