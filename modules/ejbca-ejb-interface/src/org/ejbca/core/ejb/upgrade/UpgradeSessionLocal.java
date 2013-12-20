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
package org.ejbca.core.ejb.upgrade;

import java.util.List;

import javax.ejb.Local;

/**
 * Local interface for UpgradeSession.
 */
@Local
public interface UpgradeSessionLocal  extends UpgradeSession{

	/** For internal user from UpgradeSessionBean only! */
	void postMigrateDatabase400SmallTables();
	/** For internal user from UpgradeSessionBean only! */
	void postMigrateDatabase400HardTokenData(List<String> subSet);
}
