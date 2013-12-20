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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.ejbca.core.model.AlgorithmConstants;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.ca.caadmin.X509CAInfo;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.CmsCAServiceInfo;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.ExtendedCAServiceInfo;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.OCSPCAServiceInfo;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.XKMSCAServiceInfo;
import org.ejbca.core.model.ca.catoken.CATokenInfo;
import org.ejbca.core.model.ca.catoken.HardCATokenInfo;
import org.ejbca.core.model.ca.catoken.ICAToken;
import org.ejbca.core.model.ca.catoken.SoftCATokenInfo;
import org.ejbca.core.model.ca.certificateprofiles.CertificatePolicy;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.ui.cli.ErrorAdminCommandException;
import org.ejbca.ui.cli.IllegalAdminCommandException;
import org.ejbca.util.CertTools;
import org.ejbca.util.CliTools;
import org.ejbca.util.CryptoProviderTools;
import org.ejbca.util.FileTools;
import org.ejbca.util.SimpleTime;
import org.ejbca.util.StringTools;
import org.ejbca.util.keystore.KeyTools;

/**
 * Create a CA and its first CRL. Publishes the CRL and CA certificate
 *
 * @version $Id: CaInitCommand.java 10945 2010-12-22 09:45:15Z jeklund $
 */
public class CaInitCommand extends BaseCaAdminCommand {

	public String getMainCommand() { return MAINCOMMAND; }
	public String getSubCommand() { return "init"; }
	public String getDescription() { return "Create a CA and its first CRL. Publishes the CRL and CA certificate"; }

    public void execute(String[] args) throws ErrorAdminCommandException {
    	// Install BC provider
    	CryptoProviderTools.installBCProvider();

    	// Create new CA.
        if (args.length < 10) {
    		getLogger().info("Description: " + getDescription());
    		getLogger().info("Usage: " + getCommand() + " <caname> <dn> <catokentype> <catokenpassword> <keyspec> <keytype> <validity-days> <policyID> <signalgorithm> [-certprofile profileName] [-superadmincn SuperAdmin] [<catokenproperties> or null] [<signed by caid>]");
    		getLogger().info(" catokentype defines if the CA should be created with soft keys or on a HSM. Use 'soft' for software keys and 'org.ejbca.core.model.ca.catoken.PKCS11CAToken' for PKCS#11 HSMs.");
    		getLogger().info(" catokenpassword is the password for the CA token. Set to 'null' to use the default system password for Soft token CAs. Set to 'prompt' to prompt for the password on the terminal.");
    		getLogger().info(" catokenpassword is the password for the CA token. Set to 'null' to use the default system password for Soft token CAs");
    		getLogger().info(" keytype is RSA, DSA or ECDSA.");
    		getLogger().info(" keyspec for RSA keys is size of RSA keys (1024, 2048, 4096, 8192).");
    		getLogger().info(" keyspec for DSA keys is size of DSA keys (1024).");
    		getLogger().info(" keyspec for ECDSA keys is name of curve or 'implicitlyCA', see docs.");
    		getLogger().info(" policyId can be 'null' if no Certificate Policy extension should be present, or\nobjectID as '2.5.29.32.0' or objectID and cpsurl as \"2.5.29.32.0 http://foo.bar.com/mycps.txt\".");
    		getLogger().info("    you can add multiple policies such as \"2.5.29.32.0 http://foo.bar.com/mycps.txt 1.1.1.1.1 http://foo.bar.com/111cps.txt\".");
    		String availableSignAlgs = "";
    		for (String algorithm : AlgorithmConstants.AVAILABLE_SIGALGS) {
    			availableSignAlgs += (availableSignAlgs.length()==0?"":", ") + algorithm;
    		}
    		getLogger().info(" signalgorithm is on of " + availableSignAlgs);
    		getLogger().info(" adding the parameters '-certprofile profileName' makes the CA use the certificate profile 'profileName' instead of the default ROOTCA or SUBCA. Optional parameter that can be completely left out.");
    		getLogger().info(" adding the parameters '-superadmincn SuperAdmin' makes an initial CA use the common name SuperAdmin when initializing the authorization module with an initial super administrator. Note only used when creating initial CA.");
    		getLogger().info(" catokenproperties is a file were you define key name, password and key alias for the HSM. Same as the Hard CA Token Properties in admin gui.");
    		getLogger().info(" signed by caid is the CA id of a CA that will sign this CA. If this is omitted the new CA will be self signed (i.e. a root CA).");
    		return;
        }
            
        try {             	
    		// Get and remove optional switches
    		List<String> argsList = CliTools.getAsModifyableList(args);
    		int profileInd = argsList.indexOf("-certprofile");
    		String profileName = null;
    		if (profileInd > -1) {
    			profileName = argsList.get(profileInd+1);
    			argsList.remove(profileName);
    			argsList.remove("-certprofile");
    		}
    		int superAdminCNInd = argsList.indexOf("-superadmincn");
    		String superAdminCN = BaseCaAdminCommand.defaultSuperAdminCN;
    		if (superAdminCNInd > -1) {
    			superAdminCN = argsList.get(superAdminCNInd+1);
    			argsList.remove(superAdminCN);
    			argsList.remove("-superadmincn");
    		}
    		
    		args = argsList.toArray(new String[0]); // new args array without the optional switches

    		final String caname = args[1];
            final String dn = CertTools.stringToBCDNString(StringTools.strip(args[2]));
            final String catokentype = args[3];
            String catokenpassword = StringTools.passwordDecryption(args[4], "ca.tokenpassword");
            if (StringUtils.equals(catokenpassword, "prompt")) {
            	getLogger().info("Enter CA token password: ");
            	getLogger().info("");
            	catokenpassword = String.valueOf(System.console().readPassword());
            }
            final String keyspec = args[5];
            final String keytype = args[6];
            final int validity = Integer.parseInt(args[7]);
            String policyId = args[8];
            final ArrayList<CertificatePolicy> policies = new ArrayList<CertificatePolicy>(1);
            if ( (policyId != null) && (policyId.toLowerCase().trim().equals("null")) ) {
            	policyId = null;
            } else {
            	String[] array = policyId.split(" ");
            	for (int i=0; i<array.length; i+=2) {
                	String id = array[i+0];
                	String cpsurl = "";
                	if(array.length > i+1) {
                		cpsurl = array[i+1];
                	}
                	policies.add(new CertificatePolicy(id, CertificatePolicy.id_qt_cps, cpsurl));
            	}
            }
            String signAlg = args[9];
            String catokenproperties = null;
            if (args.length > 10 && !"soft".equals(catokentype)) {
            	String filename = args[10];
            	if ( (filename != null) && (!filename.equalsIgnoreCase("null")) ) {
                	if (!(new File(filename)).exists()) {
                		throw new IllegalAdminCommandException("File " + filename + " does not exist");
                	}
                    catokenproperties = new String(FileTools.readFiletoBuffer(filename));            		
            	}
            }
            int signedByCAId = CAInfo.SELFSIGNED; 
            if (args.length > 11) {
            	String caid = args[11];
            	signedByCAId = Integer.valueOf(caid);
            }
            // Get the profile ID from the name if we specified a certain profile name
            int profileId = SecConst.CERTPROFILE_FIXED_ROOTCA;
            if (profileName == null) {
            	if (signedByCAId == CAInfo.SELFSIGNED) {
            		profileName = "ROOTCA";
            	} else {
            		profileName = "SUBCA";
                    profileId = SecConst.CERTPROFILE_FIXED_SUBCA;
            	}
            } else {                
                profileId = ejb.getCertificateProfileSession().getCertificateProfileId(getAdmin(), profileName);
            	if (profileId == 0) {
            		getLogger().info("Error: Certificate profile with name '"+profileName+"' does not exist.");
            		return;
            	}
            	
                CertificateProfile certificateProfile  = ejb.getCertificateProfileSession().getCertificateProfile(getAdmin(), profileName);
                if(certificateProfile.getType() != CertificateProfile.TYPE_ROOTCA && certificateProfile.getType() != CertificateProfile.TYPE_SUBCA) {
                    getLogger().info("Error: Certificate profile " + profileName + " is not of type ROOTCA or SUBCA.");
                    return;
                }
            }
            
            if (KeyTools.isUsingExportableCryptography()) {
            	getLogger().warn("WARNING!");
            	getLogger().warn("WARNING: Using exportable strength crypto!");
            	getLogger().warn("WARNING!");
            	getLogger().warn("The Unlimited Strength Crypto policy files have not been installed. EJBCA may not function correctly using exportable crypto.");
            	getLogger().warn("Please install the Unlimited Strength Crypto policy files as documented in the Installation guide.");
            	getLogger().warn("Sleeping 10 seconds...");
            	getLogger().warn("");
            	Thread.sleep(10000);
            }
            getLogger().info("Initializing CA");            
            
            getLogger().info("Generating rootCA keystore:");
            getLogger().info("CA name: "+caname);
            getLogger().info("SuperAdmin CN: "+superAdminCN);
            getLogger().info("DN: "+dn);
            getLogger().info("CA token type: "+catokentype);
            getLogger().info("CA token password: "+(catokenpassword == null ? "null" : "hidden"));
            getLogger().info("Keytype: "+keytype);
            getLogger().info("Keyspec: "+keyspec);
            getLogger().info("Validity (days): "+validity);
            getLogger().info("Policy ID: "+policyId);
            getLogger().info("Signature alg: "+signAlg);
            getLogger().info("Certificate profile: "+profileName);
            //getLogger().info("Certificate profile id: "+profileId);
            getLogger().info("CA token properties: "+catokenproperties);
            getLogger().info("Signed by: "+(signedByCAId == CAInfo.SELFSIGNED ? "self signed " : signedByCAId));
            if (signedByCAId != CAInfo.SELFSIGNED) {
            	CAInfo signedBy = ejb.getCAAdminSession().getCAInfo(getAdmin(), signedByCAId);
            	if (signedBy == null) {
                	throw new IllegalArgumentException("CA with id "+signedByCAId+" does not exist.");            		
            	}
            }
                            
            initAuthorizationModule(dn.hashCode(), superAdminCN);
            // Define CAToken type (soft token or hsm).
            CATokenInfo catokeninfo = null;
            if ( catokentype.equals("soft")) {
	            SoftCATokenInfo softcatokeninfo = new SoftCATokenInfo();
	            if (!catokenpassword.equalsIgnoreCase("null")) {
		        	softcatokeninfo.setAuthenticationCode(catokenpassword);	            	
	            }
	            softcatokeninfo.setSignKeySpec(keyspec);
	            softcatokeninfo.setSignKeyAlgorithm(keytype);
	            softcatokeninfo.setSignatureAlgorithm(signAlg);
	            softcatokeninfo.setEncKeySpec("2048");
	            softcatokeninfo.setEncKeyAlgorithm(AlgorithmConstants.KEYALGORITHM_RSA);
	            softcatokeninfo.setEncryptionAlgorithm(AlgorithmConstants.SIGALG_SHA1_WITH_RSA);
	            catokeninfo = softcatokeninfo;
            } else {
            	HardCATokenInfo hardcatokeninfo = new HardCATokenInfo();
            	hardcatokeninfo.setAuthenticationCode(catokenpassword);
            	hardcatokeninfo.setCATokenStatus(ICAToken.STATUS_ACTIVE);
            	hardcatokeninfo.setClassPath(catokentype);
            	hardcatokeninfo.setEncryptionAlgorithm(AlgorithmConstants.SIGALG_SHA1_WITH_RSA);
            	hardcatokeninfo.setProperties(catokenproperties);
            	hardcatokeninfo.setSignatureAlgorithm(signAlg);
            	catokeninfo = hardcatokeninfo;
            }
            
            // Create and active OSCP CA Service.
            ArrayList extendedcaservices = new ArrayList();
            String keySpec = keyspec;
            if (keytype.equals(AlgorithmConstants.KEYALGORITHM_RSA)) {
            	// Never use larger keys than 2048 bit RSA for OCSP signing
            	int len = Integer.parseInt(keySpec);
            	if (len > 2048) {
            		keySpec = "2048";				 
            	}
            }
            extendedcaservices.add(new OCSPCAServiceInfo(ExtendedCAServiceInfo.STATUS_ACTIVE));
            extendedcaservices.add(
                    new XKMSCAServiceInfo(ExtendedCAServiceInfo.STATUS_INACTIVE,
                                          "CN=XKMSCertificate, " + dn,
                                          "",
                                          keySpec,
                                          keytype));
            extendedcaservices.add(
                    new CmsCAServiceInfo(ExtendedCAServiceInfo.STATUS_INACTIVE,
                                          "CN=CmsCertificate, " + dn,
                                          "",
                                          keySpec,
                                          keytype));
              
            
            X509CAInfo cainfo = new X509CAInfo(dn, 
                                             caname, SecConst.CA_ACTIVE, new Date(),
                                             "", profileId,
                                             validity, 
                                             null, // Expiretime                                             
                                             CAInfo.CATYPE_X509,
                                             signedByCAId,
                                             (Collection) null,
                                             catokeninfo,
                                             "Initial CA",
                                             -1, null,
                                             policies, // PolicyId
                                             24 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLPeriod
                                             0 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLIssueInterval
                                             10 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLOverlapTime
                                             0 * SimpleTime.MILLISECONDS_PER_HOUR, // DeltaCRLPeriod
                                             new ArrayList(),
                                             true, // Authority Key Identifier
                                             false, // Authority Key Identifier Critical
                                             true, // CRL Number
                                             false, // CRL Number Critical
                                             "", // Default CRL Dist Point
                                             "", // Default CRL Issuer
                                             "", // Default OCSP Service Locator
                                             "", // CA defined freshest CRL
                                             true, // Finish User
                                             extendedcaservices,
			                                 false, // use default utf8 settings
			                                 new ArrayList(), // Approvals Settings
			                                 1, // Number of Req approvals
			                                 false, // Use UTF8 subject DN by default
			                                 true, // Use LDAP DN order by default
			                                 false, // Use CRL Distribution Point on CRL
			                                 false,  // CRL Distribution Point on CRL critical
			                                 true, // include in health check
			                                 true, // isDoEnforceUniquePublicKeys
			                                 true, // isDoEnforceUniqueDistinguishedName
			                                 false, // isDoEnforceUniqueSubjectDNSerialnumber
			                                 true, // useCertReqHistory
			                                 true, // useUserStorage
			                                 true, // useCertificateStorage
			                                 null //cmpRaAuthSecret
			                                 );
            
            getLogger().info("Creating CA...");
            ejb.getCAAdminSession().createCA(getAdmin(), cainfo);
            
            CAInfo newInfo = ejb.getCAAdminSession().getCAInfo(getAdmin(), caname);
            int caid = newInfo.getCAId();
            getLogger().info("CAId for created CA: " + caid);
            getLogger().info("-Created and published initial CRL.");
            getLogger().info("CA initialized");
        } catch (Exception e) {
        	getLogger().debug("An error occured: ", e);
            throw new ErrorAdminCommandException(e);
        }
    }
    
}
