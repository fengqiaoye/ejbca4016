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

import java.io.ByteArrayOutputStream;
import java.security.KeyStore;

import org.apache.log4j.Logger;
import org.ejbca.core.model.AlgorithmConstants;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.UserDataConstants;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.core.model.util.GenerateToken;
import org.ejbca.extra.db.ExtRARequest;
import org.ejbca.extra.db.ISubMessage;
import org.ejbca.extra.db.KeyStoreRetrievalRequest;
import org.ejbca.extra.db.KeyStoreRetrievalResponse;
import org.ejbca.util.keystore.KeyTools;

/**
 * Process keystore generation/retrieval requests.
 * 
 * @version $Id: KeyStoreRetrievalRequestProcessor.java 11818 2011-04-26 11:14:44Z jeklund $
 */
public class KeyStoreRetrievalRequestProcessor extends MessageProcessor implements ISubMessageProcessor {

	private static final Logger log = Logger.getLogger(KeyStoreRetrievalRequestProcessor.class);

	/** @see ISubMessageProcessor#process(Admin, ISubMessage, String) */
	public ISubMessage process(Admin admin, ISubMessage submessage, String errormessage) {
		if(errormessage == null){
			return processKeyStoreRetrievalRequest(admin, (KeyStoreRetrievalRequest) submessage);
		}else{
			return new KeyStoreRetrievalResponse(((ExtRARequest) submessage).getRequestId(), false, errormessage, null, null);
		}
	}

    /**
     * Lookup the requested user and generate or recover a keystore.
     */
    private KeyStoreRetrievalResponse processKeyStoreRetrievalRequest(Admin admin, KeyStoreRetrievalRequest submessage) {
        log.debug("Processing KeyStoreRetrievalRequest");
		try {
			UserDataVO data = null;
			try {
				data = userAdminSession.findUser(admin, submessage.getUsername());
			} catch (AuthorizationDeniedException e) {
				log.info("External RA admin was denied access to a user: " + e.getMessage());
			}
			if (data == null) {
				return new KeyStoreRetrievalResponse(((ExtRARequest) submessage).getRequestId(), false, "No such user.", null, null);
			}
			// Find out if are doing key recovery
			int endEntityProfileId = data.getEndEntityProfileId();	// TODO should probably also be used to get keysize and algorithm in the future..
			boolean usekeyrecovery = globalConfigurationSession.getCachedGlobalConfiguration(admin).getEnableKeyRecovery();
			boolean savekeys = data.getKeyRecoverable() && usekeyrecovery &&  (data.getStatus() != UserDataConstants.STATUS_KEYRECOVERY);
			boolean loadkeys = (data.getStatus() == UserDataConstants.STATUS_KEYRECOVERY) && usekeyrecovery;
			boolean reusecertificate = endEntityProfileSession.getEndEntityProfile(admin, endEntityProfileId).getReUseKeyRecoveredCertificate();
			// Generate or recover keystore and save it in the configured format 
			GenerateToken tgen = new GenerateToken(authenticationSession, userAdminSession, caAdminSession, keyRecoverySession, signSession);
			byte[] buf = null;
			int tokentype = data.getTokenType();
			boolean createJKS = (tokentype == SecConst.TOKEN_SOFT_JKS);
			KeyStore ks = tgen.generateOrKeyRecoverToken(admin, submessage.getUsername(), submessage.getPassword(), data.getCAId(), "2048", AlgorithmConstants.KEYALGORITHM_RSA,
					createJKS, loadkeys, savekeys, reusecertificate, endEntityProfileId);
			if (tokentype == SecConst.TOKEN_SOFT_PEM) {
				buf = KeyTools.getSinglePemFromKeyStore(ks, submessage.getPassword().toCharArray());
			} else if (tokentype == SecConst.TOKEN_SOFT_P12 || tokentype == SecConst.TOKEN_SOFT_JKS) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ks.store(baos, submessage.getPassword().toCharArray());
				buf = baos.toByteArray();
			} else {
				return new KeyStoreRetrievalResponse(submessage.getRequestId(), false, "Unknown token type.", null, null);
			}
			return new KeyStoreRetrievalResponse(submessage.getRequestId(), true, null, tokentype, buf);
		} catch (Exception e) {
			log.debug("External RA request generated an error: " + e.getMessage());
			return new KeyStoreRetrievalResponse(submessage.getRequestId(), false, "Error " + e.getMessage(), null, null);
		}
	}
}
