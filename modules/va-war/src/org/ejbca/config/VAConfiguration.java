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

package org.ejbca.config;

/**
 * Parses configuration bundled in conf/va.properties both for the internal and external VA.
 * 
 * @version $Id: VAConfiguration.java 15003 2012-06-17 23:55:15Z primelars $
 */
public class VAConfiguration {
	private final static String S_HASH_ALIAS_PREFIX = "va.sKIDHash.alias.";

	public static String sKIDHashFromName(String name) {
		return ConfigurationHolder.getString(S_HASH_ALIAS_PREFIX+name, null);
	}

}
