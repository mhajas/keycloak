package org.keycloak.models.map.client;

import org.infinispan.protostream.annotations.ProtoField;
import org.keycloak.models.ProtocolMapperModel;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class HotRodProtocolMapperEntity {
    @ProtoField(number = 1)
    public String id;
    @ProtoField(number = 2)
    public String name;
    @ProtoField(number = 3)
    public String protocol;
    @ProtoField(number = 4)
    public String protocolMapper;
//    @ProtoField(number = 5, defaultValue = "false")
//    public boolean consentRequired;
//    @ProtoField(number = 5)
//    public String consentText;
    @ProtoField(number = 5)
    public Set<HotRodPair<String, String>> config = new LinkedHashSet<>();
    
    public static ProtocolMapperModel toModel(HotRodProtocolMapperEntity hotRodProtocolMapperEntity) {
        ProtocolMapperModel model = new ProtocolMapperModel();

        model.setId(hotRodProtocolMapperEntity.id);
        model.setName(hotRodProtocolMapperEntity.name);
        model.setProtocol(hotRodProtocolMapperEntity.protocol);
        model.setProtocolMapper(hotRodProtocolMapperEntity.protocolMapper);
        model.setConfig(hotRodProtocolMapperEntity.config.stream().collect(Collectors.toMap(HotRodPair::getFirst, HotRodPair::getSecond)));

        return model;
    }
    
    public static HotRodProtocolMapperEntity fromModel(ProtocolMapperModel model) {
        HotRodProtocolMapperEntity entity = new HotRodProtocolMapperEntity();

        entity.id = model.getId();
        entity.name = model.getName();
        entity.protocol = model.getProtocol();
        entity.protocolMapper = model.getProtocolMapper();
        entity.config = model.getConfig().entrySet().stream().map(entry -> new HotRodPair<>(entry.getKey(), entry.getValue())).collect(Collectors.toSet());

        return entity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HotRodProtocolMapperEntity entity = (HotRodProtocolMapperEntity) o;

        return id.equals(entity.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
