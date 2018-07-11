	package org.fogbowcloud.manager.core.plugins.cloud.openstack;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.fogbowcloud.manager.core.HomeDir;
import org.fogbowcloud.manager.core.exceptions.FogbowManagerException;
import org.fogbowcloud.manager.core.exceptions.UnexpectedException;
import org.fogbowcloud.manager.core.models.images.Image;
import org.fogbowcloud.manager.core.models.tokens.Token;
import org.fogbowcloud.manager.util.connectivity.HttpRequestClientUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.google.gson.Gson;

public class OpenStackImagePluginTest {

    private static final String FAKE_IMAGE_ID = "fake-image-id";
    private static final String FAKE_IMAGE_NAME = "fake-image-name";
    private static final long FAKE_SIZE = 10l;
    private static final long FAKE_MIN_DISK = 1l;
    private static final long FAKE_MIN_RAM = 2l;
    
    private OpenStackImagePlugin plugin;
    private HttpRequestClientUtil client;
    private Properties properties;
    private Token token;
    
    @Before
    public void setUp() {
        HomeDir.getInstance().setPath("src/test/resources/private");
        this.plugin = Mockito.spy(new OpenStackImagePlugin());
        this.client = Mockito.mock(HttpRequestClientUtil.class);
        this.properties = Mockito.mock(Properties.class);
        this.token = Mockito.mock(Token.class);
    	this.plugin.setProperties(this.properties);
    	this.plugin.setClient(this.client);
    }
    
    @Test
    public void getAllImagesTest() throws FogbowManagerException, UnexpectedException, HttpResponseException {  	
    	String tenantId = "tenant-id";
    	String imageGlancev2UrlKey = "image-url-key";
    	String endpoint = 
    				imageGlancev2UrlKey
	                + OpenStackImagePlugin.COMPUTE_V2_API_ENDPOINT
	                + OpenStackImagePlugin.SUFFIX
	                + OpenStackImagePlugin.QUERY_ACTIVE_IMAGES;
    	List<Map<String, String>> generatedImages = generateImages(tenantId, 0, 100);
    	
    	String jsonResponse = getImagesJson(generatedImages);
    	
    	Token localToken = new Token();
    	Map<String, String> attributes = new HashMap<String, String>();
    	attributes.put(OpenStackImagePlugin.TENANT_ID, tenantId);
    	localToken.setAttributes(attributes);
    	Mockito.when(this.properties.getProperty(OpenStackImagePlugin.IMAGE_GLANCEV2_URL_KEY)).thenReturn(imageGlancev2UrlKey);
    	Mockito.when(this.client.doGetRequest(endpoint, localToken)).thenReturn(jsonResponse);
    	
    	Map<String, String> expectedOutput = getPublicImages(generatedImages);
    	expectedOutput.putAll(getPrivateImagesFromProject(generatedImages, tenantId));
    	Map<String, String> imagePluginOutput = this.plugin.getAllImages(localToken);
    	Assert.assertEquals(expectedOutput, imagePluginOutput);
    }
    
    @Test
    public void getAllImagesWithPaginationTest() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	String tenantId = "tenant-id";
    	String imageGlancev2UrlKey = "image-url-key";
    	String nextUrl1 = "next-url1";
    	String nextUrl2 = "next-url2";
    	
    	String endpoint1 = 
    				imageGlancev2UrlKey
	                + OpenStackImagePlugin.COMPUTE_V2_API_ENDPOINT
	                + OpenStackImagePlugin.SUFFIX
	                + OpenStackImagePlugin.QUERY_ACTIVE_IMAGES;
    	
    	String endpoint2 = 
    				imageGlancev2UrlKey
	                + nextUrl1;
    	
    	String endpoint3 =
    				imageGlancev2UrlKey
    				+ nextUrl2;
    
    	List<Map<String, String>> generatedImages1 = generateImages(tenantId, 0, 100);
    	List<Map<String, String>> generatedImages2 = generateImages(tenantId, 200, 100);
    	List<Map<String, String>> generatedImages3 = generateImages(tenantId, 400, 100);
    	
    	
    	String jsonResponse1 = getImagesJsonWithNext(generatedImages1, nextUrl1);
    	String jsonResponse2 = getImagesJsonWithNext(generatedImages2, nextUrl2);
    	String jsonResponse3 = getImagesJson(generatedImages3);
    	
    	Token localToken = new Token();
    	Map<String, String> attributes = new HashMap<String, String>();
    	attributes.put(OpenStackImagePlugin.TENANT_ID, tenantId);
    	localToken.setAttributes(attributes);
    	Mockito.when(this.properties.getProperty(OpenStackImagePlugin.IMAGE_GLANCEV2_URL_KEY)).thenReturn(imageGlancev2UrlKey);
    	Mockito.when(this.client.doGetRequest(endpoint1, localToken)).thenReturn(jsonResponse1);
    	Mockito.when(this.client.doGetRequest(endpoint2, localToken)).thenReturn(jsonResponse2);
    	Mockito.when(this.client.doGetRequest(endpoint3, localToken)).thenReturn(jsonResponse3);
    	
    	Map<String, String> expectedOutput = getPublicImages(generatedImages1);
    	expectedOutput.putAll(getPrivateImagesFromProject(generatedImages1, tenantId));
    	
    	expectedOutput.putAll(getPrivateImagesFromProject(generatedImages2, tenantId));
    	expectedOutput.putAll(getPublicImages(generatedImages2));
    	
    	expectedOutput.putAll(getPrivateImagesFromProject(generatedImages3, tenantId));
    	expectedOutput.putAll(getPublicImages(generatedImages3));
    	Map<String, String> imagePluginOutput = this.plugin.getAllImages(localToken);
    	
    	Assert.assertEquals(expectedOutput, imagePluginOutput);
    	Mockito.verify(this.client, Mockito.times(1)).doGetRequest(endpoint1, localToken);
    	Mockito.verify(this.client, Mockito.times(1)).doGetRequest(endpoint2, localToken);
    	Mockito.verify(this.client, Mockito.times(1)).doGetRequest(endpoint3, localToken);
    }
    
    private String getImagesJson(List<Map<String, String>> imagesList) {
    	Map <String, Object> jsonMap= new HashMap<String, Object>();
    	jsonMap.put("images", imagesList);
    	Gson gson = new Gson();
    	return gson.toJson(jsonMap);
    }
    
    private String getImagesJsonWithNext(List<Map<String, String>> imagesList, String nextValue) {
    	Map <String, Object> jsonMap= new HashMap<String, Object>();
    	jsonMap.put("images", imagesList);
    	jsonMap.put("next", nextValue);
    	Gson gson = new Gson();
    	return gson.toJson(jsonMap);
    }
    
    
    private List<Map<String, String>> generateImages(String tenantId, int startId, int qtdImages){
    	String tenantId2 = tenantId + "2";
    	List<Map<String, String>> myList = new ArrayList<Map<String, String>>();
    	
    	qtdImages /= 2;
    	
    	for (int i = 0; i < qtdImages; i++) {
    		Map <String, String> image = new HashMap<String, String>();
    		image.put("visibility", i % 2 == 0? "public" : "private");
    		image.put("owner", i < qtdImages / 2 ? tenantId2 : tenantId);
    		image.put("id", "id" + Integer.toString(i + startId));
    		image.put("name", "name" + Integer.toString(i + startId));
    		myList.add(image);
    	}
    	
    	for (int i = 0; i < qtdImages; i++) {
    		Map <String, String> image = new HashMap<String, String>();
    		image.put("visibility", i % 2 == 0? "community" : "shared");
    		image.put("owner", i < qtdImages / 2 ? tenantId2 : tenantId);
    		image.put("id", "id" + Integer.toString(qtdImages + i + startId));
    		image.put("name", "name" + Integer.toString(qtdImages + i + startId));
    		myList.add(image);
    	}
    	
    	return myList;
    }
    
    private Map<String, String> getPublicImages(List<Map<String, String>> arrayList) {
    	Map<String, String> imageMap = new HashMap<String, String>();
    	for (Map<String, String> image: arrayList) {
    		if (image.get("visibility").equals("public")) {
    			imageMap.put(image.get("id"), image.get("name"));
    		}
    	}
    	return imageMap;
    }
    
    private Map<String, String> getPrivateImagesFromProject(List<Map<String, String>> arrayList, String tenantId) {
    	Map<String, String> imageMap = new HashMap<String, String>();
    	for (Map<String, String> image: arrayList) {
    		if (image.get("visibility").equals("private") && image.get("owner").equals(tenantId)) {
    			imageMap.put(image.get("id"), image.get("name"));
    		}
    	}
    	return imageMap;
    }
    
    @Test
    public void getImageTest() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	String imageId = "image-id";
    	String imageGlancev2UrlKey = "image-url-key";
    	String endpoint = 
    				imageGlancev2UrlKey
	                + OpenStackImagePlugin.COMPUTE_V2_API_ENDPOINT
	                + OpenStackImagePlugin.SUFFIX
	                + "/"
	                + imageId;
    	Image expectedImage = new Image(FAKE_IMAGE_ID, FAKE_IMAGE_NAME, FAKE_SIZE, FAKE_MIN_DISK, FAKE_MIN_RAM, OpenStackImagePlugin.ACTIVE_STATE);
    	
    	String jsonResponse = getImageJsonFromImage(expectedImage);

    	Mockito.when(this.properties.getProperty(OpenStackImagePlugin.IMAGE_GLANCEV2_URL_KEY)).thenReturn(imageGlancev2UrlKey);
    	Mockito.when(this.client.doGetRequest(endpoint, this.token)).thenReturn(jsonResponse);
    	
    	Image imagePluginOuput = this.plugin.getImage(imageId, this.token);
    	Assert.assertEquals(expectedImage, imagePluginOuput);
    }
    
    private String getImageJsonFromImage(Image image) {
    	Map<String, String> jsonMap = new HashMap<String, String>();
    	jsonMap.put(OpenStackImagePlugin.ID_JSON, image.getId());
    	jsonMap.put(OpenStackImagePlugin.NAME_JSON, image.getName());
    	jsonMap.put(OpenStackImagePlugin.SIZE_JSON, Long.toString(image.getSize()));
    	jsonMap.put(OpenStackImagePlugin.MIN_DISK_JSON, Long.toString(image.getMinDisk()));
    	jsonMap.put(OpenStackImagePlugin.MIN_RAM_JSON, Long.toString(image.getMinRam()));
    	jsonMap.put(OpenStackImagePlugin.STATUS, image.getStatus());
    	Gson gson = new Gson();
    	String jsonResponse = gson.toJson(jsonMap);
    	return jsonResponse;
    }
    
    @Test
    public void getImageWhenStateIsNotActivatedTest() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	String imageId = "image-id";
    	String imageGlancev2UrlKey = "image-url-key";
    	String endpoint = 
    				imageGlancev2UrlKey
	                + OpenStackImagePlugin.COMPUTE_V2_API_ENDPOINT
	                + OpenStackImagePlugin.SUFFIX
	                + "/"
	                + imageId;
    	Image image = new Image(FAKE_IMAGE_ID, FAKE_IMAGE_NAME, FAKE_SIZE, FAKE_MIN_DISK, FAKE_MIN_RAM, "it_is_not_activated");
    	String jsonResponse = getImageJsonFromImage(image);

    	Mockito.when(this.properties.getProperty(OpenStackImagePlugin.IMAGE_GLANCEV2_URL_KEY)).thenReturn(imageGlancev2UrlKey);
    	Mockito.when(this.client.doGetRequest(endpoint, this.token)).thenReturn(jsonResponse);
    	
    	Image imagePluginOuput = this.plugin.getImage(imageId, this.token);
    	Image expectedPluginOutput = null;
    	Assert.assertEquals(expectedPluginOutput, imagePluginOuput);
    }
    
    @Test (expected = FogbowManagerException.class)
    public void getImageWhenForbiddenTest() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	String imageId = "image-id";
    	Mockito.when(this.properties.getProperty(OpenStackImagePlugin.IMAGE_GLANCEV2_URL_KEY)).thenReturn("");
    	HttpResponseException httpResponseException = new HttpResponseException(HttpStatus.SC_FORBIDDEN, "");
    	Mockito.when(this.client.doGetRequest(Mockito.anyString(), Mockito.anyObject())).thenThrow(httpResponseException);
    	this.plugin.getImage(imageId, this.token);
    }
    
    @Test (expected = UnexpectedException.class)
    public void getImageWhenUnexpectedExceptionTest() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	String imageId = "image-id";
    	int unexpectedHttpStatus = -1;
    	Mockito.when(this.properties.getProperty(OpenStackImagePlugin.IMAGE_GLANCEV2_URL_KEY)).thenReturn("");
    	HttpResponseException httpResponseException = new HttpResponseException(unexpectedHttpStatus, "");
    	Mockito.when(this.client.doGetRequest(Mockito.anyString(), Mockito.anyObject())).thenThrow(httpResponseException);
    	this.plugin.getImage(imageId, this.token);
    }
    
    @Test (expected = FogbowManagerException.class)
    public void getAllImagesWhenForbiddenTest() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	Mockito.when(this.properties.getProperty(OpenStackImagePlugin.IMAGE_GLANCEV2_URL_KEY)).thenReturn("");
    	HttpResponseException httpResponseException = new HttpResponseException(HttpStatus.SC_FORBIDDEN, "");
    	Mockito.when(this.client.doGetRequest(Mockito.anyString(), Mockito.anyObject())).thenThrow(httpResponseException);
    	this.plugin.getAllImages(this.token);
    }
    
    @Test (expected = UnexpectedException.class)
    public void getAllImagesWhenUnexpectedExceptionTest() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	int unexpectedHttpStatus = -1;
    	Mockito.when(this.properties.getProperty(OpenStackImagePlugin.IMAGE_GLANCEV2_URL_KEY)).thenReturn("");
    	HttpResponseException httpResponseException = new HttpResponseException(unexpectedHttpStatus, "");
    	Mockito.when(this.client.doGetRequest(Mockito.anyString(), Mockito.anyObject())).thenThrow(httpResponseException);
    	this.plugin.getAllImages(this.token);
    }
    
    @Test (expected = FogbowManagerException.class)
    public void getAllImagesWithPaginationWhenForbiddenTest() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	String tenantId = "tenant-id";
    	String imageGlancev2UrlKey = "image-url-key";
    	String nextUrl1 = "next-url1";
    	
    	String endpoint1 = 
    				imageGlancev2UrlKey
	                + OpenStackImagePlugin.COMPUTE_V2_API_ENDPOINT
	                + OpenStackImagePlugin.SUFFIX
	                + OpenStackImagePlugin.QUERY_ACTIVE_IMAGES;
    	
    	String endpoint2 = 
    				imageGlancev2UrlKey
	                + nextUrl1;
    
    	List<Map<String, String>> generatedImages1 = generateImages(tenantId, 0, 100);
    
    	String jsonResponse1 = getImagesJsonWithNext(generatedImages1, nextUrl1);
    	
    	Token localToken = new Token();
    	Map<String, String> attributes = new HashMap<String, String>();
    	attributes.put(OpenStackImagePlugin.TENANT_ID, tenantId);
    	localToken.setAttributes(attributes);
    	Mockito.when(this.properties.getProperty(OpenStackImagePlugin.IMAGE_GLANCEV2_URL_KEY)).thenReturn(imageGlancev2UrlKey);
    	Mockito.when(this.client.doGetRequest(endpoint1, localToken)).thenReturn(jsonResponse1);
    	
    	HttpResponseException httpResponseException = new HttpResponseException(HttpStatus.SC_FORBIDDEN, "");
    	Mockito.when(this.client.doGetRequest(endpoint2, localToken)).thenThrow(httpResponseException);
    	
    	this.plugin.getAllImages(localToken);
    }
    
    @Test (expected = UnexpectedException.class)
    public void getAllImagesWithPaginationWhenUnexpectedExceptionTest() throws FogbowManagerException, UnexpectedException, HttpResponseException {
    	String tenantId = "tenant-id";
    	String imageGlancev2UrlKey = "image-url-key";
    	String nextUrl1 = "next-url1";
    	
    	String endpoint1 = 
    				imageGlancev2UrlKey
	                + OpenStackImagePlugin.COMPUTE_V2_API_ENDPOINT
	                + OpenStackImagePlugin.SUFFIX
	                + OpenStackImagePlugin.QUERY_ACTIVE_IMAGES;
    	
    	String endpoint2 = 
    				imageGlancev2UrlKey
	                + nextUrl1;
    
    	List<Map<String, String>> generatedImages1 = generateImages(tenantId, 0, 100);
    
    	String jsonResponse1 = getImagesJsonWithNext(generatedImages1, nextUrl1);
    	
    	Token localToken = new Token();
    	Map<String, String> attributes = new HashMap<String, String>();
    	attributes.put(OpenStackImagePlugin.TENANT_ID, tenantId);
    	localToken.setAttributes(attributes);
    	Mockito.when(this.properties.getProperty(OpenStackImagePlugin.IMAGE_GLANCEV2_URL_KEY)).thenReturn(imageGlancev2UrlKey);
    	Mockito.when(this.client.doGetRequest(endpoint1, localToken)).thenReturn(jsonResponse1);
    	int unexpectedHttpStatus = -1;
    	HttpResponseException httpResponseException = new HttpResponseException(unexpectedHttpStatus, "");
    	Mockito.when(this.client.doGetRequest(endpoint2, localToken)).thenThrow(httpResponseException);
    	
    	this.plugin.getAllImages(localToken);
    }
    
}
