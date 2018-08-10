package org.fogbowcloud.manager.core.plugins.behavior.authentication.ldap;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Base64;
import org.fogbowcloud.manager.core.plugins.behavior.identity.ldap.LdapFederationIdentityPlugin;
import org.fogbowcloud.manager.util.PropertiesUtil;
import org.fogbowcloud.manager.util.RSAUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.google.common.base.Charsets;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PropertiesUtil.class, RSAUtil.class})
public class LdapAuthenticationPluginTest {
	
    private static final String IDENTITY_URL_KEY = "identity_url";
    private final String KEYSTONE_URL = "http://localhost:" + 3000;

    private LdapAuthenticationPlugin identityPlugin;
    private Map<String, String> userCredentials;
    private String name;
    private String password;

    @Before
    public void setUp() {
        Properties properties = Mockito.spy(Properties.class);

        PowerMockito.mockStatic(PropertiesUtil.class);
        Mockito.when(PropertiesUtil.readProperties(Mockito.anyString())).thenReturn(properties);
        Mockito.when(properties.getProperty(Mockito.anyString())).thenReturn(Mockito.anyString());

        this.identityPlugin = Mockito.spy(new LdapAuthenticationPlugin());

        properties.put(IDENTITY_URL_KEY, KEYSTONE_URL);

        this.name = "ldapUser";
        this.password = "ldapUserPass";

        this.userCredentials = new HashMap<String, String>();
        this.setUpCredentials();
    }

    //test case: check if isAuthentic returns true when the tokenValue is not expired.
    @Test
    public void testGetTokenValidTokenValue() throws Exception {
        //set up
        KeyPair keyPair = RSAUtil.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        LdapFederationIdentityPlugin tokenGenerator = Mockito.spy(new LdapFederationIdentityPlugin());
        LdapAuthenticationPlugin authenticationPlugin = Mockito.spy(new LdapAuthenticationPlugin());
        Mockito.doReturn(this.name).when(tokenGenerator).ldapAuthenticate(Mockito.eq(this.name),
                Mockito.eq(this.password));
        Mockito.doReturn(publicKey).when(authenticationPlugin).getPublicKey(Mockito.anyString());
        Mockito.doReturn(privateKey).when(tokenGenerator).getPrivateKey(Mockito.anyString());
        String tokenValueA = tokenGenerator.createTokenValue(this.userCredentials);
        String decodedTokenValue = decodeTokenValue(tokenValueA);
        String split[] = decodedTokenValue.split(LdapFederationIdentityPlugin.ACCESSID_SEPARATOR);
        String signature = split[1];
        Date actualDate = new Date(new Date().getTime());
        String newTokenValue = "{name:\"nome\", expirationDate:\"" + actualDate.getTime() + "\"}"
                + LdapFederationIdentityPlugin.ACCESSID_SEPARATOR + signature;
        newTokenValue = new String(Base64.encodeBase64(newTokenValue.getBytes(StandardCharsets.UTF_8), false, false),
                StandardCharsets.UTF_8);

        //exercise/verify
        Assert.assertFalse(this.identityPlugin.isAuthentic(newTokenValue));
    }

    //test case: check if isAuthentic returns false when the tokenValue is expired.
    @Test
    public void testGetTokenInvalidTokenValue() throws Exception {
        //set up
        KeyPair keyPair = RSAUtil.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        LdapFederationIdentityPlugin tokenGenerator = Mockito.spy(new LdapFederationIdentityPlugin());
        LdapAuthenticationPlugin authenticationPlugin = Mockito.spy(new LdapAuthenticationPlugin());
        Mockito.doReturn(this.name).when(tokenGenerator).ldapAuthenticate(Mockito.eq(this.name),
                Mockito.eq(this.password));
        Mockito.doReturn(publicKey).when(authenticationPlugin).getPublicKey(Mockito.anyString());
        Mockito.doReturn(privateKey).when(tokenGenerator).getPrivateKey(Mockito.anyString());
        String tokenValueA = tokenGenerator.createTokenValue(this.userCredentials);
        String decodedTokenValue = decodeTokenValue(tokenValueA);
        String split[] = decodedTokenValue.split(LdapFederationIdentityPlugin.ACCESSID_SEPARATOR);
        String signature = split[1];
        Date actualDate = new Date(new Date().getTime() + TimeUnit.DAYS.toMillis(365));
        String newTokenValue = "{name:\"nome\", expirationDate:\"" + actualDate.getTime() + "\"}"
                + LdapFederationIdentityPlugin.ACCESSID_SEPARATOR + signature;
        newTokenValue = new String(Base64.encodeBase64(newTokenValue.getBytes(StandardCharsets.UTF_8), false, false),
                StandardCharsets.UTF_8);

        //exercise/verify
        Assert.assertFalse(this.identityPlugin.isAuthentic(newTokenValue));
    }

    private String decodeTokenValue(String tokenValue) {
        return new String(Base64.decodeBase64(tokenValue), Charsets.UTF_8);
    }
    
    private void setUpCredentials() {
        this.userCredentials.put(LdapAuthenticationPlugin.CRED_USERNAME, name);
        this.userCredentials.put(LdapAuthenticationPlugin.CRED_PASSWORD, password);
        this.userCredentials.put(LdapAuthenticationPlugin.CRED_AUTH_URL, "ldapUrl");
        this.userCredentials.put(LdapAuthenticationPlugin.CRED_LDAP_BASE, "ldapBase");
        this.userCredentials.put(LdapAuthenticationPlugin.CRED_LDAP_ENCRYPT, "");
        this.userCredentials.put(LdapAuthenticationPlugin.CRED_PRIVATE_KEY, "private_key_path");
        this.userCredentials.put(LdapAuthenticationPlugin.CRED_PUBLIC_KEY, "public_key_path");
    }
}
