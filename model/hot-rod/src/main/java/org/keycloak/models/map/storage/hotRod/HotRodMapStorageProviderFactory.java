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
import org.keycloak.models.map.client.HotRodTuple;
import org.keycloak.models.map.client.MapClientEntity;
import org.keycloak.models.map.common.HotRodEntityDescriptor;
import org.keycloak.models.map.storage.MapStorageProvider;
import org.keycloak.models.map.storage.MapStorageProviderFactory;
import org.keycloak.provider.EnvironmentDependentProviderFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class HotRodMapStorageProviderFactory implements AmphibianProviderFactory<MapStorageProvider>, MapStorageProviderFactory, EnvironmentDependentProviderFactory {

    public static final String PROVIDER_ID = "hotrod";
    private static final Logger LOG = Logger.getLogger(HotRodMapStorageProviderFactory.class);
    private RemoteCacheManager remoteCacheManager;

    private static final Map<Class<?>, HotRodEntityDescriptor<?>> ENTITY_DESCRIPTOR_MAP = new HashMap<>();
    static {
        // Clients descriptor
        ENTITY_DESCRIPTOR_MAP.put(ClientModel.class,
                new HotRodEntityDescriptor<>(ClientModel.class,
                        MapClientEntity.class,
                        Arrays.asList(HotRodClientEntity.class, HotRodAttributeEntity.class, HotRodProtocolMapperEntity.class, HotRodTuple.class),
                        HotRodClientEntity::new,
                        "clients"));
    }

    @Override
    public MapStorageProvider create(KeycloakSession session) {
        if (remoteCacheManager == null) lazyInit();
        return new HotRodMapStorageProvider(this, remoteCacheManager);
    }

    public void lazyInit() {
        ConfigurationBuilder remoteBuilder = new ConfigurationBuilder();
        remoteBuilder.addServer()
                .host("localhost")
                .port(11222)
                .security()
                .authentication()
                .username("admin")
                .password("admin")
                .realm("default")
                .clientIntelligence(ClientIntelligence.BASIC)
                .marshaller(new ProtoStreamMarshaller());

        remoteCacheManager = new RemoteCacheManager(remoteBuilder.build());

        createCaches();
        registerSchemasAndMarshallers();
    }

    public HotRodEntityDescriptor<?> getEntityDescriptor(Class<?> c) {
        return ENTITY_DESCRIPTOR_MAP.get(c);
    }

    @Override
    public void init(Config.Scope config) {

    }

    private void createCaches() {
        ENTITY_DESCRIPTOR_MAP.values().stream()
                .map(HotRodEntityDescriptor::getCacheName)
                .forEach(name -> {
                    String xml = String.format("<distributed-cache name=\"%s\" mode=\"SYNC\">" +
                            "<encoding media-type=\"application/x-protostream\"/>" +
                            "<locking isolation=\"REPEATABLE_READ\"/>" +
                            "<transaction mode=\"NON_XA\"/>" +
                            "</distributed-cache>" , name);

                    if (remoteCacheManager.administration().getOrCreateCache(name, new XMLStringConfiguration(xml)) == null) {
                        throw new RuntimeException("Cache clients not found. Please make sure the server is properly configured");
                    }
                });
    }

    private void registerSchemasAndMarshallers() {
        // Register entity marshallers on the client side ProtoStreamMarshaller
        // instance associated with the remote cache manager.
        SerializationContext ctx = MarshallerUtil.getSerializationContext(remoteCacheManager);

        // Cache to register the schemas with the server too
        final RemoteCache<String, String> protoMetadataCache = remoteCacheManager.getCache(ProtobufMetadataManagerConstants.PROTOBUF_METADATA_CACHE_NAME);

        // generate the message protobuf schema file and marshaller based on the annotations on Message class
        // and register it with the SerializationContext of the client
        String msgSchemaFile = null;
        try {
            ProtoSchemaBuilder protoSchemaBuilder = new ProtoSchemaBuilder().fileName("KeycloakMapStorage.proto").packageName("org.keycloak.models.map.storage.hotrod"); // TODO: Replace with AutoProtoSchemaBuilder

            ENTITY_DESCRIPTOR_MAP.values().stream()
                    .flatMap(HotRodEntityDescriptor::getHotRodClasses)
                    .peek(aClass -> LOG.debugf("Adding class %s to proto schema", aClass.getName()))
                    .forEach(protoSchemaBuilder::addClass);

            msgSchemaFile = protoSchemaBuilder.build(ctx); // Build message definitions and register them in RemoteCacheManager context

            protoMetadataCache.put("KeycloakMapStorage.proto", msgSchemaFile); // Register message definitions in HotRod server
        } catch (Exception e) {
            throw new RuntimeException("Failed to build protobuf definition from 'Message class'", e);
        }

        // check for definition errors for the registered protobuf schemas
        String errors = protoMetadataCache.get(ProtobufMetadataManagerConstants.ERRORS_KEY_SUFFIX);
        if (errors != null) {
            throw new IllegalStateException("Some Protobuf schema files contain errors: " + errors + "\nSchema :\n" + msgSchemaFile);
        }
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
