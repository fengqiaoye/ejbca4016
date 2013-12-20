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

import java.util.Collection;

import org.ejbca.core.model.ca.caadmin.CA;
import org.ejbca.core.model.ca.store.CRLInfo;
import org.ejbca.ui.cli.ErrorAdminCommandException;
import org.ejbca.util.ValidityDate;

/**
 * List information about the latest CRL from each CA.
 *
 * @version $Id: CaGetCrlInfo.java 15009 2012-06-18 12:49:30Z primelars $
 */
public class CaGetCrlInfo extends BaseCaAdminCommand {

	public String getMainCommand() { return MAINCOMMAND; }
	public String getSubCommand() { return "getcrlinfo"; }
	public String getDescription() { return "List information about latest CRLs"; }

	@Override
	public void execute(String[] args) throws ErrorAdminCommandException {
        try {
        	Collection<Integer> caIds = ejb.getCaSession().getAvailableCAs(getAdmin());
        	for (Integer caId : caIds) {
        		final CA ca = ejb.getCaSession().getCA(getAdmin(), caId);
        		final StringBuilder sb = new StringBuilder();
        		sb.append("\"").append(ca.getName()).append("\" \"").append(ca.getSubjectDN()).append("\"");
        		final CRLInfo crlInfo = ejb.getCrlSession().getLastCRLInfo(getAdmin(), ca.getSubjectDN(), false);
        		if (crlInfo != null) {
            		sb.append(" CRL# ").append(crlInfo.getLastCRLNumber());
            		sb.append(" issued ").append(ValidityDate.formatAsUTC(crlInfo.getCreateDate()));
            		sb.append(" expires ").append(ValidityDate.formatAsUTC(crlInfo.getExpireDate()));
        		} else {
        			sb.append(" NO_CRL_ISSUED");
        		}
        		final CRLInfo deltaCrlInfo = ejb.getCrlSession().getLastCRLInfo(getAdmin(), ca.getSubjectDN(), true);
        		if (deltaCrlInfo!=null) {
            		sb.append(" DELTACRL# ").append(deltaCrlInfo.getLastCRLNumber());
            		sb.append(" issued ").append(ValidityDate.formatAsUTC(deltaCrlInfo.getCreateDate()));
            		sb.append(" expires ").append(ValidityDate.formatAsUTC(deltaCrlInfo.getExpireDate()));
        		} else {
        			sb.append(" NO_DELTACRL_ISSUED");
        		}
        		getLogger().info(sb.toString());
        	}
        } catch (Exception e) {
        	throw new ErrorAdminCommandException(e);
        }        	
	}
}
