package org.fogbowcloud.manager.core;

import org.fogbowcloud.manager.core.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.plugins.behavior.authorization.AuthorizationPlugin;
import org.fogbowcloud.manager.core.plugins.behavior.authentication.AuthenticationPlugin;
import org.fogbowcloud.manager.core.plugins.behavior.mapper.FederationToLocalMapperPlugin;
import org.fogbowcloud.manager.core.plugins.cloud.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class PluginInstantiatorTest {

    private PluginInstantiator pluginInstantiator;

    private static final String TEST_CONF_PATH = "src/test/resources/plugins_instatiator";

    @Before
    public void setUp() throws Exception {
        HomeDir.getInstance().setPath(TEST_CONF_PATH);
        this.pluginInstantiator = new PluginInstantiator();
    }

    // test case: Tests if the key xmpp_jid in manager.conf has its value as fake-localidentity-member.
    @Test
    public void testSetUpProperties() {
        // set up
        String expected_xmpp_jid_value = "fake-localidentity-member";

        // verify
        Assert.assertEquals(expected_xmpp_jid_value,
                PropertiesHolder.getInstance().getProperty(ConfigurationConstants.XMPP_JID_KEY));
    }

    // test case: Tests if getAuthorizationPlugin() returns StubAuthorizationPlugin as the plugin class name.
    @Test
    public void testCreateAuthorizationPluginInstance() {
        // set up
        String expected_authorization_class_value =
                "org.fogbowcloud.manager.core.stubs.StubAuthorizationPlugin";

        // exercise
        AuthorizationPlugin plugin = this.pluginInstantiator.getAuthorizationPlugin();

        // verify
        Assert.assertEquals(expected_authorization_class_value, plugin.getClass().getName());
    }

    // test case: Tests if getAuthenticationPlugin() returns StubAuthenticationPlugin
    // as the plugin class name.
    @Test
    public void testCreateFederationIdentityPluginInstance() {
        // set up
        String expected_federation_identity_class_value =
                "org.fogbowcloud.manager.core.stubs.StubAuthenticationPlugin";

        // exercise
        AuthenticationPlugin plugin = this.pluginInstantiator.getAuthenticationPlugin();

        // verify
        Assert.assertEquals(expected_federation_identity_class_value, plugin.getClass().getName());
    }

    // test case: Tests if getFederationToLocalMapperPlugin() returns StubFederationToLocalMapperPlugin
    // as the plugin class name.
    @Test
    public void testCreateLocalUserCredentialsMapperPluginInstance() {
        // set up
        String expected_local_user_credentials_mapper_class_value =
                "org.fogbowcloud.manager.core.stubs.StubFederationToLocalMapperPlugin";

        // exercise
        FederationToLocalMapperPlugin plugin = this.pluginInstantiator.getLocalUserCredentialsMapperPlugin();

        // verify
        Assert.assertEquals(expected_local_user_credentials_mapper_class_value, plugin.getClass().getName());
    }

    // test case: Tests if getAttachmentPlugin() returns StubAttachmentPlugin as the plugin class name.
    @Test
    public void testCreateAttachmentPlugin() {
        // set up
        String expected_attachment_plugin_class_value =
                "org.fogbowcloud.manager.core.stubs.StubAttachmentPlugin";

        // exercise
        AttachmentPlugin plugin = this.pluginInstantiator.getAttachmentPlugin();

        // verify
        Assert.assertEquals(expected_attachment_plugin_class_value, plugin.getClass().getName());
    }

    // test case: Tests if getComputePlugin() returns StubComputePlugin as the plugin class name.
    @Test
    public void testCreateComputePlugin() {
        // set up
        String expected_compute_plugin_class_value =
                "org.fogbowcloud.manager.core.stubs.StubComputePlugin";

        // exercise
        ComputePlugin plugin = this.pluginInstantiator.getComputePlugin();

        // verify
        Assert.assertEquals(expected_compute_plugin_class_value, plugin.getClass().getName());
    }

    // test case: Tests if getComputeQuotaPlugin() returns StubComputeQuotaPlugin as the plugin class name.
    @Test
    public void testCreateComputeQuotaPlugin() {
        // set up
        String expected_compute_quota_plugin_class_value =
                "org.fogbowcloud.manager.core.stubs.StubComputeQuotaPlugin";

        // exercise
        ComputeQuotaPlugin plugin = this.pluginInstantiator.getComputeQuotaPlugin();

        // verify
        Assert.assertEquals(expected_compute_quota_plugin_class_value, plugin.getClass().getName());
    }

    // test case: Tests if getNetworkPlugin() returns StubNetworkPlugin as the plugin class name.
    @Test
    public void testCreateNetworkPlugin() {
        // set up
        String expected_network_plugin_class_value =
                "org.fogbowcloud.manager.core.stubs.StubNetworkPlugin";

        // exercise
        NetworkPlugin plugin = this.pluginInstantiator.getNetworkPlugin();

        // verify
        Assert.assertEquals(expected_network_plugin_class_value, plugin.getClass().getName());
    }

    // test case: Tests if getVolumePlugin() returns StubVolumePlugin as the plugin class name.
    @Test
    public void testCreateVolumePlugin() {
        // set up
        String expected_volume_plugin_class_value =
                "org.fogbowcloud.manager.core.stubs.StubVolumePlugin";

        // exercise
        VolumePlugin plugin = this.pluginInstantiator.getVolumePlugin();

        // verify
        Assert.assertEquals(expected_volume_plugin_class_value, plugin.getClass().getName());
    }

    // test case: Tests if getImagePlugin() returns StubImagePlugin as the plugin class name.
    @Test
    public void testCreateImagePlugin() {
        // set up
        String expected_image_plugin_class_value =
                "org.fogbowcloud.manager.core.stubs.StubImagePlugin";

        // exercise
        ImagePlugin plugin = this.pluginInstantiator.getImagePlugin();

        // verify
        Assert.assertEquals(expected_image_plugin_class_value, plugin.getClass().getName());
    }
}
