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

package org.ejbca.ui.cli.ca;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.ca.crl.RevokedCertInfo;
import org.ejbca.core.model.ra.UserDataConstants;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.ui.cli.ErrorAdminCommandException;
import org.ejbca.util.CertTools;
import org.ejbca.util.CryptoProviderTools;
import org.ejbca.util.FileTools;

/**
 * Imports certificate files to the database for a given CA
 *
 * @author Anders Rundgren
 * @version $Id: CaImportCertDirCommand.java 11821 2011-04-26 13:40:25Z anatom $
 */
public class CaImportCertDirCommand extends BaseCaAdminCommand {

	@Override
	public String getMainCommand() { return MAINCOMMAND; }
	@Override
	public String getSubCommand() { return "importcertdir"; }
	@Override
	public String getDescription() { return "Imports a directory with PEM encoded certficate file(s) to the database"; }

	private static final int STATUS_OK = 0;
	private static final int STATUS_REDUNDANT = 1;
	private static final int STATUS_REJECTED = 2;

	@Override
    public void execute(String[] args) throws ErrorAdminCommandException {
		getLogger().trace(">execute()");
		CryptoProviderTools.installBCProvider();
		if (args.length != 7) {
			usage();
			return;
		}
		try {
			// Parse arguments into more coder friendly variable names and validate switches
			final String usernameFilter = args[1];
			final String caName = args[2];
			final String active = args[3];
			final String certificateDir = args[4];
			final String eeProfile = args[5];
			final String certificateProfile = args[6];				
			final int status;
			if ("ACTIVE".equalsIgnoreCase(active)) {
				status = SecConst.CERT_ACTIVE;
			} else if ("REVOKED".equalsIgnoreCase(active)) {
				status = SecConst.CERT_REVOKED;
			} else {
				throw new Exception("Invalid certificate status.");
			}
			if (!usernameFilter.equalsIgnoreCase("DN") && 
					!usernameFilter.equalsIgnoreCase ("CN") &&
					!usernameFilter.equalsIgnoreCase("FILE")) {
				throw new Exception(usernameFilter + "is not a valid option. Currently only \"DN\", \"CN\" and \"FILE\" username-source are implemented");
			}
			// Fetch CA info
			final CAInfo caInfo = getCAInfo(caName);
			final X509Certificate cacert = (X509Certificate) caInfo.getCertificateChain().iterator().next();
			final String issuer = CertTools.stringToBCDNString(cacert.getSubjectDN().toString());
			getLogger().info("CA: " + issuer);
			// Fetch End Entity Profile info
			getLogger().debug("Searching for End Entity Profile " + eeProfile);
			final int endEntityProfileId = ejb.getEndEntityProfileSession().getEndEntityProfileId(getAdmin(), eeProfile);
			if (endEntityProfileId == 0) {
				getLogger().error("End Entity Profile " + eeProfile + " doesn't exists.");
				throw new Exception("End Entity Profile '" + eeProfile + "' doesn't exists.");
			}
			// Fetch Certificate Profile info
			getLogger().debug("Searching for Certificate Profile " + certificateProfile);
			int certificateProfileId = ejb.getCertificateProfileSession().getCertificateProfileId(getAdmin(), certificateProfile);
			if (certificateProfileId == SecConst.PROFILE_NO_PROFILE) {
				getLogger().error("Certificate Profile " + certificateProfile + " doesn't exists.");
				throw new Exception("Certificate Profile '" + certificateProfile + "' doesn't exists.");
			}
			// Get all files in the directory to import from and try to read and import each as a certificate
			final File dir = new File(certificateDir);
			if ( !dir.isDirectory() ) {
				throw new IOException ("'"+certificateDir+"' is not a directory.");
			}
			final File files[] = dir.listFiles();
			if ( files==null || files.length<1 ) {
				throw new IOException("No files in directory '" + dir.getCanonicalPath() + "'. Nothing to do.");
			}
			int redundant = 0;
			int rejected = 0;
			int count = 0;
			for (final File file : files) {
				final X509Certificate certificate = (X509Certificate) loadcert(file.getCanonicalPath());
				final String filename = file.getName();
				String username = usernameFilter.equalsIgnoreCase("FILE") ? 
						 filename : CertTools.getSubjectDN(certificate);
				if (usernameFilter.equalsIgnoreCase("CN")) {
					String cn = CertTools.getPartFromDN(username, "CN");
					// Workaround for "difficult" certificates lacking CNs
					if (cn == null || cn.length () == 0) {
						getLogger ().info("Certificate '" + CertTools.getSerialNumberAsString(certificate) + "' lacks CN, DN used instead, file: " +filename);
					} else {
						username = cn;
					}
				}
				switch (performImport(certificate, status, endEntityProfileId, certificateProfileId, cacert, caInfo, filename, issuer, username)) {
				case STATUS_REDUNDANT: redundant++; break;
				case STATUS_REJECTED: rejected++; break;
				default: count++;
				}
			}
			// Print resulting statistics
			getLogger().info("\nSummary:\nImported " + count + " certificates");
			if (redundant > 0) {
				getLogger().info(redundant + " certificates were already in the database");
			}
			if (rejected > 0) {
				getLogger().info(rejected + " certificates were rejected because they did not belong to the CA");
			}
		} catch (Exception e) {
			getLogger().info("Error: " + e.getMessage());
		}
		getLogger().trace("<execute()");
	}

	/**
	 * Imports a certificate to the database and creates a user if necessary.
	 * @return STATUS_OK, STATUS_REDUNDANT or STATUS_REJECTED
	 */
	private int performImport(X509Certificate certificate, int status, int endEntityProfileId, int certificateProfileId,
			                   X509Certificate cacert, CAInfo caInfo, String filename, String issuer, String username) throws Exception {
		final String fingerprint = CertTools.getFingerprintAsString(certificate);
		if (ejb.getCertStoreSession().findCertificateByFingerprint(getAdmin(), fingerprint) != null) {
			getLogger ().info("Certificate '" + CertTools.getSerialNumberAsString(certificate) + "' is already present, file: " +filename);
			return STATUS_REDUNDANT;
		}
		final Date now = new Date();
		// Certificate has expired, but we are obviously keeping it for archival purposes
		if (CertTools.getNotAfter(certificate).compareTo(now) < 0) {
			status = SecConst.CERT_ARCHIVED;
		}
		if (!cacert.getSubjectX500Principal().getName().equals(certificate.getIssuerX500Principal().getName())){
			getLogger().info("REJECTED, CA issuer mismatch, file: " + filename);
			return STATUS_REJECTED;
		}
		try {
			certificate.verify(cacert.getPublicKey());
		} catch (GeneralSecurityException gse) {
			getLogger().info("REJECTED, CA signature mismatch,file: " + filename);
			return STATUS_REJECTED;
		}
		getLogger().debug("Loading/updating user " + username);
		// Check if username already exists.
		UserDataVO userdata = ejb.getUserAdminSession().findUser(getAdmin(), username);
		if (userdata==null) {
			// Add a "user" to map this certificate to
			final String subjectAltName = CertTools.getSubjectAlternativeName(certificate);
			final String email = CertTools.getEMailAddress(certificate);				
			userdata = new UserDataVO(username, CertTools.getSubjectDN(certificate), caInfo.getCAId(), subjectAltName, email,
					UserDataConstants.STATUS_GENERATED, SecConst.USER_ENDUSER, endEntityProfileId,
					certificateProfileId, null, null, SecConst.TOKEN_SOFT_BROWSERGEN, SecConst.NO_HARDTOKENISSUER, null);
			userdata.setPassword("foo123");
			ejb.getUserAdminSession().addUser(getAdmin(), userdata, false);
			getLogger().info("User '" + username + "' has been added.");
		}
		// addUser always adds the user with STATUS_NEW (even if we specified otherwise)
		// We always override the userdata with the info from the certificate even if the user existed.
		userdata.setStatus(UserDataConstants.STATUS_GENERATED);
		ejb.getUserAdminSession().changeUser(getAdmin(), userdata, false);
		getLogger().info("User '" + username + "' has been updated.");
		// Finally import the certificate and revoke it if necessary
		ejb.getCertStoreSession().storeCertificate(getAdmin(), certificate, username, fingerprint, SecConst.CERT_ACTIVE, SecConst.USER_ENDUSER, certificateProfileId, null, now.getTime());
		if (status == SecConst.CERT_REVOKED) {
			ejb.getUserAdminSession().revokeCert(getAdmin(), certificate.getSerialNumber(), issuer, RevokedCertInfo.REVOCATION_REASON_UNSPECIFIED);
		}
		getLogger().info("Certificate '" + CertTools.getSerialNumberAsString(certificate) + "' has been added.");
		return STATUS_OK;
	}

	/** Print out usage. */
	private void usage() {
		getLogger().info("Description: " + getDescription());
		getLogger().info("Usage: " + getCommand() + " <username-source> <caname> <status> <certificate dir> <endentityprofile> <certificateprofile>");
		getLogger().info(" Username-source: \"DN\" means use certificate's SubjectDN as username, \"CN\" means use certificate subject's common name as username and \"FILE\" means user the file's name as username");
		// List available CAs by name
		getLogger().info(" Available CAs: " + getAvailableCasString());
		getLogger().info(" Status: ACTIVE, REVOKED");
		getLogger().info(" Certificate dir: A directory where all files are PEM encoded certificates");
		getLogger().info(" Available end entity profiles: " + getAvailableEepsString());
		getLogger().info(" Available certificate profiles: " + getAvailableEndUserCpsString());
	}
	
	/** Load a PEM encoded certificate from the specified file. */
	private Certificate loadcert(final String filename) throws Exception {
		try {
			final byte[] bytes = FileTools.getBytesFromPEM(FileTools.readFiletoBuffer(filename), "-----BEGIN CERTIFICATE-----", "-----END CERTIFICATE-----");
			return CertTools.getCertfromByteArray(bytes);
		} catch (IOException ioe) {
			throw new Exception("Error reading " + filename + ": " + ioe.toString());
		} catch (CertificateException ce) {
			throw new Exception(filename + " is not a valid X.509 certificate: " + ce.toString());
		} catch (Exception e) {
			throw new Exception("Error parsing certificate from " + filename + ": " + e.toString());
		}
	}
}
