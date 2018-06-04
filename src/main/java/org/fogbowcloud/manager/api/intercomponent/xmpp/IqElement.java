package org.fogbowcloud.manager.api.intercomponent.xmpp;

public enum IqElement {

    QUERY("query"),
    ORDER("order"),
    ORDER_ID("orderId"),
    FEDERATION_USER("federationUser"),
    INSTANCE("instance"), 
    INSTANCE_TYPE("instanceType"),
    ORDER_CLASS_NAME("orderClassName"), 
    INSTANCE_CLASS_NAME("instanceClassName"),
    USER_QUOTA("userQuota"),
    USER_QUOTA_CLASS_NAME("userQuotaClassName"),
    MEMBER_ID("memberId"),
    EVENT("event");

    private final String element;

    IqElement(final String elementName) {
        this.element = elementName;
    }

    @Override
    public String toString() {
        return element;
    }
}
