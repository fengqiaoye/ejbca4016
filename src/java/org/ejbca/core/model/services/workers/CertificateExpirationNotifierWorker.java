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
package org.ejbca.core.model.services.workers;

import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.CaSessionLocal;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionLocal;
import org.ejbca.core.ejb.ra.UserAdminSessionLocal;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.core.model.ra.UserNotificationParamGen;
import org.ejbca.core.model.services.ServiceExecutionFailedException;
import org.ejbca.core.model.services.actions.MailActionInfo;

/**
 * Makes queries about which certificates that is about to expire in a given
 * number of days and creates an notification sent to either the end user or the
 * administrator.
 * 
 * 
 * @version: $Id: CertificateExpirationNotifierWorker.java 15108 2012-07-06 12:25:48Z mikekushner $
 */
public class CertificateExpirationNotifierWorker extends EmailSendingWorker {

    private static final Logger log = Logger.getLogger(CertificateExpirationNotifierWorker.class);
    /** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();
    
    private CertificateStoreSessionLocal certificateStoreSession;
    private transient List<Integer>  certificateProfileIds;
    
    /**
     * Worker that makes a query to the Certificate Store about expiring
     * certificates.
     * 
     * @see org.ejbca.core.model.services.IWorker#work()
     */
    public void work(Map<Class<?>, Object> ejbs) throws ServiceExecutionFailedException {
        log.trace(">CertificateExpirationNotifierWorker.work started");
        final CaSessionLocal caSession = (CaSessionLocal) ejbs.get(CaSessionLocal.class);
        final CAAdminSessionLocal caAdminSession = ((CAAdminSessionLocal)ejbs.get(CAAdminSessionLocal.class));
        certificateStoreSession = ((CertificateStoreSessionLocal)ejbs.get(CertificateStoreSessionLocal.class));
        final UserAdminSessionLocal userAdminSession = ((UserAdminSessionLocal)ejbs.get(UserAdminSessionLocal.class));

        ArrayList<EmailCertData> userEmailQueue = new ArrayList<EmailCertData>();
        ArrayList<EmailCertData> adminEmailQueue = new ArrayList<EmailCertData>();

        // Build Query
        String cASelectString = "";
        Collection<Integer> caIds = getCAIdsToCheck(false);
        Collection<Integer> certificateProfileIds = getCertificateProfileIdsToCheck();
        if (!caIds.isEmpty()) {
            //if caIds contains SecConst.ALLCAS, reassign caIds to contain just that.
            if(caIds.contains(SecConst.ALLCAS)) {
                caIds = caSession.getAvailableCAs();
            }
            for(Integer caid : caIds) {
                CAInfo caInfo = caAdminSession.getCAInfo(getAdmin(), caid);
                if (caInfo == null) {
                    String msg = intres.getLocalizedMessage("services.errorworker.errornoca", caid, null);
                    log.info(msg);
                    continue;
                } 
                String cadn = caInfo.getSubjectDN();
                if (cASelectString.equals("")) {
                    cASelectString = "issuerDN='" + cadn + "' ";
                } else {
                    cASelectString += " OR issuerDN='" + cadn + "' ";
                }
            }

            /*
             * Algorithm:
             * 
             * Inputs: CertificateData.status Which either is ACTIVE or
             * NOTIFIEDABOUTEXPIRATION in order to be candidates for
             * notifications.
             * 
             * nextRunTimestamp Tells when the next service run will be
             * 
             * currRunTimestamp Tells when the service should run (usually "now"
             * but there may be delayed runs as well if the app-server has been
             * down)
             * 
             * thresHold The configured "threshold"
             * 
             * We want to accomplish two things:
             * 
             * 1. Notify for expirations within the service window 2. Notify
             * _once_ for expirations that occurred before the service window
             * like flagging certificates that have a shorter life-span than the
             * threshold (pathologic test-case...)
             * 
             * The first is checked by:
             * 
             * notify = currRunTimestamp + thresHold <= ExpireDate <
             * nextRunTimestamp + thresHold AND (status = ACTIVE OR status =
             * NOTIFIEDABOUTEXPIRATION)
             * 
             * The second can be checked by:
             * 
             * notify = currRunTimestamp + thresHold > ExpireDate AND status =
             * ACTIVE
             * 
             * In both case status can be set to NOTIFIEDABOUTEXPIRATION
             * 
             * As Tomas pointed out we do not need to flag certificates that
             * have expired already which is a separate test.
             */

            long thresHold = getTimeBeforeExpire();
            long now = new Date().getTime();
            if (!cASelectString.equals("")) {
                try {
                    List<Object[]> fingerprintUsernameList = certificateStoreSession.findExpirationInfo(cASelectString, certificateProfileIds, now, (nextRunTimeStamp + thresHold), (runTimeStamp + thresHold));
                    int count = 0;
                    for (Object[] next : fingerprintUsernameList) {
                        count++;
                        // For each certificate update status.
                        String fingerprint = (String) next[0];
                        String username = (String) next[1];
                        // Get the certificate through a session bean
                        log.debug("Found a certificate we should notify. Username=" + username + ", fp=" + fingerprint);
                        Certificate cert = certificateStoreSession.findCertificateByFingerprint(new Admin(Admin.TYPE_INTERNALUSER), fingerprint);
                        UserDataVO userData = userAdminSession.findUser(getAdmin(), username);
                        if (userData != null) {
                            if (isSendToEndUsers()) {
                                if (userData.getEmail() == null || userData.getEmail().trim().equals("")) {
                                    String msg = intres.getLocalizedMessage("services.errorworker.errornoemail", username);
                                    log.info(msg);
                                } else {
                                    // Populate end user message
                                    log.debug("Adding to email queue for user: " + userData.getEmail());
                                    String message = new UserNotificationParamGen(userData, cert).interpolate(getEndUserMessage());
                                    MailActionInfo mailActionInfo = new MailActionInfo(userData.getEmail(), getEndUserSubject(), message);
                                    userEmailQueue.add(new EmailCertData(fingerprint, mailActionInfo));
                                }
                            }
                        } else {
                            log.debug("Trying to send notification to user, but no UserData can be found for user '" + username
                                    + "', will only send to admin if admin notifications are defined.");
                        }
                        if (isSendToAdmins()) {
                            // If we did not have any user for this, we will simply use empty values for substitution
                            if (userData == null) {
                                userData = new UserDataVO();
                                userData.setUsername(username);
                            }
                            // Populate admin message
                            log.debug("Adding to email queue for admin");
                            String message = new UserNotificationParamGen(userData, cert).interpolate(getAdminMessage());
                            MailActionInfo mailActionInfo = new MailActionInfo(null, getAdminSubject(), message);
                            adminEmailQueue.add(new EmailCertData(fingerprint, mailActionInfo));
                        }
                        if (!isSendToEndUsers() && !isSendToAdmins()) {
                            // a little bit of a kludge to make JUnit testing feasible...
                            log.debug("nobody to notify for cert with fp:" + fingerprint);
                            updateStatus(fingerprint, SecConst.CERT_NOTIFIEDABOUTEXPIRATION);
                        }
                    }
                    if (count == 0) {
                        log.debug("No certificates found for notification.");
                    }

                } catch (Exception fe) {
                    log.error("Error running service work: ", fe);
                    throw new ServiceExecutionFailedException(fe);
                }
                if (isSendToEndUsers()) {
                    sendEmails(userEmailQueue, ejbs);
                }
                if (isSendToAdmins()) {
                    sendEmails(adminEmailQueue, ejbs);
                }
            }

        } else {
            log.debug("No CAs to check");
        }
        log.trace("<CertificateExpirationNotifierWorker.work ended");
    }

    /**
     * Method that must be implemented by all subclasses to EmailSendingWorker,
     * used to update status of a certificate, user, or similar
     * 
     * @param pk primary key of object to update
     * @param status status to update to
     */
	@Override
    protected void updateStatus(String pk, int status) {
    	if (!certificateStoreSession.setStatus(pk, status)) {
            log.error("Error updating certificate status for certificate with fingerprint: " + pk);
    	}
    }
	
	 /**
     * Returns the Set of Certificate Profile IDs. For performance reasons cached as a 
     * transient class variable.
     * 
     * @return
     */
    private Collection<Integer>getCertificateProfileIdsToCheck() {
        if (this.certificateProfileIds == null) {
            this.certificateProfileIds = new ArrayList<Integer>();
            String idString = properties.getProperty(PROP_CERTIFICATE_PROFILE_IDS_TO_CHECK);
            if (idString != null) {
                for (String id : idString.split(";")) {
                    certificateProfileIds.add(Integer.valueOf(id));
                }
            }
        }
        return certificateProfileIds;
    }
}
