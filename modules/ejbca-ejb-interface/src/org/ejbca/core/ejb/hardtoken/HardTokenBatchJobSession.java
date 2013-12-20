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
package org.ejbca.core.ejb.hardtoken;

import java.util.Collection;

import org.ejbca.core.model.hardtoken.UnavailableTokenException;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.UserDataVO;

/**
 * @version $Id: HardTokenBatchJobSession.java 11405 2011-02-23 22:42:51Z jeklund $
 */
public interface HardTokenBatchJobSession {

	/**
     * Returns the next user scheduled for batch generation for the given issuer.
     * 
     * @param admin the administrator performing the actions
     * @return The next user to generate or NULL if there are no users i queue.
     */
    public UserDataVO getNextHardTokenToGenerate(Admin admin, String alias) throws UnavailableTokenException;

    /**
     * Returns a Collection of users scheduled for batch generation for the given issuer.
     * A maximum of MAX_RETURNED_QUEUE_SIZE users will be returned by call.
     *
     * @param admin the administrator performing the actions
     *
     * @return A Collection of users to generate or NULL if there are no users i queue.
     * @throws javax.ejb.EJBException if a communication or other error occurs.
     */
    public Collection<UserDataVO> getNextHardTokensToGenerate(Admin admin, String alias) throws UnavailableTokenException;

    /**
     * Returns the indexed user in queue scheduled for batch generation for the given issuer.
     *
     * @param admin the administrator performing the actions
     * @param index index in queue of user to retrieve. (First position is 1 according to old JDBC implementation.)
     * @return The next token to generate or NULL if the given user doesn't exist in queue.
     * @throws javax.ejb.EJBException if a communication or other error occurs.
     */
    public UserDataVO getNextHardTokenToGenerateInQueue(Admin admin, String alias, int index) throws UnavailableTokenException;

    /**
     * Returns the number of users scheduled for batch generation for the given issuer.
     *
     * @param admin the administrator performing the actions
     *
     * @return the number of users to generate.
     * @throws javax.ejb.EJBException if a communication or other error occurs.
     */
    public int getNumberOfHardTokensToGenerate(org.ejbca.core.model.log.Admin admin, java.lang.String alias);

    /**
     * Methods that checks if a user exists in the database having the given
     * hard token issuer id. This function is mainly for avoiding
     * desynchronisation when a hard token issuer is deleted.
     * 
     * @param hardtokenissuerid the id of hard token issuer to look for.
     * @return true if hardtokenissuerid exists in user database.
     */
    public boolean checkForHardTokenIssuerId(Admin admin, int hardtokenissuerid);
}
