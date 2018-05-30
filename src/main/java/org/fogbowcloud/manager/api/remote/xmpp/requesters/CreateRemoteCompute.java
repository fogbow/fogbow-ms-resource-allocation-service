package org.fogbowcloud.manager.api.remote.xmpp.requesters;

import com.google.gson.Gson;
import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.fogbowcloud.manager.api.remote.exceptions.RemoteRequestException;
import org.fogbowcloud.manager.api.remote.exceptions.UnexpectedException;
import org.fogbowcloud.manager.api.remote.xmpp.IqElement;
import org.fogbowcloud.manager.api.remote.xmpp.RemoteMethod;
import org.fogbowcloud.manager.core.exceptions.OrderManagementException;
import org.fogbowcloud.manager.core.manager.plugins.identity.exceptions.UnauthorizedException;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.jamppa.component.PacketSender;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

public class CreateRemoteCompute implements RemoteRequest<Void> {

    private static final Logger LOGGER = Logger.getLogger(CreateRemoteCompute.class);

    private PacketSender packetSender;
    private Order order;

    public CreateRemoteCompute(PacketSender packetSender, Order order) {
        this.packetSender = packetSender;
        this.order = order;
    }

    @Override
    public Void send() throws RemoteRequestException, OrderManagementException, UnauthorizedException {
        if (packetSender == null) {
            LOGGER.warn("Packet sender not set.");
            throw new IllegalArgumentException("Packet sender not set.");
        }

        IQ iq = createIq();
        IQ response = (IQ) packetSender.syncSendPacket(iq);

        if (response == null) {
            String message = "Unable to retrieve the response from providing member: " + order.getProvidingMember();
            throw new UnexpectedException(message);
        }
        if (response.getError() != null) {
            if (response.getError().getCondition() == PacketError.Condition.forbidden){
                String message = "The order was not authorized for: " + order.getId();
                throw new UnauthorizedException(message);
            } else if (response.getError().getCondition() == PacketError.Condition.bad_request){
                String message = "The order was duplicated on providing member: " + order.getProvidingMember();
                throw new OrderManagementException(message);
            }
        }
        LOGGER.debug("Request for order: " + order.getId() + " has been sent to " + order.getProvidingMember());
        return null;
    }

    private IQ createIq() {
        LOGGER.debug("Creating IQ for order: " + order.getId());

        IQ iq = new IQ(IQ.Type.set);
        iq.setTo(order.getProvidingMember());
        iq.setID(order.getId());

        Element queryElement = iq.getElement().addElement(IqElement.QUERY.toString(),
                RemoteMethod.CREATE_REMOTE_ORDER.toString());
        Element orderElement = queryElement.addElement(IqElement.ORDER.toString());

        LOGGER.debug("Jsonifying Order");
        String orderJson = new Gson().toJson(order);
        orderElement.setText(orderJson);
        return iq;
    }
}
