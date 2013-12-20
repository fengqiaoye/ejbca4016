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

package org.ejbca.core.protocol.ocsp.standalonesession;

/**
 * Card implementation. No reload needed.
 * 
 * @author primelars
 * @version  $Id: CardProviderHandler.java 11060 2011-01-04 23:06:16Z primelars $
 */
class CardProviderHandler implements ProviderHandler {
    /* (non-Javadoc)
     * @see org.ejbca.ui.web.protocol.OCSPServletStandAloneSession.ProviderHandler#getProviderName()
     */
    public String getProviderName() {
        return "PrimeKey";
    }
    /* (non-Javadoc)
     * @see org.ejbca.ui.web.protocol.OCSPServletStandAloneSession.ProviderHandler#reload()
     */
    public void reload() {
        // not needed to reload.
    }
    /* (non-Javadoc)
     * @see org.ejbca.ui.web.protocol.OCSPServletStandAloneSession.ProviderHandler#addKeyContainer(org.ejbca.ui.web.protocol.OCSPServletStandAloneSession.PrivateKeyContainer)
     */
    public void addKeyContainer(PrivateKeyContainer keyContainer) {
        // do nothing
    }
}