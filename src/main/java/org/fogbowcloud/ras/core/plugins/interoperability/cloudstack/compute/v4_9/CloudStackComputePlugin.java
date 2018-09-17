package org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.compute.v4_9;

import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import org.fogbowcloud.ras.core.HomeDir;
import org.fogbowcloud.ras.core.constants.DefaultConfigurationConstants;
import org.fogbowcloud.ras.core.constants.Messages;
import org.fogbowcloud.ras.core.exceptions.*;
import org.fogbowcloud.ras.core.models.ResourceType;
import org.fogbowcloud.ras.core.models.instances.ComputeInstance;
import org.fogbowcloud.ras.core.models.instances.InstanceState;
import org.fogbowcloud.ras.core.models.orders.ComputeOrder;
import org.fogbowcloud.ras.core.models.tokens.CloudStackToken;
import org.fogbowcloud.ras.core.plugins.interoperability.ComputePlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.CloudStackHttpToFogbowRasExceptionMapper;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.CloudStackStateMapper;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.CloudStackUrlUtil;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetAllDiskOfferingsRequest;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetAllDiskOfferingsResponse;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetVolumeRequest;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetVolumeResponse;
import org.fogbowcloud.ras.util.PropertiesUtil;
import org.fogbowcloud.ras.util.connectivity.HttpRequestClientUtil;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.publicip.v4_9.CloudStackPublicIpPlugin.DEFAULT_NETWORK_ID_KEY;

public class CloudStackComputePlugin implements ComputePlugin<CloudStackToken> {
    private static final Logger LOGGER = Logger.getLogger(CloudStackComputePlugin.class);

    public static final String ZONE_ID_KEY = "zone_id";
    public static final String EXPUNGE_ON_DESTROY_KEY = "expunge_on_destroy";
    public static final String DEFAULT_VOLUME_TYPE = "ROOT";
    public static final String FOGBOW_INSTANCE_NAME = "fogbow-compute-instance-";

    private HttpRequestClientUtil client;
    private String zoneId;
    private String expungeOnDestroy;
    private String defaultNetworkId;

    public CloudStackComputePlugin() throws FatalErrorException {
        String cloudStackConfFilePath = HomeDir.getPath() + File.separator
                + DefaultConfigurationConstants.CLOUDSTACK_CONF_FILE_NAME;

        Properties properties = PropertiesUtil.readProperties(cloudStackConfFilePath);

        this.zoneId = properties.getProperty(ZONE_ID_KEY);
        this.expungeOnDestroy = properties.getProperty(EXPUNGE_ON_DESTROY_KEY, "true");
        this.defaultNetworkId = properties.getProperty(DEFAULT_NETWORK_ID_KEY);

        this.client = new HttpRequestClientUtil();
    }

    @Override
    public String requestInstance(ComputeOrder computeOrder, CloudStackToken cloudStackToken)
            throws FogbowRasException, UnexpectedException {
        String templateId = computeOrder.getImageId();
        if (templateId == null || this.zoneId == null || this.defaultNetworkId == null) {
            LOGGER.error(Messages.Error.UNABLE_TO_COMPLETE_REQUEST);
            throw new InvalidParameterException();
        }

        // FIXME(pauloewerton): should this be creating a cloud-init script for cloudstack?
        String userData = null;
        if (computeOrder.getUserData() != null) {
            userData = computeOrder.getUserData().getExtraUserDataFileContent();
            try {
                userData = Base64.getEncoder().encodeToString(userData.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                LOGGER.warn(Messages.Warn.UNABLE_TO_ENCODE_EXTRA_USER_DATA);
            }
        }

        String networksId = resolveNetworksId(computeOrder);

        String serviceOfferingId = getServiceOfferingId(computeOrder.getvCPU(), computeOrder.getMemory(),
                cloudStackToken);
        if (serviceOfferingId == null) {
            throw new NoAvailableResourcesException();
        }

        int disk = computeOrder.getDisk();
        // NOTE(pauloewerton): cloudstack allows creating a vm without explicitly choosing a disk size. in that case,
        // a minimum root disk for the selected template is created. also zeroing disk param in case no minimum disk
        // offering is found.
        String diskOfferingId = disk > 0 ? getDiskOfferingId(disk, cloudStackToken) : null;

        String keypairName = getKeypairName(computeOrder.getPublicKey(), cloudStackToken);

        String instanceName = computeOrder.getName();
        if (instanceName == null) instanceName = FOGBOW_INSTANCE_NAME + getRandomUUID();

        // NOTE(pauloewerton): diskofferingid and hypervisor are required in case of ISO image. i haven't
        // found any clue pointing that ISO images were being used in mono though.
        DeployVirtualMachineRequest request = new DeployVirtualMachineRequest.Builder()
                .serviceOfferingId(serviceOfferingId)
                .templateId(templateId)
                .zoneId(this.zoneId)
                .name(instanceName)
                .diskOfferingId(diskOfferingId)
                .userData(userData)
                .keypair(keypairName)
                .networksId(networksId)
                .build();

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowRasExceptionMapper.map(e);
        } finally {
            if (keypairName != null) {
                deleteKeypairName(keypairName, cloudStackToken);
            }
        }

        DeployVirtualMachineResponse response = DeployVirtualMachineResponse.fromJson(jsonResponse);

        return response.getId();
    }

    @Override
    public ComputeInstance getInstance(String computeInstanceId, CloudStackToken cloudStackToken)
            throws FogbowRasException {
        GetVirtualMachineRequest request = new GetVirtualMachineRequest.Builder()
                .id(computeInstanceId)
                .build();

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowRasExceptionMapper.map(e);
        }

        GetVirtualMachineResponse computeResponse = GetVirtualMachineResponse.fromJson(jsonResponse);
        List<GetVirtualMachineResponse.VirtualMachine> vms = computeResponse.getVirtualMachines();
        if (vms != null) {
            return getComputeInstance(vms.get(0), cloudStackToken);
        } else {
            throw new InstanceNotFoundException();
        }
    }

    @Override
    public void deleteInstance(String computeInstanceId, CloudStackToken cloudStackToken)
            throws FogbowRasException, UnexpectedException {
        DestroyVirtualMachineRequest request = new DestroyVirtualMachineRequest.Builder()
                .id(computeInstanceId)
                .expunge(this.expungeOnDestroy)
                .build();

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        try {
            this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            LOGGER.error(String.format(Messages.Error.UNABLE_TO_DELETE_INSTANCE, computeInstanceId));
            CloudStackHttpToFogbowRasExceptionMapper.map(e);
        }

        LOGGER.info(String.format(Messages.Info.DELETING_INSTANCE, computeInstanceId, cloudStackToken.getTokenValue()));
    }

    private String resolveNetworksId(ComputeOrder computeOrder) {
        List<String> requestedNetworksId = new ArrayList<>();

        requestedNetworksId.add(this.defaultNetworkId);
        requestedNetworksId.addAll(computeOrder.getNetworksId());
        computeOrder.setNetworksId(requestedNetworksId);

        return StringUtils.join(requestedNetworksId, ",");
    }

    private String getServiceOfferingId(int vcpusRequirement, int memoryRequirement, CloudStackToken cloudStackToken)
            throws FogbowRasException {
        GetAllServiceOfferingsResponse serviceOfferingsResponse = getServiceOfferings(cloudStackToken);
        List<GetAllServiceOfferingsResponse.ServiceOffering> serviceOfferings = serviceOfferingsResponse.
                getServiceOfferings();

        if (serviceOfferings != null) {
            for (GetAllServiceOfferingsResponse.ServiceOffering serviceOffering : serviceOfferings) {
                if (serviceOffering.getCpuNumber() >= vcpusRequirement &&
                        serviceOffering.getMemory() >= memoryRequirement) {
                    return serviceOffering.getId();
                }
            }
        } else {
            throw new NoAvailableResourcesException();
        }

        return null;
    }

    private GetAllServiceOfferingsResponse getServiceOfferings(CloudStackToken cloudStackToken)
            throws FogbowRasException {
        GetAllServiceOfferingsRequest request = new GetAllServiceOfferingsRequest.Builder().build();
        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowRasExceptionMapper.map(e);
        }

        GetAllServiceOfferingsResponse serviceOfferingsResponse = GetAllServiceOfferingsResponse.fromJson(jsonResponse);

        return serviceOfferingsResponse;
    }

    private String getDiskOfferingId(int diskSize, CloudStackToken cloudStackToken) throws FogbowRasException {
        GetAllDiskOfferingsResponse diskOfferingsResponse = getDiskOfferings(cloudStackToken);
        List<GetAllDiskOfferingsResponse.DiskOffering> diskOfferings = diskOfferingsResponse.getDiskOfferings();

        if (diskOfferings != null) {
            for (GetAllDiskOfferingsResponse.DiskOffering diskOffering : diskOfferings) {
                if (diskOffering.getDiskSize() >= diskSize) {
                    return diskOffering.getId();
                }
            }
        }

        return null;
    }

    private GetAllDiskOfferingsResponse getDiskOfferings(CloudStackToken cloudStackToken)
            throws FogbowRasException {
        GetAllDiskOfferingsRequest request = new GetAllDiskOfferingsRequest.Builder().build();
        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowRasExceptionMapper.map(e);
        }

        GetAllDiskOfferingsResponse diskOfferingsResponse = GetAllDiskOfferingsResponse.fromJson(jsonResponse);

        return diskOfferingsResponse;
    }

    private String getKeypairName(String publicKey, CloudStackToken cloudStackToken) throws FogbowRasException, UnexpectedException {
        String keyName = null;

        if (publicKey != null && !publicKey.isEmpty()) {
            keyName = getRandomUUID();

            RegisterSSHKeypairRequest request = new RegisterSSHKeypairRequest.Builder()
                    .name(keyName)
                    .publicKey(publicKey)
                    .build();

            CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

            String jsonResponse = null;
            try {
                jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
            } catch (HttpResponseException e) {
                CloudStackHttpToFogbowRasExceptionMapper.map(e);
            }

            RegisterSSHKeypairResponse keypairResponse = RegisterSSHKeypairResponse.fromJson(jsonResponse);
            keyName = keypairResponse.getKeypairName();
        }

        return keyName;
    }

    private void deleteKeypairName(String keypairName, CloudStackToken cloudStackToken)
            throws FogbowRasException, UnexpectedException {
        String failureMessage = "Could not delete keypair " + keypairName;

        DeleteSSHKeypairRequest request = new DeleteSSHKeypairRequest.Builder()
                .name(keypairName)
                .build();

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            LOGGER.error(failureMessage);
            CloudStackHttpToFogbowRasExceptionMapper.map(e);
        }

        DeleteSSHKeypairResponse keypairResponse = DeleteSSHKeypairResponse.fromJson(jsonResponse);
        String success = keypairResponse.getSuccess();

        if (!success.equalsIgnoreCase("true")) {
            LOGGER.warn(failureMessage);
        } else {
            LOGGER.info("Deleted keypair " + keypairName);
        }
    }

    private ComputeInstance getComputeInstance(GetVirtualMachineResponse.VirtualMachine vm, CloudStackToken cloudStackToken) {
        String instanceId = vm.getId();
        String hostName = vm.getName();
        int vcpusCount = vm.getCpuNumber();
        int memory = vm.getMemory();

        int disk = -1;
        try {
            disk = getVirtualMachineDiskSize(instanceId, cloudStackToken);
        } catch (FogbowRasException e) {
            LOGGER.warn(String.format(Messages.Warn.UNABLE_TO_RETRIEVE_ROOT_VOLUME, vm.getId()));
        }

        String cloudStackState = vm.getState();
        InstanceState fogbowState = CloudStackStateMapper.map(ResourceType.COMPUTE, cloudStackState);

        GetVirtualMachineResponse.Nic[] addresses = vm.getNic();
        String address = "";
        if (addresses != null) {
            boolean firstAddressEmpty = addresses == null || addresses.length == 0 || addresses[0].getIpAddress() == null;
            address = firstAddressEmpty ? "" : addresses[0].getIpAddress();
        }

        ComputeInstance computeInstance = new ComputeInstance(
                instanceId, fogbowState, hostName, vcpusCount, memory, disk, address);

        return computeInstance;
    }

    private int getVirtualMachineDiskSize(String virtualMachineId, CloudStackToken cloudStackToken)
            throws FogbowRasException {
        GetVolumeRequest request = new GetVolumeRequest.Builder()
                .virtualMachineId(virtualMachineId)
                .type(DEFAULT_VOLUME_TYPE)
                .build();

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowRasExceptionMapper.map(e);
        }

        GetVolumeResponse volumeResponse = GetVolumeResponse.fromJson(jsonResponse);
        List<GetVolumeResponse.Volume> volumes = volumeResponse.getVolumes();
        if (volumes != null) {
            long sizeInBytes = volumes.get(0).getSize();
            int sizeInGigabytes = (int) (sizeInBytes / Math.pow(1024, 3));
            return sizeInGigabytes;
        } else {
            throw new InstanceNotFoundException();
        }
    }

    protected String getRandomUUID() {
        return UUID.randomUUID().toString();
    }

    protected void setClient(HttpRequestClientUtil client) {
        this.client = client;
    }
}
