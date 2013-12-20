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
package org.ejbca.core.ejb.ca.caadmin;

import javax.ejb.Remote;

/**
 * Remote interface for CaSession
 * 
 * @version $Id: CaSessionRemote.java 10428 2010-11-11 16:45:12Z anatom $
 *
 */
@Remote
public interface CaSessionRemote extends CaSession {

}
