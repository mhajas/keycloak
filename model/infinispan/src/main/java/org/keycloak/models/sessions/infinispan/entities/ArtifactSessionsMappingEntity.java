package org.keycloak.models.sessions.infinispan.entities;

import org.infinispan.commons.marshall.Externalizer;
import org.infinispan.commons.marshall.MarshallUtil;
import org.infinispan.commons.marshall.SerializeWith;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Objects;


@SerializeWith(ArtifactSessionsMappingEntity.ExternalizerImpl.class)
public class ArtifactSessionsMappingEntity extends SessionEntity {

    public static final Logger logger = Logger.getLogger(ArtifactSessionsMappingEntity.class);

    private String userSessionId;
    private String clientSessionId;

    public ArtifactSessionsMappingEntity(){
    }

    public ArtifactSessionsMappingEntity(String realmId, String userSessionId, String clientSessionId){
        super(realmId);
        this.userSessionId = userSessionId;
        this.clientSessionId = clientSessionId;
    }

    public String getUserSessionId() {
        return userSessionId;
    }

    public void setUserSessionId(String userSessionId) {
        this.userSessionId = userSessionId;
    }

    public String getClientSessionId() {
        return clientSessionId;
    }

    public void setClientSessionId(String clientSessionId) {
        this.clientSessionId = clientSessionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        ArtifactSessionsMappingEntity that = (ArtifactSessionsMappingEntity) o;
        return Objects.equals(userSessionId, that.userSessionId) &&
                Objects.equals(clientSessionId, that.clientSessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userSessionId, clientSessionId);
    }

    @Override
    public String toString() {
        return String.format("ArtifactResponseEntity [ realm=%s, userSessionId=%s, clientSessionId=%s ]", getRealmId(), userSessionId, clientSessionId);
    }


    public static class ExternalizerImpl implements Externalizer<ArtifactSessionsMappingEntity> {

        private static final int VERSION_1 = 1;

        @Override
        public void writeObject(ObjectOutput output, ArtifactSessionsMappingEntity value) throws IOException {
            output.writeByte(VERSION_1);

            MarshallUtil.marshallString(value.getRealmId(), output);
            MarshallUtil.marshallString(value.userSessionId, output);
            MarshallUtil.marshallString(value.clientSessionId, output);
        }

        @Override
        public ArtifactSessionsMappingEntity readObject(ObjectInput input) throws IOException {
            switch (input.readByte()) {
                case VERSION_1:
                    return readObjectVersion1(input);
                default:
                    throw new IOException("Unknown version");
            }
        }

        public ArtifactSessionsMappingEntity readObjectVersion1(ObjectInput input) throws IOException {
            return new ArtifactSessionsMappingEntity(
                    MarshallUtil.unmarshallString(input),
                    MarshallUtil.unmarshallString(input),
                    MarshallUtil.unmarshallString(input));
        }
    }

}