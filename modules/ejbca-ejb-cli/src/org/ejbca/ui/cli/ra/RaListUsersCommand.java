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
 
package org.ejbca.ui.cli.ra;

import java.util.Collection;
import java.util.Iterator;

import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * List users with specified status in the database.
 *
 * @version $Id: RaListUsersCommand.java 15417 2012-08-30 13:41:45Z samuellb $
 *
 * @see org.ejbca.core.ejb.ra.UserDataLocal
 */
public class RaListUsersCommand extends BaseRaAdminCommand {

	public String getMainCommand() { return MAINCOMMAND; }
	public String getSubCommand() { return "listusers"; }
	public String getDescription() { return "List users with a specified status"; }

    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            if (args.length < 2) {
    			getLogger().info("Description: " + getDescription());
                getLogger().info("Usage: " + getCommand() + " <status>");
                getLogger().info(" Status: ANY=00; NEW=10; FAILED=11; INITIALIZED=20; INPROCESS=30; GENERATED=40; REVOKED=50; HISTORICAL=60; KEYRECOVERY=70");
                return;
            }
            int status = Integer.parseInt(args[1]);
            Collection<UserDataVO> coll = null;
            if (status==0) {
                coll = ejb.getUserAdminSession().findAllUsersByStatus(getAdmin(), 10);
                coll.addAll(ejb.getUserAdminSession().findAllUsersByStatus(getAdmin(), 11));
                coll.addAll(ejb.getUserAdminSession().findAllUsersByStatus(getAdmin(), 20));
                coll.addAll(ejb.getUserAdminSession().findAllUsersByStatus(getAdmin(), 30));
                coll.addAll(ejb.getUserAdminSession().findAllUsersByStatus(getAdmin(), 40));
                coll.addAll(ejb.getUserAdminSession().findAllUsersByStatus(getAdmin(), 50));
            } else {
                coll = ejb.getUserAdminSession().findAllUsersByStatus(getAdmin(), status);
            }
            Iterator<UserDataVO> iter = coll.iterator();
            while (iter.hasNext()) {
                UserDataVO data = iter.next();
                getLogger().info("User: " + data.getUsername() + ", \"" + data.getDN() +
                    "\", \"" + data.getSubjectAltName() + "\", " + data.getEmail() + ", " +
                    data.getStatus() + ", " + data.getType() + ", " + data.getTokenType() + ", " + data.getHardTokenIssuerId());
            }
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
        }
    }
}
