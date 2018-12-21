package org.fogbowcloud.ras.core.plugins.aaa.authentication;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.fogbowcloud.ras.core.HomeDir;
import org.fogbowcloud.ras.core.PropertiesHolder;
import org.fogbowcloud.ras.core.constants.ConfigurationConstants;
import org.fogbowcloud.ras.core.constants.Messages;
import org.fogbowcloud.ras.core.exceptions.FatalErrorException;
import org.fogbowcloud.ras.core.exceptions.FogbowRasException;
import org.fogbowcloud.ras.core.exceptions.UnauthenticatedUserException;
import org.fogbowcloud.ras.util.RSAUtil;

public class RASAuthenticationHolder {

	private static final Logger LOGGER = Logger.getLogger(RASAuthenticationHolder.class);
	
    public static final long EXPIRATION_INTERVAL = TimeUnit.DAYS.toMillis(1); // One day
	
	private RSAPublicKey rasPublicKey;
	private RSAPrivateKey rasPrivateKey;
	private String publicKeyFilePath;
	private String privateKeyFilePath;
	private static RASAuthenticationHolder instance;

	// TODO:
	// This should not be public. It is used in LdapTokenGeneratorPluginTest only. That test should be fixed
    // not to need to call this constructor, which should then be made private.
	public RASAuthenticationHolder() {
        this.rasPublicKey = null;
        this.rasPrivateKey = null;
        this.publicKeyFilePath = null;
        this.privateKeyFilePath = null;
	}
	
    public static synchronized RASAuthenticationHolder getInstance() throws FatalErrorException {
        if (instance == null) {
            instance = new RASAuthenticationHolder();
        }
        return instance;
    }

    public void setPublicKeyFilePath(String publicKeyFilePath) {
        this.publicKeyFilePath = publicKeyFilePath;
    }

    public void setPrivateKeyFilePath(String privateKeyFilePath) {
        this.privateKeyFilePath = privateKeyFilePath;
    }

    public String createSignature(String message) throws FogbowRasException {
    	try {
    		return RSAUtil.sign(this.getPrivateKey(), message);
		} catch (Exception e) {
	    	String errorMsg = String.format(Messages.Exception.AUTHENTICATION_ERROR);
	    	LOGGER.error(errorMsg, e);
	        throw new FogbowRasException(errorMsg, e);
		}
    }
    
	public String generateExpirationTime() {
		Date expirationDate = new Date(getNow() + EXPIRATION_INTERVAL);
        String expirationTime = Long.toString(expirationDate.getTime());
		return expirationTime;
	}
    
    protected boolean verifySignature(String tokenMessage, String signature) throws FogbowRasException {
        try {
            return RSAUtil.verify(this.getPublicKey(), tokenMessage, signature);
        } catch (Exception e) {
        	String errorMsg = Messages.Exception.AUTHENTICATION_ERROR;
			LOGGER.error(errorMsg, e);
            throw new FogbowRasException(errorMsg, e);
        }
    }
    
    protected boolean checkValidity(long timestamp) {
    	Date currentDate = new Date(getNow());
    	Date expirationDate = new Date(timestamp);
        if (expirationDate.before(currentDate)) {
        	return true;
        }
        return false;
	}

    protected RSAPublicKey getPublicKey() throws IOException, GeneralSecurityException {
	    if (this.rasPublicKey == null) {
            String filename = null;
            if (this.publicKeyFilePath == null) {
                filename = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.RAS_PUBLIC_KEY_FILE_PATH);
            } else {
                filename = this.publicKeyFilePath;
            }
            LOGGER.info("PublicKey file: " + filename);
            this.rasPublicKey = RSAUtil.getPublicKey(filename);
        }
	    return this.rasPublicKey;
    }
    
    protected RSAPrivateKey getPrivateKey() throws IOException, GeneralSecurityException {
        if (this.rasPrivateKey == null) {
            String filename = null;
            if (this.privateKeyFilePath == null) {
                filename = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.RAS_PRIVATE_KEY_FILE_PATH);
            } else {
                filename = this.privateKeyFilePath;
            }
            LOGGER.info("PrivateKey file: " + filename);
            this.rasPrivateKey = RSAUtil.getPrivateKey(filename);
        }
        return this.rasPrivateKey;
    }

    protected long getNow() {
    	return System.currentTimeMillis();
    }

}
