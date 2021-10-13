package org.keycloak.models.map.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.infinispan.protostream.WrappedMessage;
import org.infinispan.protostream.annotations.ProtoField;

public class HotRodPair<T, V> {

    @JsonIgnore // Jackson annotations in this file are just workaround until we leverage cloners functionality
    @ProtoField(number = 1)
    public WrappedMessage firstWrapped;
    @JsonIgnore
    @ProtoField(number = 2)
    public WrappedMessage secondWrapped;

    public HotRodPair() {}

    public HotRodPair(T first, V second) {
        this.firstWrapped = new WrappedMessage(first);
        this.secondWrapped = new WrappedMessage(second);
    }

    @JsonProperty
    public T getFirst() {
        return firstWrapped == null ? null : (T) firstWrapped.getValue();
    }

    @JsonProperty
    public V getSecond() {
        return secondWrapped == null ? null : (V) secondWrapped.getValue();
    }

    public void setFirst(T first) {
        this.firstWrapped = new WrappedMessage(first);
    }

    public void setSecond(V second) {
        this.secondWrapped = new WrappedMessage(second);
    }
}
