package org.fogbowcloud.manager.core.threads;

import java.util.Properties;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.constants.DefaultConfigurationConstants;
import org.fogbowcloud.manager.core.instanceprovider.InstanceProvider;
import org.fogbowcloud.manager.core.models.linkedList.ChainedList;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderState;
import org.fogbowcloud.manager.core.models.orders.instances.OrderInstance;

public class OpenProcessor implements Runnable {

	private InstanceProvider localInstanceProvider;
	private InstanceProvider remoteInstanceProvider;

	private ChainedList openOrdersList;
	private ChainedList pendingOrdersList;
	private ChainedList failedOrdersList;
	private ChainedList spawningOrdersList;

	private String localMemberId;

	/**
	 * Attribute that represents the thread sleep time when there is no Orders
	 * to be processed.
	 */
	private Long sleepTime;

	private static final Logger LOGGER = Logger.getLogger(OpenProcessor.class);

	public OpenProcessor(InstanceProvider localInstanceProvider, InstanceProvider remoteInstanceProvider,
			String localMemberId, Properties properties) {
		this.localInstanceProvider = localInstanceProvider;
		this.remoteInstanceProvider = remoteInstanceProvider;
		this.localMemberId = localMemberId;

		// TODO: ChainedLists instanciation by Singleton pattern.

		String schedulerPeriodStr = properties.getProperty(ConfigurationConstants.OPEN_ORDERS_SLEEP_TIME_KEY,
				DefaultConfigurationConstants.OPEN_ORDERS_SLEEP_TIME);
		this.sleepTime = Long.valueOf(schedulerPeriodStr);
	}

	@Override
	public void run() {
		while (true) {
			try {
				Order order = this.openOrdersList.getNext();
				if (order != null) {
					this.processOpenOrder(order);
				} else {
					LOGGER.info(
							"There is no open order to be processed, sleeping for " + this.sleepTime + " milliseconds");
					Thread.sleep(this.sleepTime);
				}
			} catch (Throwable e) {
				LOGGER.error("Error while trying to process an open order", e);
			}
		}
	}

	/**
	 * Get an Instance for an Open Order. If the method fail in get the
	 * Instance, then the Order is set to FAILED, else, is set to SPAWNING if
	 * the Order is local or PENDING if the Order is remote.
	 * 
	 * @param order
	 */
	protected void processOpenOrder(Order order) {
		// This method can generate a race condition. For example: a user can
		// delete a Open Order while this method is trying to get an Instance
		// for this Order.
		synchronized (order) {
			OrderState orderState = order.getOrderState();

			if (orderState.equals(OrderState.OPEN)) {
				LOGGER.info("Trying to get an instance for order [" + order.getId() + "]");

				try {
					InstanceProvider instanceProvider = this.getInstanceProviderForOrder(order);

					// TODO: prepare Order to Change your State from Open to
					// Spawning.

					LOGGER.info("Processing order [" + order.getId() + "]");
					OrderInstance orderInstance = instanceProvider.requestInstance(order);
					order.setOrderInstance(orderInstance);

					LOGGER.info("Removing order [" + order.getId() + "] from open orders list");
					this.openOrdersList.removeItem(order);

					LOGGER.info("Updating order state after processing [" + order.getId() + "]");
					this.updateOrderStateAfterProcessing(order);

				} catch (Exception e) {
					LOGGER.error("Error while trying to get an instance for order: " + order, e);
					order.setOrderState(OrderState.FAILED);

					LOGGER.info("Adding order [" + order.getId() + "] to failed orders list");
					this.failedOrdersList.addItem(order);
				}
			}
		}
	}

	/**
	 * Update the Order State and Add the Order in the right Chained List.
	 * 
	 * @param order
	 */
	protected void updateOrderStateAfterProcessing(Order order) {
		if (order.isLocal(this.localMemberId)) {
			OrderInstance orderInstance = order.getOrderInstance();
			String orderInstanceId = orderInstance.getId();

			if (!orderInstanceId.isEmpty()) {
				LOGGER.info("The open order [" + order.getId() + "] got an local instance with id [" + orderInstanceId
						+ "], setting your state to spawning");

				order.setOrderState(OrderState.SPAWNING);

				LOGGER.info("Adding order [" + order.getId() + "] to spawning orders list");
				this.spawningOrdersList.addItem(order);

			} else {
				LOGGER.error("Order instance id for order [" + order.getId() + "] is empty");
				throw new RuntimeException("Order instance id for order [" + order.getId() + "] is empty");
			}

		} else if (order.isRemote(this.localMemberId)) {
			LOGGER.info("The open order [" + order.getId()
					+ "] was requested for remote member, setting your state to pending");

			order.setOrderState(OrderState.PENDING);

			LOGGER.info("Adding order [" + order.getId() + "] to pending orders list");
			this.pendingOrdersList.addItem(order);
		}
	}

	/**
	 * Get an Instance Provider for an Order, if the Order is Local, the
	 * returned Instance Provider is the Local, else, is the Remote.
	 * 
	 * @param order 
	 * @return Local InstanceProvider if the Order is Local, or Remote InstanceProvider if the Order is Remote
	 */
	protected InstanceProvider getInstanceProviderForOrder(Order order) {
		InstanceProvider instanceProvider = null;
		if (order.isLocal(this.localMemberId)) {
			LOGGER.info("The open order [" + order.getId() + "] is local");

			instanceProvider = this.localInstanceProvider;
		} else if (order.isRemote(this.localMemberId)) {
			LOGGER.info("The open order [" + order.getId() + "] is remote for the member [" + order.getProvidingMember()
					+ "]");

			instanceProvider = this.remoteInstanceProvider;
		}
		return instanceProvider;
	}

	// TODO: all these setters method should be removed when the ChainedList be
	// instanciated by the Singleton Pattern.
	protected void setOpenOrdersList(ChainedList openOrdersList) {
		this.openOrdersList = openOrdersList;
	}

	protected void setPendingOrdersList(ChainedList pendingOrdersList) {
		this.pendingOrdersList = pendingOrdersList;
	}

	protected void setFailedOrdersList(ChainedList failedOrdersList) {
		this.failedOrdersList = failedOrdersList;
	}

	protected void setSpawningOrdersList(ChainedList spawningOrdersList) {
		this.spawningOrdersList = spawningOrdersList;
	}

}
