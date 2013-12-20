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
 
package org.ejbca.core.model.ca.caadmin.extendedcaservices;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.XMLSignatureException;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.ejbca.config.EjbcaConfiguration;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.ca.caadmin.CA;
import org.ejbca.core.model.ca.caadmin.IllegalKeyStoreException;
import org.ejbca.core.model.ca.certificateprofiles.XKMSCertificateProfile;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.util.Base64;
import org.ejbca.util.CertTools;
import org.ejbca.util.CryptoProviderTools;
import org.ejbca.util.StringTools;
import org.ejbca.util.keystore.KeyTools;
import org.w3c.dom.Document;

/** Handles and maintains the CA-part of the XKMS functionality.
 *  The service have it's own certificate used for signing and encryption 
 * 
 * @author Philip Vendil
 * @version $Id: XKMSCAService.java 11731 2011-04-13 17:52:27Z jeklund $
 */
public class XKMSCAService extends ExtendedCAService implements Serializable {

    private static Logger m_log = Logger.getLogger(XKMSCAService.class);
    /** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();

    public static final float LATEST_VERSION = 2; 
    
    public static final String SERVICENAME = "XKMSCASERVICE";          

    private PrivateKey xKMSkey = null;
    private List<Certificate> xKMScertificatechain  = null;
    
    private XKMSCAServiceInfo info = null;  
    
    private static final String XKMSKEYSTORE   = "xkmskeystore"; 
    private static final String KEYSPEC        = "keyspec";
	private static final String KEYALGORITHM   = "keyalgorithm";
	private static final String SUBJECTDN      = "subjectdn";
	private static final String SUBJECTALTNAME = "subjectaltname";
    
	private static final String PRIVATESIGNKEYALIAS = "privatesignkeyalias";   

	public XKMSCAService(final ExtendedCAServiceInfo serviceinfo) {
		m_log.debug("XKMSCAService : constructor " + serviceinfo.getStatus());
		CryptoProviderTools.installBCProviderIfNotAvailable();
		// Currently only RSA keys are supported
		final XKMSCAServiceInfo info = (XKMSCAServiceInfo) serviceinfo;
		data = new HashMap();
		data.put(ExtendedCAServiceInfo.IMPLEMENTATIONCLASS, this.getClass().getName());	// For integration with CESeCore
		data.put(EXTENDEDCASERVICETYPE, Integer.valueOf(ExtendedCAServiceInfo.TYPE_XKMSEXTENDEDSERVICE));	// For current version of EJBCA
		data.put(KEYSPEC, info.getKeySpec());
		data.put(KEYALGORITHM, info.getKeyAlgorithm());
		setSubjectDN(info.getSubjectDN());
		setSubjectAltName(info.getSubjectAltName());
		setStatus(serviceinfo.getStatus());
		data.put(VERSION, new Float(LATEST_VERSION));
    }
    
    public XKMSCAService(final HashMap data) throws IllegalArgumentException, IllegalKeyStoreException {
    	CryptoProviderTools.installBCProviderIfNotAvailable();
    	loadData(data);
    	if (data.get(XKMSKEYSTORE) != null) {
    		// lookup keystore passwords
    		final String keystorepass = StringTools.passwordDecryption(EjbcaConfiguration.getCaXkmsKeyStorePass(), "ca.xkmskeystorepass");
    		int status = ExtendedCAServiceInfo.STATUS_INACTIVE;
    		try {
    			m_log.debug("Loading XKMS keystore");
    			final KeyStore keystore = KeyStore.getInstance("PKCS12", "BC");
    			keystore.load(new ByteArrayInputStream(Base64.decode(((String) data.get(XKMSKEYSTORE)).getBytes())), keystorepass.toCharArray());
    			m_log.debug("Finished loading XKMS keystore");
    			this.xKMSkey = (PrivateKey) keystore.getKey(PRIVATESIGNKEYALIAS, null);
    			// Due to a bug in Glassfish v1 (fixed in v2), we used to have to make sure all certificates in this 
    			// Array were of SUNs own provider, using CertTools.SYSTEM_SECURITY_PROVIDER.
    			// As of EJBCA 3.9.3 we decided that we don't have to support Glassfish v1 anymore.
    			this.xKMScertificatechain = CertTools.getCertCollectionFromArray(keystore.getCertificateChain(PRIVATESIGNKEYALIAS), null);
    			status = getStatus();
    			try {
    				if (!keystore.getCertificate(PRIVATESIGNKEYALIAS).getPublicKey().equals(((Certificate)this.xKMScertificatechain.get(0)).getPublicKey())) {
    					m_log.error("Keystore does not hold the same public key as XKMS service certificate.");
    				}
    			} catch (Exception e2) {
    				m_log.error("Could not compare public keys. " + e2.getMessage());
    			}
    		} catch (Exception e) {
    			m_log.error("Could not load keystore or certificate for CA XKMS service. Perhaps the password was changed? " + e.getMessage());
    		} finally {
    			this.info = new XKMSCAServiceInfo(status, getSubjectDN(), getSubjectAltName(), (String)data.get(KEYSPEC), 
    					(String) data.get(KEYALGORITHM), this.xKMScertificatechain);
    		}
    		data.put(EXTENDEDCASERVICETYPE, Integer.valueOf(ExtendedCAServiceInfo.TYPE_XKMSEXTENDEDSERVICE));        
    	} 
    }
    
    @Override
    public void init(final CA ca) throws Exception {
    	m_log.trace(">init");
    	// lookup keystore passwords      
    	final String keystorepass = StringTools.passwordDecryption(EjbcaConfiguration.getCaXkmsKeyStorePass(), "ca.xkmskeystorepass");
    	// Currently only RSA keys are supported
    	final XKMSCAServiceInfo info = (XKMSCAServiceInfo) getExtendedCAServiceInfo();       
    	// Create XKMS KeyStore	    
    	final KeyStore keystore = KeyStore.getInstance("PKCS12", "BC");
    	keystore.load(null, null);                              
    	final KeyPair xKMSkeys = KeyTools.genKeys(info.getKeySpec(), info.getKeyAlgorithm());
    	final UserDataVO user = new UserDataVO("NOUSERNAME", info.getSubjectDN(), 0, info.getSubjectAltName(), "NOEMAIL", 0,0,0,0, null,null,0,0,null);
    	final Certificate xKMSCertificate = ca.generateCertificate(user, xKMSkeys.getPublic(),
    			-1, // KeyUsage
    			ca.getValidity(), new XKMSCertificateProfile(),
    			null // sequence
    	);
    	xKMScertificatechain = new ArrayList<Certificate>();
    	xKMScertificatechain.add(xKMSCertificate);
    	xKMScertificatechain.addAll(ca.getCertificateChain());
    	this.xKMSkey = xKMSkeys.getPrivate(); 	  	 	  
    	keystore.setKeyEntry(PRIVATESIGNKEYALIAS,xKMSkeys.getPrivate(), null, (Certificate[]) xKMScertificatechain.toArray(new Certificate[xKMScertificatechain.size()]));              
    	final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	keystore.store(baos, keystorepass.toCharArray());
    	data.put(XKMSKEYSTORE, new String(Base64.encode(baos.toByteArray())));      
    	// Store XKMS KeyStore
    	setStatus(info.getStatus());
    	this.info = new XKMSCAServiceInfo(info.getStatus(), getSubjectDN(), getSubjectAltName(), (String)data.get(KEYSPEC), (String) data.get(KEYALGORITHM), xKMScertificatechain);
    	m_log.trace("<init");
    }   

    @Override
    public void update(final ExtendedCAServiceInfo serviceinfo, final CA ca) throws Exception {
    	final XKMSCAServiceInfo info = (XKMSCAServiceInfo) serviceinfo;
    	m_log.trace(">update: " + serviceinfo.getStatus());
    	setStatus(serviceinfo.getStatus());
    	if (info.getRenewFlag()) {
    		// Renew The XKMS Signers certificate.
    		this.init(ca);
    	}
    	// Only status is updated
    	this.info = new XKMSCAServiceInfo(serviceinfo.getStatus(), getSubjectDN(), getSubjectAltName(), (String) data.get(KEYSPEC), (String) data.get(KEYALGORITHM), xKMScertificatechain);										         									    	 									  
    	m_log.trace("<update: " + serviceinfo.getStatus());
    }

    @Override
	public ExtendedCAServiceResponse extendedService(final ExtendedCAServiceRequest request) throws ExtendedCAServiceRequestException, IllegalExtendedCAServiceRequestException,ExtendedCAServiceNotActiveException {
        m_log.trace(">extendedService");
        if (!(request instanceof XKMSCAServiceRequest)) {
            throw new IllegalExtendedCAServiceRequestException();            
        }
        if (this.getStatus() != ExtendedCAServiceInfo.STATUS_ACTIVE) {
			String msg = intres.getLocalizedMessage("caservice.notactive");
			m_log.error(msg);
			throw new ExtendedCAServiceNotActiveException(msg);                            
        }
        ExtendedCAServiceResponse returnval = null;
        final X509Certificate signerCert = (X509Certificate) xKMScertificatechain.get(0);
        final XKMSCAServiceRequest xKMSServiceReq = (XKMSCAServiceRequest)request;
        final Document doc = xKMSServiceReq.getDoc();
        if (xKMSServiceReq.isSign()) {
        	try {
				XMLSignature xmlSig = new XMLSignature(doc, "", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA1, Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
				Transforms transforms = new Transforms(doc);
				transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
				transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
				xmlSig.addDocument("#" + xKMSServiceReq.getId(), transforms, Constants.ALGO_ID_DIGEST_SHA1);        			
				xmlSig.addKeyInfo(signerCert);
				doc.getDocumentElement().insertBefore(xmlSig.getElement() ,doc.getDocumentElement().getFirstChild());
				xmlSig.sign(xKMSkey);
        		returnval = new XKMSCAServiceResponse(doc);
        	} catch (XMLSignatureException e) {
        		throw new ExtendedCAServiceRequestException(e);
			} catch (XMLSecurityException e) {
				throw new ExtendedCAServiceRequestException(e);
			}
        }
        m_log.trace("<extendedService");		  		
		return returnval;
    }

    @Override
	public float getLatestVersion() {		
    	return LATEST_VERSION;
	}

    @Override
	public void upgrade() {
    	if (Float.compare(LATEST_VERSION, getVersion()) != 0) {
    		// New version of the class, upgrade
    		data.put(ExtendedCAServiceInfo.IMPLEMENTATIONCLASS, this.getClass().getName());	// For integration with CESeCore
    		data.put(VERSION, new Float(LATEST_VERSION));
    	}  		
	}

    @Override
	public ExtendedCAServiceInfo getExtendedCAServiceInfo() {			
    	if (info == null) {
    		info = new XKMSCAServiceInfo(getStatus(), getSubjectDN(), getSubjectAltName(), (String) data.get(KEYSPEC), (String) data.get(KEYALGORITHM), xKMScertificatechain);
    	}
    	return info;
    }

	private String getSubjectDN() {
		String retval = null;
		final String str = (String)data.get(SUBJECTDN);
		 try {
			retval = new String(Base64.decode((str).getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e) {
			m_log.error("Could not decode XKMS data from Base64",e);
		} catch (ArrayIndexOutOfBoundsException e) {
			// This is an old CA, where it's not Base64encoded
			m_log.debug("Old non base64 encoded DN: "+str);
			retval = str; 
		}
		return retval;		 
	}
    
	private void setSubjectDN(final String dn) {
		try {
			data.put(SUBJECTDN,new String(Base64.encode(dn.getBytes("UTF-8"),false)));
		} catch (UnsupportedEncodingException e) {
			m_log.error("Could not encode XKMS data from Base64",e);
		}
	}
	
	private String getSubjectAltName() {
		String retval = null;
		final String str = (String) data.get(SUBJECTALTNAME);
		 try {
			retval = new String(Base64.decode((str).getBytes("UTF-8")));
		} catch (UnsupportedEncodingException e) {
			m_log.error("Could not decode XKMS data from Base64",e);
		} catch (ArrayIndexOutOfBoundsException e) {
			// This is an old CA, where it's not Base64encoded
			m_log.debug("Old non base64 encoded altname: "+str);
			retval = str; 
		}
		return retval;		 
	}
    
	private void setSubjectAltName(final String dn) {
		try {
			data.put(SUBJECTALTNAME,new String(Base64.encode(dn.getBytes("UTF-8"), false)));
		} catch (UnsupportedEncodingException e) {
			m_log.error("Could not encode XKMS data from Base64",e);
		}
	}
}
