package org.fogbowcloud.manager.utils;

import java.util.Properties;

import org.fogbowcloud.manager.core.SharedOrderHolders;
import org.fogbowcloud.manager.core.manager.constants.ConfigurationConstants;

public class PropertiesUtil {

	private static Properties properties = null;
	
    public static Properties getPropertie() {
        synchronized (SharedOrderHolders.class) {
            if (properties == null) {
            	properties = new Properties();
            }
            return properties;
        }
    }

    // TODO implement tests
    public static String getLocalMemberId() {
    	Properties propertie = getPropertie();
    	return propertie.getProperty(ConfigurationConstants.XMPP_ID_KEY);
    }
	
}
