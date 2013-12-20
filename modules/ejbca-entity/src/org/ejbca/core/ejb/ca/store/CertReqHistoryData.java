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

package org.ejbca.core.ejb.ca.store;

import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.apache.log4j.Logger;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.ejbca.core.model.ca.store.CertReqHistory;
import org.ejbca.core.model.ra.UserDataVO;
import org.ejbca.util.CertTools;
import org.ejbca.util.FixEndOfBrokenXML;
import org.ejbca.util.StringTools;

/**
 * Representation of historical information about the data user to create a certificate.
 * 
 * the information is currently used to:
 * - list request history for a user
 * - find issuing User DN (UserDataVO) when republishing a certificate (in case the userDN for the user changed)
 * 
 * @version $Id: CertReqHistoryData.java 12655 2011-09-21 12:55:56Z anatom $
 */ 
@Entity
@Table(name="CertReqHistoryData")
public class CertReqHistoryData implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final Logger log = Logger.getLogger(CertReqHistoryData.class);

	private String issuerDN;
	private String fingerprint;
	private String serialNumber;
	private long timestamp;
	private String userDataVO;
	private String username;
	private int rowVersion = 0;
	private String rowProtection;

	/**
	 * Entity Bean holding info about a request data at the time the certificate was issued.
	 * 
	 * @param incert the certificate issued
	 * @param issuerDN should be the same as CertTools.getIssuerDN(incert)
	 * @param UserDataVO, the data used to issue the certificate. 
	 */
	public CertReqHistoryData(Certificate incert, String issuerDN, UserDataVO useradmindata) {
		// Exctract fields to store with the certificate.
		setFingerprint(CertTools.getFingerprintAsString(incert));
        setIssuerDN(issuerDN);
        if (log.isDebugEnabled()) {
        	log.debug("Creating certreqhistory data, serial=" + CertTools.getSerialNumberAsString(incert) + ", issuer=" + getIssuerDN());
        }
        setSerialNumber(CertTools.getSerialNumber(incert).toString());
        setTimestamp(new Date().getTime());
		setUsername(useradmindata.getUsername());
		storeUserDataVO(useradmindata);
	}
	private void storeUserDataVO(UserDataVO useradmindata) {
		try {
			// Save the user admin data in xml encoding.
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			final XMLEncoder encoder = new XMLEncoder(baos);
			encoder.writeObject(useradmindata);
			encoder.close();
			final String s = baos.toString("UTF-8");
			if (log.isDebugEnabled()) {
				log.debug(printUserDataVOXML("useradmindata:",s));
			}
			setUserDataVO(s);
		} catch (UnsupportedEncodingException e) {
			log.error("", e);
			throw new RuntimeException(e);    	                                              
		} 
	}

	public CertReqHistoryData() { }
	
	/**
	 * DN of issuer of certificate
	 * Should not be used outside of entity bean, use getCertReqHistory instead
	 * @return issuer dn
	 */
	//@Column
	public String getIssuerDN() { return issuerDN; }
	/**
	 * Use setIssuer instead
	 * @param issuerDN issuer dn
	 */
	public void setIssuerDN(String issuerDN) { this.issuerDN =issuerDN; }

	/**
	 * Fingerprint of certificate
	 * Should not be used outside of entity bean, use getCertReqHistory instead
	 * @return fingerprint
	 */
	//@Id @Column
	public String getFingerprint() { return fingerprint; }
	/**
	 * Fingerprint of certificate
	 * Shouldn't be set after creation.
	 * @param fingerprint fingerprint
	 */
	public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

	/**
	 * Serialnumber formated as BigInteger.toString()
	 * Should not be used outside of entity bean, use getCertReqHistory instead
	 * @return serial number
	 */
	//@Column
	public String getSerialNumber() { return serialNumber; }

	/**
	 * Serialnumber formated as BigInteger.toString()
	 * Shouldn't be set after creation.
	 * @param serialNumber serial number
	 */
	public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

	/**
	 * Date formated as seconds since 1970 (== Date.getTime())
	 * Should not be used outside of entity bean, use getCertReqHistory instead
	 * @return timestamp 
	 */
	//@Column
	public long getTimestamp() { return timestamp; }

	/**
	 * Date formated as seconds since 1970 (== Date.getTime())
	 * Shouldn't be set after creation.
	 * @param timestamp when certificate request info was stored
	 */
	public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

	/**
	 * UserDataVO in xmlencoded String format
	 * Should not be used outside of entity bean, use getCertReqHistory instead
	 * @return  xmlencoded encoded UserDataVO
	 */
	//@Column @Lob
	public String getUserDataVO() { return userDataVO; }

	/**
	 * UserDataVO in  xmlencoded String format
	 * Shouldn't be set after creation.
	 * @param userDataVO xmlencoded encoded UserDataVO
	 */
	public void setUserDataVO(String userDataVO) { this.userDataVO = userDataVO; }

	/**
	 * username in database
	 * Should not be used outside of entity bean, use getCertReqHistory instead
	 * @return username
	 */
	//@Column
	public String getUsername() { return username; }

	/**
	 * username
	 * Shouldn't be set after creation.
	 * @param username username
	 */
	public void setUsername(String username) { this.username = StringTools.strip(username); }

	//@Version @Column
	public int getRowVersion() { return rowVersion; }
	public void setRowVersion(int rowVersion) { this.rowVersion = rowVersion; }

	//@Column @Lob
	public String getRowProtection() { return rowProtection; }
	public void setRowProtection(String rowProtection) { this.rowProtection = rowProtection; }

	//
	// Public business methods used to help us manage certificates
	//

	/**
	 * Returns the value object containing the information of the entity bean.
	 * This is the method that should be used to retreive cert req history 
	 * correctly.
	 * 
	 * NOTE: This method will try to repair broken XML and will in that case
	 * update the database. This means that this method must always run in a
	 * transaction! 
	 * 
	 * @return certificate request history object
	 */
	@Transient
    public CertReqHistory getCertReqHistory() {

		return new CertReqHistory(this.getFingerprint(),this.getSerialNumber(),
		                          this.getIssuerDN(),this.getUsername(),new Date(this.getTimestamp()),
		                          decodeXML(getUserDataVO(), false));
	}
	
	/** just used internally in the this class to indicate that the XML can not be fixed.
	 */
	private class NotPossibleToFixXML extends Exception {
		// just used internally in the this class to indicate that the XML can not be fixed.
		public NotPossibleToFixXML() {
			// do nothing
		}
	}
	
	/** decode objects that have been serialized to xml.
	 * This method tries to fix xml that has been broken by some characters missing in the end.
	 * This has been found in some older DB during upgrade from EJBCA 3.4, and seemed to be due to 
	 * internationalized characters. This seemed to truncate the XML somehow, and here we try to handle that
	 * in a nice way.  
	 */
	private UserDataVO decodeXML(final String sXML, final boolean lastTry) {
		final byte baXML[];
		try {
			baXML = sXML.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		final XMLDecoder decoder = new XMLDecoder(new ByteArrayInputStream(baXML));
		UserDataVO useradmindata;
		try {
			Object o = decoder.readObject();
			try {
				useradmindata  = (UserDataVO)o;
			} catch( ClassCastException e ) {
				if (log.isTraceEnabled()) {
					log.trace("Trying to decode new type of CertReqHistoryData: "+e.getMessage());
				}
				// It is probably an older object of type UserDataVO
				EndEntityInformation newdata = (EndEntityInformation)o;
				useradmindata = UserDataVO.fromEndEntityInformation(newdata);
			}
		} catch( Throwable t ) {
			// try to repair the end of the XML string.
			// this will only succeed if a limited number of chars is lost in the end of the string
			// note that this code will not make anything worse and that it will not be run if the XML can be encoded.
			// 
			try {
				if ( lastTry ) {
					return null;
				}
				final String sFixedXML = FixEndOfBrokenXML.fixXML(sXML, "string", "</void></object></java>");
				if ( sFixedXML==null ) {
					throw new NotPossibleToFixXML();					
				}
				final UserDataVO userDataVO = decodeXML(sFixedXML, true);
				if ( userDataVO==null ) {
					throw new NotPossibleToFixXML();
				}
				storeUserDataVO(userDataVO); // store it right so it does not have to be repaired again.
				log.warn(printUserDataVOXML("XML has been repaired. Trailing tags fixed. DB updated with correct XML.", sXML));
				return userDataVO;
			} catch ( NotPossibleToFixXML e ) {
				log.error(printUserDataVOXML("Not possible to decode UserDataVO. No way to fix the XML.", sXML), t);
				return null;
			}
		} finally {
			decoder.close();
		}
		if (log.isTraceEnabled() ) {
			log.trace(printUserDataVOXML("Successfully decoded UserDataVO XML.",sXML));
		}
		/* Code that fixes broken XML that has actually been parsed. It seems that the decoder is not checking for the java end tag.
		 * Currently this is left out in order to not mess with working but broken XML.
		if ( sXML.indexOf("<java")>0 && sXML.indexOf("</java>")<0 ) {
			storeUserDataVO(useradmindata); // store it right				
		}
		 */
		return useradmindata;
	}
	private String printUserDataVOXML(String sComment, String sXML) {
		final StringWriter sw = new StringWriter();
		final PrintWriter pw = new PrintWriter(sw);
		pw.println(sComment);
		pw.println("XMLDATA start on next line:");
		pw.print(sXML);
		pw.println("| end of XMLDATA. The char before '|' was the last XML.");
		pw.println();
		pw.println("Issuer DN: "+getIssuerDN());
		pw.println("Serial #"+getSerialNumber());
		pw.println("User name: "+getUsername());
		pw.println("Certificate fingerprint: "+getFingerprint());
		pw.println();
		return sw.toString();
	}

	//
	// Search functions. 
	//

	/** @return the found entity instance or null if the entity does not exist */
	public static CertReqHistoryData findById(EntityManager entityManager, String fingerprint) {
		return entityManager.find(CertReqHistoryData.class, fingerprint);
	}
	
	/** @return return the query results as a List. */
	public static List<CertReqHistoryData> findByIssuerDNSerialNumber(EntityManager entityManager, String issuerDN, String serialNumber) {
		Query query = entityManager.createQuery("SELECT a FROM CertReqHistoryData a WHERE a.issuerDN=:issuerDN AND a.serialNumber=:serialNumber");
		query.setParameter("issuerDN", issuerDN);
		query.setParameter("serialNumber", serialNumber);
		return query.getResultList();
	}

	/** @return return the query results as a List. */
	public static List<CertReqHistoryData> findByUsername(EntityManager entityManager, String username) {
		Query query = entityManager.createQuery("SELECT a FROM CertReqHistoryData a WHERE a.username=:username");
		query.setParameter("username", username);
		return query.getResultList();
	}
}
