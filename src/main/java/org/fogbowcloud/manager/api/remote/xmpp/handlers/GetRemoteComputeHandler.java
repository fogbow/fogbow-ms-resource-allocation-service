package org.fogbowcloud.manager.api.remote.xmpp.handlers;

import com.google.gson.Gson;
import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.fogbowcloud.manager.api.remote.RemoteFacade;
import org.fogbowcloud.manager.api.remote.xmpp.IqElement;
import org.fogbowcloud.manager.api.remote.xmpp.RemoteMethod;
import org.fogbowcloud.manager.core.exceptions.InstanceNotFoundException;
import org.fogbowcloud.manager.core.exceptions.PropertyNotSpecifiedException;
import org.fogbowcloud.manager.core.exceptions.RequestException;
import org.fogbowcloud.manager.core.exceptions.UnauthenticatedException;
import org.fogbowcloud.manager.core.manager.plugins.identity.exceptions.TokenCreationException;
import org.fogbowcloud.manager.core.manager.plugins.identity.exceptions.UnauthorizedException;
import org.fogbowcloud.manager.core.models.orders.instances.ComputeInstance;
import org.fogbowcloud.manager.core.models.token.FederationUser;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

public class GetRemoteComputeHandler extends AbstractQueryHandler {

    private static final Logger LOGGER = Logger.getLogger(GetRemoteComputeHandler.class);

    public static final String GET_REMOTE_INSTANCE = RemoteMethod.GET_REMOTE_INSTANCE.toString();

    public GetRemoteComputeHandler() {
        super(GET_REMOTE_INSTANCE);
    }

    @Override
    public IQ handle(IQ iq) {
        Element queryElement = iq.getElement().element(IqElement.QUERY.toString());
        Element remoteOrderIdElement = queryElement.element(IqElement.REMOTE_ORDER_ID.toString());
        String orderId = remoteOrderIdElement.getText();

        Element federationUserElement = iq.getElement().element(IqElement.FEDERATION_USER.toString());
        FederationUser federationUser = new Gson().fromJson(federationUserElement.getText(), FederationUser.class);

        IQ response = IQ.createResultIQ(iq);

        try {
            ComputeInstance compute;
            compute = RemoteFacade.getInstance().getCompute(orderId, federationUser);

            Element queryEl = response.getElement().addElement(IqElement.QUERY.toString(), GET_REMOTE_INSTANCE);
            Element instanceElement = queryEl.addElement(IqElement.INSTANCE.toString());
            instanceElement.setText(new Gson().toJson(compute));
        } catch (UnauthenticatedException e) {
            LOGGER.error("Unexpected error.");
            response.setError(PacketError.Condition.not_authorized);
        } catch (PropertyNotSpecifiedException e) {
            // TODO: Switch this error for an appropriate one.
            response.setError(PacketError.Condition.internal_server_error);
        } catch (RequestException e) {
            // TODO: Switch this error for an appropriate one.
            response.setError(PacketError.Condition.internal_server_error);
        } catch (InstanceNotFoundException e) {
            LOGGER.error("The instance does not exist.", e);
            response.setError(PacketError.Condition.item_not_found);
        } catch (TokenCreationException e) {
            LOGGER.error("Error while creating token", e);
            response.setError(PacketError.Condition.service_unavailable);
        } catch (UnauthorizedException e) {
            LOGGER.error("The user is not authorized to get the instance.", e);
            response.setError(PacketError.Condition.forbidden);
        } finally {
            return response;
        }
    }

}
