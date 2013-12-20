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
package org.ejbca.extra.caservice.processor;

import java.math.BigInteger;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.extra.db.ExtRARequest;
import org.ejbca.extra.db.ExtRAResponse;
import org.ejbca.extra.db.ISubMessage;
import org.ejbca.extra.db.RevocationRequest;
import org.ejbca.util.CertTools;

/**
 * 
 * @author tomas
 * @version $Id: RevocationRequestProcessor.java 11655 2011-03-31 21:17:16Z jeklund $
 */
public class RevocationRequestProcessor extends MessageProcessor implements ISubMessageProcessor {
    private static final Logger log = Logger.getLogger(RevocationRequestProcessor.class);

    public ISubMessage process(Admin admin, ISubMessage submessage, String errormessage) {
		if(errormessage == null){
			return processExtRARevocationRequest(admin, (RevocationRequest) submessage);
		}else{
			return new ExtRAResponse(((ExtRARequest) submessage).getRequestId(), false, errormessage);
		}
    }

	private ISubMessage processExtRARevocationRequest(Admin admin, RevocationRequest submessage) {
		log.debug("Processing ExtRARevocationRequest");
		ExtRAResponse retval = null;
		try {			 
			// If this is a message that does contain an explicit username, use it
			String username = submessage.getUsername();
			String issuerDN = submessage.getIssuerDN();
			BigInteger serno = submessage.getCertificateSN();
			if (StringUtils.isEmpty(issuerDN) && StringUtils.isEmpty(username)) {
				retval = new ExtRAResponse(submessage.getRequestId(),false,"Either username or issuer/serno is required");
			} else {
				if (StringUtils.isEmpty(username)) {
					username = certificateStoreSession.findUsernameByCertSerno(admin, serno, CertTools.stringToBCDNString(issuerDN));
				} 
				if (username != null) {
					if ( (submessage.getRevokeAll() || submessage.getRevokeUser()) ) {
						// Revoke all users certificates by revoking the whole user
						UserDataVO vo = userAdminSession.findUser(admin,username);
						if (vo != null) {
							userAdminSession.revokeUser(admin,username, submessage.getRevocationReason());
							if (!submessage.getRevokeUser()) {
								// If we were not to revoke the user itself, but only the certificates, we should set back status
								userAdminSession.setUserStatus(admin, username, vo.getStatus());
							}					
						} else {
							retval = new ExtRAResponse(submessage.getRequestId(),false,"User not found from username: username="+username);							
						}
					} else {
						// Revoke only this certificate
						userAdminSession.revokeCert(admin, serno, CertTools.stringToBCDNString(issuerDN), submessage.getRevocationReason());				
					}					
				} else {
					retval = new ExtRAResponse(submessage.getRequestId(),false,"User not found from issuer/serno: issuer='"+issuerDN+"', serno="+serno);					
				}
				// If we didn't create any other return value, it was a success
				if (retval == null) {
					retval = new ExtRAResponse(submessage.getRequestId(),true,null);					
				}
			}
		} catch (AuthorizationDeniedException e) {
			log.error("Error processing ExtRARevocationRequest : ", e);
			retval = new ExtRAResponse(submessage.getRequestId(),false, "AuthorizationDeniedException: " + e.getMessage());
		}catch(Exception e){
			log.error("Error processing ExtRARevocationRequest : ", e);
			retval = new ExtRAResponse(submessage.getRequestId(),false,e.getMessage());
		} 
		
		return retval;
	}
	
}

