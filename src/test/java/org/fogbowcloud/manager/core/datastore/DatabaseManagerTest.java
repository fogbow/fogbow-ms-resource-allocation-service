package org.fogbowcloud.manager.core.datastore;

import org.fogbowcloud.manager.core.PropertiesHolder;
import org.fogbowcloud.manager.core.datastore.orderstorage.OrderRepository;
import org.fogbowcloud.manager.core.exceptions.FatalErrorException;
import org.fogbowcloud.manager.core.exceptions.UnexpectedException;
import org.fogbowcloud.manager.core.models.linkedlists.SynchronizedDoublyLinkedList;
import org.fogbowcloud.manager.core.exceptions.InvalidParameterException;
import org.fogbowcloud.manager.core.models.orders.*;
import org.fogbowcloud.manager.core.models.quotas.allocation.ComputeAllocation;
import org.fogbowcloud.manager.core.models.tokens.FederationUserToken;
import org.fogbowcloud.manager.core.plugins.cloud.util.CloudInitUserDataBuilder;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.lang.reflect.Field;

@RunWith(PowerMockRunner.class)
@PrepareForTest(PropertiesHolder.class)
//@PowerMockRunnerDelegate(SpringRunner.class)
//@SpringBootTest
@PowerMockIgnore( {"javax.management.*"})
public class DatabaseManagerTest {

    private static final String DATABASE_PATH = "/tmp/fogbow_history_test.db";
    private String DATABASE_URL = "jdbc:sqlite:" + DATABASE_PATH;

    /**
     * Cleaning all the enviromnent before running the tests.
     */
    @BeforeClass
    public static void init() throws NoSuchFieldException, IllegalAccessException {
        resetDatabaseManagerInstance();
        cleanEnviromnent();
    }

    @Before
    public void setUp() {
        PropertiesHolder propertiesHolder = Mockito.mock(PropertiesHolder.class);
        Mockito.when(propertiesHolder.getProperty(Mockito.anyString())).thenReturn(DATABASE_URL);

        PowerMockito.mockStatic(PropertiesHolder.class);
        BDDMockito.given(PropertiesHolder.getInstance()).willReturn(propertiesHolder);
    }

    /**
     * Remove database file and reset the database manager.
     */
    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        resetDatabaseManagerInstance();
        cleanEnviromnent();
    }

    // test case: Trying to initialize the datastore when an invalid database URL is passed.
    @Test(expected = FatalErrorException.class)
    public void testInitializeDataStoreWithError() {
        // set up
        PropertiesHolder propertiesHolder = Mockito.mock(PropertiesHolder.class);
        Mockito.when(propertiesHolder.getProperty(Mockito.anyString())).thenReturn("invalid_url");

        PowerMockito.mockStatic(PropertiesHolder.class);
        BDDMockito.given(PropertiesHolder.getInstance()).willReturn(propertiesHolder);

        // exercise: it should raise an exception since the database path is an invalid one
        DatabaseManager.getInstance();
    }

    // test case: Tests if a new compute order is added properly in the database.
    @Test
    public void testAddComputeOrder() throws InvalidParameterException, UnexpectedException {
        // set up
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        FederationUserToken federationUserToken = new FederationUserToken("fake-token-provider",
                "token-value", "fake-id", "fake-user");

        Order computeOrder = new ComputeOrder(federationUserToken,
                "requestingMember", "providingMember", 8, 1024,
                30, "fake_image_name", new UserData("extraUserDataFile",
                CloudInitUserDataBuilder.FileType.CLOUD_CONFIG), "fake_public_key", null);
        computeOrder.setOrderStateInTestMode(OrderState.OPEN);

        SynchronizedDoublyLinkedList synchronizedDoublyLinkedList =
                databaseManager.readActiveOrders(OrderState.OPEN);

        // verify
        Assert.assertEquals(0, getListSize(synchronizedDoublyLinkedList));

        // exercise
        databaseManager.add(computeOrder);

        synchronizedDoublyLinkedList =
                databaseManager.readActiveOrders(OrderState.OPEN);

        // verify
        Assert.assertEquals(1, getListSize(synchronizedDoublyLinkedList));
    }

    // test case: Tests if a stored compute order is updated properly in the database.
    @Test
    public void testUpdateComputeOrderState() throws InvalidParameterException, UnexpectedException {
        // set up
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        FederationUserToken federationUserToken = new FederationUserToken("fake-token-provider",
                "token-value", "fake-id", "fake-user");

        ComputeOrder computeOrder = new ComputeOrder("id", federationUserToken,
                "requestingMember", "providingMember", 8, 1024,
                30, "fake_image_name", new UserData("extraUserDataFile",
                CloudInitUserDataBuilder.FileType.CLOUD_CONFIG), "fake_public_key", null);
        computeOrder.setOrderStateInTestMode(OrderState.OPEN);

        databaseManager.add(computeOrder);

        computeOrder.setOrderStateInTestMode(OrderState.FULFILLED);
        computeOrder.setActualAllocation(new ComputeAllocation(10, 10,10));

        // exercise
        databaseManager.update(computeOrder);

        SynchronizedDoublyLinkedList synchronizedDoublyLinkedList =
                databaseManager.readActiveOrders(OrderState.FULFILLED);

        ComputeOrder result = (ComputeOrder) synchronizedDoublyLinkedList.getNext();

        // verify
        Assert.assertEquals(result.getOrderState(), OrderState.FULFILLED);
        Assert.assertEquals(result.getActualAllocation().getRam(), 10);
        Assert.assertEquals(result.getActualAllocation().getvCPU(), 10);
        Assert.assertEquals(result.getActualAllocation().getInstances(), 10);
    }

    // test case: Tests if a new network order is added properly in the database.
    @Test
    public void testAddNetworkOrder() throws InvalidParameterException, UnexpectedException {
        // set up
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        FederationUserToken federationUserToken = new FederationUserToken("fake-token-provider",
                "token-value", "fake-id", "fake-user");

        Order networkOrder = new NetworkOrder(federationUserToken,
                "requestingMember", "providingMember", "gateway",
                "address", NetworkAllocationMode.STATIC);
        networkOrder.setOrderStateInTestMode(OrderState.OPEN);

        SynchronizedDoublyLinkedList synchronizedDoublyLinkedList =
                databaseManager.readActiveOrders(OrderState.OPEN);

        // verify
        Assert.assertEquals(0, getListSize(synchronizedDoublyLinkedList));

        // exercise
        databaseManager.add(networkOrder);

        synchronizedDoublyLinkedList =
                databaseManager.readActiveOrders(OrderState.OPEN);

        // verify
        Assert.assertEquals(1, getListSize(synchronizedDoublyLinkedList));
    }

    // test case: Tests if a stored network order is updated properly in the database.
    @Test
    public void testUpdateNetworkOrderState() throws InvalidParameterException, UnexpectedException {
        // set up
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        FederationUserToken federationUserToken = new FederationUserToken("fake-token-provider",
                "token-value", "fake-id", "fake-user");

        Order networkOrder = new NetworkOrder(federationUserToken,
                "requestingMember", "providingMember", "gateway",
                "address", NetworkAllocationMode.STATIC);
        networkOrder.setOrderStateInTestMode(OrderState.OPEN);

        databaseManager.add(networkOrder);

        networkOrder.setOrderStateInTestMode(OrderState.FULFILLED);

        // exercise
        databaseManager.update(networkOrder);

        SynchronizedDoublyLinkedList synchronizedDoublyLinkedList =
                databaseManager.readActiveOrders(OrderState.FULFILLED);

        Order result = synchronizedDoublyLinkedList.getNext();

        // verify
        Assert.assertEquals(result.getOrderState(), OrderState.FULFILLED);
    }

    // test case: Tests if a new volume order is added properly in the database.
    @Test
    public void testAddVolumeOrder() throws InvalidParameterException, UnexpectedException {
        // set up
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        FederationUserToken federationUserToken = new FederationUserToken("fake-token-provider",
                "token-value", "fake-id", "fake-user");

        Order volumeOrder = new VolumeOrder(federationUserToken,
                "requestingMember", "providingMember", 0,
                "volume-name");
        volumeOrder.setOrderStateInTestMode(OrderState.OPEN);

        SynchronizedDoublyLinkedList synchronizedDoublyLinkedList =
                databaseManager.readActiveOrders(OrderState.OPEN);

        // verify
        Assert.assertEquals(0, getListSize(synchronizedDoublyLinkedList));

        // exercise
        databaseManager.add(volumeOrder);

        synchronizedDoublyLinkedList =
                databaseManager.readActiveOrders(OrderState.OPEN);

        // verify
        Assert.assertEquals(1, getListSize(synchronizedDoublyLinkedList));
    }

    // test case: Tests if a stored volume order is updated properly in the database.
    @Test
    public void testUpdateVolumeOrderState() throws InvalidParameterException, UnexpectedException {
        // set up
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        FederationUserToken federationUserToken = new FederationUserToken("fake-token-provider",
                "token-value", "fake-id", "fake-user");

        Order volumeOrder = new VolumeOrder(federationUserToken,
                "requestingMember", "providingMember", 0,
                "volume-name");
        volumeOrder.setOrderStateInTestMode(OrderState.OPEN);

        databaseManager.add(volumeOrder);

        volumeOrder.setOrderStateInTestMode(OrderState.FULFILLED);

        // exercise
        databaseManager.update(volumeOrder);

        SynchronizedDoublyLinkedList synchronizedDoublyLinkedList =
                databaseManager.readActiveOrders(OrderState.FULFILLED);

        Order result = synchronizedDoublyLinkedList.getNext();

        // verify
        Assert.assertEquals(result.getOrderState(), OrderState.FULFILLED);
    }


    // test case: If a closed order do not have an instance id, it should not be recovered.
    @Test
    public void testGetClosedOrderWithoutInstanceId() throws InvalidParameterException, UnexpectedException {
        // set up
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        FederationUserToken federationUserToken = new FederationUserToken("fake-token-provider",
                "token-value", "fake-id", "fake-user");

        Order volumeOrder = new VolumeOrder(federationUserToken,
                "requestingMember", "providingMember", 0,
                "volume-name");
        volumeOrder.setOrderStateInTestMode(OrderState.CLOSED);

        databaseManager.add(volumeOrder);

        // verify
        Assert.assertNull(volumeOrder.getInstanceId());

        // exercise
        SynchronizedDoublyLinkedList synchronizedDoublyLinkedList =
                databaseManager.readActiveOrders(OrderState.CLOSED);

        // verify
        Assert.assertEquals(0, getListSize(synchronizedDoublyLinkedList));
    }

    // test case: If a closed order has an instance id, it should be recovered.
    @Test
    public void testGetClosedOrderWithInstanceId() throws InvalidParameterException, UnexpectedException {
        // set up
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        FederationUserToken federationUserToken = new FederationUserToken("fake-token-provider",
                "token-value", "fake-id", "fake-user");

        VolumeOrder volumeOrder = new VolumeOrder(federationUserToken,
                "requestingMember", "providingMember", 0,
                "volume-name");
        volumeOrder.setOrderStateInTestMode(OrderState.CLOSED);
        volumeOrder.setInstanceId("instanceId");

        databaseManager.add(volumeOrder);

        // verify
        Assert.assertNotNull(volumeOrder.getInstanceId());

        // exercise
        SynchronizedDoublyLinkedList synchronizedDoublyLinkedList =
                databaseManager.readActiveOrders(OrderState.CLOSED);

        // verify
        Assert.assertEquals(1, getListSize(synchronizedDoublyLinkedList));
    }

    /**
     * Returns size of synchronizedDoublyLinkedList.
     */
    protected int getListSize(SynchronizedDoublyLinkedList synchronizedDoublyLinkedList) {
        int listSize = 0;

        while (synchronizedDoublyLinkedList.getNext() != null) {
            listSize++;
        }

        return listSize;
    }

    private static void resetDatabaseManagerInstance() throws NoSuchFieldException, IllegalAccessException {
        Field instance = DatabaseManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    private static void cleanEnviromnent() {
        File folder = new File(DATABASE_PATH);
        File[] listFiles = folder.listFiles();

        if (listFiles != null) {
            for (File file : listFiles) {
                if (file != null) {
                    file.delete();
                    file.deleteOnExit();
                }
            }
        }

        folder.delete();
        folder.deleteOnExit();
    }
}