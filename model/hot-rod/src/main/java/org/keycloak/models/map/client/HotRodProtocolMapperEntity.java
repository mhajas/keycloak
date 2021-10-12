package org.keycloak.models.map.client;

import org.infinispan.protostream.annotations.ProtoField;
import org.keycloak.models.ProtocolMapperModel;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class HotRodProtocolMapperEntity implements MapProtocolMapperEntity {
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
    
    private boolean updated;
    
    public static ProtocolMapperModel toModel(MapProtocolMapperEntity hotRodProtocolMapperEntity) {
        ProtocolMapperModel model = new ProtocolMapperModel();

        model.setId(hotRodProtocolMapperEntity.getId());
        model.setName(hotRodProtocolMapperEntity.getName());
        model.setProtocolMapper(hotRodProtocolMapperEntity.getProtocolMapper());
        model.setConfig(hotRodProtocolMapperEntity.getConfig());

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

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        updated |= !Objects.equals(this.id, id);
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        updated |= !Objects.equals(this.name, name);
        this.name = name;
    }

    @Override
    public String getProtocolMapper() {
        return protocolMapper;
    }

    @Override
    public void setProtocolMapper(String protocolMapper) {
        updated |= !Objects.equals(this.protocolMapper, protocolMapper);
        this.protocolMapper = protocolMapper;
    }

    @Override
    public Map<String, String> getConfig() {
        return config.stream().collect(Collectors.toMap(HotRodPair::getFirst, HotRodPair::getSecond));
    }

    @Override
    public void setConfig(Map<String, String> config) {
        updated |= !Objects.equals(this.config, config);
        this.config.clear();

        config.entrySet().stream().map(entry -> new HotRodPair<>(entry.getKey(), entry.getValue())).forEach(this.config::add);
    }

    @Override
    public boolean isUpdated() {
        return updated;
    }
}
