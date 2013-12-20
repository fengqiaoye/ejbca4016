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
package org.ejbca.core.model.approval;

import org.ejbca.core.ejb.hardtoken.HardTokenSession;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.log.Admin;

/**
 * Helper class containing static methods for RMI lookups
 * 
 * @author Philip Vendil
 * @version $Id: ApprovalRequestHelper.java 11268 2011-01-26 23:02:58Z jeklund $
 */
public class ApprovalRequestHelper { 

    public static ApprovalDataText getTokenName(HardTokenSession hardTokenSession, Admin admin, int tokenid) {
        ApprovalDataText retval;
        if (tokenid <= SecConst.TOKEN_SOFT) {
            int tokenindex = 0;
            for (int i = 0; i < SecConst.TOKENIDS.length; i++) {
                if (SecConst.TOKENIDS[i] == tokenid) {
                    tokenindex = i;
                }
            }
            retval = new ApprovalDataText("TOKEN", SecConst.TOKENTEXTS[tokenindex], true, true);

        } else {
            String name = hardTokenSession.getHardTokenProfileName(admin, tokenid);
            retval = new ApprovalDataText("TOKEN", name, true, false);
        }
        return retval;
    }
}
