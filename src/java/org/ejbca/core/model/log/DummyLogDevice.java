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
package org.ejbca.core.model.log;

import java.io.Serializable;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Date;

import org.ejbca.util.query.IllegalQueryException;
import org.ejbca.util.query.Query;

/**
 * The dummy logging device. This does absolutely nothing and is just here for other developers to borrow.. =)
 * @version $Id: DummyLogDevice.java 11918 2011-05-06 12:41:00Z jeklund $
 */
public class DummyLogDevice implements ILogDevice, Serializable {

	private static final long serialVersionUID = 1L;

	public static final String DEFAULT_DEVICE_NAME = "DummyLogDevice";

	private static DummyLogDevice instance;
	
	private String deviceName = null;

	protected DummyLogDevice(String name) throws Exception {
		deviceName = name;
	}
	
	/**
	 * Creates (if needed) the log device and returns the object.
	 *
	 * @param prop Arguments needed for the eventual creation of the object
	 * @return An instance of the log device.
	 */
	public static synchronized ILogDevice instance(String name) throws Exception {
		if (instance == null) {
			instance = new DummyLogDevice(name);
		}
		return instance;
	}

	/** @see org.ejbca.core.model.log.ILogDevice */
	@Override
	public String getDeviceName() {
		return deviceName;
	}
	
	/** @see org.ejbca.core.model.log.ILogDevice */
	@Override
	public void log(Admin admininfo, int caid, int module, Date time, String username, Certificate certificate, int event, String comment, Exception exception) {
	}

	/** @see org.ejbca.core.model.log.ILogDevice */
	@Override
	public Collection<LogEntry> query(Query query, String viewlogprivileges, String capriviledges, int maxResults) throws IllegalQueryException {
		return null;
	}

	/** @see org.ejbca.core.model.log.ILogDevice */
	@Override
	public boolean getAllowConfigurableEvents() {
		return true;
	}

	/** @see org.ejbca.core.model.log.ILogDevice */
	@Override
	public boolean isSupportingQueries() {
		return false;
	}
}
