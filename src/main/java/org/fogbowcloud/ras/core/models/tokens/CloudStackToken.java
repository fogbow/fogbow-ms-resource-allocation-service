package org.fogbowcloud.ras.core.models.tokens;

import javax.persistence.Entity;

@Entity
public class CloudStackToken extends SignedFederationUserToken {

    public CloudStackToken() {
    }

    public CloudStackToken(String tokenProvider, String tokenValue, String userID, String userName, String signature) {
        super(tokenProvider, tokenValue, userID, userName, signature);
    }

}
