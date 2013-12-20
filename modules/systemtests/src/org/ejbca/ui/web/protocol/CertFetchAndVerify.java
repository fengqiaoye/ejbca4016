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
 
package org.ejbca.ui.web.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Set;

import javax.activation.DataSource;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeMultipart;

import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.ejbca.config.WebConfiguration;
import org.ejbca.core.protocol.certificatestore.HashID;
import org.ejbca.util.InterfaceCache;

/**
 * Tests that it is possible to find certificates for all CAs that has been created.
 * Found CA certificates issued by old CAs that has been renewed are checked. It is checked that the whole chin to the root are in the DB for this certificates.
 * Note that old CA certificates will only be found if there is now newer with same issuer and subject DN.
 * The test is started by calling {@link #doIt(X509Certificate, Set)}
 * 
 * @author Lars Silven PrimeKey
 * @version $Id: CertFetchAndVerify.java 12798 2011-10-03 13:38:29Z primelars $
 *
 */
class CertFetchAndVerify {
	private final static Logger log = Logger.getLogger(CertFetchAndVerify.class);
	private final CertificateFactory cf;
	CertFetchAndVerify() throws CertificateException {
		this.cf = CertificateFactory.getInstance("X.509");
	}
	/**
	 * One could thin that the class javax.activation.URLDataSource should be usable when connection to a server to retrieve a multipart
	 * message, but it is not. URLDataSource makes two connections when a message is received.
	 *
	 */
	static private class MyDataSource implements DataSource {
		final private HttpURLConnection connection;
		MyDataSource(URL url ) throws MalformedURLException, IOException, NoData {
			this.connection = (HttpURLConnection)url.openConnection();
			this.connection.connect();
			final int reponseCode = this.connection.getResponseCode();
			if ( reponseCode==HttpURLConnection.HTTP_NO_CONTENT ) {
				throw new NoData();
			}
			Assert.assertEquals("Fetching CRL with '"+url+"' is not working. Response code is :"+reponseCode,
			                    HttpURLConnection.HTTP_OK, reponseCode );
		}
		/**
		 * 
		 */
		class NoData extends Exception {
			// nothing
		}
		/* (non-Javadoc)
		 * @see javax.activation.DataSource#getContentType()
		 */
		@Override
		public String getContentType() {
			final String contentType = this.connection.getContentType();
			log.trace("content type: "+contentType);
			return contentType;
		}
		/* (non-Javadoc)
		 * @see javax.activation.DataSource#getInputStream()
		 */
		@Override
		public InputStream getInputStream() throws IOException {
			return this.connection.getInputStream();
		}
		/* (non-Javadoc)
		 * @see javax.activation.DataSource#getName()
		 */
		@Override
		public String getName() {
			return "my name";
		}
		/* (non-Javadoc)
		 * @see javax.activation.DataSource#getOutputStream()
		 */
		@Override
		public OutputStream getOutputStream() throws IOException {
			return null;
		}
	}
	private static String theURL;	// = "http://localhost:8080/certificates/search.cgi";
	static String getURL() {
		if (theURL == null) {
			try {
				String port = InterfaceCache.getConfigurationSession().getProperty(WebConfiguration.CONFIG_HTTPSERVERPUBHTTP, "8080");
				theURL = "http://localhost:" + port + "/certificates/search.cgi"; // Fallback, like if we run tests on a stand-alone VA
			} catch (Exception e) {
				theURL = "http://localhost:8080/certificates/search.cgi"; // Fallback, like if we run tests on a stand-alone VA
			}
		}
		return theURL;
	}
	
	private X509Certificate getCert(RFC4387URL url, HashID id) throws CertificateException, IOException, URISyntaxException {
		final String sURI = url.appendQueryToURL(getURL(), id);
		log.debug("URL: '"+sURI+"'.");
		final HttpURLConnection connection = (HttpURLConnection)new URI(sURI).toURL().openConnection();
		connection.connect();
		Assert.assertTrue( "Fetching CRL with '"+sURI+"' is not working.", HttpURLConnection.HTTP_OK==connection.getResponseCode() );
		return (X509Certificate)this.cf.generateCertificate(connection.getInputStream());
	}
	private void checkIssuer( X509Certificate bottom ) throws IOException, CertificateException, URISyntaxException {
		final String bottomName = bottom.getSubjectX500Principal().getName();
		final HashID keyID = HashID.getFromAuthorityKeyId(bottom);
		if ( keyID==null ) {
			log.info("No authority key ID in certificate: "+bottomName);
			return; // can not be checked
		}
		final X509Certificate upper = getCert(RFC4387URL.sKIDHash, keyID);
		final String upperName = upper.getSubjectX500Principal().getName();
		try {
			bottom.verify(upper.getPublicKey());
		} catch (GeneralSecurityException e) {
			Assert.assertTrue("The certificate '"+bottomName+"' is not signed by '"+upperName+"'.", false);
			return;
		}
		if ( upper.getSubjectX500Principal().equals(upper.getIssuerX500Principal()) ) {
			log.info("The old CA '"+bottomName+"' is signed by the old CA '"+upperName+"' which is a root CA.");
			return;
		}
		log.info("The old CA '"+bottomName+"' is signed by the old CA '"+upperName+"'.");
		checkIssuer(upper);
	}
	/**
	 * Finds and verifies a CA certificate.
	 * @param theCert Certificate to be tested.
	 * @param setOfSubjectKeyIDs When a CA certificate has been verified it is removed from this set.
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws URISyntaxException
	 * @throws CertificateException
	 * @throws MessagingException
	 */
	void doIt(X509Certificate theCert, Set<Integer> setOfSubjectKeyIDs) throws MalformedURLException, IOException, URISyntaxException, CertificateException, MessagingException {
        // Before running this we need to make sure the certificate cache is refreshed, there may be a cache delay which is acceptable in real life, 
        // but not when running JUnit tests  
		final String reloadURI = getURL() + "?reloadcache=true";
		log.debug("Reload cache URL: '"+reloadURI+"'.");
		final HttpURLConnection connection = (HttpURLConnection)new URI(reloadURI).toURL().openConnection();
		connection.connect();
		log.debug("reloadcache returned code: "+connection.getResponseCode());
		
		// Now on to the actual tests, with fresh caches
		log.info("Testing certificate: "+theCert.getSubjectX500Principal().getName());

		final HashID subjectID = HashID.getFromSubjectDN(theCert);
		Assert.assertEquals("Certificate fetched by subject DN was wrong.", theCert, getCert(RFC4387URL.sHash, subjectID));

		final HashID keyID = HashID.getFromKeyID(theCert);
		Assert.assertEquals("Certificate fetched by public key ID was wrong.", theCert, getCert(RFC4387URL.sKIDHash, keyID));

		final String sURI = RFC4387URL.iHash.appendQueryToURL(getURL(), subjectID);
		// remove keyID from list of CA keyIds to be checked.
		Assert.assertTrue("The certificate '"+theCert.getSubjectX500Principal().getName()+"' already tested.", setOfSubjectKeyIDs.remove(keyID.key));
		log.debug("URL: '"+sURI+"'.");
		final Multipart multipart;
		try {
			multipart = new MimeMultipart(new MyDataSource(new URI(sURI).toURL()));
		} catch (MyDataSource.NoData e1) {
			return; // no sub CAs to the this CA
		}
		final int nrOfCerts = multipart.getCount();
		for ( int i=0; i<nrOfCerts; i++ ) {
			final X509Certificate cert = (X509Certificate)this.cf.generateCertificate(multipart.getBodyPart(i).getInputStream());
			try {
				cert.verify(theCert.getPublicKey());
			} catch (GeneralSecurityException e) {
				// CA probably signed by an old not any longer existing CA. But this old CA certificate should still be in the DB. Let's check the chain.
				checkIssuer(cert);
				continue;
			}
			doIt(cert, setOfSubjectKeyIDs);
		}
	}
}
