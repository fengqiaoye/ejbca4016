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
 
package org.ejbca.core.model.ra.raadmin;

import javax.xml.ws.WebFault;

/**
 * An exception thrown when someone tries to add or edit a profile that doesnt match its end entity
 * profile.
 *
 * @author Philip Vendil 2002-04-12
 * @version $Id: UserDoesntFullfillEndEntityProfile.java 11201 2011-01-15 10:23:15Z anatom $
 */
@WebFault
public class UserDoesntFullfillEndEntityProfile extends java.lang.Exception {
    /**
     * Creates a new instance of <code>UserDoesntFullfillProfile</code> without detail message.
     */
    public UserDoesntFullfillEndEntityProfile() {
        super();
    }

    /**
     * Constructs an instance of <code>UserDoesntFullfillProfile</code> with the specified detail
     * message.
     *
     * @param msg the detail message.
     */
    public UserDoesntFullfillEndEntityProfile(String msg) {
        super(msg);
    }
}
