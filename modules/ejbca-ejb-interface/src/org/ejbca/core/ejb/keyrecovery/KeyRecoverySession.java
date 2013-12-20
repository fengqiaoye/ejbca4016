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
package org.ejbca.core.ejb.keyrecovery;

import java.security.KeyPair;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import org.ejbca.config.GlobalConfiguration;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.log.Admin;

/**
 * @version $Id: KeyRecoverySession.java 11526 2011-03-16 12:03:24Z netmackan $
 */
public interface KeyRecoverySession {

	/**
     * Adds a certificates keyrecovery data to the database.
     *
     * @param admin the administrator calling the function
     * @param certificate the certificate used with the keypair.
     * @param username of the administrator
     * @param keypair the actual keypair to save.
     *
     * @return false if the certificates keyrecovery data already exists.
     */
    public boolean addKeyRecoveryData(Admin admin, Certificate certificate, String username, KeyPair keypair);

    /**
     * Updates keyrecovery data
     *
     * @param admin the administrator calling the function
     * @param certificate the certificate used with the keypair.
     * @param markedasrecoverable DOCUMENT ME!
     * @param keypair the actual keypair to save.
     *
     * @return false if certificates keyrecovery data doesn't exists
     *
     * @throws javax.ejb.EJBException if a communication or other error occurs.
     */
    public boolean changeKeyRecoveryData(Admin admin, X509Certificate certificate, boolean markedasrecoverable, KeyPair keypair);

    /**
     * Removes a certificates keyrecovery data from the database.
     *
     * @param admin the administrator calling the function
     * @param certificate the certificate used with the keys about to be removed.
     */
    public void removeKeyRecoveryData(Admin admin, Certificate certificate);

    /** Removes a all keyrecovery data saved for a user from the database. */
    public void removeAllKeyRecoveryData(Admin admin, String username);

    /**
     * Returns the keyrecovery data for a user. Observe only one certificates
     * key can be recovered for every user at the time.
     * 
     * @param endentityprofileid the end entity profile id the user belongs to.
     * @return the marked keyrecovery data or null if none can be found.
     */
    public org.ejbca.core.model.keyrecovery.KeyRecoveryData keyRecovery(Admin admin, String username, int endEntityProfileId) throws AuthorizationDeniedException;

    /**
     * Marks a users newest certificate for key recovery. Newest means certificate with latest not
     * before date.
     *
     * @param admin the administrator calling the function
     * @param username or the user.
     * @param the end entity profile of the user, used for access control
     * @param gc The GlobalConfiguration used to extract approval information
     * @return true if operation went successful or false if no certificates could be found for
     *         user, or user already marked.
     */
    public boolean markNewestAsRecoverable(Admin admin, String username, int endEntityProfileId, GlobalConfiguration gc)
    		throws AuthorizationDeniedException, ApprovalException, WaitingForApprovalException;

    /**
     * Marks a users certificate for key recovery.
     *
     * @param admin the administrator calling the function
     * @param certificate the certificate used with the keys about to be removed.
     * @param gc The GlobalConfiguration used to extract approval information
     * @return true if operation went successful or false if  certificate couldn't be found.
     */
    public boolean markAsRecoverable(Admin admin, Certificate certificate, int endEntityProfileId, GlobalConfiguration gc)
    		throws AuthorizationDeniedException, WaitingForApprovalException, ApprovalException;

    /** Resets keyrecovery mark for a user. */
    public void unmarkUser(Admin admin, String username);

    /** @return true if user is already marked for key recovery. */
    public boolean isUserMarked(Admin admin, String username);

    /**
     * @param admin the administrator calling the function
     * @param certificate the certificate used with the keys about to be removed.
     * @return true if specified certificates keys exists in database.
     */
    public boolean existsKeys(Admin admin, Certificate certificate);
}
