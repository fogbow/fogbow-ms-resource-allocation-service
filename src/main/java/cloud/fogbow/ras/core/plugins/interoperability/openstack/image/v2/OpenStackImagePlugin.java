package cloud.fogbow.ras.core.plugins.interoperability.openstack.image.v2;

import cloud.fogbow.common.exceptions.FatalErrorException;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.models.OpenStackV3User;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.common.util.connectivity.cloud.openstack.OpenStackHttpClient;
import cloud.fogbow.ras.api.http.response.ImageInstance;
import cloud.fogbow.ras.core.plugins.interoperability.ImagePlugin;
import cloud.fogbow.common.util.connectivity.cloud.openstack.OpenStackHttpToFogbowExceptionMapper;
import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.*;

public class OpenStackImagePlugin implements ImagePlugin<OpenStackV3User> {
    private static final Logger LOGGER = Logger.getLogger(OpenStackImagePlugin.class);

    public static final String IMAGE_GLANCEV2_URL_KEY = "openstack_glance_v2_url";
    public static final String ACTIVE_STATE = "active";
    public static final String PUBLIC_VISIBILITY = "public";
    private static final String PRIVATE_VISIBILITY = "private";
    public static final String QUERY_ACTIVE_IMAGES = "?status=" + ACTIVE_STATE;
    public static final String IMAGE_V2_API_SUFFIX = "images";
    public static final String IMAGE_V2_API_ENDPOINT = "/v2/";
    private Properties properties;
    private OpenStackHttpClient client;

    public OpenStackImagePlugin(String confFilePath) throws FatalErrorException {
        this.properties = PropertiesUtil.readProperties(confFilePath);
        this.client = new OpenStackHttpClient();
    }

    @Override
    public Map<String, String> getAllImages(OpenStackV3User cloudUser) throws FogbowException {
        Map<String, String> availableImages = getAvailableImages(cloudUser);
        return availableImages;
    }

    @Override
    public ImageInstance getImage(String imageId, OpenStackV3User cloudUser) throws FogbowException {
        GetImageResponse getImageResponse = getImageResponse(imageId, cloudUser);
        String id = getImageResponse.getId();
        String status = getImageResponse.getStatus();
        if (status.equals(ACTIVE_STATE)) {
            ImageInstance imageInstance = new ImageInstance(id,
                    getImageResponse.getName(),
                    getImageResponse.getSize(),
                    getImageResponse.getMinDisk(),
                    getImageResponse.getMinRam(),
                    status
            );
            return imageInstance;
        }

        return null;
    }

    private GetImageResponse getImageResponse(String imageId, OpenStackV3User cloudUser) throws FogbowException {
        String jsonResponse = null;
        try {
            String endpoint = this.properties.getProperty(IMAGE_GLANCEV2_URL_KEY)
                    + IMAGE_V2_API_ENDPOINT + IMAGE_V2_API_SUFFIX + File.separator + imageId;
            jsonResponse = this.client.doGetRequest(endpoint, cloudUser);
        } catch (HttpResponseException e) {
            OpenStackHttpToFogbowExceptionMapper.map(e);
        }

        return GetImageResponse.fromJson(jsonResponse);
    }

    private List<GetImageResponse> getImagesResponse(OpenStackV3User cloudUser) throws FogbowException {
        String jsonResponse = null;
        try {
            String endpoint = this.properties.getProperty(IMAGE_GLANCEV2_URL_KEY)
                    + IMAGE_V2_API_ENDPOINT + IMAGE_V2_API_SUFFIX + QUERY_ACTIVE_IMAGES;
            jsonResponse = this.client.doGetRequest(endpoint, cloudUser);
        } catch (HttpResponseException e) {
            OpenStackHttpToFogbowExceptionMapper.map(e);
        }
        GetAllImagesResponse getAllImagesResponse = getAllImagesResponse(jsonResponse);

        List<GetImageResponse> getImageResponses = new ArrayList<GetImageResponse>();
        getImageResponses.addAll(getAllImagesResponse.getImages());
        getNextImageListResponseByPagination(cloudUser, getAllImagesResponse, getImageResponses);
        return getImageResponses;
    }

    private void getNextImageListResponseByPagination(OpenStackV3User cloudUser, GetAllImagesResponse getAllImagesResponse,
                                                      List<GetImageResponse> imagesJson) throws FogbowException {

        String next = getAllImagesResponse.getNext();
        if (next != null && !next.isEmpty()) {
            String endpoint = this.properties.getProperty(IMAGE_GLANCEV2_URL_KEY) + next;
            String jsonResponse = null;
            try {
                jsonResponse = this.client.doGetRequest(endpoint, cloudUser);
            } catch (HttpResponseException e) {
                OpenStackHttpToFogbowExceptionMapper.map(e);
            }
            getAllImagesResponse = getAllImagesResponse(jsonResponse);

            imagesJson.addAll(getAllImagesResponse.getImages());
            getNextImageListResponseByPagination(cloudUser, getAllImagesResponse, imagesJson);
        }
    }

    private List<GetImageResponse> getPublicImagesResponse(List<GetImageResponse> imagesResponse) {
        List<GetImageResponse> publicImagesResponse = new ArrayList<GetImageResponse>();
        for (GetImageResponse getImageResponse : imagesResponse) {
            if (getImageResponse.getVisibility().equals(PUBLIC_VISIBILITY)) {
                publicImagesResponse.add(getImageResponse);
            }
        }
        return publicImagesResponse;
    }

    private List<GetImageResponse> getPrivateImagesResponse(List<GetImageResponse> imagesResponse, String tenantId) {
        List<GetImageResponse> privateImagesResponse = new ArrayList<GetImageResponse>();
        for (GetImageResponse getImageResponse : imagesResponse) {
            if (getImageResponse.getOwner().equals(tenantId)
                    && getImageResponse.getVisibility().equals(PRIVATE_VISIBILITY)) {
                privateImagesResponse.add(getImageResponse);
            }
        }
        return privateImagesResponse;
    }

    private Map<String, String> getAvailableImages(OpenStackV3User cloudUser) throws FogbowException {
        Map<String, String> availableImages = new HashMap<String, String>();

        List<GetImageResponse> allImagesResponse = getImagesResponse(cloudUser);
        List<GetImageResponse> filteredImagesResponse = filterImagesResponse(cloudUser.getProjectId(), allImagesResponse);

        for (GetImageResponse getImageResponse : filteredImagesResponse) {
            availableImages.put(getImageResponse.getId(), getImageResponse.getName());
        }
        return availableImages;
    }

    private List<GetImageResponse> filterImagesResponse(String tenantId, List<GetImageResponse> allImagesResponse) {
        List<GetImageResponse> filteredImages = new ArrayList<GetImageResponse>();
        filteredImages.addAll(getPublicImagesResponse(allImagesResponse));
        filteredImages.addAll(getPrivateImagesResponse(allImagesResponse, tenantId));
        return filteredImages;
    }

    protected void setClient(OpenStackHttpClient client) {
        this.client = client;
    }

    private GetAllImagesResponse getAllImagesResponse(String json) {
        return GetAllImagesResponse.fromJson(json);
    }

    protected void setProperties(Properties properties) {
        this.properties = properties;
    }
}
