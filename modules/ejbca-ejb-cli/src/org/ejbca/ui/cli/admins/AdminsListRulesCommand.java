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

import java.util.Collections;
import java.util.List;

import org.ejbca.core.model.authorization.AccessRule;
import org.ejbca.core.model.authorization.AdminGroup;
import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * Lists access rules for a group
 * @version $Id: AdminsListRulesCommand.java 10945 2010-12-22 09:45:15Z jeklund $
 */
public class AdminsListRulesCommand extends BaseAdminsCommand {

	public String getMainCommand() { return MAINCOMMAND; }
	public String getSubCommand() { return "listrules"; }
	public String getDescription() { return "Lists access rules for a group"; }

    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            if (args.length < 2) {
    			getLogger().info("Description: " + getDescription());
                getLogger().info("Usage: " + getCommand() + " <name of group>");
                return;
            }
            String groupName = args[1];
            AdminGroup adminGroup = ejb.getAdminGroupSession().getAdminGroup(getAdmin(), groupName);
            if (adminGroup == null) {
            	getLogger().error("No such group \"" + groupName + "\" .");
                return;
            }
            List<AccessRule> list = (List<AccessRule>) adminGroup.getAccessRules();
            Collections.sort(list);
            for (AccessRule accessRule : list) {
            	getLogger().info(getParsedAccessRule(accessRule.getAccessRule()) + " " + AccessRule.RULE_TEXTS[accessRule.getRule()] + " " + (accessRule.isRecursive() ? "RECURSIVE" : ""));
            }
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
		}
    }
}
