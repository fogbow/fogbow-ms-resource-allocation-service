package org.fogbowcloud.manager.core.processors;

import static org.junit.Assert.assertNotNull;

import java.util.Map;
import java.util.Properties;
import org.fogbowcloud.manager.core.BaseUnitTests;
import org.fogbowcloud.manager.core.SharedOrderHolders;
import org.fogbowcloud.manager.core.instanceprovider.InstanceProvider;
import org.fogbowcloud.manager.core.manager.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.models.linkedlist.ChainedList;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderState;
import org.fogbowcloud.manager.core.models.orders.UserData;
import org.fogbowcloud.manager.core.models.orders.instances.ComputeOrderInstance;
import org.fogbowcloud.manager.core.models.orders.instances.InstanceState;
import org.fogbowcloud.manager.core.models.orders.instances.OrderInstance;
import org.fogbowcloud.manager.core.models.token.Token;
import org.fogbowcloud.manager.utils.SshConnectivityUtil;
import org.fogbowcloud.manager.utils.TunnelingServiceUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SpawningProcessorTest extends BaseUnitTests {

    private Properties properties;

    private InstanceProvider instanceProvider;

    private TunnelingServiceUtil tunnelingService;
    private SshConnectivityUtil sshConnectivity;

    private SpawningProcessor spawningProcessor;

    private Thread thread;
    private ChainedList spawningOrderList;
    private ChainedList fulfilledOrderList;
    private ChainedList failedOrderList;
    private ChainedList openOrderList;
    private ChainedList pendingOrderList;
    private ChainedList closedOrderList;

    @Before
    public void setUp() {
        this.tunnelingService = Mockito.mock(TunnelingServiceUtil.class);
        this.sshConnectivity = Mockito.mock(SshConnectivityUtil.class);
        this.instanceProvider = Mockito.mock(InstanceProvider.class);
        this.properties = new Properties();
        this.properties.put(ConfigurationConstants.XMPP_ID_KEY, ".");
        this.spawningProcessor =
                Mockito.spy(
                        new SpawningProcessor(
                                this.tunnelingService,
                                this.sshConnectivity,
                                this.instanceProvider,
                                this.properties));
        this.thread = null;

        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        this.spawningOrderList = sharedOrderHolders.getSpawningOrdersList();
        this.fulfilledOrderList = sharedOrderHolders.getFulfilledOrdersList();
        this.failedOrderList = sharedOrderHolders.getFailedOrdersList();
        this.openOrderList = sharedOrderHolders.getOpenOrdersList();
        this.pendingOrderList = sharedOrderHolders.getPendingOrdersList();
        this.closedOrderList = sharedOrderHolders.getClosedOrdersList();
    }

    @After
    public void tearDown() {
        if (this.thread != null) {
            this.thread.interrupt();
        }

        super.tearDown();
    }

    @Test
    public void testRunThrowableExceptionWhileTryingToProcessOrder() throws Exception {
        Order order = Mockito.mock(Order.class);
        OrderState state = null;
        order.setOrderState(state);
        this.spawningOrderList.addItem(order);

        Mockito.doThrow(new RuntimeException("Any Exception"))
                .when(this.spawningProcessor)
                .processSpawningOrder(order);

        this.thread = new Thread(this.spawningProcessor);
        this.thread.start();

        Thread.sleep(500);
    }

    @Test
    public void testRunProcesseComputeOrderInstanceActive() throws Exception {
    	Order order = this.createOrder();
        order.setOrderState(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);

        OrderInstance orderInstance = Mockito.spy(new ComputeOrderInstance("fake-id"));
        orderInstance.setState(InstanceState.ACTIVE);
        order.setOrderInstance(orderInstance);

        Mockito.doReturn(orderInstance).when(this.instanceProvider).getInstance(Mockito.any(Order.class));

        Mockito.doNothing().when((ComputeOrderInstance) orderInstance)
                .setExternalServiceAddresses(Mockito.anyMapOf(String.class, String.class));

        Mockito.when(this.sshConnectivity.checkSSHConnectivity(Mockito.any(ComputeOrderInstance.class))).thenReturn(true);

        Assert.assertNull(this.fulfilledOrderList.getNext());

        this.thread = new Thread(this.spawningProcessor);
        this.thread.start();
        Thread.sleep(500);
        
		Assert.assertNull(this.spawningOrderList.getNext());

		Order test = this.fulfilledOrderList.getNext();
		assertNotNull(test);
		Assert.assertEquals(order.getOrderInstance(), test.getOrderInstance());
		Assert.assertEquals(OrderState.FULFILLED, test.getOrderState());
    }

    @Test
    public void testRunProcesseComputeOrderInstanceInactive() throws InterruptedException {
        Order order = this.createOrder();
        order.setOrderState(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);

        ComputeOrderInstance computeOrderInstance =
                Mockito.spy(new ComputeOrderInstance("fake-id"));
        computeOrderInstance.setState(InstanceState.INACTIVE);
        order.setOrderInstance(computeOrderInstance);

        this.thread = new Thread(this.spawningProcessor);
        this.thread.start();
        Thread.sleep(500);

        Order test = this.spawningOrderList.getNext();
        Assert.assertNotNull(test);
        Assert.assertEquals(OrderState.SPAWNING, test.getOrderState());
    }

    @Test
    public void testRunProcesseComputeOrderInstanceFailed() throws Exception {
    	Order order = this.createOrder();
        order.setOrderState(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);

        OrderInstance orderInstance = Mockito.spy(new ComputeOrderInstance("fake-id"));
        orderInstance.setState(InstanceState.FAILED);
        order.setOrderInstance(orderInstance);

        Mockito.doReturn(orderInstance).when(this.instanceProvider).getInstance(Mockito.any(Order.class));

        Mockito.doNothing().when((ComputeOrderInstance) orderInstance)
                .setExternalServiceAddresses(Mockito.anyMapOf(String.class, String.class));

        Assert.assertNull(this.failedOrderList.getNext());

        this.thread = new Thread(this.spawningProcessor);
        this.thread.start();
        Thread.sleep(500);
        
		Assert.assertNull(this.spawningOrderList.getNext());

		Order test = this.failedOrderList.getNext();
		assertNotNull(test);
		Assert.assertEquals(order.getOrderInstance(), test.getOrderInstance());
		Assert.assertEquals(OrderState.FAILED, test.getOrderState());
    }

    @Test
    public void testProcessWithoutModifyOpenOrderState() throws Exception {
        Order order = this.createOrder();
        order.setOrderState(OrderState.OPEN);
        this.openOrderList.addItem(order);

        this.spawningProcessor.processSpawningOrder(order);

        Order test = this.openOrderList.getNext();
        Assert.assertEquals(OrderState.OPEN, test.getOrderState());
        Assert.assertNull(this.spawningOrderList.getNext());
        Assert.assertNull(this.failedOrderList.getNext());
        Assert.assertNull(this.fulfilledOrderList.getNext());
    }

    @Test
    public void testProcessWithoutModifyPendingOrderState() throws Exception {
        Order order = this.createOrder();
        order.setOrderState(OrderState.PENDING);
        this.pendingOrderList.addItem(order);

        this.spawningProcessor.processSpawningOrder(order);

        Order test = this.pendingOrderList.getNext();
        Assert.assertEquals(OrderState.PENDING, test.getOrderState());
        Assert.assertNull(this.spawningOrderList.getNext());
        Assert.assertNull(this.failedOrderList.getNext());
        Assert.assertNull(this.fulfilledOrderList.getNext());
    }

    @Test
    public void testProcessWithoutModifyFailedOrderState() throws Exception {
        Order order = this.createOrder();
        order.setOrderState(OrderState.FAILED);
        this.failedOrderList.addItem(order);

        this.spawningProcessor.processSpawningOrder(order);

        Order test = this.failedOrderList.getNext();
        Assert.assertEquals(OrderState.FAILED, test.getOrderState());
        Assert.assertNull(this.spawningOrderList.getNext());
        Assert.assertNull(this.fulfilledOrderList.getNext());
    }

    @Test
    public void testProcessWithoutModifyFulfilledOrderState() throws Exception {
        Order order = this.createOrder();
        order.setOrderState(OrderState.FULFILLED);
        this.fulfilledOrderList.addItem(order);

        this.spawningProcessor.processSpawningOrder(order);

        Order test = this.fulfilledOrderList.getNext();
        Assert.assertEquals(OrderState.FULFILLED, test.getOrderState());
        Assert.assertNull(this.spawningOrderList.getNext());
        Assert.assertNull(this.failedOrderList.getNext());
    }

    @Test
    public void testProcessWithoutModifyClosedOrderState() throws Exception {
        Order order = this.createOrder();
        order.setOrderState(OrderState.CLOSED);
        this.closedOrderList.addItem(order);

        this.spawningProcessor.processSpawningOrder(order);

        Order test = this.closedOrderList.getNext();
        Assert.assertEquals(OrderState.CLOSED, test.getOrderState());
        Assert.assertNull(this.spawningOrderList.getNext());
        Assert.assertNull(this.failedOrderList.getNext());
        Assert.assertNull(this.fulfilledOrderList.getNext());
    }

    @Test
    public void testRunThrowableExceptionWhileTryingToGetMapAddressesOfComputeOrderInstance()
            throws InterruptedException {
        Order order = this.createOrder();
        order.setOrderState(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);

        ComputeOrderInstance computeOrderInstance =
                Mockito.spy(new ComputeOrderInstance("fake-id"));
        computeOrderInstance.setState(InstanceState.ACTIVE);
        order.setOrderInstance(computeOrderInstance);

        Mockito.doThrow(new RuntimeException("Any Exception"))
                .when(this.tunnelingService)
                .getExternalServiceAddresses(order.getId());

        this.thread = new Thread(this.spawningProcessor);
        this.thread.start();
        Thread.sleep(500);
    }

    @Test
    public void testRunConfiguredToFailedAttemptSshConnectivity() throws InterruptedException {
        Order order = this.createOrder();
        order.setOrderState(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);

        ComputeOrderInstance computeOrderInstance =
                Mockito.spy(new ComputeOrderInstance("fake-id"));
        computeOrderInstance.setState(InstanceState.ACTIVE);
        order.setOrderInstance(computeOrderInstance);

        Map<String, String> externalServiceAddresses =
                this.tunnelingService.getExternalServiceAddresses(order.getId());
        Mockito.doNothing()
                .when(computeOrderInstance)
                .setExternalServiceAddresses(externalServiceAddresses);

        Mockito.when(this.sshConnectivity.checkSSHConnectivity(computeOrderInstance))
                .thenReturn(false);

        this.thread = new Thread(this.spawningProcessor);
        this.thread.start();
        Thread.sleep(500);

        Order test = this.spawningOrderList.getNext();
        Assert.assertNotNull(test);
        Assert.assertEquals(OrderState.SPAWNING, test.getOrderState());
    }

    private Order createOrder() {
        Token federationToken = Mockito.mock(Token.class);
        String requestingMember =
                String.valueOf(this.properties.get(ConfigurationConstants.XMPP_ID_KEY));
        String providingMember =
                String.valueOf(this.properties.get(ConfigurationConstants.XMPP_ID_KEY));
        UserData userData = Mockito.mock(UserData.class);
        Order order =
                new ComputeOrder(
                        federationToken,
                        requestingMember,
                        providingMember,
                        8,
                        1024,
                        30,
                        "fake_image_name",
                        userData,
                        "fake_public_key");
        return order;
    }
}