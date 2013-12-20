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
package org.ejbca.core.protocol.cmp;

import junit.framework.TestCase;

import org.ejbca.core.ejb.ca.sign.SignSessionRemote;
import org.ejbca.core.ejb.ra.UserAdminSessionRemote;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.util.SimpleMock;

/**
 * Unit tests for CrmfMessageHandler. 
 * 
 * This test verifies that the request has it's username set to the same as an existing
 * user's with the same subject DN. 
 * 
 * @author mikek
 * @version $Id: CrmfMessageHandlerTest.java 13005 2011-10-25 12:05:15Z anatom $
 */
public class CrmfMessageHandlerTest extends TestCase {

    private static String USER_NAME = "foobar";

    public CrmfMessageHandlerTest(String name) {
        super(name);
    }

    public void testExtractUserNameComponent() {
        CrmfMessageHandler crmfMessageHandler = new CrmfMessageHandler();
        /*
         * Some slight reflective manipulation of crmfMessageHandler here in
         * order to get around the fact that we're not running any of the logic
         * in its usual constructor, instead using the empty default one.
         */
        SimpleMock.inject(crmfMessageHandler, "admin", new Admin(Admin.TYPE_RA_USER));
        final UserAdminSessionRemote userAdminSessionMock = new SimpleMock(UserAdminSessionRemote.class) {{
        	map("findUserBySubjectDN", new UserDataVO() {
				private static final long serialVersionUID = 1L;
				public String getUsername() { return USER_NAME; };
			});
        }}.mock();
        SimpleMock.inject(crmfMessageHandler, "userAdminSession", userAdminSessionMock);
        SimpleMock.inject(crmfMessageHandler, "signSession", new SimpleMock(SignSessionRemote.class).mock());
        final CrmfRequestMessage requestMock = new CrmfRequestMessage() {
            private static final long serialVersionUID = 1L;
            public String getSubjectDN() {
                return "foo";	// Just return something that isn't null
            }
        };
        try {
            crmfMessageHandler.handleMessage(requestMock);
        } catch (NullPointerException e) {
            // NOPMD: Since we don't pass an actual CMP message her it will fail with an NPE
            // But the setting of username that we test here is done in anyway. 
            // Since this is a JUnit test we can't do the calling of session beans that crmfMessageHandler does
        }
        assertEquals("crmfMessageHandler.handleMessage did not process user name correctly", USER_NAME, requestMock.getUsername());
    }
}
