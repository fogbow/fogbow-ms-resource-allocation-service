package cloud.fogbow.ras.core;

import cloud.fogbow.as.core.util.AuthenticationUtil;
import cloud.fogbow.common.exceptions.*;
import cloud.fogbow.common.models.SystemUser;
import cloud.fogbow.common.plugins.authorization.AuthorizationController;
import cloud.fogbow.common.plugins.authorization.AuthorizationPlugin;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.common.util.CryptoUtil;
import cloud.fogbow.common.util.ServiceAsymmetricKeysHolder;
import cloud.fogbow.common.util.connectivity.FogbowGenericResponse;
import cloud.fogbow.ras.api.http.response.*;
import cloud.fogbow.ras.constants.ConfigurationPropertyDefaults;
import cloud.fogbow.ras.constants.ConfigurationPropertyKeys;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.constants.SystemConstants;
import cloud.fogbow.ras.core.cloudconnector.CloudConnector;
import cloud.fogbow.ras.core.cloudconnector.CloudConnectorFactory;
import cloud.fogbow.ras.core.intercomponent.xmpp.requesters.RemoteGetCloudNamesRequest;
import cloud.fogbow.ras.api.http.response.InstanceStatus;
import cloud.fogbow.ras.core.models.Operation;
import cloud.fogbow.ras.core.models.RasOperation;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.UserData;
import cloud.fogbow.ras.core.models.orders.*;
import cloud.fogbow.ras.api.http.response.quotas.ComputeQuota;
import cloud.fogbow.ras.api.http.response.quotas.Quota;
import cloud.fogbow.ras.api.http.response.quotas.allocation.Allocation;
import cloud.fogbow.ras.api.http.response.quotas.allocation.ComputeAllocation;
import cloud.fogbow.ras.api.http.response.securityrules.SecurityRule;
import cloud.fogbow.ras.core.plugins.authorization.RasAuthPlugin;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ApplicationFacade {
    private final Logger LOGGER = Logger.getLogger(ApplicationFacade.class);

    private static ApplicationFacade instance;

    private AuthorizationPlugin<RasOperation> authorizationPlugin;
    private OrderController orderController;
    private SecurityRuleController securityRuleController;
    private CloudListController cloudListController;
    private String memberId;
    private RSAPublicKey asPublicKey;
    private String buildNumber;

    private ApplicationFacade() {
        this.memberId = PropertiesHolder.getInstance().getProperty(ConfigurationPropertyKeys.LOCAL_MEMBER_ID_KEY);
        this.buildNumber = PropertiesHolder.getInstance().getProperty(ConfigurationPropertyKeys.BUILD_NUMBER_KEY,
                ConfigurationPropertyDefaults.BUILD_NUMBER);
    }

    public static ApplicationFacade getInstance() {
        synchronized (ApplicationFacade.class) {
            if (instance == null) {
                instance = new ApplicationFacade();
            }
            return instance;
        }
    }

    public void setAuthorizationPlugin(AuthorizationPlugin<RasOperation> authorizationPlugin) {
        this.authorizationPlugin = authorizationPlugin;
    }

    public synchronized void setOrderController(OrderController orderController) {
        this.orderController = orderController;
    }

    public synchronized void setSecurityRuleController(SecurityRuleController securityRuleController) {
        this.securityRuleController = securityRuleController;
    }

    public void setCloudListController(CloudListController cloudListController) {
        this.cloudListController = cloudListController;
    }

    public String getVersionNumber() {
        return SystemConstants.API_VERSION_NUMBER + "-" + this.buildNumber;
    }

    public String getPublicKey() throws UnexpectedException {
        // There is no need to authenticate the user or authorize this operation
        try {
            return CryptoUtil.savePublicKey(ServiceAsymmetricKeysHolder.getInstance().getPublicKey());
        } catch (IOException | GeneralSecurityException e) {
            throw new UnexpectedException(e.getMessage(), e);
        }
    }

    public List<String> getCloudNames(String memberId, String userToken) throws FogbowException {
        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        this.authorizationPlugin.isAuthorized(requester, new RasOperation(Operation.GET, ResourceType.CLOUD_NAMES));
        if (memberId.equals(this.memberId)) {
            return this.cloudListController.getCloudNames();
        } else {
            try {
                RemoteGetCloudNamesRequest remoteGetCloudNames = getCloudNamesFromRemoteRequest(memberId, requester);
                List<String> cloudNames = remoteGetCloudNames.send();
                return cloudNames;
            } catch (Exception e) {
                LOGGER.error(e.toString(), e);
                throw new RemoteCommunicationException(e.getMessage(), e);
            }
        }
    }

    // This method is only protected to be used in testing
	protected RemoteGetCloudNamesRequest getCloudNamesFromRemoteRequest(String memberId, SystemUser requester) {
		RemoteGetCloudNamesRequest remoteGetCloudNames = new RemoteGetCloudNamesRequest(memberId, requester);
		return remoteGetCloudNames;
	}

    public String createCompute(ComputeOrder order, String userToken) throws FogbowException {
        if (order.getUserData() != null) {
            for (UserData userDataScript : order.getUserData()) {
                if (userDataScript != null && userDataScript.getExtraUserDataFileContent() != null &&
                    userDataScript.getExtraUserDataFileContent().length() > UserData.MAX_EXTRA_USER_DATA_FILE_CONTENT) {
                    throw new InvalidParameterException(Messages.Exception.TOO_BIG_USER_DATA_FILE_CONTENT);
                }
            }
        }

        return activateOrder(order, userToken);
    }

    public ComputeInstance getCompute(String orderId, String userToken) throws FogbowException {
        return (ComputeInstance) getResourceInstance(orderId, userToken, ResourceType.COMPUTE);
    }

    public void deleteCompute(String computeId, String userToken) throws FogbowException {
        deleteOrder(computeId, userToken, ResourceType.COMPUTE);
    }

    public ComputeAllocation getComputeAllocation(String memberId, String cloudName, String userToken)
            throws FogbowException {
        return (ComputeAllocation) getUserAllocation(memberId, cloudName, userToken, ResourceType.COMPUTE);
    }

    public ComputeQuota getComputeQuota(String memberId, String cloudName, String userToken)
            throws FogbowException {
        return (ComputeQuota) getUserQuota(memberId, cloudName, userToken, ResourceType.COMPUTE);
    }

    public String createVolume(VolumeOrder volumeOrder, String userToken) throws FogbowException {
        return activateOrder(volumeOrder, userToken);
    }

    public VolumeInstance getVolume(String orderId, String userToken) throws FogbowException {
        return (VolumeInstance) getResourceInstance(orderId, userToken, ResourceType.VOLUME);
    }

    public void deleteVolume(String orderId, String userToken) throws FogbowException {
        deleteOrder(orderId, userToken, ResourceType.VOLUME);
    }

    public String createNetwork(NetworkOrder networkOrder, String userToken) throws FogbowException {
        return activateOrder(networkOrder, userToken);
    }

    public NetworkInstance getNetwork(String orderId, String userToken) throws FogbowException {
        return (NetworkInstance) getResourceInstance(orderId, userToken, ResourceType.NETWORK);
    }

    public void deleteNetwork(String orderId, String userToken) throws FogbowException {
        deleteOrder(orderId, userToken, ResourceType.NETWORK);
    }

    public String createAttachment(AttachmentOrder attachmentOrder, String userToken) throws FogbowException {
        String attachmentOrderId = activateOrder(attachmentOrder, userToken);
        return attachmentOrderId;
    }

    public AttachmentInstance getAttachment(String orderId, String userToken) throws FogbowException {
        return (AttachmentInstance) getResourceInstance(orderId, userToken, ResourceType.ATTACHMENT);
    }

    public void deleteAttachment(String orderId, String userToken) throws FogbowException {
        deleteOrder(orderId, userToken, ResourceType.ATTACHMENT);
    }

    public String createPublicIp(PublicIpOrder publicIpOrder, String userToken) throws FogbowException {
        String publicIpOrderId = activateOrder(publicIpOrder, userToken);
        return publicIpOrderId;
    }

    public PublicIpInstance getPublicIp(String publicIpOrderId, String userToken) throws FogbowException {
        return (PublicIpInstance) getResourceInstance(publicIpOrderId, userToken, ResourceType.PUBLIC_IP);
    }

    public void deletePublicIp(String publicIpOrderId, String userToken) throws FogbowException {
        deleteOrder(publicIpOrderId, userToken, ResourceType.PUBLIC_IP);
    }

    public List<InstanceStatus> getAllInstancesStatus(String userToken, ResourceType resourceType)
            throws FogbowException {
        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        this.authorizationPlugin.isAuthorized(requester, new RasOperation(Operation.GET_ALL, resourceType));
        return this.orderController.getInstancesStatus(requester, resourceType);
    }

    public Map<String, String> getAllImages(String memberId, String cloudName, String userToken)
            throws FogbowException {
        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        if (cloudName == null || cloudName.isEmpty()) cloudName = this.cloudListController.getDefaultCloudName();
        this.authorizationPlugin.isAuthorized(requester, new RasOperation(Operation.GET_ALL, ResourceType.IMAGE, cloudName));
        if (memberId == null) {
            memberId = this.memberId;
        }
        CloudConnector cloudConnector = CloudConnectorFactory.getInstance().getCloudConnector(memberId, cloudName);
        return cloudConnector.getAllImages(requester);
    }

    public Image getImage(String memberId, String cloudName, String imageId, String userToken)
            throws FogbowException {
        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        if (cloudName == null || cloudName.isEmpty()) cloudName = this.cloudListController.getDefaultCloudName();
        this.authorizationPlugin.isAuthorized(requester, new RasOperation(Operation.GET, ResourceType.IMAGE, cloudName));
        if (memberId == null) {
            memberId = this.memberId;
        }
        CloudConnector cloudConnector = CloudConnectorFactory.getInstance().getCloudConnector(memberId, cloudName);
        return cloudConnector.getImage(imageId, requester);
    }

    public String createSecurityRule(String orderId, SecurityRule securityRule,
                                     String userToken, ResourceType resourceTypeFromEndpoint)
                                     throws FogbowException {
        Order order = orderController.getOrder(orderId);
        if (order.getType() != resourceTypeFromEndpoint) {
            throw new InstanceNotFoundException();
        }

        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        this.authorizationPlugin.isAuthorized(requester, new RasOperation(Operation.CREATE,
                ResourceType.SECURITY_RULE, order.getCloudName()));
        return securityRuleController.createSecurityRule(order, securityRule, requester);
    }

    public List<SecurityRule> getAllSecurityRules(String orderId, String userToken,
                      ResourceType resourceTypeFromEndpoint) throws FogbowException {
        Order order = orderController.getOrder(orderId);
        if (order.getType() != resourceTypeFromEndpoint) {
            throw new InstanceNotFoundException();
        }
        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        this.authorizationPlugin.isAuthorized(requester, new RasOperation(Operation.GET_ALL,
                ResourceType.SECURITY_RULE, order.getCloudName()));
        return securityRuleController.getAllSecurityRules(order, requester);
    }

    public void deleteSecurityRule(String orderId, String securityRuleId, String userToken,
                               ResourceType resourceTypeFromEndpoint) throws FogbowException {
        Order order = orderController.getOrder(orderId);
        if (order.getType() != resourceTypeFromEndpoint) {
            throw new InstanceNotFoundException();
        }
        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        this.authorizationPlugin.isAuthorized(requester, new RasOperation(Operation.DELETE,
                ResourceType.SECURITY_RULE, order.getCloudName()));
        securityRuleController.deleteSecurityRule(order.getProvider(), order.getCloudName(), securityRuleId, requester);
    }

    public FogbowGenericResponse genericRequest(String cloudName, String memberId, String genericRequest,
                                                String userToken) throws FogbowException {
        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        this.authorizationPlugin.isAuthorized(requester, new RasOperation(Operation.GENERIC_REQUEST,
                ResourceType.GENERIC_RESOURCE, cloudName));
        CloudConnector cloudConnector = CloudConnectorFactory.getInstance().getCloudConnector(memberId, cloudName);
        return cloudConnector.genericRequest(genericRequest, requester);
    }

    private String activateOrder(Order order, String userToken) throws FogbowException {
        // Set order fields that have not been provided by the requester in the body of the HTTP request
        order.setRequester(this.memberId);
        if (order.getProvider() == null || order.getProvider().isEmpty()) order.setProvider(this.memberId);
        if (order.getCloudName() == null || order.getCloudName().isEmpty())
            order.setCloudName(this.cloudListController.getDefaultCloudName());
        // Check if the user is authentic
        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        order.setSystemUser(requester);
        // Check if the authenticated user is authorized to perform the requested operation
        this.authorizationPlugin.isAuthorized(requester, new RasOperation(Operation.CREATE,
                order.getType(), order.getCloudName()));
        // Set an initial state for the resource instance that is yet to be created in the cloud
        order.setCachedInstanceState(InstanceState.DISPATCHED);
        // Add order to the poll of active orders and to the OPEN linked list
        this.orderController.activateOrder(order);
        return order.getId();
    }

    private Instance getResourceInstance(String orderId, String userToken, ResourceType resourceType) throws FogbowException {
        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        Order order = this.orderController.getOrder(orderId);
        authorizeOrder(requester, order.getCloudName(), Operation.GET, resourceType, order);
        return this.orderController.getResourceInstance(order);
    }

    private void deleteOrder(String orderId, String userToken, ResourceType resourceType) throws FogbowException {
        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        Order order = this.orderController.getOrder(orderId);
        authorizeOrder(requester, order.getCloudName(), Operation.DELETE, resourceType, order);
        this.orderController.deleteOrder(order);
    }

    private Allocation getUserAllocation(String memberId, String cloudName, String userToken,
                                         ResourceType resourceType) throws FogbowException, UnexpectedException {
        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        if (cloudName == null || cloudName.isEmpty()) cloudName = this.cloudListController.getDefaultCloudName();
        this.authorizationPlugin.isAuthorized(requester, new RasOperation(Operation.GET_USER_ALLOCATION,
                resourceType, cloudName));
        return this.orderController.getUserAllocation(memberId, requester, resourceType);
    }

    private Quota getUserQuota(String memberId, String cloudName, String userToken,
                               ResourceType resourceType) throws FogbowException, UnexpectedException {
        SystemUser requester = AuthenticationUtil.authenticate(getAsPublicKey(), userToken);
        if (cloudName == null || cloudName.isEmpty()) cloudName = this.cloudListController.getDefaultCloudName();
        this.authorizationPlugin.isAuthorized(requester, new RasOperation(Operation.GET_USER_QUOTA,
                resourceType, cloudName));
        CloudConnector cloudConnector = CloudConnectorFactory.getInstance().getCloudConnector(memberId, cloudName);
        return cloudConnector.getUserQuota(requester, resourceType);
    }

	protected void authorizeOrder(SystemUser requester, String cloudName, Operation operation, ResourceType type,
                  Order order) throws UnexpectedException, UnauthorizedRequestException, InstanceNotFoundException {
		// Check if requested type matches order type
		if (!order.getType().equals(type))
			throw new InstanceNotFoundException(Messages.Exception.MISMATCHING_RESOURCE_TYPE);
		// Check whether requester owns order
		SystemUser orderOwner = order.getSystemUser();
		String ownerUserId = orderOwner.getId();
		String requestUserId = requester.getId();
		if (!ownerUserId.equals(requestUserId)) {
			throw new UnauthorizedRequestException(Messages.Exception.REQUESTER_DOES_NOT_OWN_REQUEST);
		}
		this.authorizationPlugin.isAuthorized(requester, new RasOperation(operation, type, cloudName));
	}

    public RSAPublicKey getAsPublicKey() throws FogbowException {
        if (this.asPublicKey == null) {
            this.asPublicKey = RasPublicKeysHolder.getInstance().getAsPublicKey();
        }
        return this.asPublicKey;
    }

    // Used for testing
    protected void setBuildNumber(String fileName) {
        Properties properties = PropertiesUtil.readProperties(fileName);
        this.buildNumber = properties.getProperty(ConfigurationPropertyKeys.BUILD_NUMBER_KEY,
                ConfigurationPropertyDefaults.BUILD_NUMBER);
    }
}
