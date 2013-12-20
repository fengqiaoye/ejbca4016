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
package org.ejbca.core.model.services;

/**
 * Exception throws when a service work method fails for som reason
 * It should contain a message used in logging.
 * 
 * @author Philip Vendil 2006 sep 28
 *
 * @version $Id: ServiceExecutionFailedException.java 8373 2009-11-30 14:07:00Z jeklund $
 */
public class ServiceExecutionFailedException extends Exception {

	public ServiceExecutionFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	public ServiceExecutionFailedException(String message) {
		super(message);
	}

	public ServiceExecutionFailedException(Throwable cause) {
		super(cause);
	}
}
