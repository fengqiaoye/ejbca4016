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

import java.math.BigInteger;
import java.util.List;

import javax.persistence.PersistenceException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.x509.X509Name;
import org.cesecore.core.ejb.ca.store.CertificateProfileSession;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSession;
import org.ejbca.config.CmpConfiguration;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.authorization.AuthorizationSession;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSession;
import org.ejbca.core.ejb.ca.sign.SignSession;
import org.ejbca.core.ejb.ca.store.CertificateStoreSession;
import org.ejbca.core.ejb.ra.CertificateRequestSession;
import org.ejbca.core.ejb.ra.UserAdminSession;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.ca.SignRequestSignatureException;
import org.ejbca.core.model.ca.caadmin.CADoesntExistsException;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.ExtendedInformation;
import org.ejbca.core.model.ra.NotFoundException;
import org.ejbca.core.model.ra.UserDataConstants;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.core.model.ra.UsernameGenerator;
import org.ejbca.core.model.ra.UsernameGeneratorParams;
import org.ejbca.core.model.ra.raadmin.UserDoesntFullfillEndEntityProfile;
import org.ejbca.core.protocol.ExtendedUserDataHandler;
import org.ejbca.core.protocol.FailInfo;
import org.ejbca.core.protocol.IRequestMessage;
import org.ejbca.core.protocol.IResponseMessage;
import org.ejbca.core.protocol.ResponseStatus;
import org.ejbca.core.protocol.ExtendedUserDataHandler.HandlerException;
import org.ejbca.core.protocol.cmp.authentication.HMACAuthenticationModule;
import org.ejbca.core.protocol.cmp.authentication.ICMPAuthenticationModule;
import org.ejbca.core.protocol.cmp.authentication.VerifyPKIMessage;
import org.ejbca.util.CertTools;
import org.ejbca.util.passgen.IPasswordGenerator;
import org.ejbca.util.passgen.PasswordGeneratorFactory;

/**
 * Message handler for certificate request messages in the CRMF format
 * @author tomas
 * @version $Id: CrmfMessageHandler.java 13980 2012-02-06 23:24:28Z aveen4711 $
 */
public class CrmfMessageHandler extends BaseCmpMessageHandler implements ICmpMessageHandler {
	
	private static final Logger LOG = Logger.getLogger(CrmfMessageHandler.class);
    /** Internal localization of logs and errors */
    private static final InternalResources INTRES = InternalResources.getInstance();

    /** strings for error messages defined in internal resources */
	private static final String CMP_ERRORADDUSER = "cmp.erroradduser";
	private static final String CMP_ERRORGENERAL = "cmp.errorgeneral";

	/** Parameters used for username generation if we are using RA mode to create users */
	private final UsernameGeneratorParams usernameGenParams;
	/** Parameters used for temporary password generation */
	private final String userPwdParams;
	/** Parameter used to determine the type of protection for the response message */
	private final String responseProt;
	/** Determines if it the RA will look for requested custom certificate serial numbers, if false such data is ignored */
	private final boolean allowCustomCertSerno;
	/** Extra pre-processing of requests */ 
	private final ExtendedUserDataHandler extendedUserDataHandler;
	
	private final SignSession signSession;
	private final UserAdminSession userAdminSession;
	private final CertificateRequestSession certificateRequestSession;
	private final CertificateStoreSession certStoreSession;
	private final AuthorizationSession authorizationSession;
	
	/**
	 * Used only by unit test.
	 */
	public CrmfMessageHandler () {
		super();
		this.usernameGenParams = null;
		this.userPwdParams = "random";
		this.responseProt = null;
		this.allowCustomCertSerno = false;
		this.signSession =null;
		this.userAdminSession = null;
		this.certificateRequestSession = null;
		this.certStoreSession = null;
		this.authorizationSession = null;
		this.extendedUserDataHandler = null;
	}
	
	/**
	 * Construct the message handler.
	 * @param admin
	 * @param caAdminSession
	 * @param certificateProfileSession
	 * @param certificateRequestSession
	 * @param endEntityProfileSession
	 * @param signSession
	 * @param userAdminSession
	 */
	public CrmfMessageHandler(final Admin admin, final CAAdminSession caAdminSession, final CertificateProfileSession certificateProfileSession, final CertificateRequestSession certificateRequestSession,
			final EndEntityProfileSession endEntityProfileSession, final SignSession signSession, final UserAdminSession userAdminSession, final CertificateStoreSession certSession, final AuthorizationSession authSession) {
		super(admin, caAdminSession, endEntityProfileSession, certificateProfileSession);
		// Get EJB beans, we can not use local beans here because the TCP listener does not work with that
		this.signSession = signSession;
		this.userAdminSession = userAdminSession;
		this.certificateRequestSession = certificateRequestSession;
		this.certStoreSession = certSession;
		this.authorizationSession = authSession;

		if (CmpConfiguration.getRAOperationMode()) {
			// create UsernameGeneratorParams
			this.usernameGenParams = new UsernameGeneratorParams();
			this.usernameGenParams.setMode(CmpConfiguration.getRANameGenerationScheme());
			this.usernameGenParams.setDNGeneratorComponent(CmpConfiguration.getRANameGenerationParameters());
			this.usernameGenParams.setPrefix(CmpConfiguration.getRANameGenerationPrefix());
			this.usernameGenParams.setPostfix(CmpConfiguration.getRANameGenerationPostfix());
			this.userPwdParams =  CmpConfiguration.getUserPasswordParams();
			this.allowCustomCertSerno = CmpConfiguration.getRAAllowCustomCertSerno();
			this.responseProt = CmpConfiguration.getResponseProtection();
			if (LOG.isDebugEnabled()) {
				LOG.debug("cmp.operationmode=ra");
				LOG.debug("cmp.ra.allowcustomcertserno="+allowCustomCertSerno);
				LOG.debug("cmp.ra.passwordgenparams="+userPwdParams);
				LOG.debug("cmp.responseprotection="+responseProt);
			}
		} else {
			this.usernameGenParams = null;
			this.userPwdParams = "random";
			this.responseProt = null;
			this.allowCustomCertSerno = false;
		}
		// Checks if an extended user data hander is configured and if so, creates the handler class.
		final String handlerClass = CmpConfiguration.getCertReqHandlerClass();
		if ( handlerClass!=null ) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("CertReqHandlerClass="+handlerClass);
			}
			ExtendedUserDataHandler tmp;
			try {
				tmp = (ExtendedUserDataHandler)Class.forName(handlerClass).newInstance();
			} catch (Exception e) {
				tmp = null;
				LOG.warn("The configured unid class '"+handlerClass+"' is not existing.");
			}
			this.extendedUserDataHandler = tmp;			
		} else {
			this.extendedUserDataHandler = null;
		}
	}

	public IResponseMessage handleMessage(final BaseCmpMessage msg) {
		if (LOG.isTraceEnabled()) {
			LOG.trace(">handleMessage");
		}
		IResponseMessage resp = null;
		try {
			CrmfRequestMessage crmfreq = null;
			if (msg instanceof CrmfRequestMessage) {
				crmfreq = (CrmfRequestMessage) msg;
				crmfreq.getMessage();
				// If we have usernameGeneratorParams we want to generate usernames automagically for requests
				// If we are not in RA mode, usernameGeneratorParams will be null
				if (usernameGenParams != null) {
					resp = handleRaMessage(msg, crmfreq);
				} else {
					// Try to find the user that is the subject for the request
					// if extractUsernameComponent is null, we have to find the user from the DN
					// if not empty the message will find the username itself, in the getUsername method
					final String dn = crmfreq.getSubjectDN();
					final UserDataVO data;
					/** Defines which component from the DN should be used as username in EJBCA. Can be DN, UID or nothing. Nothing means that the DN will be used to look up the user. */
					final String usernameComp = CmpConfiguration.getExtractUsernameComponent();
					if (LOG.isDebugEnabled()) {
						LOG.debug("extractUsernameComponent: "+usernameComp);
					}
					if (StringUtils.isEmpty(usernameComp)) {
						if (LOG.isDebugEnabled()) {
							LOG.debug("looking for user with dn: "+dn);
						}
						data = userAdminSession.findUserBySubjectDN(admin, dn);
					} else {
						final String username = CertTools.getPartFromDN(dn,usernameComp);
						if (LOG.isDebugEnabled()) {
							LOG.debug("looking for user with username: "+username);
						}						
						data = userAdminSession.findUser(admin, username);
					}
					if (data != null) {
						if (LOG.isDebugEnabled()) {
							LOG.debug("Found username: "+data.getUsername());
						}
						crmfreq.setUsername(data.getUsername());
						
						ICMPAuthenticationModule authenticationModule = null;
						Object verified = verifyAndGetAuthModule(msg, crmfreq, data.getUsername(), 0);
						if(verified instanceof IResponseMessage) {
							return (IResponseMessage) verified;
						} else {
							authenticationModule = (ICMPAuthenticationModule) verified;
						}
						crmfreq.setPassword(authenticationModule.getAuthenticationString());
						
					} else {
						final String errMsg = INTRES.getLocalizedMessage("cmp.infonouserfordn", dn);
						LOG.info(errMsg);
					}
				}
			} else {
				final String errMsg = INTRES.getLocalizedMessage("cmp.errornocmrfreq");
				LOG.error(errMsg);
			}
			// This is a request message, so we want to enroll for a certificate, if we have not created an error already
			if (resp == null) {
				// Get the certificate
				resp = signSession.createCertificate(admin, crmfreq, org.ejbca.core.protocol.cmp.CmpResponseMessage.class, null);				
			}
			if (resp == null) {
				final String errMsg = INTRES.getLocalizedMessage("cmp.errornullresp");
				LOG.error(errMsg);
			}
		} catch (AuthorizationDeniedException e) {
			final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
			LOG.info(errMsg, e);			
		} catch (CADoesntExistsException e) {
			final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
			LOG.info(errMsg, e); // info because this is something we should expect and we handle it	
			resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.WRONG_AUTHORITY, e.getMessage());
		} catch (SignRequestSignatureException e) {
			final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
			LOG.info(errMsg, e); // info because this is something we should expect and we handle it
			resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_POP, e.getMessage());
        } catch (EjbcaException e) {
            final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
            LOG.info(errMsg, e);           
            resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, e.getMessage());
		} catch (ClassNotFoundException e) {
			final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
			LOG.error(errMsg, e);			
		}		
		if (LOG.isTraceEnabled()) {
			LOG.trace("<handleMessage");
		}
		return resp;
	}

	/** Method that takes care of RA mode operations, i.e. when the message is authenticated with a common secret using password based encryption (pbe).
	 * This method will verify the pbe and if ok  will automatically create/edit a user and issue the certificate. In RA mode we assume that the RA knows what it is doing.
	 * 
	 * @param msg
	 * @param crmfreq
	 * @return IResponseMessage that can be sent back to the client
	 * @throws AuthorizationDeniedException
	 * @throws EjbcaException
	 * @throws ClassNotFoundException
	 */
	private IResponseMessage handleRaMessage(final BaseCmpMessage msg, final CrmfRequestMessage crmfreq) throws AuthorizationDeniedException, EjbcaException, ClassNotFoundException {
		final int eeProfileId;        // The endEntityProfile to be used when adding users in RA mode.
		final String certProfileName;  // The certificate profile to use when adding users in RA mode.
		final int certProfileId;
		// Try to find a HMAC/SHA1 protection key
		final int requestId = crmfreq.getRequestId();
		final int requestType = crmfreq.getRequestType();
		final String keyId = getSenderKeyId(crmfreq.getHeader());
		
		int caId = 0; // The CA to user when adding users in RA mode
		try {
			eeProfileId = getUsedEndEntityProfileId(keyId);
			caId = getUsedCaId(keyId, eeProfileId);
			certProfileName = getUsedCertProfileName(keyId, eeProfileId);
			certProfileId = getUsedCertProfileId(certProfileName);
		} catch (NotFoundException e) {
			LOG.info(INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage()), e);
			return CmpMessageHelper.createErrorMessage(msg, FailInfo.INCORRECT_DATA, e.getMessage(), requestId, requestType, null, keyId, this.responseProt);
		}
		
		IResponseMessage resp = null; // The CMP response message to be sent back to the client
		try {			
			ICMPAuthenticationModule authenticationModule = null;
			Object verified = verifyAndGetAuthModule(msg, crmfreq, null, caId);
			if(verified instanceof IResponseMessage) {
				return (IResponseMessage) verified;
			} else {
				authenticationModule = (ICMPAuthenticationModule) verified;
			}
			
			// Create a username and password and register the new user in EJBCA
			final UsernameGenerator gen = UsernameGenerator.getInstance(this.usernameGenParams);
			// Don't convert this DN to an ordered EJBCA DN string with CertTools.stringToBCDNString because we don't want double escaping of some characters
			final IRequestMessage req =  this.extendedUserDataHandler!=null ? this.extendedUserDataHandler.processRequestMessage(crmfreq, certProfileName) : crmfreq;
			final X509Name dnname = req.getRequestX509Name();
			if (LOG.isDebugEnabled()) {
				LOG.debug("Creating username from base dn: "+dnname.toString());
			}
			final String username = gen.generateUsername(dnname.toString());
			final String pwd;
			if(StringUtils.equals(authenticationModule.getName(), CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE)) {
				pwd = authenticationModule.getAuthenticationString();
			} else if(StringUtils.equals(authenticationModule.getName(), CmpConfiguration.AUTHMODULE_HMAC)) {
				if (StringUtils.equals(this.userPwdParams, "random")) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Setting 12 char random user password.");
					}
					final IPasswordGenerator pwdgen = PasswordGeneratorFactory.getInstance(PasswordGeneratorFactory.PASSWORDTYPE_ALLPRINTABLE);
					pwd = pwdgen.getNewPassword(12, 12);                                                                    
				} else {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Setting fixed user password from config.");
					}
					pwd = this.userPwdParams;                                                                    
				}
			} else {
				//This should not run since an error would have occurred earlier if the authentication module was unknown 
				final String errMsg = "Unknown authentication module.";
				LOG.error(errMsg);
				return CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_MESSAGE_CHECK, errMsg);
			}
			// AltNames may be in the request template
			final String altNames = req.getRequestAltNames();
			final String email;
			final List<String> emails = CertTools.getEmailFromDN(altNames);
			emails.addAll(CertTools.getEmailFromDN(dnname.toString()));
			if (!emails.isEmpty()) {
				email = emails.get(0); // Use rfc822name or first SubjectDN email address as user email address if available
			} else {
				email = null;
			}
			final ExtendedInformation ei;
			if (this.allowCustomCertSerno) {
				// Don't even try to parse out the field if it is not allowed
				final BigInteger customCertSerno = crmfreq.getSubjectCertSerialNo();
				if (customCertSerno != null) {
					// If we have a custom certificate serial number in the request, we will pass it on to the UserData object
					ei = new ExtendedInformation();
					ei.setCertificateSerialNumber(customCertSerno);
					if (LOG.isDebugEnabled()) {
						LOG.debug("Custom certificate serial number: "+customCertSerno.toString(16));					
					}
				} else {
					ei = null;
				}
			} else {
				ei = null;
			}
			final UserDataVO userdata = new UserDataVO(username, dnname.toString(), caId, altNames, email, UserDataConstants.STATUS_NEW, SecConst.USER_ENDUSER, eeProfileId, certProfileId, null, null, SecConst.TOKEN_SOFT_BROWSERGEN, 0, ei);
			userdata.setPassword(pwd);
			// Set so we have the right params in the call to processCertReq. 
			// Username and pwd in the UserDataVO and the IRequestMessage must match
			crmfreq.setUsername(username);
			crmfreq.setPassword(pwd);
			// Set all protection parameters
			CmpPbeVerifyer verifyer = null;
			if(StringUtils.equals(authenticationModule.getName(), CmpConfiguration.AUTHMODULE_HMAC)) {
				final HMACAuthenticationModule hmacmodule = (HMACAuthenticationModule) authenticationModule;
				verifyer = hmacmodule.getCmpPbeVerifyer();
				final String pbeDigestAlg = verifyer.getOwfOid();
				final String pbeMacAlg = verifyer.getMacOid();
				final int pbeIterationCount = verifyer.getIterationCount();
				final String raSecret = verifyer.getLastUsedRaSecret();
				if (LOG.isDebugEnabled()) {
					LOG.debug("responseProt="+this.responseProt+", pbeDigestAlg="+pbeDigestAlg+", pbeMacAlg="+pbeMacAlg+", keyId="+keyId+", raSecret="+(raSecret == null ? "null":"not null"));
				}
				
				//TODO check whether this code (crmfreq.setPbeParameters()) does anything useful
				if (StringUtils.equals(this.responseProt, "pbe")) {
					crmfreq.setPbeParameters(keyId, raSecret, pbeDigestAlg, pbeMacAlg, pbeIterationCount);
				}
			}
			try {
				try {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Creating new request with eeProfileId '"+eeProfileId+"', certProfileId '"+certProfileId+"', caId '"+caId+"'");                                                               
					}
					resp = this.certificateRequestSession.processCertReq(this.admin, userdata, req, org.ejbca.core.protocol.cmp.CmpResponseMessage.class);
				} catch (PersistenceException e) {
					// CreateException will catch also DuplicateKeyException because DuplicateKeyException is a subclass of CreateException 
					// This was very strange, we didn't find it before, but now it exists?
					// This should never happen when using the "single transaction" request session??
					final String updateMsg = INTRES.getLocalizedMessage("cmp.erroradduserupdate", username);
					LOG.info(updateMsg);
					// Try again
					resp = this.certificateRequestSession.processCertReq(this.admin, userdata, req, org.ejbca.core.protocol.cmp.CmpResponseMessage.class);
				}
			} catch (UserDoesntFullfillEndEntityProfile e) {
				LOG.error(INTRES.getLocalizedMessage(CMP_ERRORADDUSER, username), e);
				resp = CmpMessageHelper.createErrorMessage(msg, FailInfo.INCORRECT_DATA, e.getMessage(), requestId, requestType, verifyer, keyId, this.responseProt);
			} catch (ApprovalException e) {
				LOG.error(INTRES.getLocalizedMessage(CMP_ERRORADDUSER, username), e);
				resp = CmpMessageHelper.createErrorMessage(msg, FailInfo.NOT_AUTHORIZED, e.getMessage(), requestId, requestType, verifyer, keyId, this.responseProt);
			} catch (PersistenceException e) {
				LOG.error(INTRES.getLocalizedMessage(CMP_ERRORADDUSER, username), e);
				resp = CmpMessageHelper.createErrorMessage(msg, FailInfo.NOT_AUTHORIZED, e.getMessage(), requestId, requestType, verifyer, keyId, this.responseProt);
			}
		} catch (HandlerException e) {
			LOG.error(INTRES.getLocalizedMessage("cmp.errorexthandlerexec"), e);
			resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_MESSAGE_CHECK, e.getMessage());
		}
		return resp;
	}
	
	private Object verifyAndGetAuthModule(final BaseCmpMessage msg, final CrmfRequestMessage crmfreq, final String username, final int caId) {
		final CAInfo caInfo;
		if (caId == 0) {
			caInfo = null;
		} else {
			caInfo = this.caAdminSession.getCAInfo(this.admin, caId);	
		}
		final VerifyPKIMessage messageVerifyer = new VerifyPKIMessage(caInfo, admin, caAdminSession, userAdminSession, certStoreSession, authorizationSession, endEntityProfileSession);
		ICMPAuthenticationModule authenticationModule = null;
		if(messageVerifyer.verify(crmfreq.getPKIMessage(), username)) {
			authenticationModule = messageVerifyer.getUsedAuthenticationModule();
		}
		if(authenticationModule == null) {
			String errMsg = "";
			if(messageVerifyer.getErrorMessage() != null) {
				errMsg = messageVerifyer.getErrorMessage();
			} else {
				errMsg = "Unrecognized authentication modules";
			}
			LOG.error(errMsg);
			return CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_MESSAGE_CHECK, errMsg);
		}
		return authenticationModule;
	}

}
