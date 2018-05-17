package org.fogbowcloud.manager.core.rest.controllers;

import java.util.List;

import org.fogbowcloud.manager.core.controllers.ApplicationController;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping(value = "compute")
public class ComputeOrdersController {

	private ApplicationController applicationController = ApplicationController.getInstance();
	private static final Logger LOGGER = LoggerFactory.getLogger(ComputeOrdersController.class);


	private final String ACCESS_ID_HEADER_KEY = "accessId";
	private final String LOCAL_TOKEN_ID_HEADER_KEY = "localTokenId";

	// ExceptionHandlerController handles the possible problems in request
	@RequestMapping(method = RequestMethod.POST)
	public ResponseEntity<Order> createCompute(@RequestBody ComputeOrder computeOrder,
		@RequestHeader(ACCESS_ID_HEADER_KEY) String accessId, @RequestHeader(LOCAL_TOKEN_ID_HEADER_KEY) String localTokenId)
			throws Exception {
		LOGGER.info("New compute order request received.");

		// The applicationController is being loaded here, because the getInstance that was used in the variable declaration
		// disallows a static mock on this method.
		this.applicationController = ApplicationController.getInstance();
		this.applicationController.newOrderRequest(computeOrder, accessId, localTokenId);
		return new ResponseEntity<Order>(HttpStatus.CREATED);
	}

	@RequestMapping(method = RequestMethod.GET)
	public ResponseEntity<List<Order>> getAllCompute(
			@RequestHeader(value = "accessId") String accessId
	) throws Exception {
		List<Order> orders = this.applicationController.getAllComputes(accessId, OrderType.COMPUTE);
		return new ResponseEntity<>(orders, HttpStatus.OK);
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	public ResponseEntity<Order> getComputeById(
			@PathVariable String id,
			@RequestHeader(value = "accessId") String accessId
	) throws Exception {
		Order order = this.applicationController.getOrderById(id, accessId, OrderType.COMPUTE);
		return new ResponseEntity<Order>(order, HttpStatus.OK);
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
	public ResponseEntity<Boolean> deleteCompute(@PathVariable String id) {
		return new ResponseEntity<Boolean>(HttpStatus.OK);
	}

}
