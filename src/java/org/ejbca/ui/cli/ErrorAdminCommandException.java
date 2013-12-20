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
 
package org.ejbca.ui.cli;

/**
 * The exception thrown when an error occurs in an Admin Command (IAdminCommand)
 *
 * @version $Id: ErrorAdminCommandException.java 8373 2009-11-30 14:07:00Z jeklund $
 */
public class ErrorAdminCommandException extends org.ejbca.core.EjbcaException {
    /**
     * Creates a new instance of ErrorAdminCommandException
     *
     * @param message error message
     */
    public ErrorAdminCommandException(String message) {
        super(message);
    }

    /**
     * Creates a new instance of ErrorAdminCommandException
     *
     * @param exception root cause of error
     */
    public ErrorAdminCommandException(Exception exception) {
        super(exception);
    }
}
