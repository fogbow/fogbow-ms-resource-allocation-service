package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.exceptions.NoAvailableResourcesException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.CloudStackUser;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackHttpClient;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackHttpToFogbowExceptionMapper;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackUrlUtil;
import cloud.fogbow.ras.constants.SystemConstants;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.api.http.response.InstanceState;
import cloud.fogbow.ras.api.http.response.VolumeInstance;
import cloud.fogbow.ras.core.models.orders.VolumeOrder;
import cloud.fogbow.ras.core.plugins.interoperability.VolumePlugin;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackStateMapper;
import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;

import java.util.*;

public class CloudStackVolumePlugin implements VolumePlugin<CloudStackUser> {
    private static final Logger LOGGER = Logger.getLogger(CloudStackVolumePlugin.class);

    private static final String CLOUDSTACK_URL = "cloudstack_api_url";
    private static final String CLOUDSTACK_ZONE_ID_KEY = "zone_id";
    private static final int FIRST_ELEMENT_POSITION = 0;
    private static final String FOGBOW_TAG_SEPARATOR = ":";
    private CloudStackHttpClient client;
    private boolean diskOfferingCompatible;
    private String zoneId;
    private Properties properties;
    private String cloudStackUrl;

    public CloudStackVolumePlugin(String confFilePath) {
        this.properties = PropertiesUtil.readProperties(confFilePath);
        this.cloudStackUrl = properties.getProperty(CLOUDSTACK_URL);
        this.zoneId = properties.getProperty(CLOUDSTACK_ZONE_ID_KEY);
        this.client = new CloudStackHttpClient();
    }

    @Override
    public boolean isReady(String cloudState) {
        return CloudStackStateMapper.map(ResourceType.VOLUME, cloudState).equals(InstanceState.READY);
    }

    @Override
    public boolean hasFailed(String cloudState) {
        return CloudStackStateMapper.map(ResourceType.VOLUME, cloudState).equals(InstanceState.FAILED);
    }

    @Override
    public String requestInstance(VolumeOrder volumeOrder, CloudStackUser cloudUser) throws FogbowException {
        String diskOfferingId = getDiskOfferingId(volumeOrder, cloudUser);

        if (diskOfferingId == null) throw new NoAvailableResourcesException();

        CreateVolumeRequest request;
        if (isDiskOfferingCompatible()) {
            request = createVolumeCompatible(volumeOrder, diskOfferingId);
        } else {
            request = createVolumeCustomized(volumeOrder, diskOfferingId);
        }

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudUser);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }
        CreateVolumeResponse volumeResponse = CreateVolumeResponse.fromJson(jsonResponse);
        String volumeId = volumeResponse.getId();
        return volumeId;
    }

    @Override
    public VolumeInstance getInstance(VolumeOrder volumeOrder, CloudStackUser cloudUser) throws FogbowException {
        GetVolumeRequest request = new GetVolumeRequest.Builder()
                .id(volumeOrder.getInstanceId())
                .build(this.cloudStackUrl);
        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudUser);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        GetVolumeResponse response = GetVolumeResponse.fromJson(jsonResponse);
        List<GetVolumeResponse.Volume> volumes = response.getVolumes();

        if (volumes != null && volumes.size() > 0) {
            // since an id were specified, there should be no more than one volume in the response
            return loadInstance(volumes.get(FIRST_ELEMENT_POSITION));
        } else {
            throw new UnexpectedException();
        }
    }

    @Override
    public void deleteInstance(VolumeOrder volumeOrder, CloudStackUser cloudUser) throws FogbowException {

        DeleteVolumeRequest request = new DeleteVolumeRequest.Builder()
                .id(volumeOrder.getInstanceId())
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudUser);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        DeleteVolumeResponse volumeResponse = DeleteVolumeResponse.fromJson(jsonResponse);
        boolean success = volumeResponse.isSuccess();

        if (!success) {
            String message = volumeResponse.getDisplayText();
            throw new UnexpectedException(message);
        }
    }

    private String getDiskOfferingId(VolumeOrder volumeOrder, CloudStackUser cloudUser) throws FogbowException {
        GetAllDiskOfferingsRequest request = new GetAllDiskOfferingsRequest.Builder().build(this.cloudStackUrl);
        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudUser);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        GetAllDiskOfferingsResponse response = GetAllDiskOfferingsResponse.fromJson(jsonResponse);
        List<GetAllDiskOfferingsResponse.DiskOffering> diskOfferings = response.getDiskOfferings();
        List<GetAllDiskOfferingsResponse.DiskOffering> toRemove = new ArrayList<>();

        if (volumeOrder.getRequirements() != null && volumeOrder.getRequirements().size() > 0) {
            for (Map.Entry<String, String> tag : volumeOrder.getRequirements().entrySet()) {
                String concatenatedTag = tag.getKey() + FOGBOW_TAG_SEPARATOR + tag.getValue();

                for (GetAllDiskOfferingsResponse.DiskOffering diskOffering : diskOfferings) {
                    if (diskOffering.getTags() == null) {
                        toRemove.add(diskOffering);
                        continue;
                    }

                    List<String> tags = new ArrayList<>(Arrays.asList(diskOffering.getTags().split(",")));
                    if (!tags.contains(concatenatedTag)) {
                        toRemove.add(diskOffering);
                    }
                }
            }
        }

        diskOfferings.removeAll(toRemove);

        String diskOfferingId = getDiskOfferingIdCompatible(volumeOrder.getVolumeSize(), diskOfferings);

        if (!isDiskOfferingCompatible()) {
            diskOfferingId = getDiskOfferingIdCustomized(diskOfferings);
        }

        return diskOfferingId;
    }

    private String getDiskOfferingIdCustomized(List<GetAllDiskOfferingsResponse.DiskOffering> diskOfferings) {
        boolean customized;
        int size;

        for (GetAllDiskOfferingsResponse.DiskOffering diskOffering : diskOfferings) {
            customized = diskOffering.isCustomized();
            size = diskOffering.getDiskSize();

            if (customized && size == 0) {
                return diskOffering.getId();
            }
        }

        return null;
    }

    private String getDiskOfferingIdCompatible(int volumeSize, List<GetAllDiskOfferingsResponse.DiskOffering> diskOfferings) {
        int size;

        for (GetAllDiskOfferingsResponse.DiskOffering diskOffering : diskOfferings) {
            size = diskOffering.getDiskSize();

            if (size == volumeSize) {
                this.diskOfferingCompatible = true;
                return diskOffering.getId();
            }
        }

        this.diskOfferingCompatible = false;

        return null;
    }

    private CreateVolumeRequest createVolumeCustomized(VolumeOrder volumeOrder, String diskOfferingId) throws InvalidParameterException {

        String instanceName = volumeOrder.getName();
        String name = instanceName == null ? SystemConstants.FOGBOW_INSTANCE_NAME_PREFIX + getRandomUUID() : instanceName;
        String size = String.valueOf(volumeOrder.getVolumeSize());
        return new CreateVolumeRequest.Builder()
                .zoneId(this.zoneId)
                .name(name)
                .diskOfferingId(diskOfferingId)
                .size(size)
                .build(this.cloudStackUrl);
    }

    private CreateVolumeRequest createVolumeCompatible(VolumeOrder volumeOrder, String diskOfferingId) throws InvalidParameterException {

        String instanceName = volumeOrder.getName();
        String name = instanceName == null ? SystemConstants.FOGBOW_INSTANCE_NAME_PREFIX + getRandomUUID() : instanceName;
        return new CreateVolumeRequest.Builder()
                .zoneId(this.zoneId)
                .name(name)
                .diskOfferingId(diskOfferingId)
                .build(this.cloudStackUrl);
    }

    private VolumeInstance loadInstance(GetVolumeResponse.Volume volume) {
        String id = volume.getId();
        String state = volume.getState();
        String name = volume.getName();
        long sizeInBytes = volume.getSize();
        int sizeInGigabytes = (int) (sizeInBytes / Math.pow(1024, 3));

        VolumeInstance volumeInstance = new VolumeInstance(id, state, name, sizeInGigabytes);
        return volumeInstance;
    }

    protected void setClient(CloudStackHttpClient client) {
        this.client = client;
    }

    protected boolean isDiskOfferingCompatible() {
        return diskOfferingCompatible;
    }

    public String getZoneId() {
        return this.zoneId;
    }

    protected String getRandomUUID() {
        return UUID.randomUUID().toString();
    }
}
