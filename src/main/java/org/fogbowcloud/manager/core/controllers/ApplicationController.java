package org.fogbowcloud.manager.core.controllers;

import java.util.Collection;

import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.exceptions.OrderManagementException;
import org.fogbowcloud.manager.core.exceptions.UnauthenticatedException;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.orders.NetworkOrder;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.StorageOrder;
import org.fogbowcloud.manager.core.models.token.Token;
import org.fogbowcloud.manager.core.plugins.identity.exceptions.UnauthorizedException;
import org.fogbowcloud.manager.core.services.AuthenticationService;

public class ApplicationController {

	private static ApplicationController instance;
	private AuthenticationService authenticationController;
	private ManagerController managerController;

	private OrdersManagerController ordersManagerController;

	private ApplicationController() {
		this.ordersManagerController = new OrdersManagerController();
	}

	public static ApplicationController getInstance() {
		synchronized (ApplicationController.class) {
			if (instance == null) {
				instance = new ApplicationController();
			}
			return instance;
		}
	}

	public void allocateComputeOrder(ComputeOrder computeOrder) {
		
	}
	
	public Collection<ComputeOrder> findAllComputeOrder() {
		return null;		
	}
	
	public ComputeOrder findComputeOrderById(String id) {
		return null;		
	}
	
	public void removeComputeOrder(String id) {
		
	}
	
	public void allocateNetworkOrder(NetworkOrder networkOrder) {
		
	}
	
	public Collection<NetworkOrder> findAllNetworkOrder() {
		return null;		
	}
	
	public NetworkOrder findNetworkOrderById(String id) {
		return null;		
	}
	
	public void removeNetworkOrder(String id) {
		
	}
	
	public void allocateStorageOrder(StorageOrder storageOrder) {
		
	}
	
	public Collection<StorageOrder> findAllStorageOrder() {
		return null;		
	}
	
	public StorageOrder findStorageOrderById(String id) {
		return null;		
	}
	
	public void removeStorageOrder(String id) {
		
	}

	public AuthenticationService getAuthenticationController() {
		return authenticationController;
	}

	public void setAuthenticationController(AuthenticationService authenticationController) {
		this.authenticationController = authenticationController;
	}

	public ManagerController getManagerController() {
		return managerController;
	}

	public void setManagerController(ManagerController managerController) {
		this.managerController = managerController;
	}

	public void newOrderRequest(Order order, String accessId, String localTokenId) 
				throws OrderManagementException, UnauthorizedException, UnauthenticatedException, Exception {		
		this.authenticationController.authenticateAndAuthorize(accessId);
		Token federationToken = this.authenticationController.getFederationToken(accessId);
		String providingMember = order.getProvidingMember();
		Token localToken = this.authenticationController.getLocalToken(localTokenId, providingMember);
		this.ordersManagerController.newOrderRequest(order, federationToken, localToken);
	}

}
