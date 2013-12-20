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

package org.ejbca.core.ejb.ra;

import java.awt.print.PrinterException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.RemoveException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.naming.InvalidNameException;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.cesecore.core.ejb.ca.store.CertificateProfileSessionLocal;
import org.cesecore.core.ejb.log.LogSessionLocal;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSessionLocal;
import org.ejbca.config.GlobalConfiguration;
import org.ejbca.config.WebConfiguration;
import org.ejbca.config.XkmsConfiguration;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ErrorCode;
import org.ejbca.core.ejb.approval.ApprovalSessionLocal;
import org.ejbca.core.ejb.authorization.AuthorizationSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.CaSessionLocal;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionLocal;
import org.ejbca.core.ejb.config.GlobalConfigurationSessionLocal;
import org.ejbca.core.ejb.keyrecovery.KeyRecoverySessionLocal;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.ApprovalExecutorUtil;
import org.ejbca.core.model.approval.ApprovalOveradableClassName;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.approval.approvalrequests.AddEndEntityApprovalRequest;
import org.ejbca.core.model.approval.approvalrequests.ChangeStatusEndEntityApprovalRequest;
import org.ejbca.core.model.approval.approvalrequests.EditEndEntityApprovalRequest;
import org.ejbca.core.model.approval.approvalrequests.RevocationApprovalRequest;
import org.ejbca.core.model.authorization.AccessRulesConstants;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.authorization.Authorizer;
import org.ejbca.core.model.ca.caadmin.CADoesntExistsException;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.core.model.ca.crl.RevokedCertInfo;
import org.ejbca.core.model.ca.store.CertReqHistory;
import org.ejbca.core.model.ca.store.CertificateInfo;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.log.LogConstants;
import org.ejbca.core.model.ra.AlreadyRevokedException;
import org.ejbca.core.model.ra.CustomFieldException;
import org.ejbca.core.model.ra.ExtendedInformation;
import org.ejbca.core.model.ra.FieldValidator;
import org.ejbca.core.model.ra.NotFoundException;
import org.ejbca.core.model.ra.RAAuthorization;
import org.ejbca.core.model.ra.RevokeBackDateNotAllowedForProfileException;
import org.ejbca.core.model.ra.UserAdminConstants;
import org.ejbca.core.model.ra.UserDataConstants;
import org.ejbca.core.model.ra.UserDataFiller;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.core.model.ra.UserNotificationParamGen;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.core.model.ra.raadmin.ICustomNotificationRecipient;
import org.ejbca.core.model.ra.raadmin.UserDoesntFullfillEndEntityProfile;
import org.ejbca.core.model.ra.raadmin.UserNotification;
import org.ejbca.util.CertTools;
import org.ejbca.util.PrinterManager;
import org.ejbca.util.StringTools;
import org.ejbca.util.dn.DistinguishedName;
import org.ejbca.util.dn.DnComponents;
import org.ejbca.util.mail.MailSender;
import org.ejbca.util.query.BasicMatch;
import org.ejbca.util.query.IllegalQueryException;
import org.ejbca.util.query.Query;
import org.ejbca.util.query.UserMatch;

/**
 * Administrates users in the database using UserData Entity Bean.
 * 
 * @version $Id: UserAdminSessionBean.java 15376 2012-08-20 10:21:24Z samuellb $
 */
@Stateless(mappedName = org.ejbca.core.ejb.JndiHelper.APP_JNDI_PREFIX + "UserAdminSessionRemote")
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class UserAdminSessionBean implements UserAdminSessionLocal, UserAdminSessionRemote {

    private static final Logger log = Logger.getLogger(UserAdminSessionBean.class);
    /** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();

    @PersistenceContext(unitName = "ejbca")
    private EntityManager entityManager;

    @EJB
    private EndEntityProfileSessionLocal endEntityProfileSession;
    @EJB
    private GlobalConfigurationSessionLocal globalConfigurationSession;
    @EJB
    private CertificateStoreSessionLocal certificateStoreSession;
    @EJB
    private CertificateProfileSessionLocal certificateProfileSession;
    @EJB
    private AuthorizationSessionLocal authorizationSession;
    @EJB
    private KeyRecoverySessionLocal keyRecoverySession;
    @EJB
    private CAAdminSessionLocal caAdminSession;
    @EJB
    private CaSessionLocal caSession;
    @EJB
    private ApprovalSessionLocal approvalSession;
    @EJB
    private LogSessionLocal logSession;

    /** Columns in the database used in select. */
    private static final String USERDATA_CREATED_COL = "timeCreated";

    /** Gets the Global Configuration from ra admin session bean */
    private GlobalConfiguration getGlobalConfiguration(Admin admin) {
        return globalConfigurationSession.getCachedGlobalConfiguration(admin);
    }

    private boolean authorizedToCA(Admin admin, int caid) {
        boolean returnval = false;
        returnval = authorizationSession.isAuthorizedNoLog(admin, AccessRulesConstants.CAPREFIX + caid);
        if (!returnval) {
            log.info("Admin " + admin.getUsername() + " not authorized to resource " + AccessRulesConstants.CAPREFIX + caid);
        }
        return returnval;
    }
    
    /** Checks CA authorization and logs an official error if not and throws and AuthorizationDeniedException */
    private void assertAuthorizedToCA(final Admin admin, final int caid, final String username, final int logEvent) throws AuthorizationDeniedException {
        if (!authorizedToCA(admin, caid)) {
            final String msg = intres.getLocalizedMessage("ra.errorauthca", Integer.valueOf(caid));
            logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, logEvent, msg);
            throw new AuthorizationDeniedException(msg);
        }
    }

    private boolean authorizedToEndEntityProfile(Admin admin, int profileid, String rights) {
        boolean returnval = false;
        if (profileid == SecConst.EMPTY_ENDENTITYPROFILE
                && (rights.equals(AccessRulesConstants.CREATE_RIGHTS) || rights.equals(AccessRulesConstants.EDIT_RIGHTS))) {
            if (authorizationSession.isAuthorizedNoLog(admin, "/super_administrator")) {
                returnval = true;
            } else {
                log.info("Admin " + admin.getUsername() + " was not authorized to resource /super_administrator");
            }
        } else {
            returnval = authorizationSession.isAuthorizedNoLog(admin, AccessRulesConstants.ENDENTITYPROFILEPREFIX + profileid + rights)
                    && authorizationSession.isAuthorizedNoLog(admin, AccessRulesConstants.REGULAR_RAFUNCTIONALITY + rights);
        }
        return returnval;
    }

    /** Checks EEP authorization and logs an official error if not and throws and AuthorizationDeniedException */
    private void assertAuthorizedToEndEntityProfile(final Admin admin, final int endEntityProfileId, final String accessRule, final int caId, final String username, final int logEvent) throws AuthorizationDeniedException {
        if (!authorizedToEndEntityProfile(admin, endEntityProfileId, accessRule)) {
            final String msg = intres.getLocalizedMessage("ra.errorauthprofile", Integer.valueOf(endEntityProfileId));
            logSession.log(admin, caId, LogConstants.MODULE_RA, new Date(), username, null, logEvent, msg);
            throw new AuthorizationDeniedException(msg);
        }
    }

    @Override
    public void addUser(Admin admin, String username, String password, String subjectdn, String subjectaltname, String email, boolean clearpwd,
            int endentityprofileid, int certificateprofileid, int type, int tokentype, int hardwaretokenissuerid, int caid) throws PersistenceException,
            AuthorizationDeniedException, UserDoesntFullfillEndEntityProfile, WaitingForApprovalException, CADoesntExistsException, EjbcaException {
        UserDataVO userdata = new UserDataVO(username, subjectdn, caid, subjectaltname, email, UserDataConstants.STATUS_NEW, type, endentityprofileid,
                certificateprofileid, null, null, tokentype, hardwaretokenissuerid, null);
        userdata.setPassword(password);
        addUser(admin, userdata, clearpwd);
    }

    private static final ApprovalOveradableClassName[] NONAPPROVABLECLASSNAMES_ADDUSER = { new ApprovalOveradableClassName(
            org.ejbca.core.model.approval.approvalrequests.AddEndEntityApprovalRequest.class.getName(), null), };

    @Override
    public void addUserFromWS(Admin admin, UserDataVO userdata, boolean clearpwd) throws AuthorizationDeniedException, UserDoesntFullfillEndEntityProfile,
    	PersistenceException, WaitingForApprovalException, CADoesntExistsException, EjbcaException {
        int profileId = userdata.getEndEntityProfileId();
        EndEntityProfile profile = endEntityProfileSession.getEndEntityProfile(admin, profileId);
        if (profile.getAllowMergeDnWebServices()) {
            userdata = UserDataFiller.fillUserDataWithDefaultValues(userdata, profile);
        }
        addUser(admin, userdata, clearpwd);
    }

    @Override
    public void canonicalizeUser(Admin admin, final UserDataVO userdata) throws EjbcaException, UserDoesntFullfillEndEntityProfile {
        final int endEntityProfileId = userdata.getEndEntityProfileId();
        final String endEntityProfileName = endEntityProfileSession.getEndEntityProfileName(admin, endEntityProfileId);
        try {
            FieldValidator.validate(userdata, endEntityProfileId, endEntityProfileName);
        } catch (CustomFieldException e1) {
            throw new EjbcaException(ErrorCode.FIELD_VALUE_NOT_VALID, e1.getMessage(), e1);
        }
        
        final String dn = CertTools.stringToBCDNString(StringTools.strip(userdata.getDN()));
        userdata.setDN(dn);
        userdata.setSubjectAltName(StringTools.strip(userdata.getSubjectAltName()));
        userdata.setEmail(StringTools.strip(userdata.getEmail()));
    }

    // TODO: Try to throw an application exception instead if the PersistenceException, since this becomes
    // EJBException(java.rmi.ServerException(java.rmi.RemoteException(javax.persistence.EntityExistsException)))) on Glassfish
    // See UserAdminSessionTest
    @Override
    public void addUser(Admin admin, UserDataVO userDataVO, boolean clearpwd) throws AuthorizationDeniedException, EjbcaException,
            UserDoesntFullfillEndEntityProfile, WaitingForApprovalException, PersistenceException {
    	final int endEntityProfileId = userDataVO.getEndEntityProfileId();
        final int caid = userDataVO.getCAId();
        final String username = StringTools.strip(userDataVO.getUsername());
        // Check if administrator is authorized to add user to CA.
        assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_ERROR_ADDEDENDENTITY);
        final GlobalConfiguration globalConfiguration = getGlobalConfiguration(admin);
        if (globalConfiguration.getEnableEndEntityProfileLimitations()) {
            // Check if administrator is authorized to add user.
        	assertAuthorizedToEndEntityProfile(admin, endEntityProfileId, AccessRulesConstants.CREATE_RIGHTS, caid, username, LogConstants.EVENT_ERROR_ADDEDENDENTITY);
        }
    	final String originalDN = userDataVO.getDN();
        canonicalizeUser(admin, userDataVO);
        if (log.isTraceEnabled()) {
            log.trace(">addUser(" + userDataVO.getUsername() + ", password, " + userDataVO.getDN() + ", " + originalDN + ", " + userDataVO.getSubjectAltName() + ", "
                    + userDataVO.getEmail() + ", profileId: " + endEntityProfileId + ")");
        }
        final String endEntityProfileName = endEntityProfileSession.getEndEntityProfileName(admin, endEntityProfileId);
        final String dn = userDataVO.getDN();
        final String altName = userDataVO.getSubjectAltName();
        final String email = userDataVO.getEmail();
        final int type = userDataVO.getType();
        String newpassword = userDataVO.getPassword();
        EndEntityProfile profile = null;	// Only look this up if we need it..
        if (userDataVO.getPassword() == null) {
        	profile = endEntityProfileSession.getEndEntityProfile(admin, endEntityProfileId);
        	if (profile.useAutoGeneratedPasswd()) {
                // special case used to signal regeneration of password
                newpassword = profile.getAutoGeneratedPasswd();
        	}
        }
        if (globalConfiguration.getEnableEndEntityProfileLimitations()) {
        	if (profile==null) {
        		profile = endEntityProfileSession.getEndEntityProfile(admin, endEntityProfileId);
        	}
            // Check if user fulfills it's profile.
            try {
            	final String dirattrs = userDataVO.getExtendedinformation() != null ? userDataVO.getExtendedinformation().getSubjectDirectoryAttributes() : null;
                profile.doesUserFullfillEndEntityProfile(username, userDataVO.getPassword(), dn, altName, dirattrs, 
                		email, userDataVO.getCertificateProfileId(), clearpwd,
                        (type & SecConst.USER_KEYRECOVERABLE) != 0, (type & SecConst.USER_SENDNOTIFICATION) != 0, userDataVO.getTokenType(), userDataVO
                                .getHardTokenIssuerId(), caid, userDataVO.getExtendedinformation());
            } catch (UserDoesntFullfillEndEntityProfile e) {
                final String msg = intres.getLocalizedMessage("ra.errorfullfillprofile", endEntityProfileName, dn, e.getMessage());
                logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_ERROR_ADDEDENDENTITY, msg);
                throw e;
            }
        }
        // Get CAInfo, to be able to read configuration
        final CAInfo caInfo = caAdminSession.getCAInfoOrThrowException(admin, caid);
        // Check if approvals is required. (Only do this if store users, otherwise this approval is disabled.)
        if (caInfo.isUseUserStorage()) {
        	final int numOfApprovalsRequired = getNumOfApprovalRequired(admin, CAInfo.REQ_APPROVAL_ADDEDITENDENTITY, caid, userDataVO.getCertificateProfileId());
        	if (numOfApprovalsRequired > 0) {
            	AddEndEntityApprovalRequest ar = new AddEndEntityApprovalRequest(userDataVO, clearpwd, admin, null, numOfApprovalsRequired, caid, endEntityProfileId);
            	if (ApprovalExecutorUtil.requireApproval(ar, NONAPPROVABLECLASSNAMES_ADDUSER)) {
            		approvalSession.addApprovalRequest(admin, ar, globalConfiguration);
            		throw new WaitingForApprovalException(intres.getLocalizedMessage("ra.approvalad"));
            	}
        	}
        }
        // Check if the subjectDN serialnumber already exists.
        if (caInfo.isDoEnforceUniqueSubjectDNSerialnumber()) {
        	if (caInfo.isUseUserStorage()) {
        		if (!isSubjectDnSerialnumberUnique(caid, dn, username)) {
        			throw new EjbcaException(ErrorCode.SUBJECTDN_SERIALNUMBER_ALREADY_EXISTS, "Error: SubjectDN Serialnumber already exists.");
        		}
        	} else {
        		log.warn("CA configured to enforce unique SubjectDN serialnumber, but not to store any user data. Check will be ignored. Please verify your configuration.");
        	}
        }
        // Store a new UserData in the database, if this CA is configured to do so.
        if (caInfo.isUseUserStorage()) {
        	try {
        		// Create the user in one go with all parameters at once. This was important in EJB2.1 so the persistence layer only creates *one* single
        		// insert statement. If we do a home.create and the some setXX, it will create one insert and one update statement to the database.
        		// Probably not important in EJB3 anymore.
        		final UserData userData = new UserData(username, newpassword, clearpwd, dn, caid, userDataVO.getCardNumber(),
        				altName, email, type, endEntityProfileId, userDataVO.getCertificateProfileId(),
        				userDataVO.getTokenType(), userDataVO.getHardTokenIssuerId(), userDataVO.getExtendedinformation());
        		// Since persist will not commit and fail if the user already exists, we need to check for this
        		// Flushing the entityManager will not allow us to rollback the persisted user if this is a part of a larger transaction.
        		if (UserData.findByUsername(entityManager, username) != null) {
        			throw new EntityExistsException("User " + username + " already exists.");
        		}
        		entityManager.persist(userData);
        		// Although UserDataVO should always have a null password for
        		// autogenerated end entities, the notification framework
        		// expect it to exist. Since nothing else but printing is done after
        		// this point it is safe to set the password
        		userDataVO.setPassword(newpassword);
        		// Send notifications, if they should be sent
        		sendNotification(admin, userDataVO, UserDataConstants.STATUS_NEW);
        		if ((type & SecConst.USER_PRINT) != 0) {
                	if (profile==null) {
                		profile = endEntityProfileSession.getEndEntityProfile(admin, endEntityProfileId);
                	}
        			print(admin, profile, userDataVO);
        		}
        		final String msg = intres.getLocalizedMessage("ra.addedentity", username);
        		logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_ADDEDENDENTITY, msg);
        	} catch (PersistenceException e) {
        		// PersistenceException could also be caused by various database problems.
        		final String msg = intres.getLocalizedMessage("ra.errorentityexist", username);
        		logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_ERROR_ADDEDENDENTITY, msg);
        		throw e;
        	} catch (Exception e) {
        		final String msg = intres.getLocalizedMessage("ra.erroraddentity", username);
        		logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_ERROR_ADDEDENDENTITY, msg, e);
        		log.error(msg, e);
        		throw new EJBException(e);
        	}
        }
        if (log.isTraceEnabled()) {
            log.trace("<addUser(" + username + ", password, " + dn + ", " + email + ")");
        }
    }

    /* Does not check authorization. Calling code is responsible for this. */
    private boolean isSubjectDnSerialnumberUnique(final int caid, final String subjectDN, final String username) {
    	final String serialnumber = CertTools.getPartFromDN(subjectDN, "SN");
    	if (log.isDebugEnabled()) {
    		log.debug("subjectDN=" + subjectDN + " extracted SN=" + serialnumber);
    	}
    	// We treat the lack of a serialnumber field as unique
    	if (serialnumber == null) {
    		return true;
    	}
    	// Without a username we cannot determine if this is the same user, if we find any in the database later
        if (username == null) {
            return false;
        }
        final List<String> subjectDNs = UserData.findSubjectDNsByCaIdAndNotUsername(entityManager, caid, username, serialnumber);
        // Even though we push down most of the work to the database we still have to verify the serialnumber here since
        // for example serialnumber '1' will match both "SN=1" and "SN=10" etc
        for (final String currentSubjectDN : subjectDNs) {
        	final String currentSn = CertTools.getPartFromDN(currentSubjectDN, "SN");
        	if (serialnumber.equals(currentSn)) {
        		return false;
        	}
        }
        return true;
    }

    /**
     * Help method that checks the CA data config if specified action requires
     * approvals and how many
     * 
     * @param action one of CAInfo.REQ_APPROVAL_ constants
     * @param caid of the ca to check
     * @param certprofileid of the certificate profile to check
     * @return 0 of no approvals is required or no such CA exists, otherwise the
     *         number of approvals
     */
    private int getNumOfApprovalRequired(Admin admin, int action, int caid, int certprofileid) {
        return caAdminSession.getNumOfApprovalRequired(admin, action, caid, certprofileid);
    }

    @Deprecated
    @Override
    public void changeUser(Admin admin, String username, String password, String subjectdn, String subjectaltname, String email, boolean clearpwd,
            int endentityprofileid, int certificateprofileid, int type, int tokentype, int hardwaretokenissuerid, int status, int caid)
            throws AuthorizationDeniedException, UserDoesntFullfillEndEntityProfile, WaitingForApprovalException, CADoesntExistsException, EjbcaException {
        UserDataVO userdata = new UserDataVO(username, subjectdn, caid, subjectaltname, email, status, type, endentityprofileid, certificateprofileid, null,
                null, tokentype, hardwaretokenissuerid, null);
        userdata.setPassword(password);
        changeUser(admin, userdata, clearpwd, false);
    }

    private static final ApprovalOveradableClassName[] NONAPPROVABLECLASSNAMES_CHANGEUSER = {
            new ApprovalOveradableClassName(org.ejbca.core.model.approval.approvalrequests.EditEndEntityApprovalRequest.class.getName(), null),
            /**
             * can not use .class.getName() below, because it is not part of
             * base EJBCA dist
             */
            new ApprovalOveradableClassName("se.primeKey.cardPersonalization.ra.connection.ejbca.EjbcaConnection", null) };

    @Override
    public void changeUser(Admin admin, UserDataVO userdata, boolean clearpwd) throws AuthorizationDeniedException, UserDoesntFullfillEndEntityProfile,
            WaitingForApprovalException, CADoesntExistsException, EjbcaException {
        changeUser(admin, userdata, clearpwd, false);
    }

    @Override
    public void changeUser(final Admin admin, final UserDataVO userDataVO, final boolean clearpwd, final boolean fromWebService) throws AuthorizationDeniedException,
            UserDoesntFullfillEndEntityProfile, WaitingForApprovalException, CADoesntExistsException, EjbcaException {
    	final int endEntityProfileId = userDataVO.getEndEntityProfileId();
        final int caid = userDataVO.getCAId();
        final String username = userDataVO.getUsername();
        // Check if administrator is authorized to edit user to CA.
        assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
        final GlobalConfiguration globalConfiguration = getGlobalConfiguration(admin);
        if (globalConfiguration.getEnableEndEntityProfileLimitations()) {
            // Check if administrator is authorized to edit user.
        	assertAuthorizedToEndEntityProfile(admin, endEntityProfileId, AccessRulesConstants.EDIT_RIGHTS, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
        }
        try {
            FieldValidator.validate(userDataVO, endEntityProfileId, endEntityProfileSession.getEndEntityProfileName(admin, endEntityProfileId));
        } catch (CustomFieldException e) {
            throw new EjbcaException(ErrorCode.FIELD_VALUE_NOT_VALID, e.getMessage(), e);
        }
        String dn = CertTools.stringToBCDNString(StringTools.strip(userDataVO.getDN()));
        String altName = userDataVO.getSubjectAltName();
        if (log.isTraceEnabled()) {
            log.trace(">changeUser(" + username + ", " + dn + ", " + userDataVO.getEmail() + ")");
        }
        final UserData userData = UserData.findByUsername(entityManager, username);
        if (userData == null) {
            final String msg = intres.getLocalizedMessage("ra.erroreditentity", username);
            logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_ERROR_CHANGEDENDENTITY, msg);
            log.error(msg);
            throw new EJBException(msg);
        }
        final EndEntityProfile profile = endEntityProfileSession.getEndEntityProfile(admin, endEntityProfileId);
        // if required, we merge the existing user dn into the dn provided by the web service.
        if (fromWebService && profile.getAllowMergeDnWebServices()) {
            if (userData != null) {
                if (userData.getSubjectDN() != null) {
                    final Map<String, String> dnMap = new HashMap<String, String>();
                    if (profile.getUse(DnComponents.DNEMAIL, 0)) {
                        dnMap.put(DnComponents.DNEMAIL, userDataVO.getEmail());
                    }
                    try {
                        dn = (new DistinguishedName(userData.getSubjectDN())).mergeDN(new DistinguishedName(dn), true, dnMap).toString();
                    } catch (InvalidNameException e) {
                        log.debug("Invalid dn. We make it empty");
                        dn = "";
                    }
                }
                if (userData.getSubjectAltName() != null) {
                    final Map<String, String> dnMap = new HashMap<String, String>();
                    if (profile.getUse(DnComponents.RFC822NAME, 0)) {
                        dnMap.put(DnComponents.RFC822NAME, userDataVO.getEmail());
                    }
                    try {
                        // SubjectAltName is not mandatory so
                        if (altName == null) {
                            altName = "";
                        }
                        altName = (new DistinguishedName(userData.getSubjectAltName())).mergeDN(new DistinguishedName(altName), true, dnMap).toString();
                    } catch (InvalidNameException e) {
                        log.debug("Invalid altName. We make it empty");
                        altName = "";
                    }
                }
            }
        }
        String newpassword = userDataVO.getPassword();
        if (profile.useAutoGeneratedPasswd() && newpassword != null) {
            // special case used to signal regeneraton of password
            newpassword = profile.getAutoGeneratedPasswd();
        }

        final int type = userDataVO.getType();
        final ExtendedInformation ei = userDataVO.getExtendedinformation();
        // Check if user fulfills it's profile.
        if (globalConfiguration.getEnableEndEntityProfileLimitations()) {
            try {
            	String dirattrs = null;
            	if (ei != null) {
            		dirattrs = ei.getSubjectDirectoryAttributes();
            	}
        		// It is only meaningful to verify the password if we change it in some way, and if we are not autogenerating it
            	if (!profile.useAutoGeneratedPasswd() && StringUtils.isNotEmpty(newpassword)) {
                	profile.doesUserFullfillEndEntityProfile(username, userDataVO.getPassword(), dn, altName, dirattrs, userDataVO.getEmail(),
                			userDataVO.getCertificateProfileId(), clearpwd, (type & SecConst.USER_KEYRECOVERABLE) != 0, (type & SecConst.USER_SENDNOTIFICATION) != 0,
                			userDataVO.getTokenType(), userDataVO.getHardTokenIssuerId(), caid, ei);
            	} else {
                	profile.doesUserFullfillEndEntityProfileWithoutPassword(username, dn, altName, dirattrs, userDataVO.getEmail(),
                			userDataVO.getCertificateProfileId(), (type & SecConst.USER_KEYRECOVERABLE) != 0, (type & SecConst.USER_SENDNOTIFICATION) != 0,
                			userDataVO.getTokenType(), userDataVO.getHardTokenIssuerId(), caid, ei);
            	}
            } catch (UserDoesntFullfillEndEntityProfile e) {
                final String msg = intres.getLocalizedMessage("ra.errorfullfillprofile", Integer.valueOf(endEntityProfileId), dn, e.getMessage());
                logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
                throw e;
            }
        }
        // Check if approvals is required.
        final int numOfApprovalsRequired = getNumOfApprovalRequired(admin, CAInfo.REQ_APPROVAL_ADDEDITENDENTITY, caid, userDataVO.getCertificateProfileId());
        if (numOfApprovalsRequired > 0) {
            final UserDataVO orguserdata = userData.toUserDataVO();
            final EditEndEntityApprovalRequest ar = new EditEndEntityApprovalRequest(userDataVO, clearpwd, orguserdata, admin, null, numOfApprovalsRequired, caid, endEntityProfileId);
            if (ApprovalExecutorUtil.requireApproval(ar, NONAPPROVABLECLASSNAMES_CHANGEUSER)) {
                approvalSession.addApprovalRequest(admin, ar, getGlobalConfiguration(admin));
                throw new WaitingForApprovalException(intres.getLocalizedMessage("ra.approvaledit"));
            }
        }
        // Check if the subjectDN serialnumber already exists.
        if (caAdminSession.getCAInfoOrThrowException(admin, caid).isDoEnforceUniqueSubjectDNSerialnumber()) {
            if (!isSubjectDnSerialnumberUnique(caid, dn, username)) {
            	throw new EjbcaException(ErrorCode.SUBJECTDN_SERIALNUMBER_ALREADY_EXISTS, "Error: SubjectDN Serialnumber already exists.");
            }
        }
        try {
            userData.setDN(dn);
            userData.setSubjectAltName(altName);
            userData.setSubjectEmail(userDataVO.getEmail());
            userData.setCaId(caid);
            userData.setType(type);
            userData.setEndEntityProfileId(endEntityProfileId);
            userData.setCertificateProfileId(userDataVO.getCertificateProfileId());
            userData.setTokenType(userDataVO.getTokenType());
            userData.setHardTokenIssuerId(userDataVO.getHardTokenIssuerId());
            userData.setCardNumber(userDataVO.getCardNumber());
            final int newstatus = userDataVO.getStatus();
            final int oldstatus = userData.getStatus();
            if (oldstatus == UserDataConstants.STATUS_KEYRECOVERY && newstatus != UserDataConstants.STATUS_KEYRECOVERY && newstatus != UserDataConstants.STATUS_INPROCESS) {
                keyRecoverySession.unmarkUser(admin, username);
            }
            if (ei != null) {
            	final String requestCounter = ei.getCustomData(ExtendedInformation.CUSTOM_REQUESTCOUNTER);
            	if (StringUtils.equals(requestCounter, "0") && newstatus == UserDataConstants.STATUS_NEW && oldstatus != UserDataConstants.STATUS_NEW) {
            		// If status is set to new, we should re-set the allowed request counter to the default values
            		// But we only do this if no value is specified already, i.e. 0 or null
            		resetRequestCounter(admin, false, ei, username, endEntityProfileId);
            	} else {
            		// If status is not new, we will only remove the counter if the profile does not use it
            		resetRequestCounter(admin, true, ei, username, endEntityProfileId);
            	}
            }
            userData.setExtendedInformation(ei);
            userData.setStatus(newstatus);
            if (StringUtils.isNotEmpty(newpassword)) {
                if (clearpwd) {
                    try {
                        userData.setOpenPassword(newpassword);
                    } catch (NoSuchAlgorithmException e) {
                        log.debug("NoSuchAlgorithmException while setting password for user " + username);
                        throw new EJBException(e);
                    }
                } else {
                    userData.setPassword(newpassword);
                }
            }
            // We want to create this object before re-setting the time modified, because we may want to
            // use the old time modified in any notifications
            final UserDataVO notificationUserDataVO = userData.toUserDataVO();
            userData.setTimeModified(new Date().getTime());
            // We also want to be able to handle non-clear generated passwords in the notification, although UserDataVO
            // should always have a null password for autogenerated end entities the notification framework expects it to
            // exist.
            if (newpassword != null) {
                notificationUserDataVO.setPassword(newpassword);
            }
            // Send notification if it should be sent.
            sendNotification(admin, notificationUserDataVO, newstatus);
            if (newstatus != oldstatus) {
                // Only print stuff on a printer on the same conditions as for
                // notifications, we also only print if the status changes, not for
                // every time we press save
                if ((type & SecConst.USER_PRINT) != 0
                        && (newstatus == UserDataConstants.STATUS_NEW || newstatus == UserDataConstants.STATUS_KEYRECOVERY || newstatus == UserDataConstants.STATUS_INITIALIZED)) {
                    print(admin, profile, userDataVO);
                }
                final String msg = intres.getLocalizedMessage("ra.editedentitystatus", username, Integer.valueOf(newstatus));
                logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
            } else {
                final String msg = intres.getLocalizedMessage("ra.editedentity", username);
                logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
            }
        } catch (Exception e) {
            final String msg = intres.getLocalizedMessage("ra.erroreditentity", username);
            logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_ERROR_CHANGEDENDENTITY, msg);
            log.error("ChangeUser:", e);
            throw new EJBException(e);
        }
        if (log.isTraceEnabled()) {
            log.trace("<changeUser(" + username + ", password, " + dn + ", " + userDataVO.getEmail() + ")");
        }
    }

    @Override
    public void deleteUser(Admin admin, String username) throws AuthorizationDeniedException, NotFoundException, RemoveException {
        if (log.isTraceEnabled()) {
            log.trace(">deleteUser(" + username + ")");
        }
        // Check if administrator is authorized to delete user.
        int caid = LogConstants.INTERNALCAID;
        UserData data1 = UserData.findByUsername(entityManager, username);
        if (data1 != null) {
            caid = data1.getCaId();
            assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_ERROR_DELETEENDENTITY);
            if (getGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
            	assertAuthorizedToEndEntityProfile(admin, data1.getEndEntityProfileId(), AccessRulesConstants.DELETE_RIGHTS, caid, username, LogConstants.EVENT_ERROR_DELETEENDENTITY);
            }
        } else {
            String msg = intres.getLocalizedMessage("ra.errorentitynotexist", username);
            logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_ERROR_DELETEENDENTITY, msg);
            throw new NotFoundException(msg);
        }
        try {
            entityManager.remove(data1);
            String msg = intres.getLocalizedMessage("ra.removedentity", username);
            logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_DELETEDENDENTITY, msg);
        } catch (Exception e) {
            String msg = intres.getLocalizedMessage("ra.errorremoveentity", username);
            logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_ERROR_DELETEENDENTITY, msg);
            throw new RemoveException(msg);
        }
        if (log.isTraceEnabled()) {
            log.trace("<deleteUser(" + username + ")");
        }
    }

    private static final ApprovalOveradableClassName[] NONAPPROVABLECLASSNAMES_SETUSERSTATUS = {
            new ApprovalOveradableClassName(org.ejbca.core.model.approval.approvalrequests.ChangeStatusEndEntityApprovalRequest.class.getName(), null),
            new ApprovalOveradableClassName(org.ejbca.core.ejb.ra.UserAdminSessionBean.class.getName(), "revokeUser"),
            new ApprovalOveradableClassName(org.ejbca.core.ejb.ra.UserAdminSessionBean.class.getName(), "revokeCert"),
            new ApprovalOveradableClassName(org.ejbca.core.ejb.ca.auth.AuthenticationSessionBean.class.getName(), "finishUser"),
            new ApprovalOveradableClassName(org.ejbca.core.ejb.ra.UserAdminSessionBean.class.getName(), "unrevokeCert"),
            new ApprovalOveradableClassName(org.ejbca.core.ejb.ra.UserAdminSessionBean.class.getName(), "prepareForKeyRecovery"),
            /**
             * can not use .class.getName() below, because it is not part of
             * base EJBCA dist
             */
            new ApprovalOveradableClassName("org.ejbca.extra.caservice.ExtRACAProcess", "processExtRARevocationRequest"),
            new ApprovalOveradableClassName("se.primeKey.cardPersonalization.ra.connection.ejbca.EjbcaConnection", null) };

    @Override
    public void resetRemainingLoginAttempts(Admin admin, String username) throws AuthorizationDeniedException, FinderException {
    	if (log.isTraceEnabled()) {
    		log.trace(">resetRamainingLoginAttempts(" + username + ")");
    	}
    	int resetValue = -1;
    	int caid = LogConstants.INTERNALCAID;
    	final UserData data1 = UserData.findByUsername(entityManager, username);
    	if (data1 != null) {
    		caid = data1.getCaId();
    		assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
    		final ExtendedInformation ei = data1.getExtendedInformation();
    		if (ei != null) {
    			resetRemainingLoginAttemptsInternal(admin, ei, username, caid);
    			data1.setTimeModified(new Date().getTime());
    			data1.setExtendedInformation(ei);
    		}
    	} else {
    		String msg = intres.getLocalizedMessage("ra.errorentitynotexist", username);
    		logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
    		throw new FinderException(msg);
    	}
    	if (log.isTraceEnabled()) {
    		log.trace("<resetRamainingLoginAttempts(" + username + "): " + resetValue);
    	}
    }

    /**
     * Assumes authorization has already been checked..
     * Modifies the ExtendedInformation object to reset the remaining login attempts.
     */
    private void resetRemainingLoginAttemptsInternal(final Admin admin, final ExtendedInformation ei, final String username, final int caid) {
        if (log.isTraceEnabled()) {
            log.trace(">resetRemainingLoginAttemptsInternal");
        }
        final int resetValue = ei.getMaxLoginAttempts();
        if (resetValue != -1 || ei.getRemainingLoginAttempts() != -1) {
        	ei.setRemainingLoginAttempts(resetValue);
        	final String msg = intres.getLocalizedMessage("ra.resettedloginattemptscounter", username, resetValue);
        	logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
        }
        if (log.isTraceEnabled()) {
            log.trace("<resetRamainingLoginAttemptsInternal: " + resetValue);
        }
    }

    @Override
    public void decRemainingLoginAttempts(Admin admin, String username) throws AuthorizationDeniedException, FinderException {
        if (log.isTraceEnabled()) {
            log.trace(">decRemainingLoginAttempts(" + username + ")");
        }
        int caid = LogConstants.INTERNALCAID;
		int counter = Integer.MAX_VALUE;
        UserData data1 = UserData.findByUsername(entityManager, username);
        if (data1 != null) {
            caid = data1.getCaId();
            assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
            final ExtendedInformation ei = data1.getExtendedInformation();
            if (ei != null) {
            	counter = ei.getRemainingLoginAttempts();
            	// If we get to 0 we must set status to generated
            	if (counter == 0) {
            		// if it isn't already
            		if (data1.getStatus() != UserDataConstants.STATUS_GENERATED) {
            			data1.setStatus(UserDataConstants.STATUS_GENERATED);
            			final String msg = intres.getLocalizedMessage("ra.decreasedloginattemptscounter", username, counter);
            			logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
            			resetRemainingLoginAttemptsInternal(admin, ei, username, caid);
        				data1.setTimeModified(new Date().getTime());
        				data1.setExtendedInformation(ei);
            		}
            	} else if (counter != -1) {
            		if (log.isDebugEnabled()) {
            			log.debug("Found a remaining login counter with value " + counter);
            		}
            		ei.setRemainingLoginAttempts(--counter);
            		data1.setExtendedInformation(ei);
            		String msg = intres.getLocalizedMessage("ra.decreasedloginattemptscounter", username, counter);
            		logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
            	} else {
            		if (log.isDebugEnabled()) {
            			log.debug("Found a remaining login counter with value UNLIMITED, not decreased in db.");
            		}
            		counter = Integer.MAX_VALUE;
            	}
            }
        } else {
            String msg = intres.getLocalizedMessage("ra.errorentitynotexist", username);
            logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
            throw new FinderException(msg);
        }
        if (log.isTraceEnabled()) {
            log.trace("<decRemainingLoginAttempts(" + username + "): " + counter);
        }
    }

    @Override
    public int decRequestCounter(Admin admin, String username) throws AuthorizationDeniedException, FinderException, ApprovalException,
            WaitingForApprovalException {
        if (log.isTraceEnabled()) {
            log.trace(">decRequestCounter(" + username + ")");
        }
        // Default return value is as if the optional value does not exist for
        // the user, i.e. the default values is 0
        // because the default number of allowed requests are 1
        int counter = 0;
        // Check if administrator is authorized to edit user.
        int caid = LogConstants.INTERNALCAID;
        UserData data1 = UserData.findByUsername(entityManager, username);
        if (data1 != null) {
            caid = data1.getCaId();
            assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
            if (getGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
            	assertAuthorizedToEndEntityProfile(admin, data1.getEndEntityProfileId(), AccessRulesConstants.EDIT_RIGHTS, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
            }
            // Do the work of decreasing the counter
            ExtendedInformation ei = data1.getExtendedInformation();
            if (ei != null) {
                String counterstr = ei.getCustomData(ExtendedInformation.CUSTOM_REQUESTCOUNTER);
                boolean serialNumberCleared = false;
                if (StringUtils.isNotEmpty(counterstr)) {
                    try {
                        counter = Integer.valueOf(counterstr);
                        if (log.isDebugEnabled()) {
                            log.debug("Found a counter with value " + counter);
                        }
                        // decrease the counter, if we get to 0 we must set
                        // status to generated
                        counter--;
                        if (counter >= 0) {
                            ei.setCustomData(ExtendedInformation.CUSTOM_REQUESTCOUNTER, String.valueOf(counter));
                            ei.setCertificateSerialNumber(null);// cert serial number should also be cleared after successful command.
                            data1.setExtendedInformation(ei);
                            serialNumberCleared = true;
                            final Date now = new Date();
                            if (counter > 0) { // if 0 then update when changing type
                                data1.setTimeModified(now.getTime());
                            }
                            String msg = intres.getLocalizedMessage("ra.decreasedentityrequestcounter", username, counter);
                            logSession.log(admin, caid, LogConstants.MODULE_RA, now, username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY,
                                    msg);
                        } else {
                            if (log.isDebugEnabled()) {
                                log.debug("Counter value was already 0, not decreased in db.");
                            }
                        }
                    } catch (NumberFormatException e) {
                        String msg = intres.getLocalizedMessage("ra.errorrequestcounterinvalid", username, counterstr, e.getMessage());
                        log.error(msg, e);
                    }
                } else {
                    if (log.isDebugEnabled()) {
                        log.debug("No (optional) request counter exists for end entity: " + username);
                    }
                }
                if (!serialNumberCleared && ei.certificateSerialNumber() != null) {
                    ei.setCertificateSerialNumber(null);// cert serial number should also be cleared after successful command.
                    data1.setExtendedInformation(ei);
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("No extended information exists for user: " + data1.getUsername());
                }
            }
        } else {
            String msg = intres.getLocalizedMessage("ra.errorentitynotexist", username);
            logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
            throw new FinderException(msg);
        }
        if (counter <= 0) {
            setUserStatus(admin, data1, UserDataConstants.STATUS_GENERATED);
        }
        if (log.isTraceEnabled()) {
            log.trace("<decRequestCounter(" + username + "): " + counter);
        }
        return counter;
    }

    @Override
    public void cleanUserCertDataSN(UserDataVO data) throws ObjectNotFoundException {
        if (log.isTraceEnabled()) {
            log.trace(">cleanUserCertDataSN: " + data.getUsername());
        }
        // This admin can be the public web user, which may not be allowed to
        // change status,
        // this is a bit ugly, but what can a man do...
        Admin statusadmin = Admin.getInternalAdmin();
        try {
            cleanUserCertDataSN(statusadmin, data.getUsername());
        } catch (FinderException e) {
            String msg = intres.getLocalizedMessage("authentication.usernotfound", data.getUsername());
            logSession.log(statusadmin, statusadmin.getCaId(), LogConstants.MODULE_CA, new Date(), data.getUsername(), null,
                    LogConstants.EVENT_INFO_USERAUTHENTICATION, msg);
            throw new ObjectNotFoundException(e.getMessage());
        } catch (AuthorizationDeniedException e) {
            // Should never happen
            log.error("AuthorizationDeniedException: ", e);
            throw new EJBException(e);
        } catch (ApprovalException e) {
            // Should never happen
            log.error("ApprovalException: ", e);
            throw new EJBException(e);
        } catch (WaitingForApprovalException e) {
            // Should never happen
            log.error("ApprovalException: ", e);
            throw new EJBException(e);
        }
        if (log.isTraceEnabled()) {
            log.trace("<cleanUserCertDataSN: " + data.getUsername());
        }
    }

    @Override
    public void cleanUserCertDataSN(Admin admin, String username) throws AuthorizationDeniedException, FinderException, ApprovalException,
            WaitingForApprovalException {
        if (log.isTraceEnabled()) {
            log.trace(">cleanUserCertDataSN(" + username + ")");
        }
        final int caid = LogConstants.INTERNALCAID;
        try {
            // Check if administrator is authorized to edit user.
            UserData data1 = UserData.findByUsername(entityManager, username);
            if (data1 != null) {
                assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
                if (getGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
                	assertAuthorizedToEndEntityProfile(admin, data1.getEndEntityProfileId(), AccessRulesConstants.EDIT_RIGHTS, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
                }
                final ExtendedInformation ei = data1.getExtendedInformation();
                if (ei == null) {
                    if (log.isDebugEnabled()) {
                        log.debug("No extended information exists for user: " + data1.getUsername());
                    }
                } else {
                    ei.setCertificateSerialNumber(null);
                    data1.setExtendedInformation(ei);                	
                }
            } else {
                String msg = intres.getLocalizedMessage("ra.errorentitynotexist", username);
                logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
                throw new FinderException(msg);
            }
        } finally {
            if (log.isTraceEnabled()) {
                log.trace("<cleanUserCertDataSN(" + username + ")");
            }
        }
    }

    @Override
    public void setUserStatus(final Admin admin, final String username, final int status) throws AuthorizationDeniedException, FinderException, ApprovalException,
            WaitingForApprovalException {
        if (log.isTraceEnabled()) {
            log.trace(">setUserStatus(" + username + ", " + status + ")");
        }
        // Check if administrator is authorized to edit user.
        final UserData data = UserData.findByUsername(entityManager, username);
        if (data == null) {
            final String msg = intres.getLocalizedMessage("ra.errorentitynotexist", username);
            logSession.log(admin, LogConstants.INTERNALCAID, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
            throw new FinderException(msg);
        }
        // Check authorization
        final int caid = data.getCaId();
        assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
        if (getGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
        	assertAuthorizedToEndEntityProfile(admin, data.getEndEntityProfileId(), AccessRulesConstants.EDIT_RIGHTS, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
        }
        setUserStatus(admin, data, status);
    }

    @Override 
    public long countEndEntitiesUsingCertificateProfile(int certificateprofileid) {
       return UserData.countByCertificateProfileId(entityManager, certificateprofileid);
    }
    
    private void setUserStatus(final Admin admin, final UserData data1, final int status) throws AuthorizationDeniedException, FinderException, ApprovalException,
            WaitingForApprovalException {
        final int caid = data1.getCaId();
        final String username = data1.getUsername();
        final int endEntityProfileId = data1.getEndEntityProfileId();
        // Check if approvals is required.
        final int numOfApprovalsRequired = getNumOfApprovalRequired(admin, CAInfo.REQ_APPROVAL_ADDEDITENDENTITY, caid, data1.getCertificateProfileId());
        if (numOfApprovalsRequired > 0) {
            final ChangeStatusEndEntityApprovalRequest ar = new ChangeStatusEndEntityApprovalRequest(username, data1.getStatus(), status, admin, null,
                    numOfApprovalsRequired, data1.getCaId(), endEntityProfileId);
            if (ApprovalExecutorUtil.requireApproval(ar, NONAPPROVABLECLASSNAMES_SETUSERSTATUS)) {
                approvalSession.addApprovalRequest(admin, ar, getGlobalConfiguration(admin));
                String msg = intres.getLocalizedMessage("ra.approvaledit");
                throw new WaitingForApprovalException(msg);
            }
        }
        if (data1.getStatus() == UserDataConstants.STATUS_KEYRECOVERY
                && !(status == UserDataConstants.STATUS_KEYRECOVERY || status == UserDataConstants.STATUS_INPROCESS || status == UserDataConstants.STATUS_INITIALIZED)) {
            keyRecoverySession.unmarkUser(admin, username);
        }
        if ((status == UserDataConstants.STATUS_NEW) && (data1.getStatus() != UserDataConstants.STATUS_NEW)) {
        	final ExtendedInformation ei = data1.getExtendedInformation();
        	if (ei != null) {
        		// If status is set to new, when it is not already new, we should
        		// re-set the allowed request counter to the default values
        		resetRequestCounter(admin, false, ei, username, endEntityProfileId);
        		// Reset remaining login counter
        		resetRemainingLoginAttemptsInternal(admin, ei, username, caid);
        		//data1.setTimeModified(new Date().getTime());
        		data1.setExtendedInformation(ei);
        	}
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Status not changing from something else to new, not resetting requestCounter.");
            }
        }
        final Date timeModified = new Date();
        data1.setStatus(status);
        data1.setTimeModified(timeModified.getTime());
        final String msg = intres.getLocalizedMessage("ra.editedentitystatus", username, Integer.valueOf(status));
        logSession.log(admin, caid, LogConstants.MODULE_RA, timeModified, username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
        // Send notifications when transitioning user through work-flow, if they
        // should be sent
        final UserDataVO userdata = data1.toUserDataVO();
        sendNotification(admin, userdata, status);
        if (log.isTraceEnabled()) {
            log.trace("<setUserStatus(" + username + ", " + status + ")");
        }
    }

    @Override
    public void setPassword(Admin admin, String username, String password) throws UserDoesntFullfillEndEntityProfile, AuthorizationDeniedException,
            FinderException {
        setPassword(admin, username, password, false);
    }

    @Override
    public void setClearTextPassword(Admin admin, String username, String password) throws UserDoesntFullfillEndEntityProfile, AuthorizationDeniedException,
            FinderException {
        setPassword(admin, username, password, true);
    }

    /**
     * Sets a password, hashed or clear text, for a user.
     * 
     * @param admin the administrator pwrforming the action
     * @param username the unique username.
     * @param password the new password to be stored in clear text. Setting
     *            password to 'null' effectively deletes any previous clear
     *            text password.
     * @param cleartext true gives cleartext password, false hashed
     */
    private void setPassword(final Admin admin, final String username, final String password, final boolean cleartext) throws UserDoesntFullfillEndEntityProfile,
            AuthorizationDeniedException, FinderException {
        if (log.isTraceEnabled()) {
            log.trace(">setPassword(" + username + ", hiddenpwd), " + cleartext);
        }
        // Find user
        String newpasswd = password;
        final UserData data = UserData.findByUsername(entityManager, username);
        if (data == null) {
            throw new FinderException("Could not find user " + username);
        }
        final int caid = data.getCaId();
        final String dn = data.getSubjectDN();
        final int endEntityProfileId = data.getEndEntityProfileId();

        final EndEntityProfile profile = endEntityProfileSession.getEndEntityProfile(admin, endEntityProfileId);

        if (profile.useAutoGeneratedPasswd()) {
            newpasswd = profile.getAutoGeneratedPasswd();
        }
        if (getGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
            // Check if user fulfills it's profile.
            try {
                profile.doesPasswordFulfillEndEntityProfile(password, true);
            } catch (UserDoesntFullfillEndEntityProfile ufe) {
                final String msg = intres.getLocalizedMessage("ra.errorfullfillprofile", Integer.valueOf(endEntityProfileId), dn, ufe.getMessage());
                logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
                throw ufe;
            }
            // Check if administrator is authorized to edit user.
        	assertAuthorizedToEndEntityProfile(admin, data.getEndEntityProfileId(), AccessRulesConstants.EDIT_RIGHTS, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
        }
        assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
        try {
        	final Date now = new Date();
            if ((newpasswd == null) && (cleartext)) {
                data.setClearPassword("");
                data.setTimeModified(now.getTime());
            } else {
                if (cleartext) {
                    data.setOpenPassword(newpasswd);
                } else {
                    data.setPassword(newpasswd);
                }
                data.setTimeModified(now.getTime());
            }
            final String msg = intres.getLocalizedMessage("ra.editpwdentity", username);
            logSession.log(admin, caid, LogConstants.MODULE_RA, now, username, null, LogConstants.EVENT_INFO_CHANGEDENDENTITY, msg);
        } catch (NoSuchAlgorithmException nsae) {
            log.error("NoSuchAlgorithmException while setting password for user " + username);
            throw new EJBException(nsae);
        }
        if (log.isTraceEnabled()) {
            log.trace("<setPassword(" + username + ", hiddenpwd), " + cleartext);
        }
    }

    @Override
    public boolean verifyPassword(Admin admin, String username, String password) throws UserDoesntFullfillEndEntityProfile, AuthorizationDeniedException,
            FinderException {
        if (log.isTraceEnabled()) {
            log.trace(">verifyPassword(" + username + ", hiddenpwd)");
        }
        boolean ret = false;
        // Find user
        UserData data = UserData.findByUsername(entityManager, username);
        if (data == null) {
            throw new FinderException("Could not find user " + username);
        }
        int caid = data.getCaId();
        if (getGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
            // Check if administrator is authorized to edit user.
        	assertAuthorizedToEndEntityProfile(admin, data.getEndEntityProfileId(), AccessRulesConstants.EDIT_RIGHTS, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
        }
        assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_INFO_CHANGEDENDENTITY);
        try {
            ret = data.comparePassword(password);
        } catch (NoSuchAlgorithmException nsae) {
            log.debug("NoSuchAlgorithmException while verifying password for user " + username);
            throw new EJBException(nsae);
        }
        if (log.isTraceEnabled()) {
            log.trace("<verifyPassword(" + username + ", hiddenpwd)");
        }
        return ret;
    }

    private static final ApprovalOveradableClassName[] NONAPPROVABLECLASSNAMES_REVOKEANDDELETEUSER = { new ApprovalOveradableClassName(
            org.ejbca.core.model.approval.approvalrequests.RevocationApprovalRequest.class.getName(), null), };

    @Override
    public void revokeAndDeleteUser(Admin admin, String username, int reason) throws AuthorizationDeniedException, ApprovalException,
            WaitingForApprovalException, RemoveException, NotFoundException {
        final UserData data = UserData.findByUsername(entityManager, username);
        if (data == null) {
            throw new NotFoundException("User '" + username + "' not found.");
        }
        // Authorized?
        final int caid = data.getCaId();
        assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_ERROR_REVOKEDENDENTITY);
        if (getGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
        	assertAuthorizedToEndEntityProfile(admin, data.getEndEntityProfileId(), AccessRulesConstants.REVOKE_RIGHTS, caid, username, LogConstants.EVENT_ERROR_REVOKEDENDENTITY);
        }
        try {
            if (data.getStatus() != UserDataConstants.STATUS_REVOKED) {
                // Check if approvals is required.
                final int numOfReqApprovals = getNumOfApprovalRequired(admin, CAInfo.REQ_APPROVAL_REVOCATION, caid, data.getCertificateProfileId());
                if (numOfReqApprovals > 0) {
                    final RevocationApprovalRequest ar = new RevocationApprovalRequest(true, username, reason, admin, numOfReqApprovals, caid, data.getEndEntityProfileId());
                    if (ApprovalExecutorUtil.requireApproval(ar, NONAPPROVABLECLASSNAMES_REVOKEANDDELETEUSER)) {
                        approvalSession.addApprovalRequest(admin, ar, getGlobalConfiguration(admin));
                        throw new WaitingForApprovalException(intres.getLocalizedMessage("ra.approvalrevoke"));
                    }
                }
                try {
                    revokeUser(admin, username, reason);
                } catch (AlreadyRevokedException e) {
                    // This just means that the end entity was revoked before
                    // this request could be completed. No harm.
                }
            }
        } catch (FinderException e) {
            throw new NotFoundException("User " + username + "not found.");
        }
        deleteUser(admin, username);
    }

    private static final ApprovalOveradableClassName[] NONAPPROVABLECLASSNAMES_REVOKEUSER = {
            new ApprovalOveradableClassName(org.ejbca.core.ejb.ra.UserAdminSessionBean.class.getName(), "revokeAndDeleteUser"),
            new ApprovalOveradableClassName(org.ejbca.core.model.approval.approvalrequests.RevocationApprovalRequest.class.getName(), null), };

    @Override
    public void revokeUser(Admin admin, String username, int reason) throws AuthorizationDeniedException, FinderException, ApprovalException,
            WaitingForApprovalException, AlreadyRevokedException {
        if (log.isTraceEnabled()) {
            log.trace(">revokeUser(" + username + ")");
        }
        final UserData userData = UserData.findByUsername(entityManager, username);
        if (userData == null) {
            throw new FinderException("Could not find user " + username);
        }
        final int caid = userData.getCaId();
        assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_ERROR_REVOKEDENDENTITY);
        if (getGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
        	assertAuthorizedToEndEntityProfile(admin, userData.getEndEntityProfileId(), AccessRulesConstants.REVOKE_RIGHTS, caid, username, LogConstants.EVENT_ERROR_REVOKEDENDENTITY);
        }
        if (userData.getStatus() == UserDataConstants.STATUS_REVOKED) {
        	final String msg = intres.getLocalizedMessage("ra.errorbadrequest", Integer.valueOf(userData.getEndEntityProfileId()));
            logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_REVOKEDENDENTITY, msg);
            throw new AlreadyRevokedException(msg);
        }
        // Check if approvals is required.
        final int numOfReqApprovals = getNumOfApprovalRequired(admin, CAInfo.REQ_APPROVAL_REVOCATION, caid, userData.getCertificateProfileId());
        if (numOfReqApprovals > 0) {
        	final RevocationApprovalRequest ar = new RevocationApprovalRequest(false, username, reason, admin, numOfReqApprovals, caid, userData.getEndEntityProfileId());
            if (ApprovalExecutorUtil.requireApproval(ar, NONAPPROVABLECLASSNAMES_REVOKEUSER)) {
                approvalSession.addApprovalRequest(admin, ar, getGlobalConfiguration(admin));
                throw new WaitingForApprovalException(intres.getLocalizedMessage("ra.approvalrevoke"));
            }
        }
        // Revoke all certs, one at the time
        final Collection<Certificate> certs = certificateStoreSession.findCertificatesByUsername(admin, username);
        for (final Certificate cert : certs) {
            try {
                revokeCert(admin, CertTools.getSerialNumber(cert), CertTools.getIssuerDN(cert), reason);
            } catch (AlreadyRevokedException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Certificate from issuer '" + CertTools.getIssuerDN(cert) + "' with serial " + CertTools.getSerialNumber(cert)
                            + " was already revoked.");
                }
            }
        }
        // Finally set revoke status on the user as well
        try {
            setUserStatus(admin, userData, UserDataConstants.STATUS_REVOKED);
        } catch (ApprovalException e) {
            throw new EJBException("This should never happen", e);
        } catch (WaitingForApprovalException e) {
            throw new EJBException("This should never happen", e);
        }
        final String msg = intres.getLocalizedMessage("ra.revokedentity", username);
        logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_REVOKEDENDENTITY, msg);
        if (log.isTraceEnabled()) {
            log.trace("<revokeUser()");
        }
    }

    private static final ApprovalOveradableClassName[] NONAPPROVABLECLASSNAMES_REVOKECERT = { new ApprovalOveradableClassName(
            org.ejbca.core.model.approval.approvalrequests.RevocationApprovalRequest.class.getName(), null), };

    @Override
    public void revokeCert(final Admin admin, final BigInteger certserno, final String issuerdn, final int reason) throws AuthorizationDeniedException,
            FinderException, ApprovalException, WaitingForApprovalException, AlreadyRevokedException {
    	try {
			revokeCert(admin, certserno, null, issuerdn, reason, false);
		} catch (RevokeBackDateNotAllowedForProfileException e) {
			throw new Error("This is should not happen since there is no back dating.",e);
		}
    }

    @Override
    public void revokeCert(Admin admin, BigInteger certserno, Date revocationdate, String issuerdn, int reason, boolean checkDate) throws AuthorizationDeniedException, FinderException, WaitingForApprovalException, RevokeBackDateNotAllowedForProfileException, AlreadyRevokedException, ApprovalException {
        if (log.isTraceEnabled()) {
            log.trace(">revokeCert(" + certserno.toString(16) + ", IssuerDN: " + issuerdn + ")");
        }
        // Check that the admin has revocation rights.
        if (!authorizationSession.isAuthorizedNoLog(admin, AccessRulesConstants.REGULAR_REVOKEENDENTITY)) {
            Authorizer.throwAuthorizationException(admin, AccessRulesConstants.REGULAR_REVOKEENDENTITY, null);
        }
        // To be fully backwards compatible we just use the first fingerprint found..
        final CertificateInfo info = certificateStoreSession.findFirstCertificateInfo(issuerdn, certserno);
        if (info == null) {
        	final String msg = intres.getLocalizedMessage("ra.errorfindentitycert", issuerdn, certserno.toString(16));
            logSession.log(admin, LogConstants.INTERNALCAID, LogConstants.MODULE_RA, new Date(), null, null, LogConstants.EVENT_INFO_REVOKEDENDENTITY, msg);
            throw new FinderException(msg);
        }
        final int caid = info.getIssuerDN().hashCode();
        final String username = info.getUsername();
        assertAuthorizedToCA(admin, caid, username, LogConstants.EVENT_ERROR_REVOKEDENDENTITY);
        int certificateProfileId = info.getCertificateProfileId();
        String userDataDN = info.getSubjectDN();
        final CertReqHistory certReqHistory = certificateStoreSession.getCertReqHistory(admin, certserno, issuerdn);
        UserData data = null;
        if (certReqHistory == null || XkmsConfiguration.getEnabled()) {
        	// We could use userdata later, so try to find it
        	data = UserData.findByUsername(entityManager, username);
        }
        int endEntityProfileId = -1;
        if (certReqHistory != null) {
            // Get the EEP that was used in the original issuance, if we can find it
        	endEntityProfileId = certReqHistory.getUserDataVO().getEndEntityProfileId();
            // Republish with the same user DN that was used in the original publication, if we can find it
            userDataDN = certReqHistory.getUserDataVO().getCertificateDN();
            // If for some reason the certificate profile id was not set in the certificate data, try to get it from the certreq history
            if (certificateProfileId == SecConst.CERTPROFILE_NO_PROFILE) {
                certificateProfileId = certReqHistory.getUserDataVO().getCertificateProfileId();
            }
        } else if (data != null) {
        	// Get the EEP that is currently used as a fallback, if we can find it
        	endEntityProfileId = data.getEndEntityProfileId();
        	// Republish with the same user DN that is currently used as a fallback, if we can find it
        	userDataDN = data.toUserDataVO().getCertificateDN();
            // If for some reason the certificate profile id was not set in the certificate data, try to get it from current userdata
            if (certificateProfileId == SecConst.CERTPROFILE_NO_PROFILE) {
                certificateProfileId = data.getCertificateProfileId();
            }
        }
        if (endEntityProfileId != -1) {
        	// We can only perform this check if we have a trail of what eep was used..
            if (getGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
            	assertAuthorizedToEndEntityProfile(admin, endEntityProfileId, AccessRulesConstants.REVOKE_RIGHTS, caid, username, LogConstants.EVENT_ERROR_REVOKEDENDENTITY);
            }
        }
        // Check that unrevocation is not done on anything that can not be unrevoked
        if (reason == RevokedCertInfo.NOT_REVOKED || reason == RevokedCertInfo.REVOCATION_REASON_REMOVEFROMCRL) {
            if (info.getRevocationReason() != RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD) {
            	final String msg = intres.getLocalizedMessage("ra.errorunrevokenotonhold", issuerdn, certserno.toString(16));
                logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_REVOKEDENDENTITY, msg);
                throw new AlreadyRevokedException(msg);
            }
        } else {
            if (    info.getRevocationReason()!=RevokedCertInfo.NOT_REVOKED &&
                    // a valid certificate could have reason "REVOCATION_REASON_REMOVEFROMCRL" if it has been revoked in the past.
                    info.getRevocationReason()!=RevokedCertInfo.REVOCATION_REASON_REMOVEFROMCRL ) {
            	final String msg = intres.getLocalizedMessage("ra.errorrevocationexists");
                logSession.log(admin, caid, LogConstants.MODULE_RA, new Date(), username, null, LogConstants.EVENT_INFO_REVOKEDENDENTITY, msg);
                throw new AlreadyRevokedException(msg);
            }
        }
        if (endEntityProfileId!=-1 && certificateProfileId!=SecConst.CERTPROFILE_NO_PROFILE) {
        	// We can only perform this check if we have a trail of what eep and cp was used..
            // Check if approvals is required.
            final int numOfReqApprovals = getNumOfApprovalRequired(admin, CAInfo.REQ_APPROVAL_REVOCATION, caid, certificateProfileId);
            if (numOfReqApprovals > 0) {
                final RevocationApprovalRequest ar = new RevocationApprovalRequest(certserno, issuerdn, username, reason, admin, numOfReqApprovals, caid, endEntityProfileId);
                if (ApprovalExecutorUtil.requireApproval(ar, NONAPPROVABLECLASSNAMES_REVOKECERT)) {
                    approvalSession.addApprovalRequest(admin, ar, getGlobalConfiguration(admin));
                    throw new WaitingForApprovalException(intres.getLocalizedMessage("ra.approvalrevoke"));
                }
            }
        }
        // Finally find the publishers for the certificate profileId that we found
        Collection<Integer> publishers = new ArrayList<Integer>(0);
        final CertificateProfile certificateProfile = certificateProfileSession.getCertificateProfile(admin, certificateProfileId);
        if (certificateProfile != null) {
            publishers = certificateProfile.getPublisherList();
            if ( publishers==null || publishers.size()==0 ) {
            	if (log.isDebugEnabled()) {
            		log.debug("No publishers defined for certificate with serial #"+certserno.toString(16)+ " issued by "+issuerdn);
            	}
            }
        } else {
            log.warn("No certificate profile for certificate with serial #"+certserno.toString(16)+" issued by "+issuerdn);
        }
        if ( checkDate && revocationdate!=null && (certificateProfile==null || !certificateProfile.getAllowBackdatedRevocation()) ) {
        	final String profileName = this.certificateProfileSession.getCertificateProfileName(admin, certificateProfileId);
        	final String m = intres.getLocalizedMessage("ra.norevokebackdate", profileName, certserno.toString(16), issuerdn);
        	throw new RevokeBackDateNotAllowedForProfileException(m);
        }
        // Revoke certificate in database and all publishers
        this.certificateStoreSession.setRevokeStatus(admin, issuerdn, certserno, revocationdate!=null ? revocationdate : new Date(), publishers, reason, userDataDN);
        if (XkmsConfiguration.getEnabled() && data != null) {
        	// Reset the revocation code identifier used in XKMS
        	final ExtendedInformation inf = data.getExtendedInformation();
        	if (inf != null && inf.getRevocationCodeIdentifier() != null) {
        		inf.setRevocationCodeIdentifier(null);
        		data.setExtendedInformation(inf);
        	}
        }
        if (log.isTraceEnabled()) {
            log.trace("<revokeCert()");
        }
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public Admin getAdmin(Certificate certificate) {
        String adminUsername = certificateStoreSession.findUsernameByCertSerno(Admin.getInternalAdmin(), CertTools.getSerialNumber(certificate),
                CertTools.getIssuerDN(certificate));
        String adminEmail = null;
        if (adminUsername != null) {
        	adminEmail = UserData.findSubjectEmailByUsername(entityManager, adminUsername);
        }
        return new Admin(certificate, adminUsername, adminEmail);
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public UserDataVO findUser(final Admin admin, final String username) throws AuthorizationDeniedException {
        if (log.isTraceEnabled()) {
            log.trace(">findUser(" + username + ")");
        }
        final UserData data = UserData.findByUsername(entityManager, username);
        if (data == null) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot find user with username='" + username + "'");
            }
        }
        final UserDataVO ret = returnUserDataVO(admin, data, username);
        if (log.isTraceEnabled()) {
            log.trace("<findUser(" + username + "): " + (ret == null ? "null" : ret.getDN()));
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public UserDataVO findUserBySubjectAndIssuerDN(final Admin admin, final String subjectdn, final String issuerdn) throws AuthorizationDeniedException {
        if (log.isTraceEnabled()) {
            log.trace(">findUserBySubjectAndIssuerDN(" + subjectdn + ", " + issuerdn + ")");
        }
        // String used in SQL so strip it
        final String dn = CertTools.stringToBCDNString(StringTools.strip(subjectdn));
        if (log.isDebugEnabled()) {
            log.debug("Looking for users with subjectdn: " + dn + ", issuerdn : " + issuerdn);
        }
        final UserData data = UserData.findBySubjectDNAndCAId(entityManager, dn, issuerdn.hashCode());
        if (data == null) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot find user with subjectdn: " + dn + ", issuerdn : " + issuerdn);
            }
        }
        final UserDataVO returnval = returnUserDataVO(admin, data, null);
        if (log.isTraceEnabled()) {
            log.trace("<findUserBySubjectAndIssuerDN(" + subjectdn + ", " + issuerdn + ")");
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public UserDataVO findUserBySubjectDN(final Admin admin, final String subjectdn) throws AuthorizationDeniedException {
        if (log.isTraceEnabled()) {
            log.trace(">findUserBySubjectDN(" + subjectdn + ")");
        }
        // String used in SQL so strip it
        final String dn = CertTools.stringToBCDNString(StringTools.strip(subjectdn));
        if (log.isDebugEnabled()) {
            log.debug("Looking for users with subjectdn: " + dn);
        }
        final UserData data = UserData.findBySubjectDN(entityManager, dn);
        if (data == null) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot find user with subjectdn: " + dn);
            }
        }
        final UserDataVO returnval = returnUserDataVO(admin, data, null);
        if (log.isTraceEnabled()) {
            log.trace("<findUserBySubjectDN(" + subjectdn + ")");
        }
        return returnval;
    }

    /** @return the userdata value object if admin is authorized. Does not leak username if auth fails. */
    private UserDataVO returnUserDataVO(final Admin admin, final UserData data, final String requestedUsername) throws AuthorizationDeniedException {
        if (data != null) {
            if (getGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
                // Check if administrator is authorized to view user.
                if (!authorizedToEndEntityProfile(admin, data.getEndEntityProfileId(), AccessRulesConstants.VIEW_RIGHTS)) {
                	if (requestedUsername == null) {
                    	final String msg = intres.getLocalizedMessage("ra.errorauthprofile", Integer.valueOf(data.getEndEntityProfileId()));
                        throw new AuthorizationDeniedException(msg);
                	} else {
                        final String msg = intres.getLocalizedMessage("ra.errorauthprofileexist", Integer.valueOf(data.getEndEntityProfileId()), requestedUsername);
                        throw new AuthorizationDeniedException(msg);
                	}
                }
            }
            if (!authorizedToCA(admin, data.getCaId())) {
            	if (requestedUsername == null) {
                    final String msg = intres.getLocalizedMessage("ra.errorauthca", Integer.valueOf(data.getCaId()));
                    throw new AuthorizationDeniedException(msg);
            	} else {
            		final String msg = intres.getLocalizedMessage("ra.errorauthcaexist", Integer.valueOf(data.getCaId()), requestedUsername);
                    throw new AuthorizationDeniedException(msg);
            	}
            }
            return data.toUserDataVO();
        }
        return null;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public List<UserDataVO> findUserByEmail(Admin admin, String email) throws AuthorizationDeniedException {
        if (log.isTraceEnabled()) {
            log.trace(">findUserByEmail(" + email + ")");
        }
        if (log.isDebugEnabled()) {
            log.debug("Looking for user with email: " + email);
        }
        final List<UserData> result = UserData.findBySubjectEmail(entityManager, email);
        if (result.size() == 0) {
            if (log.isDebugEnabled()) {
                log.debug("Cannot find user with Email='" + email + "'");
            }
        }
        final List<UserDataVO> returnval = new ArrayList<UserDataVO>();
        for (final UserData data : result) {
            if (getGlobalConfiguration(admin).getEnableEndEntityProfileLimitations()) {
                // Check if administrator is authorized to view user.
                if (!authorizedToEndEntityProfile(admin, data.getEndEntityProfileId(), AccessRulesConstants.VIEW_RIGHTS)) {
                    continue;
                }
            }
            if (!authorizedToCA(admin, data.getCaId())) {
            	continue;
            }
            returnval.add(data.toUserDataVO());
        }
        if (log.isTraceEnabled()) {
            log.trace("<findUserByEmail(" + email + ")");
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public void checkIfCertificateBelongToUser(Admin admin, BigInteger certificatesnr, String issuerdn) throws AuthorizationDeniedException {
        if (log.isTraceEnabled()) {
            log.trace(">checkIfCertificateBelongToUser(" + certificatesnr.toString(16) + ")");
        }
        if (!WebConfiguration.getRequireAdminCertificateInDatabase()) {
            if (log.isTraceEnabled()) {
                log.trace("<checkIfCertificateBelongToUser Configured to ignore if cert belongs to user.");
            }
            return;
        }
        String username = certificateStoreSession.findUsernameByCertSerno(admin, certificatesnr, issuerdn);
        if (username != null) {
            if (UserData.findByUsername(entityManager, username) == null) {
                String msg = intres.getLocalizedMessage("ra.errorcertnouser", issuerdn, certificatesnr.toString(16));
                logSession.log(admin, LogConstants.INTERNALCAID, LogConstants.MODULE_RA, new Date(), null, null,
                        LogConstants.EVENT_ERROR_ADMINISTRATORLOGGEDIN, msg);
                throw new AuthorizationDeniedException(msg);
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("<checkIfCertificateBelongToUser()");
        }
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public Collection<UserDataVO> findAllUsersByStatus(Admin admin, int status) throws FinderException {
        if (log.isTraceEnabled()) {
            log.trace(">findAllUsersByStatus(" + status + ")");
        }
        if (log.isDebugEnabled()) {
            log.debug("Looking for users with status: " + status);
        }
        Query query = new Query(Query.TYPE_USERQUERY);
        query.add(UserMatch.MATCH_WITH_STATUS, BasicMatch.MATCH_TYPE_EQUALS, Integer.toString(status));
        Collection<UserDataVO> returnval = null;
        try {
            returnval = query(admin, query, false, null, null, 0);
        } catch (IllegalQueryException e) {
        }
        if (log.isDebugEnabled()) {
            log.debug("found " + returnval.size() + " user(s) with status=" + status);
        }
        if (log.isTraceEnabled()) {
            log.trace("<findAllUsersByStatus(" + status + ")");
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public Collection<UserDataVO> findAllUsersByCaId(Admin admin, int caid) {
        if (log.isTraceEnabled()) {
            log.trace(">findAllUsersByCaId(" + caid + ")");
        }
        if (log.isDebugEnabled()) {
            log.debug("Looking for users with caid: " + caid);
        }
        Query query = new Query(Query.TYPE_USERQUERY);
        query.add(UserMatch.MATCH_WITH_CA, BasicMatch.MATCH_TYPE_EQUALS, Integer.toString(caid));
        Collection<UserDataVO> returnval = null;
        try {
            returnval = query(admin, query, false, null, null, 0);
        } catch (IllegalQueryException e) {
            // Ignore ??
            log.debug("Illegal query", e);
            returnval = new ArrayList<UserDataVO>();
        }
        if (log.isDebugEnabled()) {
            log.debug("found " + returnval.size() + " user(s) with caid=" + caid);
        }
        if (log.isTraceEnabled()) {
            log.trace("<findAllUsersByCaId(" + caid + ")");
        }
        return returnval;
    }
    
    @SuppressWarnings("unchecked")
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public List<UserDataVO> findUsers(List<Integer> caIds, long timeModified, int status) {
        String queryString = "SELECT a FROM UserData a WHERE (a.timeModified <=:timeModified) AND (a.status=:status)";
        if(caIds.size() > 0) {
            queryString += " AND (a.caId=:caId0";
            for(int i = 1; i < caIds.size(); i++) {
                queryString += " OR a.caId=:caId" + i;
            }
            queryString += ")";
        }     
        if(log.isDebugEnabled()) {
            log.debug("Checking for "+caIds.size()+" CAs");
            log.debug("Generated query string: "+queryString);
        }             
        javax.persistence.Query query = entityManager.createQuery(queryString);
        query.setParameter("timeModified", timeModified);
        query.setParameter("status", status);      
        if(caIds.size() > 0) {           
            for(int i = 0; i < caIds.size(); i++) {
                query.setParameter("caId" + i, caIds.get(i));
            }
        }
        final List<UserData> queryResult = (List<UserData>) query.getResultList();
        final List<UserDataVO> ret = new ArrayList<UserDataVO>(queryResult.size());
        for (UserData userData : queryResult) {
            ret.add(userData.toUserDataVO());
        }
        return ret;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public Collection<UserDataVO> findAllUsersWithLimit(Admin admin) {
        if (log.isTraceEnabled()) {
            log.trace(">findAllUsersWithLimit()");
        }
        Collection<UserDataVO> returnval = null;
        try {
            returnval = query(admin, null, true, null, null, 0);
        } catch (IllegalQueryException e) {
        }
        if (log.isTraceEnabled()) {
            log.trace("<findAllUsersWithLimit()");
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public List<UserDataVO> findAllBatchUsersByStatusWithLimit(int status) {
        if (log.isTraceEnabled()) {
            log.trace(">findAllUsersByStatusWithLimit()");
        }
        final List<UserData> userDataList = UserData.findAllBatchUsersByStatus(entityManager, status, UserAdminConstants.MAXIMUM_QUERY_ROWCOUNT);
        final List<UserDataVO> returnval = new ArrayList<UserDataVO>(userDataList.size());
        for (UserData ud : userDataList) {
        	UserDataVO userDataVO = ud.toUserDataVO();
    		if (userDataVO.getPassword() != null && userDataVO.getPassword().length() > 0) {
            	returnval.add(userDataVO);
    		}
        }
        if (log.isTraceEnabled()) {
            log.trace("<findAllUsersByStatusWithLimit()");
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public Collection<UserDataVO> query(Admin admin, Query query, String caauthorizationstring, String endentityprofilestring, int numberofrows)
            throws IllegalQueryException {
        return query(admin, query, true, caauthorizationstring, endentityprofilestring, numberofrows);
    }

    /**
     * Help function used to retrieve user information. A query parameter of
     * null indicates all users. If caauthorizationstring or
     * endentityprofilestring are null then the method will retrieve the
     * information itself.
     * 
     * @param numberofrows
     *            the number of rows to fetch, use 0 for default
     *            UserAdminConstants.MAXIMUM_QUERY_ROWCOUNT
     */
    private Collection<UserDataVO> query(Admin admin, Query query, boolean withlimit, String caauthorizationstr, String endentityprofilestr, int numberofrows) throws IllegalQueryException {
        if (log.isTraceEnabled()) {
            log.trace(">query(): withlimit=" + withlimit);
        }
        boolean authorizedtoanyprofile = true;
        String caauthorizationstring = StringTools.strip(caauthorizationstr);
        String endentityprofilestring = StringTools.strip(endentityprofilestr);
        ArrayList<UserDataVO> returnval = new ArrayList<UserDataVO>();
        GlobalConfiguration globalconfiguration = getGlobalConfiguration(admin);
        RAAuthorization raauthorization = null;
        String caauthstring = caauthorizationstring;
        String endentityauth = endentityprofilestring;
        String sqlquery = "";
        int fetchsize = UserAdminConstants.MAXIMUM_QUERY_ROWCOUNT;

        if (numberofrows != 0) {
            fetchsize = numberofrows;
        }

        // Check if query is legal.
        if (query != null && !query.isLegalQuery()) {
            throw new IllegalQueryException();
        }

        if (query != null) {
            sqlquery = sqlquery + query.getQueryString();
        }

        if (caauthorizationstring == null || endentityprofilestring == null) {
            raauthorization = new RAAuthorization(admin, globalConfigurationSession, authorizationSession, caSession, endEntityProfileSession);
            caauthstring = raauthorization.getCAAuthorizationString();
            if (globalconfiguration.getEnableEndEntityProfileLimitations()) {
                endentityauth = raauthorization.getEndEntityProfileAuthorizationString(true);
            } else {
                endentityauth = "";
            }
        }

        if (!caauthstring.trim().equals("") && query != null) {
            sqlquery = sqlquery + " AND " + caauthstring;
        } else {
            sqlquery = sqlquery + caauthstring;
        }

        if (globalconfiguration.getEnableEndEntityProfileLimitations()) {
            if (endentityauth == null || endentityauth.trim().equals("")) {
                authorizedtoanyprofile = false;
            } else {
                if (caauthstring.trim().equals("") && query == null) {
                    sqlquery = sqlquery + endentityauth;
                } else {
                    sqlquery = sqlquery + " AND " + endentityauth;
                }            	
            }
        }
        // Finally order the return values
        sqlquery += " ORDER BY " + USERDATA_CREATED_COL + " DESC";
        if (log.isDebugEnabled()) {
            log.debug("generated query: " + sqlquery);
        }
        if (authorizedtoanyprofile) {
        	List<UserData> userDataList = UserData.findByCustomQuery(entityManager, sqlquery, fetchsize+1);
        	for (UserData userData : userDataList) {
    			returnval.add(userData.toUserDataVO());
        	}
        } else {
        	if (log.isDebugEnabled()) {
        		log.debug("authorizedtoanyprofile=false");
        	}
        }
        if (log.isTraceEnabled()) {
        	log.trace("<query(): "+returnval.size());
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public boolean checkForEndEntityProfileId(Admin admin, int endentityprofileid) {
        if (log.isTraceEnabled()) {
        	log.trace(">checkForEndEntityProfileId("+endentityprofileid+")");
        }
        long count = UserData.countByEndEntityProfileId(entityManager, endentityprofileid);
        if (log.isTraceEnabled()) {
        	log.trace("<checkForEndEntityProfileId("+endentityprofileid+"): "+count);
        }
        return count > 0;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public List<String> findByCertificateProfileId(int certificateprofileid) {
        if (log.isTraceEnabled()) {
        	log.trace(">checkForCertificateProfileId("+certificateprofileid+")");
        }
        final javax.persistence.Query query = entityManager.createQuery("SELECT a FROM UserData a WHERE a.certificateProfileId=:certificateProfileId");
        query.setParameter("certificateProfileId", certificateprofileid);

        List<String> result = new ArrayList<String>();
        for(Object userDataObject : query.getResultList()) {
                result.add(((UserData) userDataObject).getUsername());
        }
        if (log.isTraceEnabled()) {
        	log.trace("<checkForCertificateProfileId("+certificateprofileid+"): "+result.size());
        }
        return result;
        
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public boolean checkForCAId(Admin admin, int caid) {
        if (log.isTraceEnabled()) {
            log.trace(">checkForCAId()");
        }
        return UserData.countByCaId(entityManager, caid) > 0;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public boolean checkForHardTokenProfileId(Admin admin, int profileid) {
        if (log.isTraceEnabled()) {
            log.trace(">checkForHardTokenProfileId()");
        }
        return UserData.countByHardTokenProfileId(entityManager, profileid) > 0;
    }

    private void print(Admin admin, EndEntityProfile profile, UserDataVO userdata) {
        try {
            if (profile.getUsePrinting()) {
                String[] pINs = new String[1];
                pINs[0] = userdata.getPassword();
                PrinterManager.print(profile.getPrinterName(), profile.getPrinterSVGFileName(), profile.getPrinterSVGData(), profile.getPrintedCopies(), 0,
                        userdata, pINs, new String[0], "", "", "");
            }
        } catch (PrinterException e) {
            String msg = intres.getLocalizedMessage("ra.errorprint", userdata.getUsername(), e.getMessage());
            log.error(msg, e);
            try {
                logSession.log(admin, userdata.getCAId(), LogConstants.MODULE_RA, new Date(), userdata.getUsername(), null,
                        LogConstants.EVENT_ERROR_NOTIFICATION, msg);
            } catch (Exception f) {
                throw new EJBException(f);
            }
        }
    }

    private void sendNotification(Admin admin, UserDataVO data, int newstatus) {
        if (data == null) {
            if (log.isDebugEnabled()) {
                log.debug("No UserData, no notification sent.");
            }
            return;
        }
        String useremail = data.getEmail();
        if (log.isTraceEnabled()) {
            log.trace(">sendNotification: user=" + data.getUsername() + ", email=" + useremail);
        }

        // Make check if we should send notifications at all
        if (((data.getType() & SecConst.USER_SENDNOTIFICATION) != 0)) {
            int profileId = data.getEndEntityProfileId();
            EndEntityProfile profile = endEntityProfileSession.getEndEntityProfile(admin, profileId);
            Collection<UserNotification> l = profile.getUserNotifications();
            if (log.isDebugEnabled()) {
                log.debug("Number of user notifications: " + l.size());
            }
            Iterator<UserNotification> i = l.iterator();
            String rcptemail = useremail; // Default value
            while (i.hasNext()) {
                UserNotification not = i.next();
                Collection<String> events = not.getNotificationEventsCollection();
                if (events.contains(String.valueOf(newstatus))) {
                    if (log.isDebugEnabled()) {
                        log.debug("Status is " + newstatus + ", notification sent for notificationevents: " + not.getNotificationEvents());
                    }
                    try {
                        if (StringUtils.equals(not.getNotificationRecipient(), UserNotification.RCPT_USER)) {
                            rcptemail = useremail;
                        } else if (StringUtils.contains(not.getNotificationRecipient(), UserNotification.RCPT_CUSTOM)) {
                            rcptemail = "custom"; // Just if this fail it will
                                                  // say that sending to user
                                                  // with email "custom" failed.
                            // Plug-in mechanism for retrieving custom
                            // notification email recipient addresses
                            if (not.getNotificationRecipient().length() < 6) {
                                String msg = intres.getLocalizedMessage("ra.errorcustomrcptshort", not.getNotificationRecipient());
                                log.error(msg);
                            } else {
                                String cp = not.getNotificationRecipient().substring(7);
                                if (StringUtils.isNotEmpty(cp)) {
                                    ICustomNotificationRecipient plugin = (ICustomNotificationRecipient) Thread.currentThread().getContextClassLoader()
                                            .loadClass(cp).newInstance();
                                    rcptemail = plugin.getRecipientEmails(data);
                                    if (StringUtils.isEmpty(rcptemail)) {
                                        String msg = intres.getLocalizedMessage("ra.errorcustomnoemail", not.getNotificationRecipient());
                                        log.error(msg);
                                    } else {
                                        if (log.isDebugEnabled()) {
                                            log.debug("Custom notification recipient plugin returned email: " + rcptemail);
                                        }
                                    }
                                } else {
                                    String msg = intres.getLocalizedMessage("ra.errorcustomnoclasspath", not.getNotificationRecipient());
                                    log.error(msg);
                                }
                            }
                        } else {
                            // Just a plain email address specified in the
                            // recipient field
                            rcptemail = not.getNotificationRecipient();
                        }
                        if (StringUtils.isEmpty(rcptemail)) {
                            String msg = intres.getLocalizedMessage("ra.errornotificationnoemail", data.getUsername());
                            throw new Exception(msg);
                        }
                        // Get the administrators DN from the admin certificate,
                        // if one exists
                        // When approvals is used, this will be the DN of the
                        // admin that approves the request
                        Certificate adminCert = admin.getAdminInformation().getX509Certificate();
                        String approvalAdminDN = CertTools.getSubjectDN(adminCert);
                        if (log.isDebugEnabled()) {
                            log.debug("approvalAdminDN: " + approvalAdminDN);
                        }
                        UserNotificationParamGen paramGen = new UserNotificationParamGen(data, approvalAdminDN, findUser(admin, admin.getUsername()));
                        /*
                         * substitute any $ fields in the receipient and from
                         * fields
                         */
                        rcptemail = paramGen.interpolate(rcptemail);
                        String fromemail = paramGen.interpolate(not.getNotificationSender());
                        String subject = paramGen.interpolate(not.getNotificationSubject());
                        String message = paramGen.interpolate(not.getNotificationMessage());
                        MailSender.sendMailOrThrow(fromemail, Arrays.asList(rcptemail), MailSender.NO_CC, subject, message, MailSender.NO_ATTACHMENTS);
                        String logmsg = intres.getLocalizedMessage("ra.sentnotification", data.getUsername(), rcptemail);
                        logSession.log(admin, data.getCAId(), LogConstants.MODULE_RA, new Date(), data.getUsername(), null,
                                LogConstants.EVENT_INFO_NOTIFICATION, logmsg);
                    } catch (Exception e) {
                        String msg = intres.getLocalizedMessage("ra.errorsendnotification", data.getUsername(), rcptemail);
                        log.error(msg, e);
                        try {
                            logSession.log(admin, data.getCAId(), LogConstants.MODULE_RA, new Date(), data.getUsername(), null,
                                    LogConstants.EVENT_ERROR_NOTIFICATION, msg);
                        } catch (Exception f) {
                            throw new EJBException(f);
                        }
                    }
                } else { // if (events.contains(String.valueOf(newstatus)))
                    if (log.isDebugEnabled()) {
                        log.debug("Status is " + newstatus + ", no notification sent for notificationevents: " + not.getNotificationEvents());
                    }
                }
            }
        } else { // if ( ((data.getType() & SecConst.USER_SENDNOTIFICATION) !=
                 // 0) )
            if (log.isDebugEnabled()) {
                log.debug("Type does not contain SecConst.USER_SENDNOTIFICATION, no notification sent.");
            }
        }
        if (log.isTraceEnabled()) {
            log.trace("<sendNotification: user=" + data.getUsername() + ", email=" + useremail);
        }
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public boolean existsUser(Admin admin, String username) {
        boolean returnval = true;
        if (UserData.findByUsername(entityManager, username) == null) {
            returnval = false;
        }
        return returnval;
    }

    @Override
    public boolean prepareForKeyRecovery(Admin admin, String username, int endEntityProfileId, Certificate certificate) throws AuthorizationDeniedException,
            ApprovalException, WaitingForApprovalException {
        boolean ret;
        GlobalConfiguration gc = globalConfigurationSession.getCachedGlobalConfiguration(admin);
        if (certificate == null) {
            ret = keyRecoverySession.markNewestAsRecoverable(admin, username, endEntityProfileId, gc);
        } else {
            ret = keyRecoverySession.markAsRecoverable(admin, certificate, endEntityProfileId, gc);
        }
        try {
            setUserStatus(admin, username, UserDataConstants.STATUS_KEYRECOVERY);
        } catch (FinderException e) {
            ret = false;
            log.info("prepareForKeyRecovery: No such user: " + username);
        }
        return ret;
    }

    //
    // Private helper methods
    //
    /**
     * re-sets the optional request counter of a user to the default value
     * specified by the end entity profile. If the profile does not specify that
     * request counter should be used, the counter is removed.
     * 
     * @param admin administrator
     * @param ei the ExtendedInformation object to modify
     */
    private void resetRequestCounter(final Admin admin, final boolean onlyRemoveNoUpdate, final ExtendedInformation ei, final String username, final int endEntityProfileId) {
        if (log.isTraceEnabled()) {
            log.trace(">resetRequestCounter(" + username + ", " + onlyRemoveNoUpdate + ")");
        }
        final EndEntityProfile prof = endEntityProfileSession.getEndEntityProfile(admin, endEntityProfileId);
        String value = null;
        if (prof != null) {
        	if (prof.getUse(EndEntityProfile.ALLOWEDREQUESTS, 0)) {
        		value = prof.getValue(EndEntityProfile.ALLOWEDREQUESTS, 0);
        	}
        } else {
        	if (log.isDebugEnabled()) {
        		log.debug("Can not fetch entity profile with id " + endEntityProfileId);
        	}
        }
        final String counter = ei.getCustomData(ExtendedInformation.CUSTOM_REQUESTCOUNTER);
        if (log.isDebugEnabled()) {
        	log.debug("Old counter is: " + counter + ", new counter will be: " + value);
        }
        // If this end entity profile does not use ALLOWEDREQUESTS, this
        // value will be set to null
        // We only re-set this value if the COUNTER was used in the first
        // place, if never used, we will not fiddle with it
        if (counter != null) {
        	if ((!onlyRemoveNoUpdate) || (onlyRemoveNoUpdate && (value == null))) {
        		ei.setCustomData(ExtendedInformation.CUSTOM_REQUESTCOUNTER, value);
        		if (log.isDebugEnabled()) {
        			log.debug("Re-set request counter for user '" + username + "' to:" + value);
        		}
        	} else {
        		if (log.isDebugEnabled()) {
        			log.debug("No re-setting counter because we should only remove");
        		}
        	}
        } else {
        	if (log.isDebugEnabled()) {
        		log.debug("Request counter not used, not re-setting it.");
        	}
        }
        if (log.isTraceEnabled()) {
            log.trace("<resetRequestCounter(" + username + ", " + onlyRemoveNoUpdate + ")");
        }
    }
}
