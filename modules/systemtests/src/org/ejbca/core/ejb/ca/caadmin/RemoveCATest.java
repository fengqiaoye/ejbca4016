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

package org.ejbca.core.ejb.ca.caadmin;

import org.apache.log4j.Logger;
import org.cesecore.core.ejb.ca.store.CertificateProfileSessionRemote;
import org.ejbca.core.ejb.ca.CaTestCase;
import org.ejbca.core.model.log.Admin;
import org.ejbca.util.CertTools;
import org.ejbca.util.InterfaceCache;

/**
 * Tests and removes the ca data entity bean.
 *
 * @version $Id: RemoveCATest.java 11526 2011-03-16 12:03:24Z netmackan $
 */
public class RemoveCATest extends CaTestCase {
    private static final Logger log = Logger.getLogger(CAsTest.class);
    private static final Admin admin = new Admin(Admin.TYPE_CACOMMANDLINE_USER);

    private CaSessionRemote caSession = InterfaceCache.getCaSession();
    private CertificateProfileSessionRemote certificateProfileSession = InterfaceCache.getCertificateProfileSession();
    
    /**
     * Creates a new TestCAs object.
     *
     * @param name name
     */
    public RemoveCATest(String name) {
        super(name);
    }

    public void setUp() throws Exception {
    }

    public void tearDown() throws Exception {
    }

    /**
     * removes RSA CA
     *
     * @throws Exception error
     */
    public void test02removeRSACA() throws Exception {
        log.trace(">test02removeRSACA()");
        assertTrue("Removing RSA CA failed", removeTestCA("TEST"));
        log.trace("<test02removeRSACA()");
    }

    /**
     * removes ECDSA CA
     *
     * @throws Exception error
     */
    public void test03removeECDSACA() throws Exception {
        log.trace(">test03removeECDSACA()");
        boolean ret = false;
        try {
            caSession.removeCA(admin, "CN=TESTECDSA".hashCode());
            ret = true;
        } catch (Exception pee) {
        }
        assertTrue("Removing ECDSA CA failed", ret);

        try {
            caSession.removeCA(admin, "CN=TESTECDSAImplicitlyCA".hashCode());
            ret = true;
        } catch (Exception pee) {
        }
        assertTrue("Removing ECDSA ImplicitlyCA CA failed", ret);

        log.trace("<test03removeECDSACA()");
    }

    /**
     * removes RSA CA
     *
     * @throws Exception error
     */
    public void test04removeRSASha256WithMGF1CA() throws Exception {
        log.trace(">test04removeRSASha256WithMGF1CA()");
        boolean ret = false;
        try {
            caSession.removeCA(admin, "CN=TESTSha256WithMGF1".hashCode());
            ret = true;
        } catch (Exception pee) {
        }
        assertTrue("Removing RSA SHA256WithMGF1 CA failed", ret);

        log.trace("<test04removeRSASha256WithMGF1CA()");
    }

    public void test05removeRSACA4096() throws Exception {
        log.trace(">test05removeRSACA4096()");
        boolean ret = false;
        try {
        	String dn = CertTools.stringToBCDNString("CN=TESTRSA4096,OU=FooBaaaaaar veeeeeeeery long ou,OU=Another very long very very long ou,O=FoorBar Very looong O,L=Lets ad a loooooooooooooooooong Locality as well,C=SE");
        	caSession.removeCA(admin, dn.hashCode());
            ret = true;
        } catch (Exception e) {
        	log.info("Remove failed: ", e);
        }
        assertTrue("Removing RSA CA 4096 failed", ret);
        log.trace("<test05removeRSACA4096()");
    }
    
    public void test06removeRSACAReverse() throws Exception {
        log.trace(">test06removeRSACAReverse()");
        boolean ret = false;
        try {
        	String dn = CertTools.stringToBCDNString("CN=TESTRSAReverse,O=FooBar,OU=BarFoo,C=SE");
        	caSession.removeCA(admin, dn.hashCode());
            ret = true;
        } catch (Exception e) {
        	log.info("Remove failed: ", e);
        }
        assertTrue("Removing RSA CA Reverse failed", ret);
        log.trace("<test06removeRSACAReverse()");
    }

    public void test07removeCVCCA() throws Exception {
        log.trace(">test07removeCVCCA()");
        boolean ret = false;
        try {
        	String dn = CertTools.stringToBCDNString("CN=TESTCVCA,C=SE");
        	caSession.removeCA(admin, dn.hashCode());
            ret = true;
        } catch (Exception e) {
        	log.info("Remove failed: ", e);
        }
        try {
        	String dn = CertTools.stringToBCDNString("CN=TESTDV-D,C=SE");
        	caSession.removeCA(admin, dn.hashCode());
            ret = true;
        } catch (Exception e) {
        	log.info("Remove failed: ", e);
        }
        try {
        	String dn = CertTools.stringToBCDNString("CN=TESTDV-F,C=FI");
        	caSession.removeCA(admin, dn.hashCode());
            ret = true;
        } catch (Exception e) {
        	log.info("Remove failed: ", e);
        }
        // test10AddCVCCAECC
        try {
        	String dn = CertTools.stringToBCDNString("CN=TCVCAEC,C=SE");
        	caSession.removeCA(admin, dn.hashCode());
            ret = true;
        } catch (Exception e) {
        	log.info("Remove failed: ", e);
        }
        try {
        	String dn = CertTools.stringToBCDNString("CN=TDVEC-D,C=SE");
        	caSession.removeCA(admin, dn.hashCode());
            ret = true;
        } catch (Exception e) {
        	log.info("Remove failed: ", e);
        }
        try {
        	String dn = CertTools.stringToBCDNString("CN=TDVEC-F,C=FI");
        	caSession.removeCA(admin, dn.hashCode());
            ret = true;
        } catch (Exception e) {
        	log.info("Remove failed: ", e);
        }

        assertTrue("Removing CVC CA failed", ret);

        try {
        	certificateProfileSession.removeCertificateProfile(admin, "TESTCVCDV");
        } catch (Exception e) {
        	log.info("Remove profile failed: ", e);
        }
        log.trace("<test07removeCVCCA()");
    }

    public void test09removeRSASignedByExternal() throws Exception {
        log.trace(">test09removeRSASignedByExternal()");
        boolean ret = false;
        try {
            caSession.removeCA(admin, "CN=TESTSIGNEDBYEXTERNAL".hashCode());
            ret = true;
        } catch (Exception pee) {
        }
        assertTrue("Removing RSA CA failed", ret);
        log.trace("<test09removeRSASignedByExternal()");
    }
    
    public void test10removeDSACA() throws Exception {
        log.trace(">test10removeDSACA()");
        boolean ret = false;
        try {
            caSession.removeCA(admin, "CN=TESTDSA".hashCode());
            ret = true;
        } catch (Exception pee) {
        }
        assertTrue("Removing DSA CA failed", ret);

        log.trace("<test10removeDSACA()");
    }

    public void test11removeRevokeCA() throws Exception {
        log.trace(">test11removeRevokeCA()");
        boolean ret = false;
        try {
            caSession.removeCA(admin, "CN=TestRevokeCA".hashCode());
            ret = true;
        } catch (Exception pee) {
        }
        assertTrue("Removing Revoke CA failed", ret);

        log.trace("<test11removeRevokeCA()");
    }

}
