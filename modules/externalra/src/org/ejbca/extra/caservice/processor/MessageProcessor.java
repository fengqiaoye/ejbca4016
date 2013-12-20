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

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.cesecore.core.ejb.ca.store.CertificateProfileSessionLocal;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSessionLocal;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.approval.ApprovalSessionLocal;
import org.ejbca.core.ejb.authorization.AuthorizationSessionLocal;
import org.ejbca.core.ejb.ca.auth.AuthenticationSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSession;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.CaSessionLocal;
import org.ejbca.core.ejb.ca.sign.SignSessionLocal;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionLocal;
import org.ejbca.core.ejb.config.GlobalConfigurationSessionLocal;
import org.ejbca.core.ejb.hardtoken.HardTokenSessionLocal;
import org.ejbca.core.ejb.keyrecovery.KeyRecoverySessionLocal;
import org.ejbca.core.ejb.ra.CertificateRequestSessionLocal;
import org.ejbca.core.ejb.ra.UserAdminSessionLocal;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.approval.ApprovalDataVO;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.approvalrequests.AddEndEntityApprovalRequest;
import org.ejbca.core.model.approval.approvalrequests.EditEndEntityApprovalRequest;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.ExtendedInformation;
import org.ejbca.core.model.ra.RAAuthorization;
import org.ejbca.core.model.ra.UserDataConstants;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.extra.caservice.ConfigurationException;
import org.ejbca.extra.db.ExtRARequest;
import org.ejbca.extra.db.ISubMessage;
import org.ejbca.util.CertTools;
import org.ejbca.util.query.ApprovalMatch;
import org.ejbca.util.query.BasicMatch;
import org.ejbca.util.query.Query;

/**
 * 
 * @author tomas
 * @version $Id: MessageProcessor.java 11741 2011-04-15 07:57:17Z netmackan $
 */
public class MessageProcessor {

    private static final Logger log = Logger.getLogger(MessageProcessor.class);

	/** When adding users with approvals, this is a time limit if a user have been rejected, it can not be requested again within this time (minutes) */ 
	private static final int APPROVAL_REJECT_TIMEOUT = -30;

    protected ApprovalSessionLocal approvalSession;
    protected AuthenticationSessionLocal authenticationSession;
    protected AuthorizationSessionLocal authorizationSession;
    protected CAAdminSessionLocal caAdminSession;
    protected CaSessionLocal caSession;
    protected CertificateProfileSessionLocal certificateProfileSession;
    protected CertificateStoreSessionLocal certificateStoreSession;
    protected EndEntityProfileSessionLocal endEntityProfileSession;
    protected HardTokenSessionLocal hardTokenSession;
    protected KeyRecoverySessionLocal keyRecoverySession;
    protected GlobalConfigurationSessionLocal globalConfigurationSession;
    protected SignSessionLocal signSession;
    protected UserAdminSessionLocal userAdminSession;
    protected CertificateRequestSessionLocal certificateRequestSession;
    
    public void setEjbs(Map<Class<?>, Object> ejbs) {
    	approvalSession = (ApprovalSessionLocal) ejbs.get(ApprovalSessionLocal.class);
    	authenticationSession = (AuthenticationSessionLocal) ejbs.get(AuthenticationSessionLocal.class);
    	authorizationSession = (AuthorizationSessionLocal) ejbs.get(AuthorizationSessionLocal.class);
    	caAdminSession = (CAAdminSessionLocal) ejbs.get(CAAdminSessionLocal.class);
    	caSession = (CaSessionLocal) ejbs.get(CaSessionLocal.class);
    	certificateProfileSession = (CertificateProfileSessionLocal) ejbs.get(CertificateProfileSessionLocal.class);
    	certificateStoreSession = (CertificateStoreSessionLocal) ejbs.get(CertificateStoreSessionLocal.class);
    	endEntityProfileSession = (EndEntityProfileSessionLocal) ejbs.get(EndEntityProfileSessionLocal.class);
    	hardTokenSession = (HardTokenSessionLocal) ejbs.get(HardTokenSessionLocal.class);
    	keyRecoverySession = (KeyRecoverySessionLocal) ejbs.get(KeyRecoverySessionLocal.class);
    	globalConfigurationSession = (GlobalConfigurationSessionLocal) ejbs.get(GlobalConfigurationSessionLocal.class);
    	signSession = (SignSessionLocal) ejbs.get(SignSessionLocal.class);
    	userAdminSession = (UserAdminSessionLocal) ejbs.get(UserAdminSessionLocal.class);
    	certificateRequestSession = (CertificateRequestSessionLocal) ejbs.get(CertificateRequestSessionLocal.class);
    }

	/**
	 * Method generating a response to a given sub request, or null of the message should be left in the queue to be tried the next time
	 * 
	 * @param admin the administrator (signer for signed requests, internal user otherwise) performing the action
	 * @param submessage the request submessage
	 * @param errormessage message of an error response to be created
	 * @param ejbs A map between Local EJB interface classes and their injected stub
	 * @return a response, contains failinfo if anything went wrong, or null if the message should not be processed now and should be left in the queue, this can be used to handle approvals.
	 * @throws ConfigurationException 
	 * @throws ClassNotFoundException if the message processor class does not exist in the classpath
	 * @throws IllegalAccessException if the message processor class is invalid
	 * @throws InstantiationException if the message processor class is invalid
	 */
	public static ISubMessage processSubMessage(Admin admin, ISubMessage submessage, String errormessage, Map<Class<?>, Object> ejbs) throws ConfigurationException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		ISubMessageProcessor proc = null;
		
		String clazz = submessage.getClass().getName();
		log.debug("Received message of class "+clazz);
		// We can keep the messages and processors in different packages
		if (clazz.startsWith("org.ejbca.extra.db.")) {
			clazz = clazz.replaceFirst("org.ejbca.extra.db.", "org.ejbca.extra.caservice.processor.");
		}
		// Add "Processor", a message processor must always be named MyRequestProcessor, where MyRequest is the classname of the request message
		clazz = clazz + "Processor";
		log.debug("Creating message processor of class "+clazz);
		// Finally create a new message processor using reflection
		final Class implClass = Class.forName(clazz);
		proc = (ISubMessageProcessor)implClass.newInstance();
		proc.setEjbs(ejbs);

		/* The above replaces this more cumbersome code below
		if(submessage instanceof ExtRAPKCS10Request){
			proc = new PKCS10RequestProcessor();
		}
		if(submessage instanceof ExtRAPKCS12Request){
			proc = new PKCS12RequestProcessor();
		}
		if(submessage instanceof ExtRAEditUserRequest){
			proc = new EditUserRequestProcessor();
		}
		if(submessage instanceof ExtRAKeyRecoveryRequest){
			proc = new KeyRecoveryRequestProcessor();
		}
		if(submessage instanceof ExtRARevocationRequest){
			proc = new RevocationRequestProcessor();
		}
		if(submessage instanceof ExtRACardRenewalRequest){
			proc = new CardRenewalRequestProcessor();
		}
		*/
		
		if (proc == null) {
			log.error("Received an illegal submessage request :" + submessage.getClass().getName());
			return null; // Should never happen.					
		}
		
		ISubMessage ret = proc.process(admin, submessage, errormessage);
		return ret;
	}

	/**
	 * Method returning the CACertChain used to verify the message.
	 * CAChain returned is specified in the service config.
	 * 
	 * @param cAName
	 * @param checkRevokation
	 * @return the CACertChain.
	 * @throws ConfigurationException if any of the CAs doesn't exist or is revoked
	 */
	public static Collection<Certificate> getCACertChain(Admin admin, String cAName, boolean checkRevokation, CAAdminSession caAdminSession) throws ConfigurationException{		
		try{
			CAInfo cainfo = caAdminSession.getCAInfo(admin, cAName);
			if(cainfo == null){
				log.error("Misconfigured CA Name in RAService");
				throw new ConfigurationException("Misconfigured CA Name in RAService");
			}
			
			if(checkRevokation){
			  if(cainfo.getStatus()==SecConst.CA_REVOKED){
				throw new ConfigurationException("CA " + cainfo.getName() + " Have been revoked");
			  }
			
			  Iterator<Certificate> iter = cainfo.getCertificateChain().iterator();
			  iter.next(); // Throw away the first one.
			  while(iter.hasNext()){
				X509Certificate cacert = (X509Certificate) iter.next();
				CAInfo cainfo2 = caAdminSession.getCAInfo(admin,CertTools.stringToBCDNString(cacert.getSubjectDN().toString()).hashCode());
				// This CA may be an external CA, so we don't bother if we can not find it.
				if ((cainfo2 != null) && (cainfo2.getStatus()==SecConst.CA_REVOKED) ) {
					throw new ConfigurationException("CA " + cainfo2.getName() + " Have been revoked");
				}
			  }
			}  
			return cainfo.getCertificateChain();
		}catch(Exception e){
			if (e instanceof ConfigurationException) {
				throw (ConfigurationException)e;
			}
			log.error("Exception getting CA cert chain: ", e);
			throw new ConfigurationException("Couldn't instantiate CAAdminSessionBean");
		}	
	}
	

    protected UserDataVO generateUserDataVO(Admin admin, ExtRARequest submessage) throws ClassCastException, EjbcaException {
        String dirAttributes = submessage.getSubjectDirectoryAttributes();
        ExtendedInformation ext = null;
        if (dirAttributes != null) {
            ext = new ExtendedInformation();
            ext.setSubjectDirectoryAttributes(dirAttributes);
        }
           return  new UserDataVO(submessage.getUsername(),
                   submessage.getSubjectDN(),
                   getCAId(admin,submessage.getCAName()),
                   submessage.getSubjectAltName(),
                   submessage.getEmail(),
                   UserDataConstants.STATUS_INPROCESS,
                   SecConst.USER_ENDUSER,
                   getEndEntityProfileId(admin, submessage.getEndEntityProfileName()),
                   getCertificateProfileId(admin, submessage.getCertificateProfileName()),
                   null,
                   null,
                   SecConst.TOKEN_SOFT_BROWSERGEN,
                   0,
                   ext);
    }


	/**
	 * Help method used to store userdata in userdatabase with given status, that is
	 * waiting for user to be reviewed. This methid handles approval as well.
	 */
	protected void storeUserData(Admin admin, UserDataVO userdata, boolean clearpwd, int status) throws Exception {
		log.trace(">storeUserData() username : " + userdata.getUsername());

        // First we will look to see if there is an existing approval request pending for this user within the last hour
		EditEndEntityApprovalRequest ear = new EditEndEntityApprovalRequest(userdata, clearpwd, userdata, admin,null,1,userdata.getCAId(),userdata.getEndEntityProfileId());
        AddEndEntityApprovalRequest aar = new AddEndEntityApprovalRequest(userdata,clearpwd,admin,null,1,userdata.getCAId(),userdata.getEndEntityProfileId());
        int approvalid = aar.generateApprovalId();
		// Check if user already exists
        if (userAdminSession.existsUser(admin, userdata.getUsername())) {
        	// a user already exists, so this is an edit entity request we are preparing
        	log.debug("User already exist, we will look for an edit end entity request");
        	approvalid = ear.generateApprovalId();
        }
		Query query = new Query(Query.TYPE_APPROVALQUERY);		
		query.add(ApprovalMatch.MATCH_WITH_APPROVALID, BasicMatch.MATCH_TYPE_EQUALS, Integer.toString(approvalid), Query.CONNECTOR_AND);
		query.add(ApprovalMatch.MATCH_WITH_STATUS, BasicMatch.MATCH_TYPE_EQUALS, "" + ApprovalDataVO.STATUS_WAITINGFORAPPROVAL, Query.CONNECTOR_AND);
		Date now = new Date();
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.HOUR_OF_DAY, -1);
		query.add(cal.getTime(), now);
        RAAuthorization raAuthorization = new RAAuthorization(admin, globalConfigurationSession, authorizationSession, caSession, endEntityProfileSession);
		List<ApprovalDataVO> approvals = approvalSession.query(admin, query, 0, 25, raAuthorization.getCAAuthorizationString(), raAuthorization.getEndEntityProfileAuthorizationString());
		// If there is an request waiting for approval we don't have to go on and try to add the user
        if (approvals.size() > 0) {
        	log.debug("Found at least one waiting approval request for approvalid: "+approvalid);
        	throw new ApprovalException("There is already an existing approval request pending for approvalid: "+approvalid);
        }
        
		// If there is no waiting request which should be the most common, we check If there is an existing reject withing the last 30 minutes
        // If there is a reject, we will cancel this request. A new request will then probably not be possible to create until 30 minutes have passed
		query = new Query(Query.TYPE_APPROVALQUERY);		
		query.add(ApprovalMatch.MATCH_WITH_APPROVALID, BasicMatch.MATCH_TYPE_EQUALS, Integer.toString(approvalid), Query.CONNECTOR_AND);
		query.add(ApprovalMatch.MATCH_WITH_STATUS, BasicMatch.MATCH_TYPE_EQUALS, "" + ApprovalDataVO.STATUS_EXECUTIONDENIED, Query.CONNECTOR_AND);
		cal = Calendar.getInstance();
		cal.add(Calendar.MINUTE, APPROVAL_REJECT_TIMEOUT);
		query.add(cal.getTime(), now);
		approvals = approvalSession.query(admin, query, 0, 25, raAuthorization.getCAAuthorizationString(), raAuthorization.getEndEntityProfileAuthorizationString());
		// If there is an request waiting for approval we don't have to go on and try to add the user
        if (approvals.size() > 0) {
        	log.debug("Found at least one rejected approval request for approvalid: "+approvalid);
        	throw new Exception("Approval request was rejected for approvalid: "+approvalid);
        }

		// Check if it failed as well...
		query = new Query(Query.TYPE_APPROVALQUERY);		
		query.add(ApprovalMatch.MATCH_WITH_APPROVALID, BasicMatch.MATCH_TYPE_EQUALS, Integer.toString(approvalid), Query.CONNECTOR_AND);
		query.add(ApprovalMatch.MATCH_WITH_STATUS, BasicMatch.MATCH_TYPE_EQUALS, "" + ApprovalDataVO.STATUS_EXECUTIONFAILED, Query.CONNECTOR_AND);
		cal = Calendar.getInstance();
		cal.add(Calendar.MINUTE, -30);
		query.add(cal.getTime(), now);
		approvals = approvalSession.query(admin, query, 0, 25, raAuthorization.getCAAuthorizationString(), raAuthorization.getEndEntityProfileAuthorizationString());
		// If there is an request waiting for approval we don't have to go on and try to add the user
        if (approvals.size() > 0) {
        	log.debug("Found at least one failed approval request for approvalid: "+approvalid);
        	throw new Exception("Approval request execution failed for approvalid: "+approvalid);
        }
        
		// Check if user already exists
		UserDataVO oldUserData = userAdminSession.findUser(admin, userdata.getUsername());
		if (oldUserData != null) {
			log.debug("User '"+userdata.getUsername()+"' already exist, edit user.");
			if ( (oldUserData.getStatus() == UserDataConstants.STATUS_INPROCESS) || (oldUserData.getStatus() == UserDataConstants.STATUS_NEW) ) {
				log.info("User '"+userdata.getUsername()+"' have status NEW or INPROCESS, we will NOT edit it");
			} else {
				userdata.setStatus(status);
				userAdminSession.changeUser(admin,userdata,clearpwd);			  
			}
		} else {
			log.debug("User '"+userdata.getUsername()+"' does not exist, add user.");
			userAdminSession.addUser(admin,userdata,clearpwd);
			userAdminSession.setUserStatus(admin,userdata.getUsername(), status);
		}
		log.trace("<storeUserData()");
	}

	private int getCertificateProfileId(Admin admin, String certificateProfileName) throws EjbcaException {		
		int retval = certificateProfileSession.getCertificateProfileId(admin,certificateProfileName);
		if(retval == 0){
			throw new EjbcaException("Error Certificate profile '" + certificateProfileName + "' doesn't exists.");
		}
		return retval;
	}

	private int getEndEntityProfileId(Admin admin,String endEntityProfileName) throws EjbcaException {
		int retval = endEntityProfileSession.getEndEntityProfileId(admin,endEntityProfileName);
		if(retval == 0){
			throw new EjbcaException("Error End Entity profile '" + endEntityProfileName + "' doesn't exists.");
		}
		return retval;
	}

	private int getCAId(Admin admin, String cAName) throws EjbcaException {
		CAInfo info = caAdminSession.getCAInfo(admin,cAName);
		if(info == null){
			throw new EjbcaException("Error CA '" + cAName + "' doesn't exists.");
		}
		int retval = info.getCAId();
		return retval;
	}
}
