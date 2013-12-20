/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
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

import java.io.Serializable;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.Table;

import org.apache.log4j.Logger;
import org.ejbca.util.Base64;
import org.ejbca.util.CertTools;

/**
 * Base64 encoded certificates.<br>
 * If the property "database.useSeparateCertificateTable" is true then it will
 * be one new row in this table for each certificate added to {@link CertificateData}.<br>
 * If the property is false then this table will not be used.
 * 
 * @version $Id: Base64CertData.java 17189 2013-06-18 11:41:51Z primelars $
 */
@Entity
@Table(name = "Base64CertData")
public class Base64CertData implements Serializable {

    private static final long serialVersionUID = 4132839902195978822L;

    private static final Logger log = Logger.getLogger(Base64CertData.class);

    private String fingerprint = "";
    private String base64Cert;
    private int rowVersion = 0;

    /**
     * Storing an encoded certificate. Called only when
     * {@link CertificateData#CertificateData(Certificate, java.security.PublicKey, String, String, int, int, int, String, long, boolean)}
     * is called with useBase64CertTable set to true.
     * @param incert the (X509)Certificate to be stored in the database.
     */
    public Base64CertData(Certificate incert) {
        // Extract all fields to store with the certificate.
        try {
            setBase64Cert(new String(Base64.encode(incert.getEncoded())));
            setFingerprint( CertTools.getFingerprintAsString(incert) );
        } catch (CertificateEncodingException cee) {
            final String msg = "Can't extract DER encoded certificate information.";
            log.error(msg, cee);
            throw new RuntimeException(msg);
        }
    }

    public Base64CertData() {
    }

    /**
     * Fingerprint of certificate
     * 
     * @return fingerprint
     */
    // @Id @Column
    public String getFingerprint() {
        return this.fingerprint;
    }

    /**
     * Fingerprint of certificate
     * 
     * @param fingerprint fingerprint
     */
    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    /**
     * The encoded certificate.
     * Called from {@link CertificateData#getCertificate(EntityManager)} when
     * there is no encoded certificate in {@link CertificateData}.
     * 
     * @return base64 encoded certificate
     */
    // @Column @Lob
    public String getBase64Cert() {
        return this.base64Cert;
    }

    /**
     * The certificate itself
     * 
     * @param base64Cert base64 encoded certificate
     */
    public void setBase64Cert(String base64Cert) {
        this.base64Cert = base64Cert;
    }

    // @Version @Column
    public int getRowVersion() {
        return this.rowVersion;
    }

    public void setRowVersion(int rowVersion) {
        this.rowVersion = rowVersion;
    }

    //
    // Search functions.
    //

    /** @return the found entity instance or null if the entity does not exist */
    public static Base64CertData findByFingerprint(EntityManager entityManager, String fingerprint) {
        return entityManager.find(Base64CertData.class, fingerprint);
    }

    /** @return the number of entries with the given parameter */
    public static long getCount(EntityManager entityManager) {
        final Query countQuery = entityManager.createQuery("SELECT COUNT(a) FROM Base64CertData a");
        return ((Long) countQuery.getSingleResult()).longValue(); // Always returns a result
    }

}
