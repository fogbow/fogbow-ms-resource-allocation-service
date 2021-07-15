package cloud.fogbow.ras.core.processors;

import org.apache.log4j.Logger;

import com.google.common.annotations.VisibleForTesting;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InstanceNotFoundException;
import cloud.fogbow.common.models.linkedlists.ChainedList;
import cloud.fogbow.ras.api.http.response.ComputeInstance;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.OrderStateTransitioner;
import cloud.fogbow.ras.core.SharedOrderHolders;
import cloud.fogbow.ras.core.cloudconnector.CloudConnectorFactory;
import cloud.fogbow.ras.core.cloudconnector.LocalCloudConnector;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.orders.Order;
import cloud.fogbow.ras.core.models.orders.OrderState;

public class StoppingProcessor extends StoppableProcessor implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(StoppingProcessor.class);

    private String localProviderId;
    private ChainedList<Order> stoppingOrdersList;

    public StoppingProcessor(String localProviderId, String sleepTimeStr) {
        this.localProviderId = localProviderId;
        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        this.stoppingOrdersList = sharedOrderHolders.getStoppingOrdersList();
        this.sleepTime = Long.valueOf(sleepTimeStr);
        this.isActive = false;
        this.mustStop = false;
    }

    public void setSleepTime(Long sleepTime) {
        this.sleepTime = sleepTime;
    }

    /**
     * The Stopping processor monitors when the stop operation has finished. 
     * Essentially it keeps repeating getInstance().isStopped() calls until the instance is stopped.
     *
     * @param order {@link Order}
     */
    @VisibleForTesting
    void processStopOrder(Order order) throws FogbowException {
        // The order object synchronization is needed to prevent a race condition on order access.
        synchronized (order) {
            // Check if the order is still in the STOPPING state (for this particular state, this should
            // always happen, since once the order gets to this state, only this thread can operate on it. However,
            // the cost of safe programming is low).
            OrderState orderState = order.getOrderState();
            if (!orderState.equals(OrderState.STOPPING)) {
                return;
            }

            // Only compute order can be stopped.
            if (!order.getType().equals(ResourceType.COMPUTE)) {
                return;
            }

            // Only local orders need to be monitored. Remote orders are monitored by the remote provider.
            // State changes that happen at the remote provider are synchronized by the RemoteOrdersStateSynchronization
            // processor.
            if (order.isProviderRemote(this.localProviderId)) {
                // This should never happen, but the bug can be mitigated by moving the order to the remoteOrders list
                OrderStateTransitioner.transition(order, OrderState.PENDING);
                LOGGER.error(Messages.Log.UNEXPECTED_ERROR);
                return;
            }
            try {
                // Here we know that the CloudConnector is local, but the use of CloudConnectFactory facilitates testing.
                LocalCloudConnector localCloudConnector = (LocalCloudConnector)
                        CloudConnectorFactory.getInstance().getCloudConnector(this.localProviderId, order.getCloudName());

                ComputeInstance instance = (ComputeInstance) localCloudConnector.getInstance(order);

                if (instance.isStopped()) {
                    OrderStateTransitioner.transition(order, OrderState.STOPPED);
                }
            } catch (InstanceNotFoundException e) {
                OrderStateTransitioner.transition(order, OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST);
            }
        }
    }

    @Override
    protected void doProcessing(Order order) throws InterruptedException, FogbowException {
        processStopOrder(order);
    }

    @Override
    protected Order getNext() {
        return this.stoppingOrdersList.getNext();
    }

    @Override
    protected void reset() {
        this.stoppingOrdersList.resetPointer();
    }
}
