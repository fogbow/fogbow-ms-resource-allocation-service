package cloud.fogbow.ras.core.plugins.authorization;

import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import cloud.fogbow.common.exceptions.ConfigurationErrorException;
import cloud.fogbow.common.exceptions.UnauthorizedRequestException;
import cloud.fogbow.common.models.SystemUser;
import cloud.fogbow.ras.constants.ConfigurationPropertyKeys;
import cloud.fogbow.ras.core.PolicyInstantiator;
import cloud.fogbow.ras.core.PropertiesHolder;
import cloud.fogbow.ras.core.models.Operation;
import cloud.fogbow.ras.core.models.RasOperation;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.policy.XMLRolePolicy;

@RunWith(PowerMockRunner.class)
@PrepareForTest(PropertiesHolder.class)
public class RoleAwareAuthorizationPluginTest {
    
    // TODO update this role information
    /*
     * user1 has role1
     * role1 has permission1
     * user2 has role1 and role2
     * role2 has permission2
     * user3 has defaultrole (role1)
     */
    private String policyFileName = "policy.xml";

    private String identityProviderId = "provider";
    private String remoteProviderId = "remoteProvider";
    
    private String userId1 = "userId1";
    private String userId2 = "userId2";
    private String userIdWithDefaultRoles = "userIdWithDefaultRole";
    private String userIdRemoteProvider = "userIdRemoteProvider";
    
    private String userName1 = "user1";
    private String userName2 = "user2";
    private String userWithDefaultRole = "user3";
    private String userRemoteProvider = "userRemote";
        
    private String userId1Pair = String.format(RoleAwareAuthorizationPlugin.USER_NAME_PROVIDER_PAIR_CONFIGURATION_FORMAT, 
            userId1, identityProviderId);
    private String userId2Pair = String.format(RoleAwareAuthorizationPlugin.USER_NAME_PROVIDER_PAIR_CONFIGURATION_FORMAT, 
            userId2, identityProviderId);
    private String userIdDefaultRolesPair = String.format(RoleAwareAuthorizationPlugin.USER_NAME_PROVIDER_PAIR_CONFIGURATION_FORMAT, 
            userIdWithDefaultRoles, identityProviderId);
    private String userIdRemoteProviderPair = String.format(RoleAwareAuthorizationPlugin.USER_NAME_PROVIDER_PAIR_CONFIGURATION_FORMAT, 
            userIdRemoteProvider, remoteProviderId);
    
    private RoleAwareAuthorizationPlugin plugin;
    private PropertiesHolder propertiesHolder;

    private RasOperation operationGet;
    private RasOperation operationCreate;
    private RasOperation operationReload;
    
    private PolicyInstantiator policyInstantiator;
    private XMLRolePolicy rolePolicy;
    
    @Before
    public void setUp() throws ConfigurationErrorException {
        // set up PropertiesHolder 
        PowerMockito.mockStatic(PropertiesHolder.class);
        this.propertiesHolder = Mockito.mock(PropertiesHolder.class);
        Mockito.doReturn(policyFileName).when(propertiesHolder).getProperty(ConfigurationPropertyKeys.POLICY_FILE_KEY);
        Mockito.doReturn(identityProviderId).when(propertiesHolder).getProperty(ConfigurationPropertyKeys.PROVIDER_ID_KEY);
        BDDMockito.given(PropertiesHolder.getInstance()).willReturn(propertiesHolder);
        
        // set up PolicyInstantiator
        this.policyInstantiator = Mockito.mock(PolicyInstantiator.class);
        this.rolePolicy = Mockito.mock(XMLRolePolicy.class);
        Mockito.when(this.policyInstantiator.getRolePolicyInstanceFromFile(policyFileName)).thenReturn(rolePolicy);
        
        // set up operations
        this.operationGet = new RasOperation(Operation.GET, ResourceType.ATTACHMENT, 
                identityProviderId, identityProviderId);
        this.operationCreate = new RasOperation(Operation.CREATE, ResourceType.ATTACHMENT, 
                identityProviderId, identityProviderId);
        this.operationReload = new RasOperation(Operation.RELOAD, ResourceType.CONFIGURATION, 
                identityProviderId, identityProviderId);
        
        // set up RolePolicy
        Mockito.when(this.rolePolicy.userIsAuthorized(userId1Pair, operationGet)).thenReturn(true);
        Mockito.when(this.rolePolicy.userIsAuthorized(userId1Pair, operationCreate)).thenReturn(false);
        Mockito.when(this.rolePolicy.userIsAuthorized(userId1Pair, operationReload)).thenReturn(false);
        
        Mockito.when(this.rolePolicy.userIsAuthorized(userId2Pair, operationGet)).thenReturn(true);
        Mockito.when(this.rolePolicy.userIsAuthorized(userId2Pair, operationCreate)).thenReturn(true);
        Mockito.when(this.rolePolicy.userIsAuthorized(userId2Pair, operationReload)).thenReturn(false);
        
        Mockito.when(this.rolePolicy.userIsAuthorized(userIdDefaultRolesPair, operationGet)).thenReturn(true);
        Mockito.when(this.rolePolicy.userIsAuthorized(userIdDefaultRolesPair, operationCreate)).thenReturn(false);
        Mockito.when(this.rolePolicy.userIsAuthorized(userIdDefaultRolesPair, operationReload)).thenReturn(false);
        
        Mockito.when(this.rolePolicy.userIsAuthorized(userIdRemoteProviderPair, operationGet)).thenReturn(true);
        Mockito.when(this.rolePolicy.userIsAuthorized(userIdRemoteProviderPair, operationCreate)).thenReturn(false);
        Mockito.when(this.rolePolicy.userIsAuthorized(userIdRemoteProviderPair, operationReload)).thenReturn(false);
        
        this.plugin = new RoleAwareAuthorizationPlugin(this.policyInstantiator);
    }

    @Test
    public void constructorReadsConfigurationCorrectly() {
        Mockito.verify(propertiesHolder, Mockito.times(1)).getProperty(ConfigurationPropertyKeys.POLICY_FILE_KEY);
        PowerMockito.verifyStatic(PropertiesHolder.class, Mockito.atLeastOnce());
    }

    @Test
    public void testIsAuthorized() throws UnauthorizedRequestException {
        // user1 has role1
        // role1 has permission1
        // permission1 allows only get operations
        SystemUser user1 = new SystemUser(userId1, userName1, identityProviderId);
        
        assertTrue(this.plugin.isAuthorized(user1, operationGet));
        assertIsAuthorizedThrowsException(user1, operationCreate);
        assertIsAuthorizedThrowsException(user1, operationReload);

        // user2 has role1 and role2
        // role2 has permission2
        // permission2 allows only get and create operations
        SystemUser user2 = new SystemUser(userId2, userName2, identityProviderId);
        
        assertTrue(this.plugin.isAuthorized(user2, operationGet));
        assertTrue(this.plugin.isAuthorized(user2, operationCreate));
        assertIsAuthorizedThrowsException(user2, operationReload);
    }

    @Test
    public void testIsAuthorizedUserIsNotOnUsersList() throws UnauthorizedRequestException {
        // user3 is not listed on users names list
        // therefore user3 will have the default role, role 1
        SystemUser userWithDefaultRoles = new SystemUser(userIdWithDefaultRoles, userWithDefaultRole, identityProviderId);

        assertTrue(this.plugin.isAuthorized(userWithDefaultRoles, operationGet));
        assertIsAuthorizedThrowsException(userWithDefaultRoles, operationCreate);
        assertIsAuthorizedThrowsException(userWithDefaultRoles, operationReload);

        // remoteuser is not listed on users names list
        // therefore remoteuser will have the default role, role 1
        SystemUser remoteUser = new SystemUser(userIdRemoteProvider, userRemoteProvider, remoteProviderId);
        
        assertTrue(this.plugin.isAuthorized(remoteUser, operationGet));
        assertIsAuthorizedThrowsException(remoteUser, operationCreate);
        assertIsAuthorizedThrowsException(remoteUser, operationReload);
    }
    
    @Test
    public void testRemoteOperationsAreAlwaysAuthorized() throws UnauthorizedRequestException {
        this.operationGet = new RasOperation(Operation.GET, ResourceType.ATTACHMENT, 
                this.identityProviderId, remoteProviderId);
        this.operationCreate = new RasOperation(Operation.CREATE, ResourceType.ATTACHMENT, 
                this.identityProviderId, remoteProviderId);
        this.operationReload = new RasOperation(Operation.RELOAD, ResourceType.CONFIGURATION, 
                this.identityProviderId, remoteProviderId);
        
        SystemUser user1 = new SystemUser(userId1, userName1, identityProviderId);
        SystemUser user2 = new SystemUser(userId2, userName2, identityProviderId);
        SystemUser remoteUser = new SystemUser(userIdRemoteProvider, userRemoteProvider, remoteProviderId);
        
        assertTrue(this.plugin.isAuthorized(user1, operationGet));
        assertTrue(this.plugin.isAuthorized(user1, operationCreate));
        assertTrue(this.plugin.isAuthorized(user1, operationReload));
        
        assertTrue(this.plugin.isAuthorized(user2, operationGet));
        assertTrue(this.plugin.isAuthorized(user2, operationCreate));
        assertTrue(this.plugin.isAuthorized(user2, operationReload));
        
        assertTrue(this.plugin.isAuthorized(remoteUser, operationGet));
        assertTrue(this.plugin.isAuthorized(remoteUser, operationCreate));
        assertTrue(this.plugin.isAuthorized(remoteUser, operationReload));
    }
    
    private void assertIsAuthorizedThrowsException(SystemUser user, RasOperation operation) {
        try {
            this.plugin.isAuthorized(user, operation);
            Assert.fail("isAuthorized call should fail.");
        } catch (UnauthorizedRequestException e) {

        }
    }
}
