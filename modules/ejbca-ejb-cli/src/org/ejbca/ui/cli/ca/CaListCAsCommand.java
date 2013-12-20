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
 
package org.ejbca.ui.cli.ca;

import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Iterator;

import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.ui.cli.ErrorAdminCommandException;
import org.ejbca.util.CertTools;
import org.ejbca.util.CryptoProviderTools;

/**
 * Lists the names of all available CAs.
 *
 * @version $Id: CaListCAsCommand.java 10945 2010-12-22 09:45:15Z jeklund $
 */
public class CaListCAsCommand extends BaseCaAdminCommand {

	public String getMainCommand() { return MAINCOMMAND; }
	public String getSubCommand() { return "listcas"; }
	public String getDescription() { return "Lists the names of all available CAs"; }

    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
        	CryptoProviderTools.installBCProvider();
            Collection<Integer> caids = ejb.getCaSession().getAvailableCAs(getAdmin());
            Iterator<Integer> iter = caids.iterator();
            while (iter.hasNext()) {
                int caid = ((Integer)iter.next()).intValue();
                CAInfo ca = ejb.getCAAdminSession().getCAInfo(getAdmin(),caid);
                Collection<Certificate> certs = ca.getCertificateChain();
                Iterator<Certificate> ci = certs.iterator();
                Certificate cacert = null;
                if (ci.hasNext()) {
                    cacert = (Certificate)ci.next();                	
                }
                getLogger().info("CA Name: "+ca.getName());
                getLogger().info(" Id: "+ca.getCAId());
                if (cacert != null) {
                	getLogger().info(" Issuer DN: "+CertTools.getIssuerDN(cacert));                	
                }
                getLogger().info(" Subject DN: "+ca.getSubjectDN());
                getLogger().info(" Type: "+ca.getCAType());
                getLogger().info(" Expire time: "+ca.getExpireTime());
                getLogger().info(" Signed by: "+ca.getSignedBy());
            }
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
        }
    }
}
