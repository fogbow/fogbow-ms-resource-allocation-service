package org.fogbowcloud.manager.core.processors;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.Properties;
import org.fogbowcloud.manager.core.BaseUnitTests;
import org.fogbowcloud.manager.core.OrderController;
import org.fogbowcloud.manager.core.SharedOrderHolders;
import org.fogbowcloud.manager.core.instanceprovider.InstanceProvider;
import org.fogbowcloud.manager.core.manager.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.models.linkedlist.ChainedList;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.instances.OrderInstance;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ClosedProcessorTest extends BaseUnitTests {

    private ClosedProcessor closedProcessor;

    private InstanceProvider localInstanceProvider;
    private InstanceProvider remoteInstanceProvider;

    private Properties properties;

    private Thread thread;
    private OrderController orderController;

    @Before
    public void setUp() {
        this.properties = new Properties();
        this.properties.setProperty(
                ConfigurationConstants.XMPP_ID_KEY, BaseUnitTests.LOCAL_MEMBER_ID);

        this.orderController = Mockito.mock(OrderController.class);
        this.localInstanceProvider = Mockito.mock(InstanceProvider.class);
        this.remoteInstanceProvider = Mockito.mock(InstanceProvider.class);

        this.closedProcessor =
                Mockito.spy(
                        new ClosedProcessor(
                                this.localInstanceProvider,
                                this.remoteInstanceProvider,
                                this.orderController,
                                this.properties));
    }

    @Override
    public void tearDown() {
        if (this.thread != null) {
            this.thread.interrupt();
            this.thread = null;
        }
        super.tearDown();
    }

    @Test
    public void testProcessClosedLocalOrder() throws Exception {
        OrderInstance orderInstance = new OrderInstance("fake-id");
        Order localOrder = createLocalOrder(getLocalMemberId());
        localOrder.setOrderInstance(orderInstance);

        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        ChainedList closedOrders = sharedOrderHolders.getClosedOrdersList();

        Map<String, Order> activeOrdersMap = sharedOrderHolders.getActiveOrdersMap();
        activeOrdersMap.put(localOrder.getId(), localOrder);
        closedOrders.addItem(localOrder);

        Mockito.doNothing()
                .when(this.localInstanceProvider)
                .deleteInstance(Mockito.any(OrderInstance.class));

        this.thread = new Thread(this.closedProcessor);
        this.thread.start();

        Thread.sleep(500);

        assertNull(activeOrdersMap.get(localOrder.getId()));

        closedOrders.resetPointer();
        assertNull(closedOrders.getNext());
    }

    @Test
    public void testProcessClosedLocalOrderFails() throws Exception {
        OrderInstance orderInstance = new OrderInstance("fake-id");
        Order localOrder = createLocalOrder(getLocalMemberId());
        localOrder.setOrderInstance(orderInstance);

        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        ChainedList closedOrders = sharedOrderHolders.getClosedOrdersList();
        Map<String, Order> activeOrdersMap = sharedOrderHolders.getActiveOrdersMap();

        activeOrdersMap.put(localOrder.getId(), localOrder);
        closedOrders.addItem(localOrder);

        Mockito.doThrow(Exception.class)
                .when(this.localInstanceProvider)
                .deleteInstance(Mockito.any(OrderInstance.class));

        this.thread = new Thread(this.closedProcessor);
        this.thread.start();

        Thread.sleep(500);

        assertEquals(localOrder, activeOrdersMap.get(localOrder.getId()));

        closedOrders.resetPointer();
        assertEquals(localOrder, closedOrders.getNext());
    }
}
