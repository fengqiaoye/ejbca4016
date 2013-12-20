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

package org.ejbca.ui.cli.batch;

import java.io.File;

import org.apache.log4j.Logger;
import org.ejbca.core.ejb.ca.CaTestCase;
import org.ejbca.core.ejb.ra.UserAdminSessionRemote;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.log.Admin;
import org.ejbca.util.InterfaceCache;

/** Tests the batch making of soft cards.
 *
 * @version $Id: BatchMakeP12Test.java 9566 2010-07-29 23:12:16Z jeklund $
 */

public class BatchMakeP12Test extends CaTestCase {
    private static final Logger log = Logger.getLogger(BatchMakeP12Test.class);
    private static final Admin admin = new Admin(Admin.TYPE_BATCHCOMMANDLINE_USER);
    private int caid = getTestCAId();

    private UserAdminSessionRemote userAdminSession = InterfaceCache.getUserAdminSession();
    
    /**
     * Creates a new TestBatchMakeP12 object.
     *
     * @param name name
     */
    public BatchMakeP12Test(String name) {
        super(name);
        assertTrue("Could not create TestCA.", createTestCA());
    }

    public void setUp() throws Exception {
    }

    public void tearDown() throws Exception {
    }

    /**
     * test creation of new user
     *
     * @throws Exception error
     */
    public void test01CreateNewUsers() throws Exception {
        log.trace(">test01CreateNewUser()");
        String username = genRandomUserName();
        Object o = null;
        try {
            userAdminSession.addUser(admin, username, "foo123", "C=SE, O=AnaTom, CN=" + username, "", username + "@anatom.se", false,
                    SecConst.EMPTY_ENDENTITYPROFILE, SecConst.CERTPROFILE_FIXED_ENDUSER,
                    SecConst.USER_ENDUSER, SecConst.TOKEN_SOFT_P12, 0, caid);
            userAdminSession.setClearTextPassword(admin, username, "foo123");
            o = new String("");
        } catch (Exception e) {
            assertNotNull("Failed to create user " + username, o);
        }

        log.debug("created " + username + ", pwd=foo123");

        String username1 = genRandomUserName();
        o = null;
        try {
        	userAdminSession.addUser(admin, username1, "foo123", "C=SE, O=AnaTom, CN=" + username1, "", username1 + "@anatom.se", false,
                    SecConst.EMPTY_ENDENTITYPROFILE, SecConst.CERTPROFILE_FIXED_ENDUSER,
                    SecConst.USER_ENDUSER, SecConst.TOKEN_SOFT_P12, 0, caid);
        	userAdminSession.setClearTextPassword(admin, username1, "foo123");
            o = new String("");
        } catch (Exception e) {
            assertNotNull("Failed to create user " + username1, o);
        }
        log.debug("created " + username1 + ", pwd=foo123");
        log.trace("<test01CreateNewUsers()");
    }

    /**
     * Tests creation of P12 file
     *
     * @throws Exception error
     */
    public void test02MakeP12() throws Exception {
        log.trace(">test02MakeP12()");

        BatchMakeP12 makep12 = new BatchMakeP12();
        File tmpfile = File.createTempFile("ejbca", "p12");

        //log.debug("tempdir="+tmpfile.getParent());
        makep12.setMainStoreDir(tmpfile.getParent());
        makep12.createAllNew();
        log.trace("<test02MakeP12()");
    }
    
	public void test99RemoveTestCA() throws Exception {
		removeTestCA();
	}
}
