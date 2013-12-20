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

import org.apache.log4j.Logger;
import org.cesecore.core.ejb.log.OldLogSession;
import org.cesecore.core.ejb.log.OldLogSessionLocal;
import org.ejbca.core.model.InternalResources;
import org.ejbca.util.query.IllegalQueryException;
import org.ejbca.util.query.Query;

/**
 * Implements a log device using the old logging system, implements the Singleton pattern.
 * @version $Id: OldLogDevice.java 11918 2011-05-06 12:41:00Z jeklund $
 */
public class OldLogDevice implements ILogDevice, Serializable {
	
	private static final long serialVersionUID = 1L;

	public final static String DEFAULT_DEVICE_NAME = "OldLogDevice";
	
	/** Internal localization of logs and errors */
	private static final InternalResources intres = InternalResources.getInstance();

	private static final Logger log = Logger.getLogger(OldLogDevice.class);

	/** A handle to the unique Singleton instance. */
	private static ILogDevice instance;

	private OldLogSession oldLogSession;
    private String deviceName = null;

    /** Initializes */
	protected OldLogDevice(String name) throws Exception {
		deviceName = name;
	}

	// Workaround to be able to avoid local ENC lookup and use injection instead.
	// This method might be invoked once for each LogSessionBean that is created,
	// but this should not affect the functionality.
	public void setOldLogSessionInterface(OldLogSessionLocal oldLogSession) {
		this.oldLogSession = oldLogSession;
	}

	/**
	 * Creates (if needed) the log device and returns the object.
	 *
	 * @param prop Arguments needed for the eventual creation of the object
	 * @return An instance of the log device.
	 */
	public static synchronized ILogDevice instance(String name) throws Exception {
		if (instance == null) {
			instance = new OldLogDevice(name);
		}
		return instance;
	}
	
    /** Log everything in the database using the log entity bean. */
	@Override
	public void log(Admin admin, int caid, int module, Date time, String username, Certificate certificate, int event, String comment, Exception exception) {
		if (exception != null) {
			comment += ", Exception: " + exception.getMessage();
		}
		boolean successfulLog = false;
		try {
			successfulLog = oldLogSession.log(admin, caid, module, time, username, certificate, event, comment, exception);
		} catch (Throwable e) {
			log.debug("", e);
		}
		if (!successfulLog) {
			// We are losing a db audit entry in this case.
			log.error(intres.getLocalizedMessage("log.errormissingentry"));
		}
    }

	/** @see org.ejbca.core.model.log.ILogDevice */
	@Override
	public String getDeviceName() {
		return deviceName;
	}

	/**
	 * Method to execute a customized query on the log db data. The parameter query should be a legal Query object.
	 *
	 * @param query a number of statements compiled by query class to a SQL 'WHERE'-clause statement.
	 * @param viewlogprivileges is a SQL query string returned by a LogAuthorization object.
	 * @param maxResults Maximum size of Collection
	 * @return a collection of LogEntry.
	 * @throws IllegalQueryException when query parameters internal rules isn't fulfilled.
	 * @see org.ejbca.util.query.Query
	 */
	@Override
	public Collection<LogEntry> query(Query query, String viewlogprivileges, String capriviledges, int maxResults) throws IllegalQueryException {
		return oldLogSession.query(query, viewlogprivileges, capriviledges, maxResults);
	}

	/** @see org.ejbca.core.model.log.ILogDevice */
	@Override
	public boolean getAllowConfigurableEvents() {
		return true;
	}

	/** @see org.ejbca.core.model.log.ILogDevice */
	@Override
	public boolean isSupportingQueries() {
		return true;
	}
}
