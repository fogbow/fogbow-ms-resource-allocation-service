package org.fogbowcloud.manager.api.remote.xmpp.requesters;

import org.fogbowcloud.manager.api.remote.exceptions.RemoteRequestException;
import org.fogbowcloud.manager.core.exceptions.OrderManagementException;
import org.fogbowcloud.manager.core.manager.plugins.identity.exceptions.UnauthorizedException;

public interface RemoteRequest {

    void send() throws RemoteRequestException, OrderManagementException, UnauthorizedException;

}
