/************************************************************************
 *																		*
 *  EJBCA: The OpenSource Certificate Authority							*
 *																		*
 *  This software is free software; you can redistribute it and/or		*
 *  modify it under the terms of the GNU Lesser General Public			*
 *  License as published by the Free Software Foundation; either		*
 *  version 2.1 of the License, or any later version.					*
 *																		*
 *  See terms of license at gnu.org.									*
 *																		*
 ***********************************************************************/
package org.ejbca.core.protocol.ws;

import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERObject;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;
import org.ejbca.core.model.AlgorithmConstants;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfileExistsException;
import org.ejbca.core.model.ca.certificateprofiles.EndUserCertificateProfile;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileExistsException;
import org.ejbca.core.protocol.ws.client.gen.AuthorizationDeniedException_Exception;
import org.ejbca.core.protocol.ws.client.gen.CADoesntExistsException_Exception;
import org.ejbca.core.protocol.ws.client.gen.CertificateResponse;
import org.ejbca.core.protocol.ws.client.gen.EjbcaException_Exception;
import org.ejbca.core.protocol.ws.client.gen.ExtendedInformationWS;
import org.ejbca.core.protocol.ws.client.gen.NotFoundException_Exception;
import org.ejbca.core.protocol.ws.client.gen.UserDataVOWS;
import org.ejbca.core.protocol.ws.common.CertificateHelper;
import org.ejbca.util.CertTools;
import org.ejbca.util.keystore.KeyTools;
import org.junit.Test;

/**
 * Test of certificate extensions with values from WS.
 * 
 * @author Lars Silvén
 * @version $Id: CertificateExtensionTest.java 12671 2011-09-21 16:57:30Z primelars $
 */
public class CertificateExtensionTest extends CommonEjbcaWS {

	private static final Logger log = Logger.getLogger(CertificateExtensionTest.class);
	private static final String CERTIFICATE_PROFILE = "certExtension";
	private static final String TEST_USER = "certExtension";
	private static final String END_ENTITY_PROFILE = "endEntityProfile";
	private static final String sOID_one = "1.2.3.4";
	private static final String sOID_several = "1.2.3.5";
	private static final int nrOfValues = 3;
	private static final Random random = new Random();

	@Test
	public void test0() throws CertificateProfileExistsException, EndEntityProfileExistsException {
		try {
			super.setupAccessRights();
		} catch (Exception e) {
			log.debug(e.getMessage());
		}
		if (this.certificateProfileSession.getCertificateProfileId(intAdmin, CERTIFICATE_PROFILE) != 0) {
			this.certificateProfileSession.removeCertificateProfile(intAdmin, CERTIFICATE_PROFILE);
		}
		final int certProfID; {
			final CertificateProfile profile = new EndUserCertificateProfile();
			final List<Integer> usedCertificateExtensions = new LinkedList<Integer>();
			usedCertificateExtensions.add(new Integer(1));
			usedCertificateExtensions.add(new Integer(2));
			profile.setUsedCertificateExtensions(usedCertificateExtensions);
			this.certificateProfileSession.addCertificateProfile(intAdmin, CERTIFICATE_PROFILE, profile);
			certProfID = this.certificateProfileSession.getCertificateProfileId(intAdmin, CERTIFICATE_PROFILE);
		}
		if ( this.endEntityProfileSession.getEndEntityProfile(intAdmin, END_ENTITY_PROFILE)!=null ) {
			this.endEntityProfileSession.removeEndEntityProfile(intAdmin, END_ENTITY_PROFILE);
		}
		{
			final EndEntityProfile profile = new EndEntityProfile(true);
			profile.setValue(EndEntityProfile.AVAILCERTPROFILES, 0, Integer.toString(certProfID));
			this.endEntityProfileSession.addEndEntityProfile(intAdmin, END_ENTITY_PROFILE, profile);
		}
	}

	@Test
	public void test1() throws Exception {
		super.setUpAdmin();
		getCertificateWithExtension(true);
	}

	@Test
	public void test2() throws Exception {
		super.setUpAdmin();
		getCertificateWithExtension(false);
	}

	private void getCertificateWithExtension(boolean isExpectedToWork) throws Exception {

		final byte[]values[] = getRandomValues(nrOfValues);
		final byte[]value[] = isExpectedToWork ? getRandomValues(1) : new byte[1][0];

		editUser(values, value[0]);
		final X509Certificate cert = getMyCertificate();
		if ( cert==null ) {
			assertTrue(!isExpectedToWork);
			return;
		}
		assertTrue(isExpectedToWork);
		checkExtension( value, cert.getExtensionValue(sOID_one), sOID_one );
		checkExtension( values, cert.getExtensionValue(sOID_several), sOID_several );
	}
	private void checkExtension(byte[] values[], byte extension[], String sOID) throws IOException {
		assertNotNull(getNoCertExtensionProperties(sOID), extension);
		final byte octets[]; {
			final ASN1Object asn1o = ASN1Object.fromByteArray(extension);
			assertNotNull(asn1o);
			log.info("The extension for the OID '"+sOID+"' of class '"+asn1o.getClass().getCanonicalName()+ "' is: "+asn1o);
			assertTrue(asn1o instanceof ASN1OctetString);
			octets = ((ASN1OctetString)asn1o).getOctets();
			if ( values.length==1 ) {
				assertArrayEquals(values[0], octets);
				return;
			}
		}
		final ASN1Sequence seq; {
			final ASN1Object asn1o = ASN1Object.fromByteArray(octets);
			log.info("The contents of the '"+sOID+"' can be decoded to a '"+asn1o.getClass().getCanonicalName()+ "' class.");
			assertTrue(asn1o instanceof ASN1Sequence);
			seq= (ASN1Sequence)asn1o;
		}
		assertEquals( values.length, seq.size() );
		for ( int i=0; i<seq.size(); i++ ) {
			final DERObject derO = seq.getObjectAt(i).getDERObject();
			assertTrue(derO instanceof ASN1OctetString);
			assertArrayEquals(values[i], ((ASN1OctetString)derO).getOctets());
		}
	}
	private byte[][] getRandomValues( int nr) {
		final byte values[][] = new byte[nr][400];
		for ( int i=0; i<nr; i++ ) {
			random.nextBytes(values[i]);
		}
		return values;
	}
	private void editUser( byte[] values[], byte value[] ) throws Exception {
		final UserDataVOWS userData = new UserDataVOWS(TEST_USER, PASSWORD, true, "C=SE, CN=cert extension test",
				getAdminCAName(), null, "foo@anatom.se", UserDataVOWS.STATUS_NEW,
				UserDataVOWS.TOKEN_TYPE_USERGENERATED, END_ENTITY_PROFILE, CERTIFICATE_PROFILE, null);
		final List<ExtendedInformationWS> lei = new LinkedList<ExtendedInformationWS>();
		for( int i=0; i<values.length; i++ ){
			final ExtendedInformationWS ei = new ExtendedInformationWS();
			ei.setName( sOID_several + ".value" + Integer.toString(i+1) );
			ei.setValue(new String(Hex.encode(values[i])));
			lei.add(ei);
		}
		if ( value!=null ){
			final ExtendedInformationWS ei = new ExtendedInformationWS();
			ei.setName( sOID_one );
			ei.setValue(new String(Hex.encode(value)));
			lei.add(ei);
		}
		userData.setExtendedInformation(lei);
		this.ejbcaraws.editUser(userData);
	}
	private X509Certificate getMyCertificate() throws GeneralSecurityException, AuthorizationDeniedException_Exception, CADoesntExistsException_Exception, NotFoundException_Exception {
		final KeyPair keys = KeyTools.genKeys("1024", AlgorithmConstants.KEYALGORITHM_RSA);
		final PKCS10CertificationRequest pkcs10 = new PKCS10CertificationRequest("SHA1WithRSA", CertTools.stringToBcX509Name("CN=NOUSED"), keys.getPublic(),
				new DERSet(), keys.getPrivate());

		final CertificateResponse certenv;
		try {
			certenv = this.ejbcaraws.pkcs10Request(TEST_USER, PASSWORD, new String(Base64.encode(pkcs10.getEncoded())), null,
					CertificateHelper.RESPONSETYPE_CERTIFICATE);
		} catch (EjbcaException_Exception e) {
			return null;
		}
		assertNotNull(certenv);
		assertTrue(certenv.getResponseType().equals(CertificateHelper.RESPONSETYPE_CERTIFICATE));
		return (X509Certificate)CertificateHelper.getCertificate(certenv.getData());
	}
	private String getNoCertExtensionProperties(String sOID) {
		final StringWriter sw = new StringWriter();
		final PrintWriter pw = new PrintWriter(sw);
		pw.println("No '"+sOID+"' extension in generated certificate.");
		pw.println("The reason might be that '"+sOID+"' is not defined in the file src/java/certextensions.properties .");
		pw.println("The files should look something like this:");
		pw.println();
		pw.println("id1.oid = "+sOID_one);
		pw.println("id1.classpath=org.ejbca.core.model.ca.certextensions.BasicCertificateExtension");
		pw.println("id1.displayname=SingleExtension");
		pw.println("id1.used=true");
		pw.println("id1.translatable=false");
		pw.println("id1.critical=false");
		pw.println("id1.property.dynamic=true");
		pw.println("id1.property.encoding=RAW");
		pw.println();
		pw.println("id2.oid = "+sOID_several);
		pw.println("id2.classpath=org.ejbca.core.model.ca.certextensions.BasicCertificateExtension");
		pw.println("id2.displayname=MultipleExtension");
		pw.println("id2.used=true");
		pw.println("id2.translatable=false");
		pw.println("id2.critical=false");
		pw.println("id2.property.dynamic=true");
		pw.println("id2.property.nvalues="+nrOfValues);
		pw.println("id2.property.encoding=DEROCTETSTRING");
		pw.flush();
		return sw.toString();
	}
	
	public void test99cleanUpAdmins() {
		try {
			this.certificateProfileSession.removeCertificateProfile(intAdmin, CERTIFICATE_PROFILE);
		} catch (Throwable e) {
			// do nothing
		}
		try {
			this.endEntityProfileSession.removeEndEntityProfile(intAdmin, END_ENTITY_PROFILE);
		} catch (Throwable e) {
			// do nothing
		}
		try {
			this.userAdminSession.deleteUser(intAdmin, TEST_USER);
		} catch (Throwable e) {
			// do nothing
		}
		try {
			super.cleanUpAdmins();
		} catch (Throwable e) {
			// do nothing
		}
	}
}
