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

import java.io.Serializable;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.HashMap;

import org.apache.log4j.Logger;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.ca.caadmin.CA;
import org.ejbca.core.protocol.ocsp.OCSPUtil;
import org.ejbca.util.CryptoProviderTools;

/** Handles and maintains the CA-part of the OCSP functionality
 * 
 * @version $Id: OCSPCAService.java 11731 2011-04-13 17:52:27Z jeklund $
 */
public class OCSPCAService extends ExtendedCAService implements Serializable {

    private static Logger log = Logger.getLogger(OCSPCAService.class);
    /** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();

    public static final float LATEST_VERSION = 4; 
    
    public static final String SERVICENAME = "OCSPCASERVICE";

    private OCSPCAServiceInfo info = null;  
    
    /** kept for upgrade purposes 3.9 -> 3.10 */
    private static final String OCSPKEYSTORE   = "ocspkeystore"; 
    private static final String KEYSPEC        = "keyspec";
	private static final String KEYALGORITHM   = "keyalgorithm";
	private static final String SUBJECTDN      = "subjectdn";
	private static final String SUBJECTALTNAME = "subjectaltname";

	/** kept for upgrade purposes 3.3 -> 3.4 */
    private static final String KEYSIZE        = "keysize";
            
    public OCSPCAService(final ExtendedCAServiceInfo serviceinfo)  {
    	log.debug("OCSPCAService : constructor " + serviceinfo.getStatus());
    	CryptoProviderTools.installBCProviderIfNotAvailable();
    	data = new HashMap();
		data.put(ExtendedCAServiceInfo.IMPLEMENTATIONCLASS, this.getClass().getName());	// For integration with CESeCore
		data.put(EXTENDEDCASERVICETYPE, Integer.valueOf(ExtendedCAServiceInfo.TYPE_OCSPEXTENDEDSERVICE));	// For current version of EJBCA
    	setStatus(serviceinfo.getStatus());
    	data.put(VERSION, new Float(LATEST_VERSION));
    }

    public OCSPCAService(final HashMap data) {
    	loadData(data);
    }

    @Override
    public void init(final CA ca) throws Exception {
    	log.debug("OCSPCAService : init ");
    	final OCSPCAServiceInfo info = (OCSPCAServiceInfo) getExtendedCAServiceInfo();       
    	setStatus(info.getStatus());
    }   

    @Override
    public void update(final ExtendedCAServiceInfo serviceinfo, final CA ca) throws Exception {		   
    	log.debug("OCSPCAService : update " + serviceinfo.getStatus());
    	setStatus(serviceinfo.getStatus());
    	// Only status is updated
    	this.info = new OCSPCAServiceInfo(serviceinfo.getStatus());
    }

    @Override
	public ExtendedCAServiceResponse extendedService(final ExtendedCAServiceRequest request) throws ExtendedCAServiceRequestException, IllegalExtendedCAServiceRequestException,ExtendedCAServiceNotActiveException {
        log.trace(">extendedService");
        if (this.getStatus() != ExtendedCAServiceInfo.STATUS_ACTIVE) {
			String msg = intres.getLocalizedMessage("caservice.notactive");
			log.error(msg);
			throw new ExtendedCAServiceNotActiveException(msg);                            
        }
        if (!(request instanceof OCSPCAServiceRequest)) {
            throw new IllegalExtendedCAServiceRequestException();            
        }
        final OCSPCAServiceRequest ocspServiceReq = (OCSPCAServiceRequest)request;
        final PrivateKey privKey = ocspServiceReq.getPrivKey();
        final String providerName = ocspServiceReq.getPrivKeyProvider();
        final ExtendedCAServiceResponse returnval = OCSPUtil.createOCSPCAServiceResponse(
        		ocspServiceReq, privKey, providerName, (X509Certificate[])ocspServiceReq.getCertificateChain().toArray(new X509Certificate[0]));
        log.trace("<extendedService");		  		
		return returnval;
	}

    @Override
	public float getLatestVersion() {		
		return LATEST_VERSION;
	}

    @Override
	public void upgrade() {
		if (Float.compare(LATEST_VERSION, getVersion()) != 0) {
			String msg = intres.getLocalizedMessage("ocspcaservice.upgrade", new Float(getVersion()));
			log.info(msg);
			data.remove(KEYALGORITHM);
			data.remove(KEYSIZE);
			data.remove(KEYSPEC);
			data.remove(OCSPKEYSTORE);
			data.remove(SUBJECTALTNAME);
			data.remove(SUBJECTDN);
	    	data.put(ExtendedCAServiceInfo.IMPLEMENTATIONCLASS, this.getClass().getName());	// For integration with CESeCore
			data.put(VERSION, new Float(LATEST_VERSION));
		}  		
	}

    @Override
	public ExtendedCAServiceInfo getExtendedCAServiceInfo() {		
		if(info == null) {
		  info = new OCSPCAServiceInfo(getStatus());
		}
		return this.info;
	}
}

