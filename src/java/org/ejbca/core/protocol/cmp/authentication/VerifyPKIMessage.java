/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.ejbca.core.protocol.cmp.authentication;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSession;
import org.ejbca.config.CmpConfiguration;
import org.ejbca.core.ejb.authorization.AuthorizationSession;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSession;
import org.ejbca.core.ejb.ca.store.CertificateStoreSession;
import org.ejbca.core.ejb.ra.UserAdminSession;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.log.Admin;

import com.novosec.pkix.asn1.cmp.PKIMessage;

/**
 * Verifies a CMP message using a suitable authentication module. The authentication modules 
 * are specified in the properties file.
 * 
 * @version $Id: VerifyPKIMessage.java 13721 2012-01-10 11:55:42Z aveen4711 $
 *
 */
public class VerifyPKIMessage {
	
	private static final Logger log = Logger.getLogger(VerifyPKIMessage.class);
	
	private CAInfo cainfo;
	private ICMPAuthenticationModule authModule;
	
	private Admin admin;
	private CAAdminSession caAdminSession;
	private UserAdminSession userAdminSession;
	private CertificateStoreSession certificateStoreSession;
	private AuthorizationSession authorizationSessoin;
	private EndEntityProfileSession eeProfileSession;
	
	private String errMsg;

	public VerifyPKIMessage() {
		this.cainfo = null;
		this.authModule = null;
		
		this.admin = null;
		this.caAdminSession = null;
		this.userAdminSession = null;
		this.certificateStoreSession = null;
		this.authorizationSessoin = null;
		this.eeProfileSession = null;
		
		this.errMsg = null;
	}
	
	public VerifyPKIMessage(final CAInfo cainfo, final Admin admin, final CAAdminSession caSession, final UserAdminSession userSession, final CertificateStoreSession certSession,
			final AuthorizationSession authSession, final EndEntityProfileSession eeprofSession) {
		this.cainfo = cainfo;
		this.authModule = null;
		
		this.admin = admin;
		this.caAdminSession = caSession;
		this.userAdminSession = userSession;
		this.certificateStoreSession = certSession;
		this.authorizationSessoin = authSession;
		this.eeProfileSession = eeprofSession;
	}
	
	
	/**
	 * Returns the error message resulted in failing to verify the PKIMessage
	 * 
	 * The error message is set if verify() returns false.
	 * 
	 * @return the error message as String. Null if the verification succeeded.
	 */
	public String getErrorMessage() {
		return this.errMsg;
	}
	
	/**
	 * Returns the name of the authentication module that was successfully used to authenticate the message.
	 * 
	 * The authentication module is set if verify() returns true.
	 * 
	 * @return the name of the successful authentication module. Null if message verification failed.
	 */
	public ICMPAuthenticationModule getUsedAuthenticationModule() {
		return this.authModule;
	}
	
	/**
	 * Verifies the authenticity of msg
	 * 
     * @param msg PKIMessage to verify
     * @param username that the PKIMessage should match or null
     * @return True if verification is successful. False otherwise
     */
    public boolean verify(final PKIMessage msg, final String username) {
		if (log.isTraceEnabled()) {
			log.trace(">verify");
		}
		boolean ret = false;
		final String authModules = CmpConfiguration.getAuthenticationModule();
		final String authparameters = CmpConfiguration.getAuthenticationParameters();
		final String modules[] = authModules.split(";");
		final String params[] = authparameters.split(";");
		
		ICMPAuthenticationModule module = null;
		int i=0;
		while (i<modules.length) {
			if (log.isDebugEnabled()) {
                log.debug("Trying to verify the message authentication by using: " + modules[i] );
                log.debug("Authentication module parameter: " + (params[i]!=null ? params[i]:"null") ); 
			}

			module = getAuthModule(modules[i].trim(), params[i].trim(), msg);
			if((module != null) && module.verifyOrExtract(msg, username)) {
				this.authModule = module;
				ret = true;
				break;
			}
			if ((module != null) && (module.getErrorMessage() != null)) {
				errMsg = module.getErrorMessage();
			}
			i++;
		}
		if (log.isTraceEnabled()) {
			log.trace("<verify: "+ret);
		}
		return ret;
	}
	
	/**
	 * Returns the authentication module whose name is 'module'
	 * 
	 * @param module
	 * @param parameter
	 * @param pkimsg
	 * @return The authentication module whose name is 'module'. Null if no such module is implemented.
	 */
	private ICMPAuthenticationModule getAuthModule(final String module, final String parameter, final PKIMessage pkimsg) {
		if(StringUtils.equals(module, CmpConfiguration.AUTHMODULE_HMAC)) {
			final HMACAuthenticationModule hmacmodule = new HMACAuthenticationModule(parameter);
			hmacmodule.setSession(this.admin, this.userAdminSession, this.certificateStoreSession);
			hmacmodule.setCaInfo(this.cainfo);
			return hmacmodule;
		} else if(StringUtils.equals(module, CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE)) {
			final EndEntityCertificateAuthenticationModule eemodule = new EndEntityCertificateAuthenticationModule(parameter);
			eemodule.setSession(this.admin, this.caAdminSession, this.certificateStoreSession, this.authorizationSessoin, this.eeProfileSession, this.userAdminSession);
			return eemodule;
		}
		if(!CmpConfiguration.getRAOperationMode()) {
			if(StringUtils.equals(module, CmpConfiguration.AUTHMODULE_REG_TOKEN_PWD)){
				return new RegTokenPasswordExtractor();
			} else if(StringUtils.equals(module, CmpConfiguration.AUTHMODULE_DN_PART_PWD)) {
				return new DnPartPasswordExtractor(parameter);
			}
		}			
		errMsg = "Unrecognized authentication module '" + module + "'";
		return null;
	}

}
