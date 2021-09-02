package org.keycloak.models.map.client;

import org.infinispan.protostream.annotations.ProtoField;

public class HotRodTuple {
    @ProtoField(number = 1)
    public String first;
    @ProtoField(number = 2)
    public String second;

    public HotRodTuple() {}

    public HotRodTuple(String first, String second) {
        this.first = first;
        this.second = second;
    }

    public String getFirst() {
        return first;
    }

    public String getSecond() {
        return second;
    }
}
