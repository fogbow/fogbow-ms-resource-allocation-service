package org.fogbowcloud.manager.core.plugins.compute.openstack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.models.ResponseConstants;
import org.fogbowcloud.manager.core.models.StorageLink;
import org.fogbowcloud.manager.core.models.exceptions.RequestException;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.orders.UserData;
import org.fogbowcloud.manager.core.models.orders.instances.ComputeOrderInstance;
import org.fogbowcloud.manager.core.models.token.Token;
import org.fogbowcloud.manager.core.plugins.compute.ComputePlugin;
import org.fogbowcloud.manager.core.utils.HttpRequestUtil;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenStackNovaV2ComputePlugin implements ComputePlugin {
	
	private static final String ID_JSON_FIELD = "id";
//	private static final String IMAGES_JSON_FIELD = "images";
	protected static final String NAME_JSON_FIELD = "name";
//	private static final String TENANT_ID = "tenantId";
	private static final String SERVERS = "/servers";
	
	private static final Logger LOGGER = Logger.getLogger(OpenStackNovaV2ComputePlugin.class);
	
	private HttpClient client;
	
	public OpenStackNovaV2ComputePlugin() {
		this.client = HttpRequestUtil.createHttpClient(60000, null, null);
	}
	
	public String requestInstance(ComputeOrder computeOrder, String imageId) {
//		LOGGER.debug("Requesting instance with token=" + computeOrder.getFederationToken());
					
//		String imageId = "8d4ab46c-3c57-4c11-998c-f2c839d1e574";
		String flavorId = "f820a0a0-ccb2-4478-ab39-c4ae0cdd55c9";
		String tenantId = "3324431f606d4a74a060cf78c16fcb21";
//		String openstackEndpoint = "https://cloud.lsd.ufcg.edu.br:8774/v2/";
		String networkId = "64ee4355-4d7f-4170-80b4-5e8348af6a61";
//		String userId = "3e57892203271c195f5d473fc84f484b8062103275ce6ad6e7bcd1baedf70d5c";
		
		try {
			JSONObject json = generateJsonRequest(imageId, flavorId, null, null, networkId);
						
			String requestEndpoint = "https://cloud.lsd.ufcg.edu.br:8774/v2.1/" + tenantId + SERVERS;
			String jsonResponse = doPostRequest(requestEndpoint, "gAAAAABa1K3fVhWcnLaohQc6EdKYhqWoejIP2Mki"
					+ "Fv_0C2B2i10oMD3yuIudBe8IHXoKt3HSwXyRpeRiaFsMGmdOTH4qScca7W4J_aG6RAIklxNj0oJCT"
					+ "qUn9_3NEOTx_JwDU9KYThF2pMWxhRWryA1ADdtlaqbipG1IBtNueQFEq3tOvVBkUd6XwuLGqji"
					+ "1LzpEaJC_dt0fhRoGweZdPQhIID2UClXGfSVz8R0ZphJZH2XtUJ2UfQE", json);
			
			return getAttFromJson(ID_JSON_FIELD, jsonResponse);
		} catch (JSONException e) {
			LOGGER.error(e);
			throw new RequestException(HttpStatus.SC_BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} finally {
			return null;
		}
	}
	
	private String getAttFromJson(String attName, String jsonStr) throws JSONException {
		JSONObject root = new JSONObject(jsonStr);
		return root.getJSONObject("server").getString(attName);
	}	
	
	protected String doPostRequest(String endpoint, String authToken, JSONObject jsonRequest) throws RequestException {
		HttpResponse response = null;
		String responseStr = null;
		
		try {	
			HttpPost request = new HttpPost(endpoint);
			request.addHeader("Content-Type", "application/json");
			request.addHeader("Accept", "application/json");
			request.addHeader("X-Auth-Token", authToken);

			request.setEntity(new StringEntity(jsonRequest.toString(), StandardCharsets.UTF_8));
			response = client.execute(request);
			responseStr = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.error(e);
			throw new RequestException(HttpStatus.SC_BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} finally {
			try {
				EntityUtils.consume(response.getEntity());
			} catch (Throwable t) {
				// Do nothing
			}
		}
		
//		checkStatusResponse(response, responseStr);
		
		return responseStr;
	}
	
//	private void checkStatusResponse(HttpResponse response, String message) {
//		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
//			throw new RequestException(HttpStatus.SC_UNAUTHORIZED, ResponseConstants.UNAUTHORIZED);
//		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_NOT_FOUND) {
//			throw new RequestException(HttpStatus.SC_NOT_FOUND, ResponseConstants.NOT_FOUND);
//		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_BAD_REQUEST) {
//			throw new RequestException(HttpStatus.SC_BAD_REQUEST, message);
//		} else if (response.getStatusLine().getStatusCode() == HttpStatus.SC_REQUEST_TOO_LONG 
//				|| response.getStatusLine().getStatusCode() == HttpStatus.SC_FORBIDDEN) {
//			if (message.contains(ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES)) {
//				throw new RequestException(HttpStatus.QUOTA_EXCEEDED,
//						ResponseConstants.QUOTA_EXCEEDED_FOR_INSTANCES);
//			}
//			throw new RequestException(RequestException.SC_BAD_REQUEST, message);
//		} else if ((response.getStatusLine().getStatusCode() == HttpStatus.SC_INTERNAL_SERVER_ERROR) &&
//				(message.contains(NO_VALID_HOST_WAS_FOUND))){
//			throw new OCCIException(ErrorType.NO_VALID_HOST_FOUND, ResponseConstants.NO_VALID_HOST_FOUND);
//		}
//		else if (response.getStatusLine().getStatusCode() > 204) {
//			throw new OCCIException(ErrorType.BAD_REQUEST, 
//					"Status code: " + response.getStatusLine().toString() + " | Message:" + message);
//		}
//	}
	
	protected String generateRequestEndpoint(Token token) {
//		return computeV2APIEndpoint + tenantId + SERVERS;
		return null;
	}
	
	private JSONObject generateJsonRequest(String imageRef, String flavorRef, UserData userdata,
			String keyName, String networkId) throws JSONException {

		JSONObject server = new JSONObject();
		server.put(NAME_JSON_FIELD, "fogbow-instance-" + UUID.randomUUID().toString());
		server.put("imageRef", imageRef);
		server.put("flavorRef", flavorRef);
		
		if (userdata != null) {
			server.put("user_data", userdata);
		}

		if (networkId != null && !networkId.isEmpty()) {
			List<JSONObject> nets = new ArrayList<JSONObject>();
			JSONObject net = new JSONObject();
			net.put("uuid", networkId);
			nets.add(net);
			server.put("networks", nets);
		}
		
		if (keyName != null && !keyName.isEmpty()){
			server.put("key_name", keyName);
		}

		JSONObject root = new JSONObject();
		root.put("server", server);
				
		return root;
	}
	
	public ComputeOrderInstance getInstance(Token localToken, String instanceId) {
		LOGGER.info("Getting instance " + instanceId + " with token " + localToken);
		
//		if (getFlavors() == null || getFlavors().isEmpty()) {
//			updateFlavors(localToken);
//		}

		String requestEndpoint = generateRequestEndpoint(localToken);
//		String jsonResponse = doGetRequest(requestEndpoint, localToken);
		
//		LOGGER.debug("Getting instance from json: " + jsonResponse);
//		return getInstanceFromJson(jsonResponse, localToken);
		return null;
	}
	
	protected String doGetRequest(String endpoint, String authToken) throws RequestException {
		HttpResponse response = null;
		String responseStr = null;
		
		try {
			HttpGet request = new HttpGet(endpoint);			
			request.addHeader("Content-Type", "application/json");
			request.addHeader("Accept", "application/json");
			request.addHeader("X-Auth-Token", authToken);
			
			response = client.execute(request);
			responseStr = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.error("Could not make GET request.", e);
			throw new RequestException(HttpStatus.SC_BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} finally {
			try {
				EntityUtils.consume(response.getEntity());
			} catch (Throwable t) {
//				 Do nothing
			}
		}
//		checkStatusResponse(response, responseStr);
		return responseStr;
	}
	
	private ComputeOrderInstance getInstanceFromJson(String json, Token token) {
//		try {
//			JSONObject rootServer = new JSONObject(json);
//			String id = rootServer.getJSONObject("server").getString(ID_JSON_FIELD);
////
//			Map<String, String> attributes = new HashMap<String, String>();
//			InstanceState state = getInstanceState(rootServer.getJSONObject("server").getString(STATUS_JSON_FIELD));
////			// CPU Architecture of the instance
//			attributes.put("occi.compute.state", state.getOcciState());
////			  CPU Clock frequency (speed) in gigahertz
////			// TODO How to get speed?
//			attributes.put("occi.compute.speed", "Not defined");
////			// TODO How to get Arch?
//			attributes.put("occi.compute.architecture", "Not defined"); 
////
////			// getting info from flavor
//			String flavorId = rootServer.getJSONObject("server").getJSONObject("flavor")
//					.getString(ID_JSON_FIELD);
//			String requestEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
//					+ "/flavors/" + flavorId;
//			String jsonFlavor = doGetRequest(requestEndpoint, token.getAccessId());
//			JSONObject rootFlavor = new JSONObject(jsonFlavor);
//			double mem = Double.parseDouble(rootFlavor.getJSONObject("flavor").getString("ram"));
//			attributes.put("occi.compute.memory", String.valueOf(mem / 1024)); // Gb
//			attributes.put("occi.compute.cores",
//					rootFlavor.getJSONObject("flavor").getString("vcpus"));
////
//			attributes.put("occi.compute.hostname",
//					rootServer.getJSONObject("server").getString(NAME_JSON_FIELD));
//			attributes.put("occi.core.id", id);
////			
////			// getting local private IP
//			JSONArray addressesNamesArray = rootServer.getJSONObject("server").getJSONObject("addresses").names();
//			String networkMac = "";
//			String networkName = null;
//			if (addressesNamesArray != null && addressesNamesArray.length() > 0) {
//				networkName = rootServer.getJSONObject("server").getJSONObject("addresses").names().getString(0);
////							
//				JSONArray networkArray = rootServer.getJSONObject("server").getJSONObject("addresses").getJSONArray(networkName);
//				if (networkArray != null) {
//					for (int i = 0; i < networkArray.length(); i++) {
//						JSONObject networkObject = networkArray.getJSONObject(i);
//						String addr = networkObject.getString("addr");
//						networkMac = networkObject.getString("OS-EXT-IPS-MAC:mac_addr");
//						if (addr != null && !addr.isEmpty()) {
//							attributes.put(Instance.LOCAL_IP_ADDRESS_ATT, addr);
//							break;
//						}
//					}
//				}
//			}
//
//			List<Resource> resources = new ArrayList<Resource>();
//			resources.add(ResourceRepository.getInstance().get("compute"));
//			resources.add(ResourceRepository.getInstance().get("os_tpl"));
//			
//			//TODO check this line
//			resources.add(ResourceRepository.generateFlavorResource(getUsedFlavor(flavorId)));
//
//			LOGGER.debug("Instance resources: " + resources);
//
//			ArrayList<Link> links = new ArrayList<Instance.Link>();
//			
//			Link privateIpLink = new Link();
//			privateIpLink.setType(OrderConstants.NETWORK_TERM);
//			String serverNetworkId = getNetworkIdByName(token, networkName);
//			privateIpLink.setId(serverNetworkId);
//			privateIpLink.setName("</" + OrderConstants.NETWORK_TERM + "/" + serverNetworkId + ">");
//			
//			Map<String, String> linkAttributes = new HashMap<String, String>();
//			linkAttributes.put("rel", OrderConstants.INFRASTRUCTURE_OCCI_SCHEME 
//					+ OrderConstants.NETWORK_TERM);
//			linkAttributes.put("category", OrderConstants.INFRASTRUCTURE_OCCI_SCHEME 
//					+ OrderConstants.NETWORK_INTERFACE_TERM);
//			linkAttributes.put(OCCIConstants.NETWORK_INTERFACE_INTERFACE, "eth0");
//			linkAttributes.put(OCCIConstants.NETWORK_INTERFACE_MAC, networkMac);
//			linkAttributes.put(OCCIConstants.NETWORK_INTERFACE_STATE, 
//					OCCIConstants.NetworkState.ACTIVE.getValue());
//			
//			privateIpLink.setAttributes(linkAttributes);
//			links.add(privateIpLink);
//			
//			return new Instance(id, resources, attributes, links, state);
//		} catch (JSONException e) {
//			LOGGER.warn("There was an exception while getting instances from json.", e);
//		}
		return null;
	}

	public List<ComputeOrderInstance> getInstances(Token localToken) {
//		String requestEndpoint = computeV2APIEndpoint + token.getAttributes().get(TENANT_ID)
//				+ SERVERS;
//		String jsonResponse = doGetRequest(requestEndpoint, token.getAccessId());
		return getInstancesFromJson(null);
	}
	
	private List<ComputeOrderInstance> getInstancesFromJson(String json) {
//		LOGGER.debug("Getting instances from json: " + json);
//		List<Instance> instances = new ArrayList<Instance>();
//		JSONObject root;
//		try {
//			root = new JSONObject(json);
//			JSONArray servers = root.getJSONArray("servers");
//			for (int i = 0; i < servers.length(); i++) {
//				JSONObject currentServer = servers.getJSONObject(i);
//				instances.add(new Instance(currentServer.getString(ID_JSON_FIELD)));
//			}
//		} catch (JSONException e) {
//			LOGGER.warn("There was an exception while getting instances from json.", e);
//		}
		
//		return instances;
		return null;
	}

	public void removeInstance(Token localToken, String instanceId) {
		String requestEndpoint = generateRequestEndpoint(localToken);
//		doDeleteRequest(requestEndpoint, localToken);
	}
	
	protected void doDeleteRequest(String endpoint, String authToken) throws RequestException {
		HttpResponse response = null;
		
		try {
			HttpDelete request = new HttpDelete(endpoint);
			request.addHeader("X-Auth-Token", authToken);
			response = client.execute(request);
		} catch (Exception e) {
			LOGGER.error(e);
			throw new RequestException(HttpStatus.SC_BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		} finally {
			try {
				EntityUtils.consume(response.getEntity());
			} catch (Throwable t) {
				// Do nothing
			}
		}
//		 delete message does not have message
//		checkStatusResponse(response, "");
	}

	public void removeInstances(Token localToken) {
		List<ComputeOrderInstance> allInstances = getInstances(localToken);
		for (ComputeOrderInstance instance : allInstances) {
			removeInstance(localToken, instance.getId());
		}
	}

	public String attachStorage(Token localToken, StorageLink storageLink) {
//		String tenantId = token.getAttributes().get(TENANT_ID);
//		if (tenantId == null) {
//			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
//		}	
		
//		String storageIdd = xOCCIAtt.get(StorageAttribute.TARGET.getValue());
//		String instanceId = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
//		String mountpoint = xOCCIAtt.get(StorageAttribute.DEVICE_ID.getValue());
		
		JSONObject jsonRequest = null;
		try {			
			jsonRequest = generateJsonToAttach("storageIdd", "mountpoint");
		} catch (JSONException e) {
			LOGGER.error("An error occurred when generating json.", e);
//			throw new OCCIException(ErrorType.BAD_REQUEST, ResponseConstants.IRREGULAR_SYNTAX);
		}			
		
//		String prefixEndpoint = this.computeV2APIEndpoint;
//		String endpoint = prefixEndpoint + tenantId + SERVERS
//				+ "/" +  instanceId + OS_VOLUME_ATTACHMENTS;
		String requestEndpoint = generateRequestEndpoint(localToken);
		
//		String responseStr = doPostRequest(requestEndpoint, localToken, jsonRequest);
		
		return getAttAttachmentIdJson("responseStr");
	}
	
	private String getAttAttachmentIdJson(String responseStr) {
		try {
			JSONObject root = new JSONObject(responseStr);
			return root.getJSONObject("volumeAttachment").getString("id").toString();
		} catch (JSONException e) {
			return null;
		}
	}
	
	protected JSONObject generateJsonToAttach(String volume, String mountpoint) {

		JSONObject osAttachContent = new JSONObject();
		osAttachContent.put("volumeId", volume);

		JSONObject osAttach = new JSONObject();
		osAttach.put("volumeAttachment", osAttachContent);
		
		return osAttach;
	}

	public String detachStorage(Token localToken, StorageLink storageLink) {
//		String tenantId = localToken.getAttributes().get(TENANT_ID);
//		if (tenantId == null) {
//			throw new RequestException(HttpStatus.BAD_REQUEST, ResponseConstants.INVALID_TOKEN);
//		}	
		
//		String instanceId = xOCCIAtt.get(StorageAttribute.SOURCE.getValue());
//		String attachmentId = xOCCIAtt.get(StorageAttribute.ATTACHMENT_ID.getValue());		
		
		String requestEndpoint = generateRequestEndpoint(localToken);
//		doDeleteRequest(requestEndpoint, localToken);	
		return null;
	}

	public String getImageId(Token localToken, String imageName) {
//		String requestEndpoint = generateRequestEndpoint(localToken);
//		
//		String responseJsonImages = doGetRequest(requestEndpoint, localToken);
//
//		try {
//			JSONArray arrayImages = new JSONObject(responseJsonImages)
//					.getJSONArray(IMAGES_JSON_FIELD);
//			for (int i = 0; i < arrayImages.length(); i++) {
//				if (arrayImages.getJSONObject(i).getString(NAME_JSON_FIELD).equals(imageName)) {
//					return arrayImages.getJSONObject(i).getString(ID_JSON_FIELD);
//				}
//			}
//		} catch (JSONException e) {
//			LOGGER.error("Error while parsing JSONObject for image state.", e);
//		}

		return null;
	}
	
	public static void main(String[] args) {
		OpenStackNovaV2ComputePlugin compute = new OpenStackNovaV2ComputePlugin();
		
		compute.requestInstance(null, "0bd03dd3-ba50-4eb8-a71f-3c46b4290471");
	}
}
