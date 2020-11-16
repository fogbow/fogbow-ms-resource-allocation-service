package cloud.fogbow.ras.core.processors;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.UnavailableProviderException;
import cloud.fogbow.common.models.linkedlists.ChainedList;
import cloud.fogbow.ras.api.http.response.OrderInstance;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.OrderStateTransitioner;
import cloud.fogbow.ras.core.SharedOrderHolders;
import cloud.fogbow.ras.core.cloudconnector.CloudConnectorFactory;
import cloud.fogbow.ras.core.cloudconnector.LocalCloudConnector;
import cloud.fogbow.ras.core.models.orders.Order;
import cloud.fogbow.ras.core.models.orders.OrderState;
import com.google.common.annotations.VisibleForTesting;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class SpawningProcessor extends StoppableProcessor implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(SpawningProcessor.class);
    private static final int INITIAL_FAILED_REQUESTS_COUNT = 0;
    @VisibleForTesting static final int FAILED_REQUESTS_LIMIT = 5;

    private ChainedList<Order> spawningOrderList;
    private Map<Order, Integer> failedRequestsMap;
    private String localProviderId;

    public SpawningProcessor(String providerId, String sleepTimeStr) {
        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        this.spawningOrderList = sharedOrderHolders.getSpawningOrdersList();
        this.failedRequestsMap = new HashMap<>();
        this.sleepTime = Long.valueOf(sleepTimeStr);
        this.localProviderId = providerId;
        this.isActive = false;
        this.mustStop = false;
    }

    public void setSleepTime(Long sleepTime) {
        this.sleepTime = sleepTime;
    }
    
    protected void processSpawningOrder(Order order) throws FogbowException {
        // The order object synchronization is needed to prevent a race
        // condition on order access. For example: a user can delete an spawning
        // order while this method is trying to check the status of an instance
        // that has been requested in the cloud.
        synchronized (order) {
            // Check if the order is still in the SPAWNING state (it could have been changed by another thread)
            OrderState orderState = order.getOrderState();
            if (!orderState.equals(OrderState.SPAWNING)) {
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
            // Here we know that the CloudConnector is local, but the use of CloudConnectFactory facilitates testing.
            LocalCloudConnector localCloudConnector = (LocalCloudConnector)
                    CloudConnectorFactory.getInstance().getCloudConnector(this.localProviderId, order.getCloudName());
            // We don't audit requests we make
            localCloudConnector.switchOffAuditing();

            try {
                OrderInstance instance = localCloudConnector.getInstance(order);
                if (instance.hasFailed()) {
                    OrderStateTransitioner.transition(order, OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST);
                } else if (instance.isReady()) {
                    OrderStateTransitioner.transition(order, OrderState.FULFILLED);
                }

                if (this.failedRequestsMap.containsKey(order)) {
                    this.failedRequestsMap.remove(order);
                }
            } catch (UnavailableProviderException e1) {
                OrderStateTransitioner.transition(order, OrderState.UNABLE_TO_CHECK_STATUS);
                throw e1;
            } catch (Exception e2) {
                if (!this.failedRequestsMap.containsKey(order)) {
                    this.failedRequestsMap.put(order, INITIAL_FAILED_REQUESTS_COUNT);
                }

                int failedRequestsCount = this.failedRequestsMap.get(order);
                failedRequestsCount++;

                if (failedRequestsCount >= FAILED_REQUESTS_LIMIT) {
                    order.setOnceFaultMessage(e2.getMessage());
                    LOGGER.info(String.format(Messages.Exception.GENERIC_EXCEPTION_S, e2));
                    this.failedRequestsMap.remove(order);
                    OrderStateTransitioner.transition(order, OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST);
                } else {
                    int attemptsLeft = FAILED_REQUESTS_LIMIT - failedRequestsCount;
                    LOGGER.info(String.format(Messages.Exception.ERROR_WHILE_CHECKING_INSTANCE_STATUS_ATTEMPTS_LEFT_D,
                            attemptsLeft));
                    this.failedRequestsMap.put(order, failedRequestsCount);
                }
            }
        }
    }

    @VisibleForTesting
    void setFailedRequestsMap(Map<Order, Integer> failedRequestsMap) {
        this.failedRequestsMap = failedRequestsMap;
    }

    @Override
    protected void doProcessing(Order order) throws InterruptedException, FogbowException {
        processSpawningOrder(order);
    }

    @Override
    protected Order getNext() {
        return this.spawningOrderList.getNext();
    }

    @Override
    protected void reset() {
        this.spawningOrderList.resetPointer();
    }
}
