package cloud.fogbow.ras.core.datastore.orderstorage;

import cloud.fogbow.ras.core.models.orders.Order;
import cloud.fogbow.ras.core.models.orders.OrderState;

import javax.persistence.*;
import java.sql.Timestamp;

@Entity
@Table(name = "state_change_event")
public class AuditableOrderStateChange {
    @Id
    @GeneratedValue
    private int id;

    @ManyToOne
    private Order order;

    @Column
    private Timestamp timestamp;

    @Column
    @Enumerated(EnumType.STRING)
    private OrderState newState;

    public AuditableOrderStateChange(Timestamp timestamp, Order order, OrderState newState) {
        this.order = order;
        this.timestamp = timestamp;
        this.newState = newState;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
}
