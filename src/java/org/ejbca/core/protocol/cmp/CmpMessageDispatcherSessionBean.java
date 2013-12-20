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

package org.ejbca.core.protocol.cmp;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DEREncodable;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.cmp.PKIMessages;
import org.cesecore.core.ejb.ca.store.CertificateProfileSessionLocal;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSessionLocal;
import org.ejbca.config.CmpConfiguration;
import org.ejbca.core.ejb.JndiHelper;
import org.ejbca.core.ejb.authorization.AuthorizationSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.CaSessionLocal;
import org.ejbca.core.ejb.ca.sign.SignSessionLocal;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionLocal;
import org.ejbca.core.ejb.ra.CertificateRequestSessionLocal;
import org.ejbca.core.ejb.ra.UserAdminSessionLocal;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.protocol.FailInfo;
import org.ejbca.core.protocol.IResponseMessage;
import org.ejbca.core.protocol.ResponseStatus;
import org.ejbca.ui.web.LimitLengthASN1Reader;
import org.ejbca.util.CryptoProviderTools;

import com.novosec.pkix.asn1.cmp.PKIBody;
import com.novosec.pkix.asn1.cmp.PKIHeader;
import com.novosec.pkix.asn1.cmp.PKIMessage;

/**
 * Class that receives a CMP message and passes it on to the correct message handler.
 * 
 * ----- 
 * This processes does the following: 
 * 1. receive a CMP message 
 * 2. check which message type it is 
 * 3. dispatch to the correct message handler 
 * 4. send back the response received from the handler 
 * -----
 * 
 * Messages supported:
 * - Initialization Request - will return an Initialization Response
 * - Revocation Request - will return a Revocation Response
 * - PKI Confirmation - same as certificate confirmation accept - will return a PKIConfirm
 * - Certificate Confirmation - accept or reject by client - will return a PKIConfirm
 * 
 * @author tomas
 * @version $Id: CmpMessageDispatcherSessionBean.java 13497 2011-12-23 14:30:59Z aveen4711 $
 */
@Stateless(mappedName = JndiHelper.APP_JNDI_PREFIX + "CmpMessageDispatcherSessionRemote")
public class CmpMessageDispatcherSessionBean implements CmpMessageDispatcherSessionLocal, CmpMessageDispatcherSessionRemote {

	private static final Logger log = Logger.getLogger(CmpMessageDispatcherSessionBean.class);
	/** Internal localization of logs and errors */
	private static final InternalResources intres = InternalResources.getInstance();
	
    @EJB
	private SignSessionLocal signSession;
	@EJB
	private UserAdminSessionLocal userAdminSession;
	@EJB
	private CAAdminSessionLocal caAdminSession;
	@EJB
	private CaSessionLocal caSession;
	@EJB
	private EndEntityProfileSessionLocal endEntityProfileSession;
	@EJB
	private CertificateProfileSessionLocal certificateProfileSession;
	@EJB
	private CertificateStoreSessionLocal certificateStoreSession;
	@EJB
	private CertificateRequestSessionLocal certificateRequestSession;
	@EJB
	private AuthorizationSessionLocal authorizationSession;
	
	@PostConstruct
	public void postConstruct() {
		CryptoProviderTools.installBCProviderIfNotAvailable();	// Install BouncyCastle provider, if not already available
	}

	/** The message may have been received by any transport protocol, and is passed here in it's binary ASN.1 form.
	 * 
	 * @param message der encoded CMP message as a byte array
	 * @return IResponseMessage containing the CMP response message or null if there is no message to send back or some internal error has occurred
	 * @throws IOException 
	 */
	@TransactionAttribute(TransactionAttributeType.REQUIRED)
	public IResponseMessage dispatch(final Admin admin, final byte[] ba) throws IOException {
		DERObject derObject = new LimitLengthASN1Reader(new ByteArrayInputStream(ba), ba.length).readObject();
		return dispatch(admin, derObject);
	}

	/** The message may have been received by any transport protocol, and is passed here in it's binary ASN.1 form.
	 * 
	 * @param message der encoded CMP message
	 * @return IResponseMessage containing the CMP response message or null if there is no message to send back or some internal error has occurred
	 */
	private IResponseMessage dispatch(final Admin admin, final DERObject derObject) {
		final PKIMessage req;
		try {
			req = PKIMessage.getInstance(derObject);
			if ( req==null ) {
				throw new Exception("No CMP message could be parsed from received Der object.");
			}
		} catch (Throwable t) {
			final String eMsg = intres.getLocalizedMessage("cmp.errornotcmpmessage");
			log.error(eMsg, t);
			// If we could not read the message, we should return an error BAD_REQUEST
			return CmpMessageHelper.createUnprotectedErrorMessage(null, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, eMsg);
		}
		try {
			final PKIHeader header = req.getHeader();
			final PKIBody body = req.getBody();
			
			int tagno = body.getTagNo();
			if (log.isDebugEnabled()) {
				log.debug("Received CMP message with pvno="+header.getPvno()+", sender="+header.getSender().toString()+", recipient="+header.getRecipient().toString());
				log.debug("Body is of type: "+tagno);
				log.debug(req);
				//log.debug(ASN1Dump.dumpAsString(req));				
			}
			BaseCmpMessage cmpMessage = null;
			ICmpMessageHandler handler = null;
			int unknownMessageType = -1;
			switch (tagno) {
			case 0:
				// 0 (ir, Initialization Request) and 2 (cr, Certification Req) are both certificate requests
				handler = new CrmfMessageHandler(admin, caAdminSession,  certificateProfileSession, certificateRequestSession, endEntityProfileSession, signSession, userAdminSession, certificateStoreSession, authorizationSession);
				cmpMessage = new CrmfRequestMessage(req, CmpConfiguration.getDefaultCA(), CmpConfiguration.getAllowRAVerifyPOPO(), CmpConfiguration.getExtractUsernameComponent());
				break;
			case 2:
				handler = new CrmfMessageHandler(admin, caAdminSession, certificateProfileSession, certificateRequestSession, endEntityProfileSession, signSession, userAdminSession, certificateStoreSession, authorizationSession);
				cmpMessage = new CrmfRequestMessage(req, CmpConfiguration.getDefaultCA(), CmpConfiguration.getAllowRAVerifyPOPO(), CmpConfiguration.getExtractUsernameComponent());
				break;
            case 7:
                // Key Update request (kur, Key Update Request)
                handler = new CrmfKeyUpdateHandler(admin, caAdminSession, certificateProfileSession, endEntityProfileSession, signSession, certificateStoreSession, authorizationSession, userAdminSession);
                cmpMessage = new CrmfRequestMessage(req, CmpConfiguration.getDefaultCA(), CmpConfiguration.getAllowRAVerifyPOPO(), CmpConfiguration.getExtractUsernameComponent());
                break;
			case 19:
				// PKI confirm (pkiconf, Confirmation)
			case 24:
				// Certificate confirmation (certConf, Certificate confirm)
				//handler = new ConfirmationMessageHandler(admin, caAdminSession, endEntityProfileSession, certificateProfileSession);
				handler = new ConfirmationMessageHandler(admin, caAdminSession, caSession, endEntityProfileSession, certificateProfileSession, userAdminSession, certificateStoreSession, authorizationSession);
				cmpMessage = new GeneralCmpMessage(req);
				break;
			case 11:
				// Revocation request (rr, Revocation Request)
				handler = new RevocationMessageHandler(admin, caSession, certificateStoreSession, userAdminSession, caAdminSession, endEntityProfileSession, certificateProfileSession, authorizationSession);
				cmpMessage = new GeneralCmpMessage(req);
				break;
			case 20:
				// NestedMessageContent (nested)
				if(log.isDebugEnabled()) {
					log.debug("Received a NestedMessageContent");
				}

				final NestedMessageContent nestedMessage = new NestedMessageContent(req);
				if(nestedMessage.verify()) {
                    if(log.isDebugEnabled()) {
                        log.debug("The NestedMessageContent was verified successfully");
                    }
                    try {
					    final DEREncodable nested = nestedMessage.getPKIMessage().getBody().getNested();
                        PKIMessages nestedMessages = PKIMessages.getInstance(nested);
                        org.bouncycastle.asn1.cmp.PKIMessage[] pkiMessages = nestedMessages.toPKIMessageArray();
					    return dispatch(admin, pkiMessages[0].getDERObject().getDEREncoded());

                    } catch (IllegalArgumentException e) {
                        final String errMsg = e.getLocalizedMessage();
                        log.error(errMsg);
                        cmpMessage = new NestedMessageContent(req);
                        return CmpMessageHelper.createUnprotectedErrorMessage(cmpMessage, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, errMsg);
                    }
				} else {
					final String errMsg = "Could not verify the RA";
					log.error(errMsg);
					cmpMessage = new NestedMessageContent(req);
					return CmpMessageHelper.createUnprotectedErrorMessage(cmpMessage, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, errMsg);
				}
				
			default:
				unknownMessageType = tagno;
				log.info("Received an unknown message type, tagno="+tagno);
				break;
			}
			if ( handler==null || cmpMessage==null ) {
				if (unknownMessageType > -1) {
					final String eMsg = intres.getLocalizedMessage("cmp.errortypenohandle", Integer.valueOf(unknownMessageType));
					log.error(eMsg);
					return CmpMessageHelper.createUnprotectedErrorMessage(null, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, eMsg);
				}
				throw new Exception("Something is null! Handler="+handler+", cmpMessage="+cmpMessage);
			}
			final IResponseMessage ret  = handler.handleMessage(cmpMessage);
			if (ret != null) {
				log.debug("Received a response message from CmpMessageHandler.");
			} else {
				log.error( intres.getLocalizedMessage("cmp.errorresponsenull") );
			}
			return ret;
		} catch (Exception e) {
			log.error(intres.getLocalizedMessage("cmp.errorprocess"), e);
			return null;
		}
	}
}
