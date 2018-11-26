package org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.securityrule.v4_9;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import org.fogbowcloud.ras.core.constants.Messages;
import org.fogbowcloud.ras.core.exceptions.FogbowRasException;
import org.fogbowcloud.ras.core.exceptions.InvalidParameterException;
import org.fogbowcloud.ras.core.exceptions.UnexpectedException;
import org.fogbowcloud.ras.core.models.ResourceType;
import org.fogbowcloud.ras.core.models.orders.Order;
import org.fogbowcloud.ras.core.models.securityrules.Direction;
import org.fogbowcloud.ras.core.models.securityrules.EtherType;
import org.fogbowcloud.ras.core.models.securityrules.Protocol;
import org.fogbowcloud.ras.core.models.securityrules.SecurityRule;
import org.fogbowcloud.ras.core.models.tokens.CloudStackToken;
import org.fogbowcloud.ras.core.models.tokens.Token;
import org.fogbowcloud.ras.core.plugins.interoperability.SecurityRulePlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.CloudStackHttpToFogbowRasExceptionMapper;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.CloudStackQueryAsyncJobResponse;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.CloudStackQueryJobResult;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.CloudStackUrlUtil;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.publicip.v4_9.CreateFirewallRuleAsyncResponse;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.publicip.v4_9.CreateFirewallRuleRequest;
import org.fogbowcloud.ras.util.connectivity.HttpRequestClientUtil;

import static org.fogbowcloud.ras.core.constants.Messages.Error.UNEXPECTED_JOB_STATUS;
import static org.fogbowcloud.ras.core.constants.Messages.Exception.JOB_HAS_FAILED;
import static org.fogbowcloud.ras.core.constants.Messages.Exception.JOB_TIMEOUT;
import static org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.CloudStackRestApiConstants.SecurityGroupPlugin.*;

public class CloudStackSecurityRulePlugin implements SecurityRulePlugin<CloudStackToken> {

    public static final int ONE_SECOND_IN_MILIS = 1000;
    private static final Logger LOGGER = Logger.getLogger(CloudStackSecurityRulePlugin.class);
    private static final int MAX_TRIES = 15;
    private HttpRequestClientUtil client;

    public CloudStackSecurityRulePlugin() {
        this.client = new HttpRequestClientUtil();
    }

    @Override
    public String requestSecurityRule(SecurityRule securityRule, Order majorOrder, CloudStackToken localUserAttributes)
            throws FogbowRasException, UnexpectedException {
        if (securityRule.getDirection() == Direction.OUT) {
            throw new UnsupportedOperationException();
        }
        if (majorOrder.getType() == ResourceType.PUBLIC_IP) {
            String cidr = securityRule.getCidr();
            String portFrom = Integer.toString(securityRule.getPortFrom());
            String portTo = Integer.toString(securityRule.getPortTo());
            String protocol = securityRule.getProtocol().toString();

            CreateFirewallRuleRequest createFirewallRuleRequest = new CreateFirewallRuleRequest.Builder()
                    .protocol(protocol)
                    .startPort(portFrom)
                    .endPort(portTo)
                    .ipAddressId(majorOrder.getInstanceId())
                    .cidrList(cidr)
                    .build();

            CloudStackUrlUtil.sign(createFirewallRuleRequest.getUriBuilder(), localUserAttributes.getTokenValue());

            String jsonResponse = null;
            try {
                jsonResponse = this.client.doGetRequest(createFirewallRuleRequest.getUriBuilder().toString(), localUserAttributes);
            } catch (HttpResponseException e) {
                CloudStackHttpToFogbowRasExceptionMapper.map(e);
            }

            CreateFirewallRuleAsyncResponse response = CreateFirewallRuleAsyncResponse.fromJson(jsonResponse);

            return waitForJobResult(this.client, response.getJobId(), localUserAttributes);
        } else {
            throw new InvalidParameterException(Messages.Exception.INVALID_RESOURCE);
        }
    }

    @Override
    public List<SecurityRule> getSecurityRules(Order majorOrder, CloudStackToken localUserAttributes) throws FogbowRasException, UnexpectedException {
        switch (majorOrder.getType()) {
        	case PUBLIC_IP:
        		return getFirewallRules(majorOrder.getInstanceId(), localUserAttributes);
        	case NETWORK:
        		throw new UnsupportedOperationException();
        	default:
				String errorMsg = String.format(Messages.Error.INVALID_LIST_SECURITY_RULE_TYPE, majorOrder.getType());
				LOGGER.error(errorMsg);
				throw new UnexpectedException(errorMsg);
        }
    }
       
    @Override
    public void deleteSecurityRule(String securityRuleId, CloudStackToken localUserAttributes)
            throws FogbowRasException, UnexpectedException {
        throw new UnsupportedOperationException();
    }

	protected List<SecurityRule> getFirewallRules(String ipAddressId, CloudStackToken localUserAttributes) throws FogbowRasException, UnexpectedException {
		ListFirewallRulesRequest request = new ListFirewallRulesRequest.Builder()
				.ipAddressId(ipAddressId)
				.build();

		CloudStackUrlUtil.sign(request.getUriBuilder(), localUserAttributes.getTokenValue());

		String jsonResponse = null;
		try {
			jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), localUserAttributes);
		} catch (HttpResponseException e) {
			CloudStackHttpToFogbowRasExceptionMapper.map(e);
		}

		ListFirewallRulesResponse response = ListFirewallRulesResponse.fromJson(jsonResponse);
		List<ListFirewallRulesResponse.SecurityRuleResponse> securityRulesResponse = response.getSecurityRulesResponse();
		return convertToFogbowSecurityRules(securityRulesResponse);
	}

	protected List<SecurityRule> convertToFogbowSecurityRules(List<ListFirewallRulesResponse.SecurityRuleResponse> securityRulesResponse) throws UnexpectedException {
		List<SecurityRule> securityRules = new ArrayList<SecurityRule>();
		for (ListFirewallRulesResponse.SecurityRuleResponse securityRuleResponse : securityRulesResponse) {
			Direction direction = securityRuleResponse.getDirection();
			int portFrom = securityRuleResponse.getPortFrom();
			int portTo = securityRuleResponse.getPortTo();
			String cidr = securityRuleResponse.getCidr();
			String ipAddress = securityRuleResponse.getIpAddress();
			EtherType etherType = inferEtherType(ipAddress);
			Protocol protocol = getFogbowProtocol(securityRuleResponse.getProtocol());

			securityRules.add(new SecurityRule(direction, portFrom, portTo, cidr, etherType, protocol));
		}
		return securityRules;
	}

	private Protocol getFogbowProtocol(String protocol) throws UnexpectedException {
		switch (protocol) {
			case TCP_VALUE_PROTOCOL:
				return Protocol.TCP;
			case UDP_VALUE_PROTOCOL:
				return Protocol.UDP;
			case ICMP_VALUE_PROTOCOL:
				return Protocol.ICMP;
			case ALL_VALUE_PROTOCOL:
				return Protocol.ANY;
			default:
				throw new UnexpectedException(Messages.Exception.INVALID_CLOUDSTACK_PROTOCOL);
		}
	}

	private EtherType inferEtherType(String ipAddress) {
		if (CidrUtils.isIpv4(ipAddress)) {
			return EtherType.IPv4;
		} else if (CidrUtils.isIpv6(ipAddress)) {
			return EtherType.IPv6;
		} else {
			return null;
		}
	}

    protected String waitForJobResult(HttpRequestClientUtil client, String jobId, CloudStackToken token)
            throws FogbowRasException, UnexpectedException {
        CloudStackQueryAsyncJobResponse queryAsyncJobResult = getAsyncJobResponse(client, jobId, token);

        if (queryAsyncJobResult.getJobStatus() == CloudStackQueryJobResult.PROCESSING) {
            for (int i = 0; i < MAX_TRIES; i++) {
                queryAsyncJobResult = getAsyncJobResponse(client, jobId, token);
                if (queryAsyncJobResult.getJobStatus() != CloudStackQueryJobResult.PROCESSING) {
                    return processJobResult(queryAsyncJobResult, jobId);
                }
                try {
                    Thread.sleep(ONE_SECOND_IN_MILIS);
                } catch (InterruptedException e) {
                    throw new FogbowRasException();
                }
            }
            deleteSecurityRule(queryAsyncJobResult.getJobInstanceId(), token);
            throw new FogbowRasException(String.format(JOB_TIMEOUT, jobId));
        }
        return processJobResult(queryAsyncJobResult, jobId);
    }

    protected String processJobResult(CloudStackQueryAsyncJobResponse queryAsyncJobResult,
                                      String jobId)
            throws FogbowRasException, UnexpectedException {
        switch (queryAsyncJobResult.getJobStatus()){
            case CloudStackQueryJobResult.SUCCESS:
                return queryAsyncJobResult.getJobInstanceId();
            case CloudStackQueryJobResult.FAILURE:
                throw new FogbowRasException(String.format(JOB_HAS_FAILED, jobId));
            default:
                throw new UnexpectedException(UNEXPECTED_JOB_STATUS);
        }
    }

    protected CloudStackQueryAsyncJobResponse getAsyncJobResponse(HttpRequestClientUtil client, String jobId, Token token)
            throws FogbowRasException {
        String jsonResponse = CloudStackQueryJobResult.getQueryJobResult(client, jobId, token);
        return CloudStackQueryAsyncJobResponse.fromJson(jsonResponse);
    }

    protected void setClient(HttpRequestClientUtil client) {
        this.client = client;
    }

}
