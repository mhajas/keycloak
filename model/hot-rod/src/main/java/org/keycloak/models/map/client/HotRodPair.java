package org.keycloak.models.map.client;

import org.infinispan.protostream.WrappedMessage;
import org.infinispan.protostream.annotations.ProtoField;

public class HotRodPair<T, V> {

    @ProtoField(number = 1)
    public WrappedMessage first;
    @ProtoField(number = 2)
    public WrappedMessage second;

    public HotRodPair() {}

    public HotRodPair(T first, V second) {
        this.first = new WrappedMessage(first);
        this.second = new WrappedMessage(second);
    }

    public T getFirst() {
        return (T) first.getValue();
    }

    public V getSecond() {
        return (V) second.getValue();
    }
}
