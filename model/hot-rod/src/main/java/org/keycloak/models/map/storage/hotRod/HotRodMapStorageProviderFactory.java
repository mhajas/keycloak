package org.keycloak.models.map.storage.hotRod;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ClientIntelligence;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.marshall.MarshallerUtil;
import org.infinispan.commons.configuration.XMLStringConfiguration;
import org.infinispan.commons.marshall.ProtoStreamMarshaller;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.annotations.ProtoSchemaBuilder;
import org.infinispan.query.remote.client.ProtobufMetadataManagerConstants;
import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.component.AmphibianProviderFactory;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.map.client.HotRodAttributeEntity;
import org.keycloak.models.map.client.HotRodClientEntity;
import org.keycloak.models.map.client.HotRodProtocolMapperEntity;
import org.keycloak.models.map.client.HotRodPair;
import org.keycloak.models.map.client.MapClientEntity;
import org.keycloak.models.map.common.HotRodEntityDescriptor;
import org.keycloak.models.map.connections.HotRodConnectionProvider;
import org.keycloak.models.map.storage.MapStorageProvider;
import org.keycloak.models.map.storage.MapStorageProviderFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class HotRodMapStorageProviderFactory implements AmphibianProviderFactory<MapStorageProvider>, MapStorageProviderFactory, EnvironmentDependentProviderFactory {

    public static final String PROVIDER_ID = "hotrod";
    private static final Logger LOG = Logger.getLogger(HotRodMapStorageProviderFactory.class);

    public static final Map<Class<?>, HotRodEntityDescriptor<?>> ENTITY_DESCRIPTOR_MAP = new HashMap<>();
    static {
        // Clients descriptor
        ENTITY_DESCRIPTOR_MAP.put(ClientModel.class,
                new HotRodEntityDescriptor<>(ClientModel.class,
                        MapClientEntity.class,
                        Arrays.asList(HotRodClientEntity.class, HotRodAttributeEntity.class, HotRodProtocolMapperEntity.class, HotRodPair.class),
                        HotRodClientEntity::new,
                        "clients"));
    }

    @Override
    public MapStorageProvider create(KeycloakSession session) {
        HotRodConnectionProvider cacheProvider = session.getProvider(HotRodConnectionProvider.class);
        
        if (cacheProvider == null) {
            throw new IllegalStateException("Cannot find HotRodConnectionProvider interface implementation");
        }
        
        return new HotRodMapStorageProvider(this, cacheProvider);
    }

    public HotRodEntityDescriptor<?> getEntityDescriptor(Class<?> c) {
        return ENTITY_DESCRIPTOR_MAP.get(c);
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public String getHelpText() {
        return null;
    }
}
