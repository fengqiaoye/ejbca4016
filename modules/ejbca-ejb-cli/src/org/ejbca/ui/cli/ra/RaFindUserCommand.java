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

import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.ra.ExtendedInformation;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * Find details of a user in the database.
 *
 * @version $Id: RaFindUserCommand.java 10945 2010-12-22 09:45:15Z jeklund $
 */
public class RaFindUserCommand extends BaseRaAdminCommand {

	public String getMainCommand() { return MAINCOMMAND; }
	public String getSubCommand() { return "finduser"; }
	public String getDescription() { return "Find and show details of a user"; }

    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            if (args.length < 2) {
    			getLogger().info("Description: " + getDescription());
                getLogger().info("Usage: " + getCommand() + " <username>");
                return;
            }
            String username = args[1];
            try {
                UserDataVO data = ejb.getUserAdminSession().findUser(getAdmin(), username);
                if (data != null) {
                	getLogger().info("Found user:");
                	getLogger().info("username=" + data.getUsername());
                    getLogger().info("password=" + data.getPassword());
                    getLogger().info("dn: \"" + data.getDN() + "\"");
                    getLogger().info("altName: \"" + data.getSubjectAltName() + "\"");
                    ExtendedInformation ei = data.getExtendedinformation();
                    getLogger().info("directoryAttributes: \"" + (ei != null ? ei.getSubjectDirectoryAttributes() : "") + "\"");
                    getLogger().info("email=" + data.getEmail());
                    getLogger().info("status=" + data.getStatus());
                    getLogger().info("type=" + data.getType());
                    getLogger().info("token type=" + data.getTokenType());
                    getLogger().info("end entity profile id=" + data.getEndEntityProfileId());
                    getLogger().info("certificate entity profile id=" + data.getCertificateProfileId());
                    getLogger().info("hard token issuer id=" + data.getHardTokenIssuerId());
                    getLogger().info("created=" + data.getTimeCreated());
                    getLogger().info("modified=" + data.getTimeModified());
                } else {
                    getLogger().error("User '" + username + "' does not exist.");
                }
            } catch (AuthorizationDeniedException e) {
                getLogger().error("Error : Not authorized to view user.");
            }
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
        }
    }
}
