package org.fogbowcloud.manager.core.controllers;

import java.util.Collection;
import java.util.Map;

import org.fogbowcloud.manager.core.ManagerController;
import org.fogbowcloud.manager.core.datastructures.SharedOrderHolders;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.orders.NetworkOrder;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderType;
import org.fogbowcloud.manager.core.exceptions.OrderManagementException;
import org.fogbowcloud.manager.core.models.orders.StorageOrder;
import org.fogbowcloud.manager.core.models.token.Token;
import org.fogbowcloud.manager.core.plugins.identity.exceptions.UnauthorizedException;
import org.fogbowcloud.manager.core.services.AuthenticationService;
import org.fogbowcloud.manager.core.services.ComputeOrdersService;

public class ApplicationController {

	private static ApplicationController instance;
	private AuthenticationService authenticationController;
	private ManagerController managerController;
	private ComputeOrdersService computeOrdersService;

	private OrdersManagerController ordersManagerController;

	private ApplicationController() {
		this.computeOrdersService = new ComputeOrdersService();
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
	
	public Token authenticate(String accessId) throws UnauthorizedException {
		return this.authenticationController.authenticate(accessId);
	}
	
	public void deleteOrder(String id, String accessId, OrderType orderType) throws UnauthorizedException {
		Token token = this.authenticate(accessId);
		SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
		Map<String, Order> activeOrdersMap = sharedOrderHolders.getActiveOrdersMap();
		Order order = activeOrdersMap.get(id);
		if (order.getType().equals(orderType)) {
			this.computeOrdersService.deleteOrder(order, token);
		}		
	}
	
	public void newOrderRequest(Order order, String accessId, String localTokenId) throws OrderManagementException, UnauthorizedException {
		Token federationToken = this.authenticationController.authenticate(accessId);
		Token localToken = createLocalToken(localTokenId);
		this.ordersManagerController.newOrderRequest(order, federationToken, localToken);
	}

	private Token createLocalToken(String localTokenId) {
		Token localToken = new Token();
		localToken.setAccessId(localTokenId);
		return localToken;
	}

}
