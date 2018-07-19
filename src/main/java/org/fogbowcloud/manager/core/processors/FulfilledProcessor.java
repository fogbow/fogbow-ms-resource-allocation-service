package org.fogbowcloud.manager.core.processors;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.OrderStateTransitioner;
import org.fogbowcloud.manager.core.SharedOrderHolders;
import org.fogbowcloud.manager.core.cloudconnector.CloudConnector;
import org.fogbowcloud.manager.core.cloudconnector.CloudConnectorFactory;
import org.fogbowcloud.manager.core.exceptions.UnexpectedException;
import org.fogbowcloud.manager.core.models.instances.InstanceState;
import org.fogbowcloud.manager.util.connectivity.SshTunnelConnectionData;
import org.fogbowcloud.manager.core.models.linkedlists.ChainedList;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderState;
import org.fogbowcloud.manager.core.models.instances.InstanceType;
import org.fogbowcloud.manager.core.models.instances.Instance;
import org.fogbowcloud.manager.util.connectivity.ComputeInstanceConnectivityUtil;
import org.fogbowcloud.manager.util.connectivity.SshConnectivityUtil;
import org.fogbowcloud.manager.util.connectivity.TunnelingServiceUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Process orders in fulfilled state. It monitors the resourced that have been successfully
 * initiated, to check for failures that may affect them.
 */
public class FulfilledProcessor implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(FulfilledProcessor.class);
    private static final int MAX_TRIES = 10;
    private static final int MAX_SIZE = 1000;

    private String localMemberId;

    private CloudConnector localCloudConnector;

    private ComputeInstanceConnectivityUtil computeInstanceConnectivity;

    private ChainedList fulfilledOrdersList;

    private Map<String, Integer> connectionAttempts;

    /**
     * Attribute that represents the thread sleep time when there is no orders to be processed.
     */
    private Long sleepTime;

    public FulfilledProcessor(String localMemberId, TunnelingServiceUtil tunnelingService,
                              SshConnectivityUtil sshConnectivity, String sleepTimeStr) {
        CloudConnectorFactory cloudConnectorFactory = CloudConnectorFactory.getInstance();
        this.localMemberId = localMemberId;
        this.localCloudConnector = cloudConnectorFactory.getCloudConnector(localMemberId);
        this.computeInstanceConnectivity =
            new ComputeInstanceConnectivityUtil(tunnelingService, sshConnectivity);
        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        this.fulfilledOrdersList = sharedOrderHolders.getFulfilledOrdersList();
        this.sleepTime = Long.valueOf(sleepTimeStr);
        this.connectionAttempts = new HashMap<>();
    }

    /**
     * Iterates over the fulfilled orders list and try to process one fulfilled order per time. If
     * the order is null it indicates the iteration is in the end of the list or the list is empty.
     */
    @Override
    public void run() {
        boolean isActive = true;

        while (isActive) {
            try {
                Order order = this.fulfilledOrdersList.getNext();

                if (order != null) {
                    processFulfilledOrder(order);
                } else {
                    // This code is to avoid a "memory leak" but should be removed soon
                    // when we get rid of all this stuff related to checking if the compute is reachable.
                    if (this.connectionAttempts.size() >= MAX_SIZE) {
                        this.connectionAttempts.clear();
                    }
                    this.fulfilledOrdersList.resetPointer();
                    LOGGER.debug("There is no fulfilled order to be processed, sleeping for "
                            + this.sleepTime + " milliseconds");
                    Thread.sleep(this.sleepTime);
                }
            } catch (InterruptedException e) {
                isActive = false;
                LOGGER.error("Thread interrupted", e);
            } catch (UnexpectedException e) {
                LOGGER.error(e.getMessage(), e);
            } catch (Throwable e) {
                LOGGER.error("Unexpected error", e);
            }
        }
    }

    /**
     * Gets an instance for a fulfilled order. If that instance is not reachable the order state is
     * set to failed.
     *
     * @param order {@link Order}
     */
    protected void processFulfilledOrder(Order order) throws UnexpectedException {

        Instance instance = null;
        InstanceState instanceState = null;
        InstanceType instanceType = null;


        // The order object synchronization is needed to prevent a race
        // condition on order access. For example: a user can delete a fulfilled
        // order while this method is trying to check the status of an instance
        // that was allocated to an order.

        synchronized (order) {
            OrderState orderState = order.getOrderState();

            // Only orders that have been served by the local cloud are checked; remote ones are checked by
            // the Fogbow manager running in the other member, which reports back any changes in the status.
            if (!order.isProviderLocal(this.localMemberId)) {
                return;
            }

            // Check if the order is still in the Fulfilled state (it could have been changed by another thread)
            if (!orderState.equals(OrderState.FULFILLED)) {
                return;
            }

            LOGGER.info("Trying to get an instance for order [" + order.getId() + "]");
            try {
                instance = this.localCloudConnector.getInstance(order);
            } catch (Exception e) {
                LOGGER.error("Error while getting instance from the cloud.", e);
                OrderStateTransitioner.transition(order, OrderState.FAILED);
                return;
            }
            instanceType = order.getType();
            instanceState = instance.getState();
            if (instanceState.equals(InstanceState.FAILED)) {
                LOGGER.info("Instance state is failed for order [" + order.getId() + "]");
                OrderStateTransitioner.transition(order, OrderState.FAILED);
                return;
            }
        }

        // Checking if the compute instance is reacheable. This needs to be done outside the synchonized block
        // because it may take a few seconds, and keeping the lock on the order could slow down other threads.
        // Since the order object is not accessed, there is no race condition that can happen.
        if (instanceState.equals(InstanceState.READY) && instanceType.equals(InstanceType.COMPUTE)) {
            LOGGER.info("Processing active compute instance for order [" + order.getId() + "]");

            SshTunnelConnectionData sshTunnelConnectionData = this.computeInstanceConnectivity
                    .getSshTunnelConnectionData(order.getId());
            if (sshTunnelConnectionData != null) {
                boolean instanceReachable = this.computeInstanceConnectivity.isInstanceReachable(sshTunnelConnectionData);
                if (!instanceReachable) {
                    //taking the mutex back.
                    //Since we might have lost the CPU, it is possible the order is not longer FULFILLED
                    synchronized (order) {
                        if (!((order.getOrderState()).equals(OrderState.FULFILLED))) {
                            return;
                        }
                        // This code below guarantees that after a compute fails communication MAX_TRIES(10) times in a
                        // row, it will be transitioned to FAILED.
                        Integer nAttempts = this.connectionAttempts.get(order.getId());
                        if (nAttempts == null) {
                            this.connectionAttempts.put(order.getId(), 1);
                        } else {
                            if (nAttempts == MAX_TRIES) {
                                LOGGER.warn("Instance failed, cannot reach the compute from order [" + order.getId() + "].");
                                OrderStateTransitioner.transition(order, OrderState.FAILED);
                                this.connectionAttempts.remove(order.getId());
                            } else {
                                LOGGER.info("Compute failed to make connection ("+ (nAttempts + 1) +"/"+ MAX_TRIES +")," +
                                        " to order [" + order.getId() + "].");
                                this.connectionAttempts.put(order.getId(), ++nAttempts);
                            }
                        }
                        return;
                    }
                } else {
                    this.connectionAttempts.put(order.getId(), 0);
                }
            }
        }
        LOGGER.debug("The instance was processed successfully");
    }

}
