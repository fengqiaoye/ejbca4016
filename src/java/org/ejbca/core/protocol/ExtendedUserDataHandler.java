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

package org.ejbca.core.protocol;


/**
 * To be implemented by classes that is extending the handling of the user data.
 * Could be to store it or to change something in DN.
 * @author primelars
 * @version $Id: ExtendedUserDataHandler.java 12006 2011-05-17 15:49:18Z primelars $
 */
public interface ExtendedUserDataHandler {
	/**
	 * Called when the data handling should be done.
	 * @param req Request to be modified.
	 * @param otherData some other data
	 * @return the modified request
	 * @throws HandlerException The handle may throw this exception if some error occurs. Throwing it prevents the certificate creation.
	 */
	IRequestMessage processRequestMessage( IRequestMessage req, String otherData ) throws HandlerException;
	/**
	 * Exception thrown by handler. No certificate should be created if this exception is thrown.
	 *
	 */
	class HandlerException extends Exception {
		public HandlerException(String message) {
			super(message);
		}
	}
}
