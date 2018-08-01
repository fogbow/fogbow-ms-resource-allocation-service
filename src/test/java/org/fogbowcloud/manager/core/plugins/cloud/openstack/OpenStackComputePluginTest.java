package org.fogbowcloud.manager.core.plugins.cloud.openstack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.fogbowcloud.manager.core.HomeDir;
import org.fogbowcloud.manager.core.PropertiesHolder;
import org.fogbowcloud.manager.core.exceptions.FogbowManagerException;
import org.fogbowcloud.manager.core.exceptions.InvalidParameterException;
import org.fogbowcloud.manager.core.exceptions.NoAvailableResourcesException;
import org.fogbowcloud.manager.core.exceptions.UnauthenticatedUserException;
import org.fogbowcloud.manager.core.exceptions.UnauthorizedRequestException;
import org.fogbowcloud.manager.core.exceptions.UnavailableProviderException;
import org.fogbowcloud.manager.core.exceptions.UnexpectedException;
import org.fogbowcloud.manager.core.models.instances.ComputeInstance;
import org.fogbowcloud.manager.core.models.instances.InstanceState;
import org.fogbowcloud.manager.core.models.ResourceType;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.tokens.Token;
import org.fogbowcloud.manager.core.models.tokens.Token.User;
import org.fogbowcloud.manager.core.plugins.cloud.util.LaunchCommandGenerator;
import org.fogbowcloud.manager.util.connectivity.HttpRequestClientUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.google.gson.Gson;

public class OpenStackComputePluginTest {

	private OpenStackNovaV2ComputePlugin computePlugin;
	private Token localToken;
	private LaunchCommandGenerator launchCommandGeneratorMock;
	private HttpRequestClientUtil httpRequestClientUtilMock;
	private Properties propertiesMock;
	private PropertiesHolder propertiesHolderMock;
	private ArgumentCaptor<String> argString = ArgumentCaptor.forClass(String.class);
	private ArgumentCaptor<Token> argToken = ArgumentCaptor.forClass(Token.class);
	private ArgumentCaptor<JSONObject> argJson = ArgumentCaptor.forClass(JSONObject.class);
	private final String defaultNetworkId = "fake-default-network-id";
	private final String imageId = "image-id";
	private final String publicKey = "public-key";
	private final String idKeyName = "493315b3-dd01-4b38-974f-289570f8e7ee";
	private final String bestFlavorId = "best-flavor";
	private final int bestCpu = 2;
	private final int bestMemory = 1024;

	private final int bestDisk = 8;
	private final String privateNetworkId = "fake-private-network-id";
	private final String userData = "userDataFromLauchCommand";
	private final JSONObject rootKeypairJson = generateRootKeyPairJson(idKeyName, publicKey);
	private final List<String> networksId = new ArrayList<String>();
	private final List<String> responseNetworkIds = new ArrayList<String>(networksId);
	private final String idInstanceName = "12345678-dd01-4b38-974f-289570f8e7ee";
	private final String expectedInstanceId = "instance-id-00";
	private final String expectedInstanceIdJson = generateInstaceId(expectedInstanceId);
	private final String localIpAddress = "localIpAddress";
	private final String instanceId = "compute-instance-id";
	private final int vCPU = 10;
	private final int ram = 15;
	private final int disk = 20;
	private String computeEndpoint;
	private String flavorEndpoint;
	private String osKeyPairEndpoint;
	private String hostName = "hostName";
	private String flavorId = "flavorId";
	private final String computeNovaV2UrlKey = "compute-nova-v2-url-key";
	private String openstackStateActive = OpenStackStateMapper.ACTIVE_STATUS;
	
	@Before
    public void setUp() throws Exception {
        this.propertiesHolderMock = Mockito.mock(PropertiesHolder.class);
        this.propertiesMock = Mockito.mock(Properties.class);
        this.httpRequestClientUtilMock = Mockito.mock(HttpRequestClientUtil.class);
        this.launchCommandGeneratorMock = Mockito.mock(LaunchCommandGenerator.class);
        this.networksId.add(privateNetworkId);
        this.responseNetworkIds.add(defaultNetworkId);
        this.responseNetworkIds.add(privateNetworkId);

        String accessId = "accessID";
        String tenantId = "tenant-id";
        Map <String, String> attributes = new HashMap<String, String>();
        attributes.put(OpenStackNovaV2ComputePlugin.TENANT_ID, tenantId);
        User user = new User("iduser", "nameuser");
        Date expirationTime = new Date();
        this.localToken = new Token(accessId, user, expirationTime, attributes);
        
        HomeDir.getInstance().setPath("src/test/resources/private");
        
		this.computePlugin = Mockito.spy(new OpenStackNovaV2ComputePlugin(this.propertiesMock,
				this.launchCommandGeneratorMock, this.httpRequestClientUtilMock));
		this.osKeyPairEndpoint = computeNovaV2UrlKey + OpenStackNovaV2ComputePlugin.COMPUTE_V2_API_ENDPOINT
				+ this.localToken.getAttributes().get(OpenStackNovaV2ComputePlugin.TENANT_ID)
				+ OpenStackNovaV2ComputePlugin.SUFFIX_ENDPOINT_KEYPAIRS;
		this.computeEndpoint = computeNovaV2UrlKey + OpenStackNovaV2ComputePlugin.COMPUTE_V2_API_ENDPOINT
				+ this.localToken.getAttributes().get(OpenStackNovaV2ComputePlugin.TENANT_ID)
				+ OpenStackNovaV2ComputePlugin.SERVERS;
		this.flavorEndpoint = this.computeNovaV2UrlKey + OpenStackNovaV2ComputePlugin.COMPUTE_V2_API_ENDPOINT
				+ this.localToken.getAttributes().get(OpenStackNovaV2ComputePlugin.TENANT_ID)
				+ OpenStackNovaV2ComputePlugin.SUFFIX_ENDPOINT_FLAVORS;

		Mockito.when(this.propertiesMock.getProperty(OpenStackNovaV2ComputePlugin.COMPUTE_NOVAV2_URL_KEY))
				.thenReturn(this.computeNovaV2UrlKey);
		Mockito.when(this.propertiesMock.getProperty(OpenStackNovaV2ComputePlugin.DEFAULT_NETWORK_ID_KEY))
				.thenReturn(defaultNetworkId);
		Mockito.when(propertiesHolderMock.getProperties()).thenReturn(propertiesMock);
    }
    
    // test case: If a RequestInstance method works as expected
    @Test
    public void testRequestInstance() throws IOException, FogbowManagerException, UnexpectedException {
    	
    	// set up
		mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
		ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, networksId);
		JSONObject computeJson = generateJsonRequest(imageId, bestFlavorId, userData, idKeyName, responseNetworkIds,
				idInstanceName);
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
				this.argJson.capture())).thenReturn("");
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
		Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
		Mockito.doReturn(expectedInstanceIdJson).when(this.httpRequestClientUtilMock).doPostRequest(argString.capture(),
				argToken.capture(), argJson.capture());
    	
    	// exercise
    	String instanceId = this.computePlugin.requestInstance(computeOrder, this.localToken);
    	
    	// verify
    	Assert.assertEquals(this.argString.getAllValues().get(0), this.osKeyPairEndpoint);
    	Assert.assertEquals(this.argToken.getAllValues().get(0), this.localToken);
    	Assert.assertEquals(this.argJson.getAllValues().get(0).toString(), rootKeypairJson.toString());
    	
    	Assert.assertEquals(this.argString.getAllValues().get(1), computeEndpoint);
    	Assert.assertEquals(this.argToken.getAllValues().get(1), this.localToken);
    	Assert.assertEquals(this.argJson.getAllValues().get(1).toString(), computeJson.toString());
    	
    	Assert.assertEquals(expectedInstanceId, instanceId);
    }
    
    // test case: Check if a getInstance builds a compute instance from http response properly
    @Test
    public void testGetInstance() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	// set up
    	InstanceState fogbowState = OpenStackStateMapper.map(ResourceType.COMPUTE, openstackStateActive);
    	String newComputeEndpoint = this.computeEndpoint + "/" + instanceId;
    	String computeInstanceJson = generateComputeInstanceJson(instanceId, hostName, localIpAddress, flavorId, openstackStateActive);
    	ComputeInstance expectedComputeInstance = new ComputeInstance(instanceId, fogbowState, hostName, vCPU, ram, disk, localIpAddress);
    	
    	Mockito.when(this.httpRequestClientUtilMock.doGetRequest(newComputeEndpoint, this.localToken)).thenReturn(computeInstanceJson);
    	mockGetFlavorsRequest(flavorId, vCPU, ram, disk);
    	
    	// exercise
    	ComputeInstance pluginComputeInstance = this.computePlugin.getInstance(instanceId, this.localToken);
    	
    	// verify
    	Assert.assertEquals(expectedComputeInstance.getHostName(), pluginComputeInstance.getHostName());
    	Assert.assertEquals(expectedComputeInstance.getId(), pluginComputeInstance.getId());
    	Assert.assertEquals(expectedComputeInstance.getLocalIpAddress(), pluginComputeInstance.getLocalIpAddress());
    	Assert.assertEquals(expectedComputeInstance.getDisk(), pluginComputeInstance.getDisk());
    	Assert.assertEquals(expectedComputeInstance.getRam(), pluginComputeInstance.getRam());
    	Assert.assertEquals(expectedComputeInstance.getState(), pluginComputeInstance.getState());
    	Assert.assertEquals(expectedComputeInstance.getvCPU(), pluginComputeInstance.getvCPU());
    }
    
    // test case: If a DeleteInstance method works as expected
    @Test
    public void testDeleteInstance() throws HttpResponseException, FogbowManagerException, UnexpectedException { 
    	// set up
		String deleteEndpoint = this.computeEndpoint + "/" + instanceId;
		Mockito.doNothing().when(this.httpRequestClientUtilMock).doDeleteRequest(this.argString.capture(),
				this.argToken.capture());

    	// exercise
    	this.computePlugin.deleteInstance(instanceId, this.localToken);
    	
    	// verify
    	Assert.assertEquals(this.argString.getValue(), deleteEndpoint);
    	Assert.assertEquals(this.argToken.getValue(), this.localToken);
    }
    
    // test case: GetInstance should throw Unauthorized if a http request is Forbidden
    @Test (expected = UnauthorizedRequestException.class)
    public void testGetInstanceOnForbidden() throws FogbowManagerException, UnexpectedException, HttpResponseException {	
    	// set up
		String newComputeEndpoint = this.computeEndpoint + "/" + instanceId;
		Mockito.when(this.httpRequestClientUtilMock.doGetRequest(newComputeEndpoint, this.localToken))
				.thenThrow(new HttpResponseException(HttpStatus.SC_FORBIDDEN, ""));
    	
    	// exercise/verify
    	this.computePlugin.getInstance(instanceId, this.localToken);
    }
    
    // test case: DeleteInstance should return Unauthorized is a http request is Forbidden
    @Test (expected = UnauthorizedRequestException.class)
    public void testDeleteInstanceTestOnForbidden() throws HttpResponseException, FogbowManagerException, UnexpectedException { 
    	// set up
		String deleteEndpoint = this.computeEndpoint + "/" + instanceId;
		Mockito.doThrow(new HttpResponseException(HttpStatus.SC_FORBIDDEN, "")).when(this.httpRequestClientUtilMock)
				.doDeleteRequest(deleteEndpoint, this.localToken);
    	
    	// exercise
    	this.computePlugin.deleteInstance(instanceId, this.localToken);
    }
    
    // test case: Request Instance should throw Unauthenticated if a http request is Anauthorized
    @Test (expected = UnauthenticatedUserException.class)
    public void testRequestInstanceOnAnauthorizedComputePost() throws IOException, FogbowManagerException, UnexpectedException {
    	// set up
		ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, "", null, "",
				null);

		mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn("");
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(Mockito.any())).thenReturn("");
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(Mockito.any(), Mockito.any(), Mockito.any()))
				.thenThrow(new HttpResponseException(HttpStatus.SC_UNAUTHORIZED, ""));
    	
    	// exercise
    	this.computePlugin.requestInstance(computeOrder, this.localToken);
    }
    
    // test case: RequestInstance should still work even if there is no public key as parameter
    @Test
    public void testRequestInstanceWhenPublicKeyIsNull() throws IOException, FogbowManagerException, UnexpectedException {
    	// set up
    	String publicKey = null;
    	String idKeyName = null;
    	ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, networksId);
		JSONObject computeJson = generateJsonRequest(imageId, bestFlavorId, userData, idKeyName, responseNetworkIds,
				idInstanceName);
		
		mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
		Mockito.doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
				this.argJson.capture())).thenReturn(expectedInstanceIdJson);
    	
    	// exercise
    	String instanceId = this.computePlugin.requestInstance(computeOrder, this.localToken);
    	
    	// verify
    	Assert.assertEquals(this.argString.getValue(), computeEndpoint);
    	Assert.assertEquals(this.argToken.getValue(), this.localToken);
    	Assert.assertEquals(this.argJson.getValue().toString(), computeJson.toString());
    	Assert.assertEquals(expectedInstanceId, instanceId);
    }
    
    // test case: RequestInstance should throw InvalidParameter when KeyName post returns Bad Request
    @Test (expected = InvalidParameterException.class)
    public void testRequestInstanceOnBadRequestKeyNamePost() throws IOException, FogbowManagerException, UnexpectedException {
    	// set up
		ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, null);
    	
		mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(Mockito.any(), Mockito.any(), Mockito.any()))
				.thenThrow(new HttpResponseException(HttpStatus.SC_BAD_REQUEST, ""));
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
    	Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
    	
    	// exercise
    	this.computePlugin.requestInstance(computeOrder, this.localToken);
    }
    
    // test case: Request Instance should throw Unauthenticated when delete key is Anauthorized
    @Test (expected = UnauthenticatedUserException.class)
    public void testRequestInstanceWhenDeleteKeyUnauthorized() throws IOException, FogbowManagerException, UnexpectedException {
    	// set up
    	ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, null);
    	
		mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
				this.argJson.capture())).thenReturn("");
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
		Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(argString.capture(), argToken.capture(),
				argJson.capture())).thenReturn(expectedInstanceIdJson);
		Mockito.doThrow(new HttpResponseException(HttpStatus.SC_UNAUTHORIZED, "")).when(this.httpRequestClientUtilMock)
				.doDeleteRequest(Mockito.any(), Mockito.any());
    	
    	// exercise
    	this.computePlugin.requestInstance(computeOrder, this.localToken);
    }
    
    // test case: test if Hardware Requirements caching works as expected
    @Test
    public void testRequestInstanceHardwareRequirementsCaching() throws IOException, FogbowManagerException, UnexpectedException {
    	// set up
		ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, networksId);
		JSONObject computeJson = generateJsonRequest(imageId, bestFlavorId, userData, idKeyName, responseNetworkIds,
				idInstanceName);

		mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
				this.argJson.capture())).thenReturn("");
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
		Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
		Mockito.doReturn(expectedInstanceIdJson).when(this.httpRequestClientUtilMock).doPostRequest(argString.capture(),
				argToken.capture(), argJson.capture());

    	//exercise 1
    	String instanceId = this.computePlugin.requestInstance(computeOrder, this.localToken);
    	
    	//verify 1
    	Assert.assertEquals(this.argString.getAllValues().get(0), this.osKeyPairEndpoint);
    	Assert.assertEquals(this.argToken.getAllValues().get(0), this.localToken);
    	Assert.assertEquals(this.argJson.getAllValues().get(0).toString(), rootKeypairJson.toString());
    	
    	Assert.assertEquals(this.argString.getAllValues().get(1), computeEndpoint);
    	Assert.assertEquals(this.argToken.getAllValues().get(1), this.localToken);
    	Assert.assertEquals(this.argJson.getAllValues().get(1).toString(), computeJson.toString());
    	
    	Assert.assertEquals(expectedInstanceId, instanceId);
    	
    	// exercise 2
    	instanceId = this.computePlugin.requestInstance(computeOrder, this.localToken);
    	
    	// verify 2
    	Assert.assertEquals(this.argString.getAllValues().get(0), this.osKeyPairEndpoint);
    	Assert.assertEquals(this.argToken.getAllValues().get(0), this.localToken);
    	Assert.assertEquals(this.argJson.getAllValues().get(0).toString(), rootKeypairJson.toString());
    	
    	Assert.assertEquals(this.argString.getAllValues().get(1), computeEndpoint);
    	Assert.assertEquals(this.argToken.getAllValues().get(1), this.localToken);
    	Assert.assertEquals(this.argJson.getAllValues().get(1).toString(), computeJson.toString());
    	
    	Assert.assertEquals(expectedInstanceId, instanceId);
    }
    
    // test case: Get Instance should throw NoAvailableResources when compute flavor id from request is not stored locally so that
    // we can't get memory, cpu and disk information
    @Test (expected = NoAvailableResourcesException.class)
    public void testGetInstanceWhenThereIsNoFlavorIdOnGetFlavorById() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	// set up
		String newComputeEndpoint = this.computeEndpoint + "/" + instanceId;
		String computeInstanceJson = generateComputeInstanceJson(instanceId, hostName, localIpAddress, flavorId,
				openstackStateActive);
		Mockito.when(this.httpRequestClientUtilMock.doGetRequest(newComputeEndpoint, this.localToken))
				.thenReturn(computeInstanceJson);
		mockGetFlavorsRequest(flavorId + "wrong", vCPU, ram, disk);
    	
    	// exercise
    	this.computePlugin.getInstance(instanceId, this.localToken);
    }
    
    // test case: Get Instance should still work even if there is no address field on response
    @Test
    public void testGetInstanceWhenThereIsNoAddressFieldOnResponse() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	// set up
    	InstanceState fogbowState = OpenStackStateMapper.map(ResourceType.COMPUTE, openstackStateActive);
    	String localIpAddress = null;
    	String newComputeEndpoint = this.computeEndpoint + "/" + instanceId;
    	ComputeInstance expectedComputeInstance = new ComputeInstance(instanceId, fogbowState, hostName, vCPU, ram, disk, "");
    	String computeInstanceJson = generateComputeInstanceJsonWithoutAddressField(instanceId, hostName, localIpAddress, flavorId, openstackStateActive);
    	Mockito.when(this.httpRequestClientUtilMock.doGetRequest(newComputeEndpoint, this.localToken)).thenReturn(computeInstanceJson);
    	mockGetFlavorsRequest(flavorId, vCPU, ram, disk);

    	// exercise
    	ComputeInstance pluginComputeInstance = this.computePlugin.getInstance(instanceId, this.localToken);
    	
    	// verify
    	Assert.assertEquals(expectedComputeInstance.getHostName(), pluginComputeInstance.getHostName());
    	Assert.assertEquals(expectedComputeInstance.getId(), pluginComputeInstance.getId());
    	Assert.assertEquals(expectedComputeInstance.getLocalIpAddress(), pluginComputeInstance.getLocalIpAddress());
    	Assert.assertEquals(expectedComputeInstance.getDisk(), pluginComputeInstance.getDisk());
    	Assert.assertEquals(expectedComputeInstance.getRam(), pluginComputeInstance.getRam());
    	Assert.assertEquals(expectedComputeInstance.getState(), pluginComputeInstance.getState());
    	Assert.assertEquals(expectedComputeInstance.getvCPU(), pluginComputeInstance.getvCPU());
    }
    
    // test case: Get Instance should still work even if there is no provider network field on response
    @Test
    public void testGetInstanceWhenThereIsNoProviderNetworkFieldOnResponse() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	// set up
    	InstanceState fogbowState = OpenStackStateMapper.map(ResourceType.COMPUTE, openstackStateActive);
    	String newComputeEndpoint = this.computeEndpoint + "/" + instanceId;
    	String computeInstanceJson = generateComputeInstanceJsonWithoutProviderNetworkField(instanceId, hostName, localIpAddress, flavorId, openstackStateActive);
    	ComputeInstance expectedComputeInstance = new ComputeInstance(instanceId, fogbowState, hostName, vCPU, ram, disk, "");
    	
    	Mockito.when(this.httpRequestClientUtilMock.doGetRequest(newComputeEndpoint, this.localToken)).thenReturn(computeInstanceJson);
    	mockGetFlavorsRequest(flavorId, vCPU, ram, disk);
    	
    	// exercise
    	ComputeInstance pluginComputeInstance = this.computePlugin.getInstance(instanceId, this.localToken);
    	
    	// verify
    	Assert.assertEquals(expectedComputeInstance.getHostName(), pluginComputeInstance.getHostName());
    	Assert.assertEquals(expectedComputeInstance.getId(), pluginComputeInstance.getId());
    	Assert.assertEquals(expectedComputeInstance.getLocalIpAddress(), pluginComputeInstance.getLocalIpAddress());
    	Assert.assertEquals(expectedComputeInstance.getDisk(), pluginComputeInstance.getDisk());
    	Assert.assertEquals(expectedComputeInstance.getRam(), pluginComputeInstance.getRam());
    	Assert.assertEquals(expectedComputeInstance.getState(), pluginComputeInstance.getState());
    	Assert.assertEquals(expectedComputeInstance.getvCPU(), pluginComputeInstance.getvCPU());
    }
    
    // test case: Request Instance should throw NoAvailableResources when there is no cpu that meets the criteria
    @Test (expected = NoAvailableResourcesException.class)
    public void testRequestInstanceWhenThereIsNoFlavorAvailableForCPU() throws IOException, FogbowManagerException, UnexpectedException {
    	// set up
    	int worst = -1;
		ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, null);
    	
		mockGetFlavorsRequest(bestFlavorId, bestCpu + worst, bestMemory, bestDisk);
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
				this.argJson.capture())).thenReturn("");
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
		Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
		Mockito.doReturn(expectedInstanceIdJson).when(this.httpRequestClientUtilMock).doPostRequest(argString.capture(),
				argToken.capture(), argJson.capture());
    	
    	// exercise
    	this.computePlugin.requestInstance(computeOrder, this.localToken);
    }
    
    // test case: Request Instance should throw NoAvailableResources when there is no memory that meets the criteria
    @Test (expected = NoAvailableResourcesException.class)
    public void testRequestInstanceWhenThereIsNoFlavorAvailableForMemory() throws IOException, FogbowManagerException, UnexpectedException {
    	// set up
    	int worst = -1;
		ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, null);
		
		mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory + worst, bestDisk);
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
				this.argJson.capture())).thenReturn("");
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
		Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
		Mockito.doReturn(expectedInstanceIdJson).when(this.httpRequestClientUtilMock).doPostRequest(argString.capture(),
				argToken.capture(), argJson.capture());
    	
    	// exercise
    	this.computePlugin.requestInstance(computeOrder, this.localToken);
    }
    
    // test case: Request Instance should throw NoAvailableResources when there is no disk that meets the criteria
    @Test (expected = NoAvailableResourcesException.class)
    public void testRequestInstanceWhenThereIsNoFlavorAvailableForDisk() throws IOException, FogbowManagerException, UnexpectedException {
    	// set up
    	int worst = -1;
		ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, null);
		
		mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk + worst);
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
				this.argJson.capture())).thenReturn("");
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
		Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
		Mockito.doReturn(expectedInstanceIdJson).when(this.httpRequestClientUtilMock).doPostRequest(argString.capture(),
				argToken.capture(), argJson.capture());

    	// exercise
    	this.computePlugin.requestInstance(computeOrder, this.localToken);
    }
    
    // test case: getTenantID should throw InvalidParameter when there is no tenant id(null)
    @Test (expected = InvalidParameterException.class)
    public void testRequestInstanceWhenThereIsNoTenantId() throws IOException, FogbowManagerException, UnexpectedException {
    	// set up
    	Map<String , String> attributes = new HashMap<String, String>();
    	attributes.put(OpenStackNovaV2ComputePlugin.TENANT_ID, null);
    	this.localToken.setAttributes(attributes);
		ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, null);
    	
		mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
				this.argJson.capture())).thenReturn("");
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
		Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
		Mockito.doReturn(expectedInstanceIdJson).when(this.httpRequestClientUtilMock).doPostRequest(argString.capture(),
				argToken.capture(), argJson.capture());
    	
    	// exercise
    	this.computePlugin.requestInstance(computeOrder, this.localToken);
    }
    
    // test case: Request Instance should still work even if there is no user data (null)
    @Test
    public void testRequestInstanceWhenThereIsNoUserData() throws IOException, FogbowManagerException, UnexpectedException {
    	// set up
		ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, networksId);
		mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
				this.argJson.capture())).thenReturn("");
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
		Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
		JSONObject computeJson = generateJsonRequest(imageId, bestFlavorId, userData, idKeyName, responseNetworkIds,
				idInstanceName);
		Mockito.doReturn(expectedInstanceIdJson).when(this.httpRequestClientUtilMock).doPostRequest(argString.capture(),
				argToken.capture(), argJson.capture());
    	
    	// exercise
    	String instanceId = this.computePlugin.requestInstance(computeOrder, this.localToken);
    	
    	// verify
    	Assert.assertEquals(this.argString.getAllValues().get(0), this.osKeyPairEndpoint);
    	Assert.assertEquals(this.argToken.getAllValues().get(0), this.localToken);
    	Assert.assertEquals(this.argJson.getAllValues().get(0).toString(), rootKeypairJson.toString());
    	
    	Assert.assertEquals(this.argString.getAllValues().get(1), computeEndpoint);
    	Assert.assertEquals(this.argToken.getAllValues().get(1), this.localToken);
    	Assert.assertEquals(this.argJson.getAllValues().get(1).toString(), computeJson.toString());
    	
    	Assert.assertEquals(expectedInstanceId, instanceId);
    }
    
    // test case: Compute networksId should always contain a default network id even if there is no network id in a compute order
    @Test
    public void testRequestInstanceWhenThereIsNoNetworkId() throws IOException, FogbowManagerException, UnexpectedException {
    	// set up
		List<String> networksId = null;
		ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, networksId);
		responseNetworkIds.remove(this.privateNetworkId);

		JSONObject computeJson = generateJsonRequest(imageId, bestFlavorId, userData, idKeyName, responseNetworkIds,
				idInstanceName);

		mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
				this.argJson.capture())).thenReturn("");
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
		Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
		Mockito.doReturn(expectedInstanceIdJson).when(this.httpRequestClientUtilMock).doPostRequest(argString.capture(),
				argToken.capture(), argJson.capture());
    	
    	// exercise
    	String instanceId = this.computePlugin.requestInstance(computeOrder, this.localToken);
    	
    	// verify
    	Assert.assertEquals(this.argString.getAllValues().get(0), this.osKeyPairEndpoint);
    	Assert.assertEquals(this.argToken.getAllValues().get(0), this.localToken);
    	Assert.assertEquals(this.argJson.getAllValues().get(0).toString(), rootKeypairJson.toString());
    	
    	Assert.assertEquals(this.argString.getAllValues().get(1), computeEndpoint);
    	Assert.assertEquals(this.argToken.getAllValues().get(1), this.localToken);
    	Assert.assertEquals(this.argJson.getAllValues().get(1).toString(), computeJson.toString());
    	
    	Assert.assertEquals(expectedInstanceId, instanceId);
    }
    
    // test case: Request Instance should throw InvalidParameter if the Get Request on updateFlavors returns Bad Request
    // while getting flavors id
    @Test (expected = InvalidParameterException.class)
    public void testRequestInstanceWhenFlavorsIdRequestException() throws HttpResponseException, FogbowManagerException, UnexpectedException {
		// set up
		ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, networksId);
		Mockito.when(this.httpRequestClientUtilMock.doGetRequest(Mockito.any(), Mockito.any()))
				.thenThrow(new HttpResponseException(HttpStatus.SC_BAD_REQUEST, ""));
		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
				this.argJson.capture())).thenReturn("");
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
		Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
		Mockito.doReturn(expectedInstanceIdJson).when(this.httpRequestClientUtilMock).doPostRequest(argString.capture(),
				argToken.capture(), argJson.capture());
    	
    	// exercise
    	this.computePlugin.requestInstance(computeOrder, this.localToken);
    }
    
    // test case: Request Instance should throw InvalidParameter if the Get Request on updateFlavors returns Bad Request
    // while getting flavor specification
    @Test (expected = InvalidParameterException.class)
    public void testRequestInstanceWhenSpecificFlavorRequestException() throws IOException, FogbowManagerException, UnexpectedException {
		// set up
		String newEndpoint = flavorEndpoint + "/" + bestFlavorId;
		ComputeOrder computeOrder = new ComputeOrder(null, null, null, bestCpu, bestMemory, bestDisk, imageId, null,
				publicKey, networksId);
		mockGetFlavorsRequest(bestFlavorId, bestCpu, bestMemory, bestDisk);
		Mockito.doThrow(new HttpResponseException(HttpStatus.SC_BAD_REQUEST, "")).when(this.httpRequestClientUtilMock)
				.doGetRequest(newEndpoint, this.localToken);

		Mockito.when(this.httpRequestClientUtilMock.doPostRequest(this.argString.capture(), this.argToken.capture(),
				this.argJson.capture())).thenReturn("");
		Mockito.when(this.launchCommandGeneratorMock.createLaunchCommand(computeOrder)).thenReturn(userData);
		Mockito.doReturn(idKeyName).doReturn(idInstanceName).when(this.computePlugin).getRandomUUID();
		Mockito.doReturn(expectedInstanceIdJson).when(this.httpRequestClientUtilMock).doPostRequest(argString.capture(),
				argToken.capture(), argJson.capture());
    	
    	// exercise
    	this.computePlugin.requestInstance(computeOrder, this.localToken);
    }
    
    /*
     * This method mocks the behavior of a http flavor request by mocking GET"/flavors" and GET"/flavors/id" and adds
     * bestFlavorId as a flavor from this response in addition to other flavors. Besides that, bestFlavorId will be
     * the flavor with best Vcpu, memory and disk from this response. 
     */
    private void mockGetFlavorsRequest(String bestFlavorId, int bestVcpu, int bestMemory, int bestDisk) throws HttpResponseException, UnavailableProviderException {
    	
    	int qtdFlavors = 100;
    	
    	Mockito.when(this.httpRequestClientUtilMock.doGetRequest(flavorEndpoint, this.localToken))
    			.thenReturn(generateJsonFlavors(qtdFlavors, bestFlavorId));
    	
    	for (int i = 0; i < qtdFlavors - 1; i++) {
    		String flavorId = "flavor" + Integer.toString(i);
    		String newEndpoint = flavorEndpoint + "/" + flavorId;
    		String flavorJson = generateJsonFlavor(
    				flavorId, 
    				"nameflavor" + Integer.toString(i), 
    				Integer.toString(Math.max(1, bestVcpu - 1 - i)), 
    				Integer.toString(Math.max(1, bestMemory - 1 - i)), 
    				Integer.toString(Math.max(1, bestDisk - 1 - i)));
    		Mockito.when(this.httpRequestClientUtilMock.doGetRequest(newEndpoint, this.localToken))
    				.thenReturn(flavorJson);
    	}
    	
    	String newEndpoint = flavorEndpoint + "/" + bestFlavorId;
		String flavorJson = generateJsonFlavor(
				bestFlavorId, 
				"nameflavor" + bestFlavorId, 
				Integer.toString(bestVcpu), 
				Integer.toString(bestMemory), 
				Integer.toString(bestDisk));
		
		Mockito.when(this.httpRequestClientUtilMock.doGetRequest(newEndpoint, this.localToken))
				.thenReturn(flavorJson);
    }
    
    //mocks GET"/flavors"
    private String generateJsonFlavors(int qtd, String bestFlavorId) {
    	Map <String, Object> jsonFlavorsMap = new HashMap<String, Object>();
    	List<Map <String, String>> jsonArrayFlavors = new ArrayList<Map<String, String>>();
    	for (int i = 0; i < qtd - 1; i++) {
    		Map <String, String> flavor = new HashMap<String, String>();
    		flavor.put(OpenStackNovaV2ComputePlugin.ID_JSON_FIELD, "flavor" + Integer.toString(i));
    		jsonArrayFlavors.add(flavor);
    	}
		
    	Map <String, String> flavor = new HashMap<String, String>();
		flavor.put(OpenStackNovaV2ComputePlugin.ID_JSON_FIELD, bestFlavorId);
		jsonArrayFlavors.add(flavor);
		
    	jsonFlavorsMap.put(OpenStackNovaV2ComputePlugin.FLAVOR_JSON_KEY, jsonArrayFlavors);
    	Gson gson = new Gson();
    	return gson.toJson(jsonFlavorsMap);
    }
    
    private String generateJsonFlavor(String id, String name, String vcpu, String memory, String disk) {
    	Map<String, Object> flavorMap = new HashMap<String, Object>();
    	Map<String, String> flavorAttributes = new HashMap<String, String>();
    	flavorAttributes.put(OpenStackNovaV2ComputePlugin.ID_JSON_FIELD, id);
    	flavorAttributes.put(OpenStackNovaV2ComputePlugin.NAME_JSON_FIELD, name);
    	flavorAttributes.put(OpenStackNovaV2ComputePlugin.DISK_JSON_FIELD, disk);
    	flavorAttributes.put(OpenStackNovaV2ComputePlugin.MEMORY_JSON_FIELD, memory);
    	flavorAttributes.put(OpenStackNovaV2ComputePlugin.VCPU_JSON_FIELD, vcpu);
    	flavorMap.put(OpenStackNovaV2ComputePlugin.FLAVOR_JSON_OBJECT, flavorAttributes);
    	return new Gson().toJson(flavorMap);
    }
    
    private String generateInstaceId(String id) {
    	Map <String, String> instanceMap = new HashMap<String, String>();
    	instanceMap.put(OpenStackNovaV2ComputePlugin.ID_JSON_FIELD, id);
    	Map<String, Object> root = new HashMap<String, Object>();
    	root.put(OpenStackNovaV2ComputePlugin.SERVER_JSON_FIELD, instanceMap);
    	return new Gson().toJson(root);
    }
    
    private JSONObject generateRootKeyPairJson(String keyName, String publicKey) {
    	JSONObject keypair = new JSONObject();
    	keypair.put(OpenStackNovaV2ComputePlugin.NAME_JSON_FIELD, keyName);
    	keypair.put(OpenStackNovaV2ComputePlugin.PUBLIC_KEY_JSON_FIELD, publicKey);
    	JSONObject root = new JSONObject();
    	root.put(OpenStackNovaV2ComputePlugin.KEYPAIR_JSON_FIELD, keypair);
    	return root;
    }
    
    private JSONObject generateJsonRequest(String imageId, String flavorId, String userData, String keyName, List<String> networksId, String randomUUID) {
    	JSONObject server = new JSONObject();
    	server.put(OpenStackNovaV2ComputePlugin.NAME_JSON_FIELD, OpenStackNovaV2ComputePlugin.FOGBOW_INSTANCE_NAME + randomUUID);
    	server.put(OpenStackNovaV2ComputePlugin.IMAGE_JSON_FIELD, imageId);
    	server.put(OpenStackNovaV2ComputePlugin.FLAVOR_REF_JSON_FIELD, flavorId);
    	server.put(OpenStackNovaV2ComputePlugin.USER_DATA_JSON_FIELD, userData);
    	
    	JSONArray networks = new JSONArray();
    	
        for (String id : networksId) {
            JSONObject netId = new JSONObject();
            netId.put(OpenStackNovaV2ComputePlugin.UUID_JSON_FIELD, id);
            networks.put(netId);
        }
        server.put(OpenStackNovaV2ComputePlugin.NETWORK_JSON_FIELD, networks);

        if (networksId.size() > 1) {
	        JSONArray securityGroups = new JSONArray();
	        JSONObject securityGroup = new JSONObject();
	        String securityGroupName = OpenStackV2NetworkPlugin.SECURITY_GROUP_PREFIX + "-" + this.privateNetworkId;
	        securityGroup.put(OpenStackNovaV2ComputePlugin.NAME_JSON_FIELD, securityGroupName);
	        securityGroups.put(securityGroup);
	        server.put(OpenStackNovaV2ComputePlugin.SECURITY_JSON_FIELD, securityGroups);
        }

        if (keyName != null) {
        	server.put(OpenStackNovaV2ComputePlugin.KEY_JSON_FIELD, keyName);
        }
        
    	JSONObject root = new JSONObject();
    	root.put(OpenStackNovaV2ComputePlugin.SERVER_JSON_FIELD, server);
    	return root;
    }
    

    private String generateComputeInstanceJson(String instanceId, String hostName, String localIpAddress, String flavorId, String status) {
    	Map<String, Object> root = new HashMap<String, Object>();
    	Map<String, Object> computeInstance = new HashMap<String, Object>();
    	computeInstance.put(OpenStackNovaV2ComputePlugin.ID_JSON_FIELD, instanceId);
    	computeInstance.put(OpenStackNovaV2ComputePlugin.NAME_JSON_FIELD, hostName);
    	
    	Map<String, Object> addressField = new HashMap<String, Object>();
    	List <Object> providerNetworkArray = new ArrayList<Object>();
    	Map<String, String> providerNetwork = new HashMap<String, String>();
    	providerNetwork.put(OpenStackNovaV2ComputePlugin.ADDR_FIELD, localIpAddress);
    	providerNetworkArray.add(providerNetwork);
    	addressField.put(OpenStackNovaV2ComputePlugin.PROVIDER_NETWORK_FIELD, providerNetworkArray);
    	
    	Map<String, Object> flavor = new HashMap<String, Object>();
    	flavor.put(OpenStackNovaV2ComputePlugin.FLAVOR_ID_JSON_FIELD, flavorId);
    	
    	computeInstance.put(OpenStackNovaV2ComputePlugin.FLAVOR_JSON_FIELD, flavor);
    	computeInstance.put(OpenStackNovaV2ComputePlugin.ADDRESS_FIELD, addressField);
    	computeInstance.put(OpenStackNovaV2ComputePlugin.STATUS_JSON_FIELD, status);
    	
    	root.put(OpenStackNovaV2ComputePlugin.SERVER_JSON_FIELD, computeInstance);
    	return new Gson().toJson(root);
    }
    
    private String generateComputeInstanceJsonWithoutAddressField(String instanceId, String hostName, String localIpAddress, String flavorId, String status) {
    	Map<String, Object> root = new HashMap<String, Object>();
    	Map<String, Object> computeInstance = new HashMap<String, Object>();
    	computeInstance.put(OpenStackNovaV2ComputePlugin.ID_JSON_FIELD, instanceId);
    	computeInstance.put(OpenStackNovaV2ComputePlugin.NAME_JSON_FIELD, hostName);
    	
    	List <Object> providerNetworkArray = new ArrayList<Object>();
    	Map<String, String> providerNetwork = new HashMap<String, String>();
    	providerNetwork.put(OpenStackNovaV2ComputePlugin.ADDR_FIELD, localIpAddress);
    	providerNetworkArray.add(providerNetwork);
    	
    	Map<String, Object> flavor = new HashMap<String, Object>();
    	flavor.put(OpenStackNovaV2ComputePlugin.FLAVOR_ID_JSON_FIELD, flavorId);
    	
    	computeInstance.put(OpenStackNovaV2ComputePlugin.FLAVOR_JSON_FIELD, flavor);
    	computeInstance.put(OpenStackNovaV2ComputePlugin.STATUS_JSON_FIELD, status);
    	
    	root.put(OpenStackNovaV2ComputePlugin.SERVER_JSON_FIELD, computeInstance);
    	return new Gson().toJson(root);
    }
    
    private String generateComputeInstanceJsonWithoutProviderNetworkField(String instanceId, String hostName, String localIpAddress, String flavorId, String status) {
    	Map<String, Object> root = new HashMap<String, Object>();
    	Map<String, Object> computeInstance = new HashMap<String, Object>();
    	computeInstance.put(OpenStackNovaV2ComputePlugin.ID_JSON_FIELD, instanceId);
    	computeInstance.put(OpenStackNovaV2ComputePlugin.NAME_JSON_FIELD, hostName);
    	
    	Map<String, Object> addressField = new HashMap<String, Object>();

    	Map<String, Object> flavor = new HashMap<String, Object>();
    	flavor.put(OpenStackNovaV2ComputePlugin.FLAVOR_ID_JSON_FIELD, flavorId);
    	
    	computeInstance.put(OpenStackNovaV2ComputePlugin.FLAVOR_JSON_FIELD, flavor);
    	computeInstance.put(OpenStackNovaV2ComputePlugin.ADDRESS_FIELD, addressField);
    	computeInstance.put(OpenStackNovaV2ComputePlugin.STATUS_JSON_FIELD, status);
    	
    	root.put(OpenStackNovaV2ComputePlugin.SERVER_JSON_FIELD, computeInstance);
    	return new Gson().toJson(root);
    }
}
