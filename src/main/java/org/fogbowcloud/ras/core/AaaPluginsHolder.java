package org.fogbowcloud.ras.core;

import org.fogbowcloud.ras.core.plugins.aaa.authentication.AuthenticationPlugin;
import org.fogbowcloud.ras.core.plugins.aaa.authorization.AuthorizationPlugin;
import org.fogbowcloud.ras.core.plugins.aaa.identity.FederationIdentityPlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.mapper.FederationToLocalMapperPlugin;
import org.fogbowcloud.ras.core.plugins.aaa.tokengenerator.TokenGeneratorPlugin;

public class AaaPluginsHolder {
    private TokenGeneratorPlugin tokenGeneratorPlugin;
    private FederationIdentityPlugin federationIdentityPlugin;
    private AuthenticationPlugin authenticationPlugin;
    private AuthorizationPlugin authorizationPlugin;

    public AaaPluginsHolder(AaaPluginInstantiator instantiationInitService) {
        this.tokenGeneratorPlugin = instantiationInitService.getTokenGeneratorPlugin();
        this.federationIdentityPlugin = instantiationInitService.getFederationIdentityPlugin();
        this.authenticationPlugin = instantiationInitService.getAuthenticationPlugin();
        this.authorizationPlugin = instantiationInitService.getAuthorizationPlugin();
    }

    public TokenGeneratorPlugin getTokenGeneratorPlugin() {
        return this.tokenGeneratorPlugin;
    }

    public FederationIdentityPlugin getFederationIdentityPlugin() {
        return this.federationIdentityPlugin;
    }

    public AuthenticationPlugin getAuthenticationPlugin() {
        return this.authenticationPlugin;
    }

    public AuthorizationPlugin getAuthorizationPlugin() {
        return this.authorizationPlugin;
    }
}
