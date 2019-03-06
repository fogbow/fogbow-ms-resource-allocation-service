package cloud.fogbow.ras.core.intercomponent.xmpp.requesters;

import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.SystemUser;
import cloud.fogbow.common.util.GsonHolder;
import cloud.fogbow.common.util.connectivity.GenericRequestResponse;
import cloud.fogbow.ras.core.intercomponent.xmpp.IqElement;
import cloud.fogbow.ras.core.intercomponent.xmpp.PacketSenderHolder;
import cloud.fogbow.ras.core.intercomponent.xmpp.RemoteMethod;
import cloud.fogbow.ras.core.intercomponent.xmpp.XmppErrorConditionToExceptionTranslator;
import cloud.fogbow.common.util.connectivity.GenericRequest;
import cloud.fogbow.ras.core.plugins.interoperability.genericrequest.FogbowGenericRequest;
import org.dom4j.Element;
import org.xmpp.packet.IQ;

public class RemoteGenericRequest implements RemoteRequest<GenericRequestResponse> {

    private SystemUser systemUser;
    private FogbowGenericRequest genericRequest;
    private String cloudName;
    private String provider;

    public RemoteGenericRequest(String provider, String cloudName, FogbowGenericRequest genericRequest, SystemUser systemUser) {
        this.systemUser = systemUser;
        this.provider = provider;
        this.cloudName = cloudName;
        this.genericRequest = genericRequest;
    }

    @Override
    public GenericRequestResponse send() throws Exception {
        IQ iq = marshal(provider, cloudName, genericRequest, systemUser);

        IQ response = (IQ) PacketSenderHolder.getPacketSender().syncSendPacket(iq);
        XmppErrorConditionToExceptionTranslator.handleError(response, this.provider);
        return unmarshal(response);
    }

    private GenericRequestResponse unmarshal(IQ response) throws UnexpectedException {
        Element queryElement = response.getElement().element(IqElement.QUERY.toString());
        String genericRequestResponseStr = queryElement.element(IqElement.GENERIC_REQUEST_RESPONSE.toString()).getText();

        String instanceClassName = queryElement.element(IqElement.GENERIC_REQUEST_RESPONSE_CLASS_NAME.toString()).getText();

        GenericRequestResponse genericRequestResponse;
        try {
            genericRequestResponse = (GenericRequestResponse) GsonHolder.getInstance().
                    fromJson(genericRequestResponseStr, Class.forName(instanceClassName));
        } catch (Exception e) {
            throw new UnexpectedException(e.getMessage());
        }
        return genericRequestResponse;
    }

    public static IQ marshal(String provider, String cloudName, FogbowGenericRequest genericRequest, SystemUser systemUser) {
        IQ iq = new IQ(IQ.Type.set);
        iq.setTo(provider);

        Element queryElement = iq.getElement().addElement(IqElement.QUERY.toString(),
                RemoteMethod.REMOTE_GENERIC_REQUEST.toString());

        Element cloudNameElement = queryElement.addElement(IqElement.CLOUD_NAME.toString());
        cloudNameElement.setText(cloudName);

        Element userElement = queryElement.addElement(IqElement.FEDERATION_USER.toString());
        userElement.setText(GsonHolder.getInstance().toJson(systemUser));

        Element genericRequestElement = queryElement.addElement(IqElement.GENERIC_REQUEST.toString());
        genericRequestElement.setText(GsonHolder.getInstance().toJson(genericRequest));

        Element genericRequestClassNameElement = queryElement.addElement(IqElement.GENERIC_REQUEST_CLASS_NAME.toString());
        genericRequestClassNameElement.setText(genericRequest.getClass().getName());
        return iq;
    }
}