package org.fogbowcloud.manager.core.datastore;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.datastore.commands.*;
import org.fogbowcloud.manager.core.datastore.orderstorage.*;
import org.fogbowcloud.manager.core.models.linkedlist.SynchronizedDoublyLinkedList;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderState;

import java.sql.SQLException;

public class DatabaseManager implements StableStorage {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class);

    private static DatabaseManager instance;

    private ComputeOrderStorage computeOrderStorage;
    private NetworkOrderStorage networkOrderStorage;
    private VolumeOrderStorage volumeOrderStorage;
    private AttachmentOrderStorage attachmentOrderStorage;
    private OrderTimestampStorage orderTimestampStorage;

    private DatabaseManager() throws SQLException {
        this.computeOrderStorage = new ComputeOrderStorage();
        this.networkOrderStorage = new NetworkOrderStorage();
        this.volumeOrderStorage = new VolumeOrderStorage();
        this.attachmentOrderStorage = new AttachmentOrderStorage();
        this.orderTimestampStorage = new OrderTimestampStorage();
    }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            try {
                instance = new DatabaseManager();
            } catch (SQLException e) {
                LOGGER.error("Error instantiating database manager", e);
                throw new Error(e);
            }
        }

        return instance;
    }

    @Override
    public void add(Order order) {
        switch (order.getType()) {
            case COMPUTE:
                this.computeOrderStorage.addOrder(order);
                this.orderTimestampStorage.addOrder(order);
                break;
            case NETWORK:
                this.networkOrderStorage.addOrder(order);
                this.orderTimestampStorage.addOrder(order);
                break;
            case VOLUME:
                this.volumeOrderStorage.addOrder(order);
                this.orderTimestampStorage.addOrder(order);
                break;
            case ATTACHMENT:
                this.attachmentOrderStorage.addOrder(order);
                this.orderTimestampStorage.addOrder(order);
                break;
        }
    }

    @Override
    public void update(Order order) {
        switch (order.getType()) {
            case COMPUTE:
                this.computeOrderStorage.updateOrder(order);
                this.orderTimestampStorage.addOrder(order);
                break;
            case NETWORK:
                this.networkOrderStorage.updateOrder(order);
                this.orderTimestampStorage.addOrder(order);
                break;
            case VOLUME:
                this.volumeOrderStorage.updateOrder(order);
                this.orderTimestampStorage.addOrder(order);
                break;
            case ATTACHMENT:
                this.attachmentOrderStorage.updateOrder(order);
                this.orderTimestampStorage.addOrder(order);
                break;
        }
    }

    @Override
    public SynchronizedDoublyLinkedList readActiveOrders(OrderState orderState) {
        SynchronizedDoublyLinkedList synchronizedDoublyLinkedList = new SynchronizedDoublyLinkedList();

        if (orderState.equals(OrderState.CLOSED)) {
            ComputeSQLCommands.updateSelectCommand();
            NetworkSQLCommands.updateSelectCommand();
            VolumeSQLCommands.updateSelectCommand();
            AttachmentSQLCommands.updateSelectCommand();
        }

        this.computeOrderStorage.readOrdersByState(orderState, synchronizedDoublyLinkedList);
        this.networkOrderStorage.readOrdersByState(orderState, synchronizedDoublyLinkedList);
        this.volumeOrderStorage.readOrdersByState(orderState, synchronizedDoublyLinkedList);
        this.attachmentOrderStorage.readOrdersByState(orderState, synchronizedDoublyLinkedList);

        return synchronizedDoublyLinkedList;
    }
}
