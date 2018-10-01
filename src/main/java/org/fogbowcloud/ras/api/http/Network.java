package org.fogbowcloud.ras.api.http;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.log4j.Logger;
import org.fogbowcloud.ras.core.ApplicationFacade;
import org.fogbowcloud.ras.core.constants.ApiDocumentation;
import org.fogbowcloud.ras.core.constants.Messages;
import org.fogbowcloud.ras.core.exceptions.FogbowRasException;
import org.fogbowcloud.ras.core.exceptions.UnexpectedException;
import org.fogbowcloud.ras.core.models.InstanceStatus;
import org.fogbowcloud.ras.core.models.ResourceType;
import org.fogbowcloud.ras.core.models.instances.NetworkInstance;
import org.fogbowcloud.ras.core.models.orders.NetworkOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping(value = Network.NETWORK_ENDPOINT)
@Api(description = ApiDocumentation.Network.API)
public class Network {

    public static final String NETWORK_ENDPOINT = "networks";
    public static final String FEDERATION_TOKEN_VALUE_HEADER_KEY = "federationTokenValue";
    public static final String ORDER_CONTROLLER_TYPE = "network";

    private final Logger LOGGER = Logger.getLogger(Network.class);

    @ApiOperation(value = ApiDocumentation.Network.CREATE_OPERATION)
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity<String> createNetwork(
            @ApiParam(value = ApiDocumentation.Network.CREATE_REQUEST_BODY)
            @RequestBody NetworkOrder networkOrder,
            @ApiParam(value = ApiDocumentation.CommonParameters.FEDERATION_TOKEN)
            @RequestHeader(required = false, value = FEDERATION_TOKEN_VALUE_HEADER_KEY) String federationTokenValue)
            throws FogbowRasException, UnexpectedException {
        LOGGER.info(String.format(Messages.Info.RECEIVING_CREATE_REQUEST, ORDER_CONTROLLER_TYPE));
        String networkId = ApplicationFacade.getInstance().createNetwork(networkOrder, federationTokenValue);
        return new ResponseEntity<String>(networkId, HttpStatus.CREATED);
    }

    @ApiOperation(value = ApiDocumentation.Network.GET_OPERATION)
    @RequestMapping(value = "/status", method = RequestMethod.GET)
    public ResponseEntity<List<InstanceStatus>> getAllNetworksStatus(
            @ApiParam(value = ApiDocumentation.CommonParameters.FEDERATION_TOKEN)
            @RequestHeader(required = false, value = FEDERATION_TOKEN_VALUE_HEADER_KEY) String federationTokenValue)
            throws Exception {
        LOGGER.info(String.format(Messages.Info.RECEIVING_GET_ALL_REQUEST, ORDER_CONTROLLER_TYPE));
        List<InstanceStatus> networkInstanceStatus =
                ApplicationFacade.getInstance().getAllInstancesStatus(federationTokenValue, ResourceType.NETWORK);
        return new ResponseEntity<>(networkInstanceStatus, HttpStatus.OK);
    }

    @ApiOperation(value = ApiDocumentation.Network.GET_BY_ID_OPERATION)
    @RequestMapping(value = "/{networkId}", method = RequestMethod.GET)
    public ResponseEntity<NetworkInstance> getNetwork(
            @ApiParam(value = ApiDocumentation.Network.ID)
            @PathVariable String networkId,
            @ApiParam(value = ApiDocumentation.CommonParameters.FEDERATION_TOKEN)
            @RequestHeader(required = false, value = FEDERATION_TOKEN_VALUE_HEADER_KEY) String federationTokenValue)
            throws Exception {
        LOGGER.info(String.format(Messages.Info.RECEIVING_GET_REQUEST, ORDER_CONTROLLER_TYPE, networkId));
        NetworkInstance networkInstance = ApplicationFacade.getInstance().getNetwork(networkId, federationTokenValue);
        return new ResponseEntity<>(networkInstance, HttpStatus.OK);
    }

    @ApiOperation(value = ApiDocumentation.Network.DELETE_OPERATION)
    @RequestMapping(value = "/{networkId}", method = RequestMethod.DELETE)
    public ResponseEntity<Boolean> deleteNetwork(
            @ApiParam(value = ApiDocumentation.Network.ID)
            @PathVariable String networkId,
            @ApiParam(value = ApiDocumentation.CommonParameters.FEDERATION_TOKEN)
            @RequestHeader(required = false, value = FEDERATION_TOKEN_VALUE_HEADER_KEY) String federationTokenValue)
            throws Exception {
        LOGGER.info(String.format(Messages.Info.RECEIVING_DELETE_REQUEST, ORDER_CONTROLLER_TYPE, networkId));
        ApplicationFacade.getInstance().deleteNetwork(networkId, federationTokenValue);
        return new ResponseEntity<Boolean>(HttpStatus.OK);
    }
}
