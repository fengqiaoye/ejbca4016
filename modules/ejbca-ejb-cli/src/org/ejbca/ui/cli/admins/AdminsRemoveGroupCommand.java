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
 
package org.ejbca.ui.cli.admins;

import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * Remove admin group
 */
public class AdminsRemoveGroupCommand extends BaseAdminsCommand {

	public String getMainCommand() { return MAINCOMMAND; }
	public String getSubCommand() { return "removegroup"; }
	public String getDescription() { return "Remove admin group"; }

    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            if (args.length < 2) {
    			getLogger().info("Description: " + getDescription());
                getLogger().info("Usage: " + getCommand() + " <name of group>");
                return;
            }
            String groupName = args[1];
            if (ejb.getAdminGroupSession().getAdminGroup(getAdmin(), groupName) == null) {
            	getLogger().error("No such group \"" + groupName + "\" .");
                return;
            }
            ejb.getAdminGroupSession().removeAdminGroup(getAdmin(), groupName);
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
		}
    }
}
