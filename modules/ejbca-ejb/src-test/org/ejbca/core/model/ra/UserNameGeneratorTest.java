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

package org.ejbca.core.model.ra;

import junit.framework.TestCase;

import org.bouncycastle.asn1.x509.X509Name;

/**
 * Test of the UserNameGenerator class.
 * 
 * @version $Id: UserNameGeneratorTest.java 8865 2010-04-09 15:14:51Z mikekushner $
 */
public class UserNameGeneratorTest extends TestCase {

    public UserNameGeneratorTest(String testName) {
        super(testName);
    }

    /**
     * Test user generation based on both SN and CN.
     */
	public void test01() throws Exception {
		UsernameGeneratorParams usernameGeneratorParams = new UsernameGeneratorParams();
		usernameGeneratorParams.setMode("DN");
		usernameGeneratorParams.setDNGeneratorComponent("SN;CN");
		usernameGeneratorParams.setPrefix(null);
		usernameGeneratorParams.setPostfix(null);
		UsernameGenerator usernameGenerator = UsernameGenerator.getInstance(usernameGeneratorParams);

		final String errorMessage = "Did not generate an expected username.";
		assertEquals(errorMessage, "test", usernameGenerator.generateUsername(new X509Name("CN=test").toString()));
		assertEquals(errorMessage, null, usernameGenerator.generateUsername("".toString()));
		assertEquals(errorMessage, null, usernameGenerator.generateUsername(" ".toString()));
		assertEquals(errorMessage, "test", usernameGenerator.generateUsername(new X509Name("CN=test, serialNumber=1234").toString()));
		assertEquals(errorMessage, null, usernameGenerator.generateUsername(new X509Name("O=org").toString()));
		assertEquals(errorMessage, "12345", usernameGenerator.generateUsername("CN=test, SN=12345"));
		assertEquals(errorMessage, "1234", usernameGenerator.generateUsername("SN=1234"));
		
		// These wont work since new X509Name converts SN to SERIALNUMBER in toString()
		// Is this something we should compensate for in CertTools.getPartFromDN(...) ?
		//assertEquals(errorMessage, "12345", usernameGenerator.generateUsername(new X509Name("CN=test, SN=12345").toString()));
		//assertEquals(errorMessage, "1234", usernameGenerator.generateUsername(new X509Name("SN=1234").toString()));
	}
	
	/*
	 * Test method for 'org.ejbca.core.model.ra.UsernameGenerator.UsernameGenerator(String)'
	 */
	public void testUsernameGeneratorRandom() {
		UsernameGenerator gen = UsernameGenerator.getInstance(UsernameGeneratorParams.RANDOM);
		String u = gen.generateUsername();
		assertEquals(u.length(), 12);
		
		UsernameGeneratorParams params = gen.getParams();
		params.setRandomNameLength(5);
		gen.setParams(params);
		u = gen.generateUsername();
		assertEquals(u.length(), 5);
		
		params.setPrefix("foo-");
		gen.setParams(params);
		u = gen.generateUsername();
		assertEquals(u.length(), 9);
		assertTrue(u.startsWith("foo-"));

		params.setPostfix("-foo");
		params.setPrefix(null);
		gen.setParams(params);
		u = gen.generateUsername();
		assertEquals(u.length(), 9);
		assertTrue(u.endsWith("-foo"));
		
		params.setPrefix("foo-${RANDOM}");
		params.setPostfix(null);
		params.setRandomPrefixLength(6);
		gen.setParams(params);
		u = gen.generateUsername();
		assertEquals(u.length(), 15);
		assertTrue(u.startsWith("foo-"));

		params.setPostfix("${RANDOM}-foo");
		params.setPrefix(null);
		params.setRandomPrefixLength(5);
		gen.setParams(params);
		u = gen.generateUsername();
		assertEquals(u.length(), 14);
		assertTrue(u.endsWith("-foo"));
		
		params.setPrefix("foo-");
		gen.setParams(params);
		u = gen.generateUsername();
		assertEquals(u.length(), 18);
		assertTrue(u.endsWith("-foo"));
		assertTrue(u.startsWith("foo-"));
	}

	public void testUsernameGeneratorDN() {
		String dn = "C=SE, O=FooO, UID=foo, CN=bar";
		UsernameGenerator gen = UsernameGenerator.getInstance(UsernameGeneratorParams.DN);
		String u = gen.generateUsername(dn);
		assertEquals(u, "bar");
		
		UsernameGeneratorParams params = gen.getParams();
		params.setDNGeneratorComponent("UID");
		gen.setParams(params);
		u = gen.generateUsername(dn);
		assertEquals(u, "foo");
		
		params.setPrefix("pre-");
		gen.setParams(params);
		u = gen.generateUsername(dn);
		assertEquals(u, "pre-foo");

		params.setPostfix("-post");
		params.setPrefix(null);
		gen.setParams(params);
		u = gen.generateUsername(dn);
		assertEquals(u, "foo-post");
		
		params.setPrefix("pre-${RANDOM}-");
		params.setPostfix(null);
		params.setRandomPrefixLength(6);
		gen.setParams(params);
		u = gen.generateUsername(dn);
		assertEquals(u.length(), 14);
		assertTrue(u.startsWith("pre-"));
		assertTrue(u.endsWith("-foo"));

		params.setPostfix("-${RANDOM}-post");
		params.setPrefix(null);
		params.setRandomPrefixLength(5);
		gen.setParams(params);
		u = gen.generateUsername(dn);
		assertEquals(u.length(), 14);
		assertTrue(u.startsWith("foo-"));
		assertTrue(u.endsWith("-post"));		
	}

	public void testUsernameGeneratorUsername() {
		String username = "foo";
		UsernameGenerator gen = UsernameGenerator.getInstance(UsernameGeneratorParams.USERNAME);
		String u = gen.generateUsername(username);
		assertEquals(u, "foo");
		
		UsernameGeneratorParams params = gen.getParams();
		params.setPrefix("pre-");
		gen.setParams(params);
		u = gen.generateUsername(username);
		assertEquals(u, "pre-foo");

		params.setPostfix("-post");
		params.setPrefix(null);
		gen.setParams(params);
		u = gen.generateUsername(username);
		assertEquals(u, "foo-post");
		
		params.setPrefix("pre-${RANDOM}-");
		params.setPostfix(null);
		params.setRandomPrefixLength(6);
		gen.setParams(params);
		u = gen.generateUsername(username);
		assertEquals(u.length(), 14);
		assertTrue(u.startsWith("pre-"));
		assertTrue(u.endsWith("-foo"));

		params.setPostfix("-${RANDOM}-post");
		params.setPrefix(null);
		params.setRandomPrefixLength(5);
		gen.setParams(params);
		u = gen.generateUsername(username);
		assertEquals(u.length(), 14);
		assertTrue(u.startsWith("foo-"));
		assertTrue(u.endsWith("-post"));		
	}

}
