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
package org.ejbca.ui.web.admin.cainterface.exception;

/**
 * Thrown if too many results are encountered to process.
 * 
 * @version $Id: ExcessiveResultsException.java 15256 2012-08-07 14:45:11Z mikekushner $
 *
 */
public class ExcessiveResultsException extends Exception {

    private static final long serialVersionUID = -3569778666070980397L;

    public ExcessiveResultsException() {

    }


    public ExcessiveResultsException(String arg0) {
        super(arg0);

    }

    public ExcessiveResultsException(Throwable arg0) {
        super(arg0);
    }

    public ExcessiveResultsException(String arg0, Throwable arg1) {
        super(arg0, arg1);
    }

}
