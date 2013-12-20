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

package org.cesecore.core.ejb.log;

import javax.ejb.Local;

/**
 * @see org.cesecore.core.ejb.log.LogConfigurationSession
 * @version $Id: LogConfigurationSessionLocal.java 15009 2012-06-18 12:49:30Z primelars $
 */
@Local
public interface LogConfigurationSessionLocal extends LogConfigurationSession {

    /**
     * Update counter stored in LogConfiguration with id 0.
     * @return an allocated logEntryRowNumber or -1 if the operation failed
     */
	int getAndIncrementRowCount();
}
