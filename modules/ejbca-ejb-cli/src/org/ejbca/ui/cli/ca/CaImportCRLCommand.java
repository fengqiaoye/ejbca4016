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

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509CRL;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;

import javax.security.auth.x500.X500Principal;

import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.ejbca.core.model.AlgorithmConstants;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.ca.crl.RevokedCertInfo;
import org.ejbca.core.model.ra.AlreadyRevokedException;
import org.ejbca.core.model.ra.UserDataConstants;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.ui.cli.ErrorAdminCommandException;
import org.ejbca.util.CertTools;
import org.ejbca.util.CryptoProviderTools;
import org.ejbca.util.cert.CrlExtensions;
import org.ejbca.util.keystore.KeyTools;

/**
 * Imports a CRL file to the database.
 *
 * @author Anders Rundgren
 * @version $Id: CaImportCRLCommand.java 14975 2012-06-16 12:28:32Z primelars $
 */
public class CaImportCRLCommand extends BaseCaAdminCommand {

	@Override
	public String getMainCommand() { return MAINCOMMAND; }
	@Override
	public String getSubCommand() { return "importcrl"; }
	@Override
	public String getDescription() { return "Imports a CRL file (and update certificates) to the database"; }

	private static final String STRICT_OP = "STRICT";
	private static final String LENIENT_OP = "LENIENT";
	private static final String ADAPTIVE_OP = "ADAPTIVE";

	@Override
	public void execute(String[] args) throws ErrorAdminCommandException {
		getLogger().trace(">execute()");
		CryptoProviderTools.installBCProvider();
		if (args.length != 4 || (!args[3].equalsIgnoreCase(STRICT_OP) && !args[3].equalsIgnoreCase(LENIENT_OP) && !args[3].equalsIgnoreCase(ADAPTIVE_OP))) {
			usage();
			return;
		}
		try {
			// Parse arguments
			final String caname = args[1];
			final String crl_file = args[2];
			final boolean strict = args[3].equalsIgnoreCase(STRICT_OP);
			final boolean adaptive = args[3].equalsIgnoreCase(ADAPTIVE_OP);
			// Fetch CA and related info
			final CAInfo cainfo = getCAInfo(caname);
			final X509Certificate cacert = (X509Certificate) cainfo.getCertificateChain().iterator().next();
			final String issuer = CertTools.stringToBCDNString(cacert.getSubjectDN().toString());
			getLogger().info("CA: " + issuer);
			// Read the supplied CRL and verify that it is issued by the specified CA
			final X509CRL x509crl = (X509CRL) CertTools.getCertificateFactory().generateCRL(new FileInputStream (crl_file));
			if (!x509crl.getIssuerX500Principal().getName().equals(cacert.getSubjectX500Principal().getName())){
				throw new IOException ("CRL wasn't issued by this CA");
			}
			x509crl.verify(cacert.getPublicKey());
			int crl_no = CrlExtensions.getCrlNumber(x509crl).intValue();
			getLogger().info("Processing CRL #" + crl_no);
			int miss_count = 0;	// Number of certs not already in database
			int revoked = 0;	// Number of certs activly revoked by this algorithm
			int already_revoked = 0;	// Number of certs already revoked in database and ignored in non-strict mode
			final String missing_user_name = "*** Missing During CRL Import to: " + caname;
			Set<X509CRLEntry> revoked_certs = (Set<X509CRLEntry>)x509crl.getRevokedCertificates();
			if (revoked_certs != null) {
				for (final X509CRLEntry entry : revoked_certs) {
					final BigInteger serialNr = entry.getSerialNumber();
					final String serialHex = serialNr.toString(16).toUpperCase();
					final String username = ejb.getCertStoreSession().findUsernameByCertSerno(getAdmin(), serialNr, issuer);
					// If this certificate exists and has an assigned username, we keep using that. Otherwise we create this coupling to a user.
					if (username == null) {
						getLogger().info ("Certificate '"+ serialHex +"' missing in the database");
						if (strict) {
							throw new IOException ("Aborted! Running in strict mode and is missing certificate in database.");
						}
						miss_count++;
						if (!adaptive) {
							continue;
						}
						final Date time = new Date();              // time from which certificate is valid
						final KeyPair key_pair = KeyTools.genKeys("1024", AlgorithmConstants.KEYALGORITHM_RSA);		
						final X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
						final X500Principal dnName = new X500Principal("CN=Dummy Missing in Imported CRL, serialNumber=" + serialHex);
						certGen.setSerialNumber(serialNr);
						certGen.setIssuerDN(cacert.getSubjectX500Principal());
						certGen.setNotBefore(time);
						certGen.setNotAfter(new Date (time.getTime() + 1000L * 60 * 60 * 24 * 365 * 10));  // 10 years of life
						certGen.setSubjectDN(dnName);                       // note: same as issuer
						certGen.setPublicKey(key_pair.getPublic());
						certGen.setSignatureAlgorithm("SHA1withRSA");
						final X509Certificate certificate = certGen.generate(key_pair.getPrivate(), "BC");
						final String fingerprint = CertTools.getFingerprintAsString(certificate);
						// We add all certificates that does not have a user already to "missing_user_name"
						final UserDataVO missingUserDataVO = ejb.getUserAdminSession().findUser(getAdmin(), missing_user_name);
						if (missingUserDataVO == null) {
							// Add the user and change status to REVOKED
							getLogger().debug("Loading/updating user " + missing_user_name);
							final UserDataVO userdataNew = new UserDataVO(missing_user_name, CertTools.getSubjectDN(certificate), cainfo.getCAId(), null, null,
									UserDataConstants.STATUS_NEW, SecConst.USER_ENDUSER, SecConst.EMPTY_ENDENTITYPROFILE,
									SecConst.CERTPROFILE_FIXED_ENDUSER, null, null, SecConst.TOKEN_SOFT_BROWSERGEN, SecConst.NO_HARDTOKENISSUER, null);
							userdataNew.setPassword("foo123");
							ejb.getUserAdminSession().addUser(getAdmin(), userdataNew, false);
							getLogger().info("User '" + missing_user_name + "' has been added.");
							ejb.getUserAdminSession().setUserStatus(getAdmin(), missing_user_name, UserDataConstants.STATUS_REVOKED);
							getLogger().info("User '" + missing_user_name + "' has been updated.");
						}
						ejb.getCertStoreSession().storeCertificate(getAdmin(), certificate, missing_user_name, fingerprint,
								SecConst.CERT_ACTIVE, SecConst.USER_ENDUSER, SecConst.CERTPROFILE_FIXED_ENDUSER, null, new Date().getTime());
						getLogger().info("Dummy certificate  '" + serialHex + "' has been stored.");
					}
					// This check will not catch a certificate with status SecConst.CERT_ARCHIVED
					if (!strict && ejb.getCertStoreSession().isRevoked(issuer, serialNr)) {
						getLogger().info("Certificate '" + serialHex +"' is already revoked");
						already_revoked++;
						continue;
					}
					getLogger().info("Revoking '" + serialHex +"' " + "(" + serialNr.toString() + ")");
					try {
						ejb.getUserAdminSession().revokeCert(getAdmin(), serialNr, entry.getRevocationDate(), issuer, RevokedCertInfo.REVOCATION_REASON_UNSPECIFIED, false);
						revoked++;
					} catch (AlreadyRevokedException e) {
						already_revoked++;
						getLogger().warn("Failed to revoke '" + serialHex +"'. (Status might be 'Archived'.) Error message was: " + e.getMessage());
					}
				}
			}
			if (ejb.getCrlSession().getLastCRLNumber(getAdmin(), issuer, false) < crl_no) {
				ejb.getCrlSession().storeCRL(getAdmin(), x509crl.getEncoded(), CertTools.getFingerprintAsString(cacert), crl_no, issuer, x509crl.getThisUpdate(), x509crl.getNextUpdate(), -1);
			} else {
				if (strict) {
					throw new IOException("CRL #" + crl_no + " or higher is already in the database");
				}
			}
			getLogger().info("\nSummary:\nRevoked " + revoked + " certificates");
			if (already_revoked > 0) {
				getLogger().info(already_revoked + " certificates were already revoked");
			}
			if (miss_count > 0) {
				getLogger().info("There were " + miss_count + (adaptive ? " dummy certificates added to" : " certificates missing in") +  " the database");
			}
			getLogger().info("CRL #" + crl_no + " stored in the database");
		} catch (Exception e) {
			getLogger().info("Error: " + e.getMessage());
		}
		getLogger().trace("<execute()");
	}

	private void usage() {
		getLogger().info("Description: " + getDescription());
		getLogger().info("Usage: " + getCommand() + " <caname> <crl file> <" + STRICT_OP + "|" + LENIENT_OP + "|" + ADAPTIVE_OP + ">");
		getLogger().info(STRICT_OP + " means that all certificates must be in the database and that the CRL must not already be in the database");
		getLogger().info(LENIENT_OP + " means not strict and not adaptive");
		getLogger().info(ADAPTIVE_OP + " means that missing certficates will be replaced by dummy certificates to cater for proper CRLs for missing certificates");
		getLogger().info(" Existing CAs: " + getAvailableCasString());
	}	
}
