package org.keycloak.models.map.storage.hotRod;

public class IckleQueryUtils {
    public static String escapeIfNecessary(Object o) {
        if (o instanceof String) {
            return "'" + o + "'";
        }

        if (o instanceof Integer) {
            return o.toString();
        }

        if (o instanceof Boolean) {
            return o.toString();
        }

        throw new IllegalArgumentException("Wrong argument of type " + o.getClass().getName());
    }
}
