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
package org.cesecore.core.ejb.ca.crl;

import javax.ejb.Local;

/**
 * Local interface for CreateCRLSession
 * @version $Id: CrlSessionLocal.java 11340 2011-02-11 12:26:30Z jeklund $
 */
@Local
public interface CrlSessionLocal extends CrlSession {
}
