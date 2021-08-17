package org.keycloak.models.map.client;

import org.infinispan.protostream.annotations.ProtoField;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class AttributeEntity {
    @ProtoField(number = 1)
    public String name;

    @ProtoField(number = 2)
    public List<String> values = new LinkedList<>();

    public AttributeEntity() {
    }

    public AttributeEntity(String name, List<String> values) {
        this.name = name;
        this.values.addAll(values);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AttributeEntity that = (AttributeEntity) o;
        return Objects.equals(name, that.name) && Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, values);
    }
}
