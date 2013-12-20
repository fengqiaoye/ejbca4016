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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.ejbca.config.GlobalConfiguration;
import org.ejbca.core.model.authorization.AccessRule;
import org.ejbca.core.model.authorization.AdminGroup;
import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * Changes an access rule
 */
public class AdminsChangeRuleCommand extends BaseAdminsCommand {

	public String getMainCommand() { return MAINCOMMAND; }
	public String getSubCommand() { return "changerule"; }
	public String getDescription() { return "Changes an access rule"; }

	public void execute(String[] args) throws ErrorAdminCommandException {
		try {
			if (args.length < 5) {
    			getLogger().info("Description: " + getDescription());
				getLogger().info("Usage: " + getCommand() + " <name of group> <access rule> <rule> <recursive>");
				Collection<AdminGroup> adminGroups = ejb.getAdminGroupSession().getAuthorizedAdminGroupNames(getAdmin(), ejb.getCaSession().getAvailableCAs(getAdmin()));
				Collections.sort((List<AdminGroup>) adminGroups);
				String availableGroups = "";
				for (AdminGroup adminGroup : adminGroups) {
					availableGroups += (availableGroups.length()==0?"":", ") + "\"" + adminGroup.getAdminGroupName() + "\"";
				}
				getLogger().info("Available Admin groups: " + availableGroups);
				getLogger().info("Available access rules:");
				GlobalConfiguration globalConfiguration = ejb.getGlobalConfigurationSession().getCachedGlobalConfiguration(getAdmin());
				for (String current : (Collection<String>) ejb.getAuthorizationSession().getAuthorizedAvailableAccessRules(getAdmin(), ejb.getCaSession().getAvailableCAs(getAdmin()),
						globalConfiguration.getEnableEndEntityProfileLimitations(), globalConfiguration.getIssueHardwareTokens(), globalConfiguration.getEnableKeyRecovery(),
						ejb.getEndEntityProfileSession().getAuthorizedEndEntityProfileIds(getAdmin()), ejb.getUserDataSourceSession().getAuthorizedUserDataSourceIds(getAdmin(), true))) {
					getLogger().info(" " + getParsedAccessRule(current));
				}
				String availableRules = "";
				for (String current : AccessRule.RULE_TEXTS) {
					availableRules += (availableRules.length()==0?"":", ") + current;
				}
				getLogger().info("Available rules: " + availableRules);
				getLogger().info("Recursive is one of: TRUE, FALSE");
				return;
			}
			String groupName = args[1];
            if (ejb.getAdminGroupSession().getAdminGroup(getAdmin(), groupName) == null) {
            	getLogger().error("No such group \"" + groupName + "\" .");
                return;
            }
			String accessRule = getOriginalAccessRule(args[2]);
			GlobalConfiguration globalConfiguration = ejb.getGlobalConfigurationSession().getCachedGlobalConfiguration(getAdmin());
			if (!((Collection<String>) ejb.getAuthorizationSession().getAuthorizedAvailableAccessRules(getAdmin(), ejb.getCaSession().getAvailableCAs(getAdmin()),
					globalConfiguration.getEnableEndEntityProfileLimitations(), globalConfiguration.getIssueHardwareTokens(), globalConfiguration.getEnableKeyRecovery(),
					ejb.getEndEntityProfileSession().getAuthorizedEndEntityProfileIds(getAdmin()), ejb.getUserDataSourceSession().getAuthorizedUserDataSourceIds(getAdmin(), true))).contains(accessRule)) {
				getLogger().error("Accessrule \"" + accessRule + "\" is not available.");
				return;
			}
			int rule = Arrays.asList(AccessRule.RULE_TEXTS).indexOf(args[3]);
			if (rule == -1) {
				getLogger().error("No such rule \"" + args[3] + "\".");
				return;
			}
			boolean recursive = "TRUE".equalsIgnoreCase(args[4]);
			List<String> accessRuleStrings = new ArrayList<String>();
			accessRuleStrings.add(accessRule);
			if (rule == AccessRule.RULE_NOTUSED) {
			    ejb.getAdminGroupSession().removeAccessRules(getAdmin(), groupName, accessRuleStrings);
			} else {
			    ejb.getAdminGroupSession().removeAccessRules(getAdmin(), groupName, accessRuleStrings);
				AccessRule accessRuleObject = new AccessRule(accessRule, rule, recursive);
				Collection<AccessRule> accessRules = new ArrayList<AccessRule>();
				accessRules.add(accessRuleObject);
				ejb.getAdminGroupSession().addAccessRules(getAdmin(), groupName, accessRules);
			}
		} catch (Exception e) {
			getLogger().error("",e);
			throw new ErrorAdminCommandException(e);
		}
	}
}
