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

import javax.ejb.Remote;

/**
 * Remote interface for CrlStoresession
 * 
 * @version $Id: CrlCreateSessionRemote.java 10401 2010-11-09 12:20:21Z anatom $
 *
 */
@Remote
public interface CrlCreateSessionRemote extends CrlCreateSession {

}
