package cloud.fogbow.ras.core.plugins.interoperability.openstack.network.v2;

import cloud.fogbow.common.constants.HttpMethod;
import cloud.fogbow.common.constants.OpenStackConstants;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.CloudUser;
import cloud.fogbow.common.util.HomeDir;
import cloud.fogbow.common.util.connectivity.HttpResponse;
import cloud.fogbow.ras.constants.SystemConstants;
import cloud.fogbow.ras.core.PropertiesHolder;
import cloud.fogbow.ras.core.datastore.AuditService;
import cloud.fogbow.ras.core.datastore.DatabaseManager;
import cloud.fogbow.ras.core.models.NetworkAllocationMode;
import cloud.fogbow.ras.api.http.response.InstanceState;
import cloud.fogbow.ras.api.http.response.NetworkInstance;
import cloud.fogbow.ras.core.models.orders.NetworkOrder;
import cloud.fogbow.common.util.cloud.openstack.OpenStackHttpClient;
import cloud.fogbow.ras.core.plugins.interoperability.openstack.OpenStackStateMapper;
import cloud.fogbow.common.models.OpenStackV3User;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

public class OpenStackNetworkPluginTest {

    private static final String NETWORK_NEUTRONV2_URL_KEY = "openstack_neutron_v2_url";

    private static final String UTF_8 = "UTF-8";
    private static final String DEFAULT_GATEWAY_INFO = "000000-gateway_info";
    private static final String DEFAULT_PROJECT_ID = "PROJECT_ID";
    private static final String DEFAULT_NETWORK_URL = "http://localhost:0000";
    private static final String SECURITY_GROUP_ID = "fake-sg-id";
    private static final String NETWORK_ID = "networkId";

    private static final String FAKE_TOKEN_VALUE = "fake-token-value";
    private static final String FAKE_USER_ID = "fake-user-id";
    private static final String FAKE_NAME = "fake-name";
    private static final String FAKE_PROJECT_ID = "fake-project-id";

    private static final String SUFFIX_ENDPOINT_DELETE_NETWORK = OpenStackNetworkPlugin.SUFFIX_ENDPOINT_NETWORK +
            File.separator + NETWORK_ID;

    private static final String SUFFIX_ENDPOINT_DELETE_SECURITY_GROUP = OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP +
            File.separator + SECURITY_GROUP_ID;
    private static final String SUFFIX_ENDPOINT_GET_SECURITY_GROUP = OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP + "?" +
            OpenStackNetworkPlugin.QUERY_NAME + "=" + OpenStackNetworkPlugin.SECURITY_GROUP_PREFIX + NETWORK_ID;

    private OpenStackNetworkPlugin openStackNetworkPlugin;
    private OpenStackV3User openStackV3Token;
    private Properties properties;
    private OpenStackHttpClient openStackHttpClient;

    @Before
    public void setUp() throws InvalidParameterException, UnexpectedException {
        PropertiesHolder propertiesHolder = PropertiesHolder.getInstance();
        this.properties = propertiesHolder.getProperties();
        this.properties.put(OpenStackNetworkPlugin.KEY_EXTERNAL_GATEWAY_INFO, DEFAULT_GATEWAY_INFO);
        this.properties.put(NETWORK_NEUTRONV2_URL_KEY, DEFAULT_NETWORK_URL);
        String cloudConfPath = HomeDir.getPath() + SystemConstants.CLOUDS_CONFIGURATION_DIRECTORY_NAME + File.separator
                + "default" + File.separator + SystemConstants.CLOUD_SPECIFICITY_CONF_FILE_NAME;
        this.openStackNetworkPlugin = Mockito.spy(new OpenStackNetworkPlugin(cloudConfPath));

        this.openStackHttpClient = Mockito.spy(new OpenStackHttpClient());
        this.openStackNetworkPlugin.setClient(this.openStackHttpClient);
        this.openStackV3Token = new OpenStackV3User(FAKE_USER_ID, FAKE_NAME, FAKE_TOKEN_VALUE, FAKE_PROJECT_ID);
    }

    @After
    public void validate() {
        Mockito.validateMockitoUsage();
    }

    //requestInstance tests

    //test case: The http client must make only 5 requests
    @Test
    public void testNumberOfRequestsInSucceededRequestInstance() throws IOException, FogbowException {
        //set up
        // post network
        String createNetworkResponse = new CreateNetworkResponse(new CreateNetworkResponse.Network(NETWORK_ID)).toJson();
        Mockito.doReturn(createNetworkResponse).when(this.openStackHttpClient)
                .doPostRequest(Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_NETWORK),
                        Mockito.anyString(), Mockito.eq(this.openStackV3Token));
        //post subnet
        Mockito.doReturn("").when(this.openStackHttpClient)
                .doPostRequest(Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SUBNET),
                        Mockito.anyString(), Mockito.eq(this.openStackV3Token));

        //post security group
        CreateSecurityGroupResponse.SecurityGroup securityGroup = new CreateSecurityGroupResponse.SecurityGroup(SECURITY_GROUP_ID);
        CreateSecurityGroupResponse createSecurityGroupResponse = new CreateSecurityGroupResponse(securityGroup);
        Mockito.doReturn(createSecurityGroupResponse.toJson()).when(this.openStackHttpClient)
                .doPostRequest(Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP),
                        Mockito.anyString(), Mockito.eq(this.openStackV3Token));
        //post ssh and icmp rule
        Mockito.doReturn("").when(this.openStackHttpClient)
                .doPostRequest(Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP_RULES),
                        Mockito.anyString(), Mockito.eq(this.openStackV3Token));

        Mockito.doReturn(null).when(this.openStackNetworkPlugin).getNetworkIdFromJson(Mockito.anyString());
        NetworkOrder order = createEmptyOrder();

        //exercise
        this.openStackNetworkPlugin.requestInstance(order, this.openStackV3Token);

        //verify
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doPostRequest(
                Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_NETWORK), Mockito.anyString(), Mockito.eq(this.openStackV3Token)
        );
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doPostRequest(
                Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SUBNET), Mockito.anyString(), Mockito.eq(this.openStackV3Token)
        );
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doPostRequest(
                Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP), Mockito.anyString(), Mockito.eq(this.openStackV3Token)
        );
        Mockito.verify(this.openStackHttpClient, Mockito.times(3)).doPostRequest(
                Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP_RULES), Mockito.anyString(), Mockito.eq(this.openStackV3Token)
        );
    }

    //test case: Tests if an exception will be thrown in case that openstack raise an error in network request.
    @Test
    public void testRequestInstancePostNetworkError() throws IOException, FogbowException {
        //set up
        HttpResponse postSubnetResponse = new HttpResponse("", HttpStatus.SC_BAD_REQUEST, null);
        Mockito.doReturn(postSubnetResponse).when(this.openStackHttpClient).
                doGenericRequest(Mockito.any(HttpMethod.class), Mockito.anyString(), Mockito.any(HashMap.class),
                        Mockito.any(HashMap.class), Mockito.any(CloudUser.class));

//        Mockito.when(this.client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(httpResponsePostNetwork);
        NetworkOrder order = createEmptyOrder();

        //exercise
        try {
            this.openStackNetworkPlugin.requestInstance(order, this.openStackV3Token);
            Assert.fail();
        } catch (FogbowException e) {
            // Throws an exception, as expected
        }

        //verify
//        Mockito.verify(this.client, Mockito.times(1)).execute(Mockito.any(HttpUriRequest.class));
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).
                doGenericRequest(Mockito.any(HttpMethod.class), Mockito.anyString(), Mockito.any(HashMap.class),
                        Mockito.any(HashMap.class), Mockito.any(CloudUser.class));
    }

    //test case: Tests if an exception will be thrown in case that openstack raise an error when requesting for a new subnet.
    @Test
    public void testRequestInstancePostSubnetError() throws IOException, FogbowException {
        // set up
        String createNetworkResponse = new CreateNetworkResponse(new CreateNetworkResponse.Network(NETWORK_ID)).toJson();

        HttpResponse postNetworkResponse = new HttpResponse(createNetworkResponse, HttpStatus.SC_OK, null);
        HttpResponse postSubnetResponse = new HttpResponse("", HttpStatus.SC_BAD_REQUEST, null);
        Mockito.doReturn(postNetworkResponse).doReturn(postSubnetResponse).when(this.openStackHttpClient).
                doGenericRequest(Mockito.any(HttpMethod.class), Mockito.anyString(), Mockito.any(HashMap.class),
                        Mockito.any(HashMap.class), Mockito.any(CloudUser.class));

//        Mockito.when(this.client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(httpResponsePostNetwork,
//                httpResponsePostSubnet, httpResponseRemoveNetwork);
        NetworkOrder order = createEmptyOrder();

        try {
            // exercise
            this.openStackNetworkPlugin.requestInstance(order, this.openStackV3Token);
            Assert.fail();
        } catch (FogbowException e) {

        }

        // verify
//        Mockito.verify(this.client, Mockito.times(3)).execute(Mockito.any(HttpUriRequest.class));
        Mockito.verify(this.openStackHttpClient, Mockito.times(3)).
                doGenericRequest(Mockito.any(HttpMethod.class), Mockito.anyString(), Mockito.any(HashMap.class),
                        Mockito.any(HashMap.class), Mockito.any(CloudUser.class));
    }

    //test case: Tests the case that security group raise an exception. This implies that network will be removed.
    @Test
    public void testErrorInPostSecurityGroup() throws IOException, FogbowException {
        //set up
        //post network
        CreateNetworkResponse createNetworkResponse = new CreateNetworkResponse(new CreateNetworkResponse.Network(NETWORK_ID));
        Mockito.doReturn(createNetworkResponse.toJson()).when(this.openStackHttpClient)
                .doPostRequest(Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_NETWORK),
                        Mockito.anyString(), Mockito.eq(this.openStackV3Token));
        //post subnet
        Mockito.doReturn("").when(this.openStackHttpClient)
                .doPostRequest(Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SUBNET),
                        Mockito.anyString(), Mockito.eq(this.openStackV3Token));
        //post security group
        Mockito.doThrow(new HttpResponseException(HttpStatus.SC_FORBIDDEN, "")).when(this.openStackHttpClient)
                .doPostRequest(Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP),
                        Mockito.anyString(), Mockito.eq(this.openStackV3Token));
        //remove network
        Mockito.doNothing().when(this.openStackHttpClient).doDeleteRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_NETWORK), Mockito.eq(this.openStackV3Token));

        Mockito.doReturn(NETWORK_ID).when(this.openStackNetworkPlugin).getNetworkIdFromJson(Mockito.anyString());
        NetworkOrder order = createEmptyOrder();

        //exercise
        try {
            this.openStackNetworkPlugin.requestInstance(order, this.openStackV3Token);
        } catch (FogbowException e) {
            //doNothing
        }

        //verify
        //request checks
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doPostRequest(
                Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_NETWORK), Mockito.anyString(), Mockito.eq(this.openStackV3Token)
        );
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doPostRequest(
                Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SUBNET), Mockito.anyString(), Mockito.eq(this.openStackV3Token)
        );
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doPostRequest(
                Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP), Mockito.anyString(), Mockito.eq(this.openStackV3Token)
        );
        Mockito.verify(this.openStackHttpClient, Mockito.never()).doPostRequest(
                Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP_RULES), Mockito.anyString(), Mockito.eq(this.openStackV3Token)
        );

        //remove checks
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doDeleteRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_NETWORK), Mockito.eq(this.openStackV3Token));
    }

    //test case: Tests the case that security group rules raise an exception. This implies that network
    // and security group will be removed.
    @Test
    public void testErrorInPostSecurityGroupRules() throws IOException, FogbowException {
        //set up
        //post network
        CreateNetworkResponse createNetworkResponse = new CreateNetworkResponse(new CreateNetworkResponse.Network(NETWORK_ID));
        Mockito.doReturn(createNetworkResponse.toJson()).when(this.openStackHttpClient)
                .doPostRequest(Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_NETWORK),
                        Mockito.anyString(), Mockito.eq(this.openStackV3Token));

        //post subnet
        Mockito.doReturn("").when(this.openStackHttpClient)
                .doPostRequest(Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SUBNET),
                        Mockito.anyString(), Mockito.eq(this.openStackV3Token));

        //post security group
        CreateSecurityGroupResponse.SecurityGroup securityGroup = new CreateSecurityGroupResponse.SecurityGroup(SECURITY_GROUP_ID);
        CreateSecurityGroupResponse createSecurityGroupResponse = new CreateSecurityGroupResponse(securityGroup);
        Mockito.doReturn(createSecurityGroupResponse.toJson()).when(this.openStackHttpClient)
                .doPostRequest(Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP),
                        Mockito.anyString(), Mockito.eq(this.openStackV3Token));

        //error in post security group rules
        Mockito.doThrow(new HttpResponseException(HttpStatus.SC_FORBIDDEN, "")).when(this.openStackHttpClient)
                .doPostRequest(Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP_RULES),
                        Mockito.anyString(), Mockito.eq(this.openStackV3Token));

        //remove network
        Mockito.doNothing().when(this.openStackHttpClient).doDeleteRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_NETWORK), Mockito.eq(this.openStackV3Token));
        Mockito.doNothing().when(this.openStackHttpClient).doDeleteRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_SECURITY_GROUP), Mockito.eq(this.openStackV3Token));

        Mockito.doReturn(NETWORK_ID).when(this.openStackNetworkPlugin).getNetworkIdFromJson(Mockito.anyString());
        NetworkOrder order = createEmptyOrder();

        //exercise
        try {
            this.openStackNetworkPlugin.requestInstance(order, this.openStackV3Token);
        } catch (FogbowException e) {
            //doNothing
        }

        //verify
        //request checks
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doPostRequest(
                Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_NETWORK), Mockito.anyString(), Mockito.eq(this.openStackV3Token)
        );
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doPostRequest(
                Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SUBNET), Mockito.anyString(), Mockito.eq(this.openStackV3Token)
        );
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doPostRequest(
                Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP), Mockito.anyString(), Mockito.eq(this.openStackV3Token)
        );
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doPostRequest(
                Mockito.endsWith(OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP_RULES), Mockito.anyString(), Mockito.eq(this.openStackV3Token)
        );

        //remove checks
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doDeleteRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_NETWORK), Mockito.eq(this.openStackV3Token));
        Mockito.verify(this.openStackHttpClient, Mockito.never()).doGetRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_GET_SECURITY_GROUP), Mockito.eq(this.openStackV3Token));
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doDeleteRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_SECURITY_GROUP), Mockito.eq(this.openStackV3Token));
    }

    //requestInstance collaborators tests

    //test case: Tests if the dns list will be returned as expected
    @Test
    public void testSetDnsList() {
        //set up
        String dnsOne = "one";
        String dnsTwo = "Two";
        this.properties.put(OpenStackNetworkPlugin.KEY_DNS_NAMESERVERS, dnsOne + "," + dnsTwo);

        //exercise
        this.openStackNetworkPlugin.setDNSList(this.properties);

        //verify
        Assert.assertEquals(2, this.openStackNetworkPlugin.getDnsList().length);
        Assert.assertEquals(dnsOne, this.openStackNetworkPlugin.getDnsList()[0]);
        Assert.assertEquals(dnsTwo, this.openStackNetworkPlugin.getDnsList()[1]);
    }

    //test case: Tests if the json to request subnet was generated as expected
    @Test
    public void testGenerateJsonEntityToCreateSubnet() throws JSONException {
        //set up
        String dnsOne = "one";
        String dnsTwo = "Two";
        this.properties.put(OpenStackNetworkPlugin.KEY_DNS_NAMESERVERS, dnsOne + "," + dnsTwo);
        this.openStackNetworkPlugin.setDNSList(this.properties);
        String networkId = "networkId";
        String address = "10.10.10.0/24";
        String gateway = "10.10.10.11";
        NetworkOrder order = createNetworkOrder(networkId, address, gateway, NetworkAllocationMode.DYNAMIC);

        //exercise
        String generateJsonEntityToCreateSubnet = this.openStackNetworkPlugin
                .generateJsonEntityToCreateSubnet(order.getId(), DEFAULT_PROJECT_ID, order);

        //verify
        String subnetJson = generateJsonEntityToCreateSubnet;
        JSONObject subnetJsonObject = new JSONObject(subnetJson).optJSONObject(OpenStackConstants.Network.SUBNET_KEY_JSON);
        Assert.assertEquals(DEFAULT_PROJECT_ID, subnetJsonObject.optString(OpenStackConstants.Network.PROJECT_ID_KEY_JSON));
        Assert.assertTrue(subnetJsonObject.optString(OpenStackConstants.Network.NAME_KEY_JSON)
                .contains(OpenStackNetworkPlugin.DEFAULT_SUBNET_NAME));
        Assert.assertEquals(order.getId(), subnetJsonObject.optString(OpenStackConstants.Network.NETWORK_ID_KEY_JSON));
        Assert.assertEquals(order.getCidr(), subnetJsonObject.optString(OpenStackConstants.Network.CIDR_KEY_JSON));
        Assert.assertEquals(order.getGateway(), subnetJsonObject.optString(OpenStackConstants.Network.GATEWAY_IP_KEY_JSON));
        Assert.assertEquals(true, subnetJsonObject.optBoolean(OpenStackConstants.Network.ENABLE_DHCP_KEY_JSON));
        Assert.assertEquals(OpenStackNetworkPlugin.DEFAULT_IP_VERSION,
                subnetJsonObject.optInt(OpenStackConstants.Network.IP_VERSION_KEY_JSON));
        Assert.assertEquals(dnsOne, subnetJsonObject.optJSONArray(OpenStackNetworkPlugin.KEY_DNS_NAMESERVERS).get(0));
        Assert.assertEquals(dnsTwo, subnetJsonObject.optJSONArray(OpenStackNetworkPlugin.KEY_DNS_NAMESERVERS).get(1));
    }

    //test case: Tests if the json to request subnet was generated as expected, when address is not provided.
    @Test
    public void testGenerateJsonEntityToCreateSubnetDefaultAddress() throws JSONException {
        //set up
        String networkId = "networkId";
        NetworkOrder order = createNetworkOrder(networkId, null, null, null);

        //exercise
        String entityToCreateSubnet = this.openStackNetworkPlugin
                .generateJsonEntityToCreateSubnet(order.getId(), DEFAULT_PROJECT_ID, order);
        JSONObject generateJsonEntityToCreateSubnet = new JSONObject(entityToCreateSubnet);

        //verify
        JSONObject subnetJsonObject = generateJsonEntityToCreateSubnet
                .optJSONObject(OpenStackConstants.Network.SUBNET_KEY_JSON);
        Assert.assertEquals(OpenStackNetworkPlugin.DEFAULT_NETWORK_CIDR,
                subnetJsonObject.optString(OpenStackConstants.Network.CIDR_KEY_JSON));
    }

    //test case: Tests if the json to request subnet was generated as expected, when dns is not provided.
    @Test
    public void testGenerateJsonEntityToCreateSubnetDefaultDns() throws JSONException {
        //set up
        String networkId = "networkId";
        NetworkOrder order = createNetworkOrder(networkId, null, null, null);

        //exercise
        String entityToCreateSubnet = this.openStackNetworkPlugin
                .generateJsonEntityToCreateSubnet(order.getId(), DEFAULT_PROJECT_ID, order);
        JSONObject generateJsonEntityToCreateSubnet = new JSONObject(entityToCreateSubnet);

        //verify
        JSONObject subnetJsonObject = generateJsonEntityToCreateSubnet
                .optJSONObject(OpenStackConstants.Network.SUBNET_KEY_JSON);
        Assert.assertEquals(OpenStackNetworkPlugin.DEFAULT_DNS_NAME_SERVERS[0],
                subnetJsonObject.optJSONArray(OpenStackNetworkPlugin.KEY_DNS_NAMESERVERS).get(0));
        Assert.assertEquals(OpenStackNetworkPlugin.DEFAULT_DNS_NAME_SERVERS[1],
                subnetJsonObject.optJSONArray(OpenStackNetworkPlugin.KEY_DNS_NAMESERVERS).get(1));
    }

    //test case: Tests if the json to request subnet was generated as expected, when a static allocation is required.
    @Test
    public void testGenerateJsonEntityToCreateSubnetStaticAllocation() throws JSONException {
        //set up
        String networkId = "networkId";
        NetworkOrder order = createNetworkOrder(networkId, null, null, NetworkAllocationMode.STATIC);

        //exercise
        String entityToCreateSubnet = this.openStackNetworkPlugin
                .generateJsonEntityToCreateSubnet(order.getId(), DEFAULT_PROJECT_ID, order);
        JSONObject generateJsonEntityToCreateSubnet = new JSONObject(entityToCreateSubnet);

        //verify
        JSONObject subnetJsonObject = generateJsonEntityToCreateSubnet
                .optJSONObject(OpenStackConstants.Network.SUBNET_KEY_JSON);
        Assert.assertEquals(false, subnetJsonObject.optBoolean(OpenStackConstants.Network.ENABLE_DHCP_KEY_JSON));
    }

    //test case: Tests if the json to request subnet was generated as expected, when gateway is not provided.
    @Test
    public void testGenerateJsonEntityToCreateSubnetWithoutGateway() throws JSONException {
        //set up
        String networkId = "networkId";
        NetworkOrder order = createNetworkOrder(networkId, null, null, null);

        //exercise
        String entityToCreateSubnet = this.openStackNetworkPlugin
                .generateJsonEntityToCreateSubnet(order.getId(), DEFAULT_PROJECT_ID, order);
        JSONObject generateJsonEntityToCreateSubnet = new JSONObject(entityToCreateSubnet);

        //verify
        JSONObject subnetJsonObject = generateJsonEntityToCreateSubnet
                .optJSONObject(OpenStackConstants.Network.SUBNET_KEY_JSON);
        Assert.assertTrue(subnetJsonObject.optString(OpenStackConstants.Network.GATEWAY_IP_KEY_JSON).isEmpty());
    }

    //getInstance tests

    //test case: Tests get networkId from json response
    @Test
    public void testGetNetworkIdFromJson() throws JSONException, UnexpectedException {
        //set up
        String networkId = "networkId00";
        JSONObject networkContentJsonObject = new JSONObject();
        networkContentJsonObject.put(OpenStackConstants.Network.ID_KEY_JSON, networkId);
        JSONObject networkJsonObject = new JSONObject();

        //exercise
        networkJsonObject.put(OpenStackConstants.Network.NETWORK_KEY_JSON, networkContentJsonObject);

        //verify
        Assert.assertEquals(networkId,
                this.openStackNetworkPlugin.getNetworkIdFromJson(networkJsonObject.toString()));
    }

    //test case: Tests get instance from json response
    @Test
    public void testGetInstanceFromJson() throws JSONException, IOException, FogbowException {
        //set up
        DatabaseManager.getInstance().setAuditService(Mockito.mock(AuditService.class));

        String networkId = "networkId00";
        String networkName = "netName";
        String subnetId = "subnetId00";
        String vlan = "vlan00";
        String gatewayIp = "10.10.10.10";
        String cidr = "10.10.10.0/24";
        // Generating network response string
        JSONObject networkContentJsonObject = generateJsonResponseForNetwork(networkId, networkName, subnetId, vlan);
        JSONObject networkJsonObject = new JSONObject();
        networkJsonObject.put(OpenStackConstants.Network.NETWORK_KEY_JSON, networkContentJsonObject);

        // Generating subnet response string
        JSONObject subnetContentJsonObject = generateJsonResponseForSubnet(gatewayIp, cidr);
        JSONObject subnetJsonObject = new JSONObject();
        subnetJsonObject.put(OpenStackConstants.Network.SUBNET_KEY_JSON, subnetContentJsonObject);

//        HttpResponse httpResponseGetNetwork = createHttpResponse(networkJsonObject.toString(), HttpStatus.SC_OK);
//        HttpResponse httpResponseGetSubnet = createHttpResponse(subnetJsonObject.toString(), HttpStatus.SC_OK);
//        Mockito.when(this.client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(httpResponseGetNetwork,
//                httpResponseGetSubnet);
//        Mockito.when(this.openStackHttpClient.doGetRequest(Mockito.anyString(), Mockito.any(CloudUser.class))).
//                thenReturn(networkJsonObject.toString(), subnetJsonObject.toString());

        Mockito.doReturn(networkJsonObject.toString()).doReturn(subnetJsonObject.toString()).when(this.openStackHttpClient).doGetRequest(Mockito.anyString(), Mockito.any(CloudUser.class));

        //exercise
        NetworkInstance instance = this.openStackNetworkPlugin.getInstance("instanceId00", this.openStackV3Token);

        //verify
        Assert.assertEquals(networkId, instance.getId());
        Assert.assertEquals(networkName, instance.getName());
        Assert.assertEquals(vlan, instance.getvLAN());
        Assert.assertEquals(InstanceState.READY, instance.getState());
        Assert.assertEquals(gatewayIp, instance.getGateway());
        Assert.assertEquals(cidr, instance.getCidr());
        Assert.assertEquals(NetworkAllocationMode.DYNAMIC, instance.getAllocationMode());
    }


    //deleteInstance tests

    //test case: Tests remove instance, it must execute a http client exactly 3 times: 1 GetRequest, 2 DeleteRequests
    @Test
    public void testRemoveInstance() throws IOException, JSONException, FogbowException {
        //set up
        JSONObject securityGroupResponse = createSecurityGroupGetResponse(SECURITY_GROUP_ID);
        String suffixEndpointNetwork = OpenStackNetworkPlugin.SUFFIX_ENDPOINT_NETWORK + "/" + NETWORK_ID;
        String suffixEndpointGetSG = OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP + "?" +
                OpenStackNetworkPlugin.QUERY_NAME + "=" + OpenStackNetworkPlugin.SECURITY_GROUP_PREFIX + NETWORK_ID;
        String suffixEndpointDeleteSG = OpenStackNetworkPlugin.SUFFIX_ENDPOINT_SECURITY_GROUP + "/" + SECURITY_GROUP_ID;

        Mockito.doNothing().when(this.openStackHttpClient).doDeleteRequest(
                Mockito.endsWith(suffixEndpointNetwork), Mockito.eq(this.openStackV3Token));
        Mockito.doReturn(securityGroupResponse.toString()).when(this.openStackHttpClient).doGetRequest(
                Mockito.endsWith(suffixEndpointGetSG), Mockito.eq(this.openStackV3Token));
        Mockito.doNothing().when(this.openStackHttpClient).doDeleteRequest(
                Mockito.endsWith(suffixEndpointDeleteSG), Mockito.eq(this.openStackV3Token));

        //exercise
        this.openStackNetworkPlugin.deleteInstance(NETWORK_ID, this.openStackV3Token);

        //verify
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doDeleteRequest(
                Mockito.endsWith(suffixEndpointNetwork), Mockito.eq(this.openStackV3Token));
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doGetRequest(
                Mockito.endsWith(suffixEndpointGetSG), Mockito.eq(this.openStackV3Token));
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doDeleteRequest(
                Mockito.endsWith(suffixEndpointDeleteSG), Mockito.eq(this.openStackV3Token));
    }

    //test: Tests a delete in a network which has compute attached to it
    @Test
    public void testRemoveNetworkWithInstanceAssociated() throws JSONException, IOException, FogbowException {
        //set up
        String suffixEndpointNetwork = OpenStackNetworkPlugin.SUFFIX_ENDPOINT_NETWORK + "/" + NETWORK_ID;

        Mockito.doThrow(new HttpResponseException(HttpStatus.SC_CONFLICT, "conflict")).when(this.openStackHttpClient)
                .doDeleteRequest(Mockito.endsWith(suffixEndpointNetwork), Mockito.eq(this.openStackV3Token));

        //exercise
        try {
            this.openStackNetworkPlugin.deleteInstance(NETWORK_ID, this.openStackV3Token);
            Assert.fail();
        } catch (FogbowException e) {
            // TODO: check error message
        } catch (Exception e) {
            Assert.fail();
        }

        // verify
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doDeleteRequest(
                Mockito.anyString(), Mockito.eq(this.openStackV3Token));
        Mockito.verify(this.openStackHttpClient, Mockito.never()).doGetRequest(
                Mockito.anyString(), Mockito.eq(this.openStackV3Token));
    }

    // test case: throws an exception when try to delete the security group
    @Test(expected = FogbowException.class)
    public void testDeleteInstanceExceptionSecurityGroupDeletion() throws FogbowException, IOException {
        // set up
        JSONObject securityGroupResponse = createSecurityGroupGetResponse(SECURITY_GROUP_ID);
        // network deletion ok
        Mockito.doNothing().when(this.openStackHttpClient)
                .doDeleteRequest(Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_NETWORK), Mockito.eq(this.openStackV3Token));
        // retrieving securityGroupId ok
        Mockito.doReturn(securityGroupResponse.toString()).when(this.openStackHttpClient).doGetRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_GET_SECURITY_GROUP), Mockito.eq(this.openStackV3Token));
        // security group deletion not ok
        Mockito.doThrow(new HttpResponseException(org.apache.commons.httpclient.HttpStatus.SC_BAD_REQUEST, "")).when(this.openStackHttpClient)
                .doDeleteRequest(Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_SECURITY_GROUP), Mockito.eq(this.openStackV3Token));

        // exercise
        this.openStackNetworkPlugin.deleteInstance(NETWORK_ID, this.openStackV3Token);

        // verify
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doDeleteRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_NETWORK), Mockito.eq(this.openStackV3Token));
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doGetRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_GET_SECURITY_GROUP), Mockito.eq(this.openStackV3Token));
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doDeleteRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_SECURITY_GROUP), Mockito.eq(this.openStackV3Token));
    }

    // test case: throws a "notFoundInstance" exception and continue try to delete the security group
    @Test
    public void testDeleteInstanceNotFoundNetworkException() throws FogbowException, IOException {
        // set up
        JSONObject securityGroupResponse = createSecurityGroupGetResponse(SECURITY_GROUP_ID);
        // network deletion not ok and return nof found
        Mockito.doThrow(new HttpResponseException(org.apache.commons.httpclient.HttpStatus.SC_NOT_FOUND, "")).when(this.openStackHttpClient)
                .doDeleteRequest(Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_NETWORK), Mockito.eq(this.openStackV3Token));
        // retrieved securityGroupId ok
        Mockito.doReturn(securityGroupResponse.toString()).when(this.openStackHttpClient).doGetRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_GET_SECURITY_GROUP), Mockito.eq(this.openStackV3Token));
        // security group deletion ok
        Mockito.doNothing().when(this.openStackHttpClient).doDeleteRequest(Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_SECURITY_GROUP), Mockito.eq(this.openStackV3Token));

        // exercise
        this.openStackNetworkPlugin.deleteInstance(NETWORK_ID, this.openStackV3Token);

        // verify
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doDeleteRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_NETWORK), Mockito.eq(this.openStackV3Token));
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doGetRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_GET_SECURITY_GROUP), Mockito.eq(this.openStackV3Token));
        Mockito.verify(this.openStackHttpClient, Mockito.times(1)).doDeleteRequest(
                Mockito.endsWith(SUFFIX_ENDPOINT_DELETE_SECURITY_GROUP), Mockito.eq(this.openStackV3Token));
    }


    //test case: Tests if getSecurityGroupIdFromGetResponse can retrieve the respective id from a valid json
    @Test
    public void testRetrieveSecurityGroupIdFromGetResponse() throws UnexpectedException {
        //set up
        JSONObject securityGroup = new JSONObject();
        securityGroup.put(OpenStackConstants.Network.PROJECT_ID_KEY_JSON, "fake-tenant-id");
        securityGroup.put(OpenStackConstants.Network.NAME_KEY_JSON, "fake-name");
        securityGroup.put(OpenStackConstants.Network.ID_KEY_JSON, SECURITY_GROUP_ID);

        JSONArray securityGroups = new JSONArray();
        securityGroups.put(securityGroup);
        JSONObject response = new JSONObject();
        response.put(OpenStackConstants.Network.SECURITY_GROUPS_KEY_JSON, securityGroups);

        //exercise
        String id = this.openStackNetworkPlugin.getSecurityGroupIdFromGetResponse(response.toString());

        //verify
        Assert.assertEquals(SECURITY_GROUP_ID, id);
    }

    //test case: Tests if getSecurityGroupIdFromGetResponse throws exception when cannot get id from json
    @Test(expected = UnexpectedException.class)
    public void testErrorToRetrieveSecurityGroupIdFromGetResponse() throws UnexpectedException {
        //set up
        JSONObject securityGroup = new JSONObject();
        securityGroup.put(OpenStackConstants.Network.PROJECT_ID_KEY_JSON, "fake-tenant-id");
        securityGroup.put(OpenStackConstants.Network.NAME_KEY_JSON, "fake-name");

        JSONArray securityGroups = new JSONArray();
        securityGroups.put(securityGroup);
        JSONObject response = new JSONObject();
        response.put(OpenStackConstants.Network.SECURITY_GROUPS_KEY_JSON, securityGroups);

        //exercise
        this.openStackNetworkPlugin.getSecurityGroupIdFromGetResponse(response.toString());
    }

    private NetworkOrder createNetworkOrder(String networkId, String address, String gateway,
                                            NetworkAllocationMode allocation) {
        String requestingMember = "fake-requesting-member";
        String providingMember = "fake-providing-member";
        String name = "name";
        NetworkOrder order = new NetworkOrder(providingMember,
                "default", name, gateway, address, allocation);
        return order;
    }

    private NetworkOrder createEmptyOrder() {
        return new NetworkOrder(null, null, null, "default", null, null, null, null);
    }

    private JSONObject generateJsonResponseForSubnet(String gatewayIp, String cidr) {
        JSONObject subnetContentJsonObject = new JSONObject();

        subnetContentJsonObject.put(OpenStackConstants.Network.GATEWAY_IP_KEY_JSON, gatewayIp);
        subnetContentJsonObject.put(OpenStackConstants.Network.ENABLE_DHCP_KEY_JSON, true);
        subnetContentJsonObject.put(OpenStackConstants.Network.CIDR_KEY_JSON, cidr);

        return subnetContentJsonObject;
    }

    private JSONObject generateJsonResponseForNetwork(String networkId, String networkName, String subnetId, String vlan) {
        JSONObject networkContentJsonObject = new JSONObject();

        networkContentJsonObject.put(OpenStackConstants.Network.ID_KEY_JSON, networkId);
        networkContentJsonObject.put(OpenStackConstants.Network.PROVIDER_SEGMENTATION_ID_KEY_JSON, vlan);
        networkContentJsonObject.put(OpenStackConstants.Network.STATUS_KEY_JSON,
                OpenStackStateMapper.ACTIVE_STATUS);
        networkContentJsonObject.put(OpenStackConstants.Network.NAME_KEY_JSON, networkName);
        JSONArray subnetJsonArray = new JSONArray(Arrays.asList(new String[]{subnetId}));
        networkContentJsonObject.put(OpenStackConstants.Network.SUBNETS_KEY_JSON, subnetJsonArray);

        return networkContentJsonObject;
    }

    private JSONObject createSecurityGroupGetResponse(String id) {
        JSONObject jsonObject = new JSONObject();
        JSONArray securityGroups = new JSONArray();
        JSONObject securityGroupInfo = new JSONObject();
        securityGroupInfo.put(OpenStackConstants.Network.PROJECT_ID_KEY_JSON, "fake-project-id");
        securityGroupInfo.put(OpenStackNetworkPlugin.QUERY_NAME, "fake-name");
        securityGroupInfo.put(OpenStackConstants.Network.ID_KEY_JSON, id);
        securityGroups.put(securityGroupInfo);
        jsonObject.put(OpenStackConstants.Network.SECURITY_GROUPS_KEY_JSON, securityGroups);
        return jsonObject;
    }
}
