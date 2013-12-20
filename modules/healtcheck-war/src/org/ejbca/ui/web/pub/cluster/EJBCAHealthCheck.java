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

package org.ejbca.ui.web.pub.cluster;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.ejbca.config.EjbcaConfiguration;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionLocal;
import org.ejbca.core.ejb.ca.publisher.PublisherSessionLocal;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionLocal;

/**
 * EJBCA Health Checker. 
 * 
 * Does the following system checks.
 * 
 * * If a maintenance file is specific and the property is set to true, this message will be returned
 * * Not about to run out if memory i below configurable value
 * * Database connection can be established.
 * * All CATokens are active, if not set as offline and not set to specifically not be monitored
 * * All Publishers can establish connection
 * 
 * * Optionally you can configure the CAToken test to also make a test signature, not only check if the token status is active.
 * 
 * @author Philip Vendil
 * @version $Id: EJBCAHealthCheck.java 10442 2010-11-12 08:26:11Z anatom $
 */

public class EJBCAHealthCheck extends CommonHealthCheck {
	
	private static Logger log = Logger.getLogger(EJBCAHealthCheck.class);

	private boolean checkPublishers = EjbcaConfiguration.getHealthCheckPublisherConnections();
	
	private CAAdminSessionLocal caAdminSession;
	private PublisherSessionLocal publisherSession;
	private CertificateStoreSessionLocal certificateStoreSession;

	public EJBCAHealthCheck(CAAdminSessionLocal caAdminSession, PublisherSessionLocal publisherSession, CertificateStoreSessionLocal certificateStoreSession) {
	    this.caAdminSession = caAdminSession;
	    this.publisherSession = publisherSession;
	    this.certificateStoreSession = certificateStoreSession;
	}
	
	public String checkHealth(HttpServletRequest request) {
		if (log.isDebugEnabled()) {
			log.debug("Starting HealthCheck requested by : " + request.getRemoteAddr());
		}
		String errormessage = "";
		
		errormessage += checkMaintenance();
		if( !errormessage.equals("") ) { 
			// if Down for maintenance do not perform more checks
			return errormessage; 
		} 
		errormessage += checkDB();
		if(errormessage.equals("")) {
			errormessage += checkMemory();								
			errormessage += checkCAs();	

			if(checkPublishers){
				errormessage += checkPublishers();
			}
		}
		
		if(errormessage.equals("")){
			// everything seems ok.
			errormessage = null;
		}
		
		return errormessage;
	}
		
	private String checkDB(){
		if (log.isDebugEnabled()) {
			log.debug("Checking database connection.");
		}
		return certificateStoreSession.getDatabaseStatus();
	}

	private String checkCAs(){
		if (log.isDebugEnabled()) {
			log.debug("Checking CAs.");
		}
		return caAdminSession.healthCheck();
	}
	
	private String checkPublishers(){
		if (log.isDebugEnabled()) {
			log.debug("Checking publishers.");
		}
		return publisherSession.testAllConnections();
	}

    public void setCheckPublishers(boolean checkPublishers) {
        this.checkPublishers = checkPublishers;
    }
	
}
