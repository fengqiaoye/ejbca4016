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

import java.security.cert.Certificate;
import java.util.Collection;

import org.ejbca.ui.cli.ErrorAdminCommandException;
import org.ejbca.util.CertTools;

/**
 * Output all certificates for a user.
 *
 * @version $Id: RaGetUserCertCommand.java 10945 2010-12-22 09:45:15Z jeklund $
 */
public class RaGetUserCertCommand extends BaseRaAdminCommand {

	public String getMainCommand() { return MAINCOMMAND; }
	public String getSubCommand() { return "getusercert"; }
	public String getDescription() { return "Output all certificates for a user"; }

    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            if (args.length < 2) {
    			getLogger().info("Description: " + getDescription());
                getLogger().info("Usage: " + getCommand() + " <username>");
                return;
            }
            final String username = args[1];
            final Collection<Certificate> data = ejb.getCertStoreSession().findCertificatesByUsername(getAdmin(), username);
            if (data != null) {
            	getLogger().info(new String(CertTools.getPEMFromCerts(data)));
            } else {
            	getLogger().info("User '" + username + "' does not exist.");
            }
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
        }
    }
}
