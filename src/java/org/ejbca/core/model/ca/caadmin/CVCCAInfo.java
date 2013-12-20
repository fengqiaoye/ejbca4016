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

package org.ejbca.core.model.ca.caadmin;

import java.security.cert.Certificate;
import java.util.Collection;
import java.util.Date;

import org.ejbca.core.model.ca.caadmin.extendedcaservices.ExtendedCAServiceInfo;
import org.ejbca.core.model.ca.catoken.CATokenInfo;
import org.ejbca.util.CertTools;
import org.ejbca.util.StringTools;


/**
 * Holds non-sensitive information about a CVC CA (Card Verifiable Certificate).
 *
 * @version $Id: CVCCAInfo.java 11111 2011-01-09 15:25:27Z anatom $
 */
public class CVCCAInfo extends CAInfo {

	private static final long serialVersionUID = 2L;

	/**
	 * Constructor that should be used when creating CA and retrieving CA info.
	 */
	public CVCCAInfo(String subjectdn, String name, int status, Date updateTime, int certificateprofileid, 
			long validity, Date expiretime, int catype, int signedby, Collection<Certificate> certificatechain, 
			CATokenInfo catokeninfo, String description, int revocationReason, Date revocationDate,
			long crlperiod, long crlIssueInterval, long crlOverlapTime, long deltacrlperiod, 
			Collection<Integer> crlpublishers,boolean finishuser,Collection<ExtendedCAServiceInfo> extendedcaserviceinfos, 
			Collection<Integer> approvalSettings, int numOfReqApprovals,
			boolean includeInHealthCheck, boolean _doEnforceUniquePublicKeys,
			boolean _doEnforceUniqueDistinguishedName, boolean _doEnforceUniqueSubjectDNSerialnumber,
			boolean _useCertReqHistory, boolean _useUserStorage, boolean _useCertificateStorage) {
		this.subjectdn = CertTools.stringToBCDNString(StringTools.strip(subjectdn));
		this.caid = this.subjectdn.hashCode();
		this.name = name;
		this.status = status;
		this.updatetime = updateTime;
		this.validity = validity;
		this.expiretime = expiretime;
		this.catype = catype;
		this.signedby = signedby;
        this.certificatechain = certificatechain;	
		this.catokeninfo = catokeninfo; 
		this.description = description;
		this.revocationReason = revocationReason;
		this.revocationDate = revocationDate;
		this.crlperiod = crlperiod;
		this.crlIssueInterval = crlIssueInterval;
		this.crlOverlapTime = crlOverlapTime;
		this.deltacrlperiod = deltacrlperiod;
		this.crlpublishers = crlpublishers;
		this.finishuser = finishuser;                     
		this.certificateprofileid = certificateprofileid;
		this.extendedcaserviceinfos = extendedcaserviceinfos; 
		this.approvalSettings = approvalSettings;
		this.numOfReqApprovals = numOfReqApprovals;
		this.includeInHealthCheck = includeInHealthCheck;
		this.doEnforceUniquePublicKeys = _doEnforceUniquePublicKeys;
		this.doEnforceUniqueDistinguishedName = _doEnforceUniqueDistinguishedName;
		this.doEnforceUniqueSubjectDNSerialnumber = _doEnforceUniqueSubjectDNSerialnumber;
		this.useCertReqHistory = _useCertReqHistory;
        this.useUserStorage = _useUserStorage;
        this.useCertificateStorage = _useCertificateStorage;
	}

	/**
	 * Constructor that should be used when updating CA data.
     * Used by the web. Jsp and stuff like that.
	 */
	public CVCCAInfo(int caid, long validity, CATokenInfo catokeninfo, String description,
			long crlperiod, long crlIssueInterval, long crlOverlapTime, long deltacrlperiod, 
			Collection<Integer> crlpublishers,
			boolean finishuser, Collection<ExtendedCAServiceInfo> extendedcaserviceinfos, 
			Collection<Integer> approvalSettings, int numOfReqApprovals,
			boolean includeInHealthCheck, boolean _doEnforceUniquePublicKeys,
			boolean _doEnforceUniqueDistinguishedName, boolean _doEnforceUniqueSubjectDNSerialnumber,
			boolean _useCertReqHistory, boolean _useUserStorage, boolean _useCertificateStorage) {        
		this.caid = caid;
		this.validity=validity;
		this.catokeninfo = catokeninfo; 
		this.description = description;    
		this.crlperiod = crlperiod;
		this.crlIssueInterval = crlIssueInterval;
		this.crlOverlapTime = crlOverlapTime;
		this.deltacrlperiod = deltacrlperiod;
		this.crlpublishers = crlpublishers;
		this.finishuser = finishuser;
		this.extendedcaserviceinfos = extendedcaserviceinfos; 
		this.approvalSettings = approvalSettings;
		this.numOfReqApprovals = numOfReqApprovals;
		this.includeInHealthCheck = includeInHealthCheck;
		this.doEnforceUniquePublicKeys = _doEnforceUniquePublicKeys;
		this.doEnforceUniqueDistinguishedName = _doEnforceUniqueDistinguishedName;
		this.doEnforceUniqueSubjectDNSerialnumber = _doEnforceUniqueSubjectDNSerialnumber;
		this.useCertReqHistory = _useCertReqHistory;
        this.useUserStorage = _useUserStorage;
        this.useCertificateStorage = _useCertificateStorage;
	}  


	public CVCCAInfo(){}

}
