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

import org.apache.commons.lang.StringUtils;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.core.model.ra.raadmin.UserDoesntFullfillEndEntityProfile;
import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * Set the SubjectDN for a user.
 *
 * @author aveen
 * @version $Id: RaSetSubjectDNCommand.java 11063 2011-01-05 10:16:47Z jeklund $
 */
public class RaSetSubjectDNCommand extends BaseRaAdminCommand {

	@Override
	public String getMainCommand() {
		return MAINCOMMAND;
	}

	@Override
	public String getSubCommand() {
		return "setsubjectdn";
	}
	
	@Override
	public String getDescription() {
		return "Set or update the SubjectDN for a user";
	}

	@Override
	public void execute(String[] args) throws ErrorAdminCommandException {

        try {
            if (args.length < 3) {
    			getLogger().info("Description: " + getDescription());
            	getLogger().info("Usage: " + getCommand() + " <username> <subjectDN>");
            	getLogger().info(" The SubjectDN fields are: emailAddress=<string>, UID=< unique identifier>, CN=<common name>, serialNumber=<serialnumber>, " +
            			"givenName=<string>, initials=<string>, surname=<string>, title=<string>, " +
            			"OU=<the organizational unit>, O=<the organization>, L=<locality>, ST=<state of province>, " +
            			"DC=<domain component>, C=<two letter ISO3166>, unstructuredAddress=<IP address>, " +
            			"unstructuredName=<domain name>, postalCode=<string>, businessCategory=<organization type>, " +
            			"dnQualifier=<string>, postalAddress=<the postal address>, telephoneNumber=<telephone number>, " +
            			"pseudonym=<string>, streetAddress=<string>, name=<string>, CIF=<tax ID code for companies in Spain>, " +
            			"NIF=<tax ID number for companied in Spain>");
                return;
            }
            String username = args[1];
            String subjectDN = args[2];
            if (StringUtils.isEmpty(subjectDN)) {
            	getLogger().error("SubjectDN must be supplied.");
            	return;
            }
            getLogger().info("Setting subjectDN '" + subjectDN + "' for user " + username);
            try {
            	UserDataVO uservo = ejb.getUserAdminSession().findUser(getAdmin(), username);
            	uservo.setDN(subjectDN);
            	ejb.getUserAdminSession().changeUser(getAdmin(), uservo, false);
            } catch (AuthorizationDeniedException e) {
            	getLogger().error("Not authorized to change userdata.");
            } catch (UserDoesntFullfillEndEntityProfile e) {
            	getLogger().error("Given userdata doesn't fullfill end entity profile. : " + e.getMessage());
            }
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
        }
	}
}
