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
package org.ejbca.extra.ra;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.DERObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.DERPrintableString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.smime.SMIMECapability;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.X509Extension;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.cms.CMSEnvelopedData;
import org.bouncycastle.cms.CMSEnvelopedDataGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSSignedGenerator;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.util.encoders.Base64;
import org.ejbca.core.model.AlgorithmConstants;
import org.ejbca.core.protocol.scep.ScepRequestMessage;
import org.ejbca.util.CertTools;

public class ScepRequestGenerator {
    private static Logger log = Logger.getLogger(ScepRequestGenerator.class);

    private X509Certificate cert = null;
    private X509Certificate cacert = null;
    private String reqdn = null;
    private KeyPair keys = null;
    private String digestOid = CMSSignedGenerator.DIGEST_SHA1;
    private PKCS10CertificationRequest p10request;

    private String senderNonce = null;
    private String transactionId = null;
    
    public void setKeys(KeyPair myKeys) {
        this.keys = myKeys;
    }
    public void setDigestOid(String oid) {
    	digestOid = oid;
    }
    /** Base 64 encode senderNonce
     */
    public String getSenderNonce() {
        return senderNonce;
    }
    public String getTransactionId() {
        return transactionId;
    }
    /** Generates a SCEP CrlReq. Keys must have been set in the generator for this to succeed 
     * 
     */
    public byte[] generateCrlReq(String dn, X509Certificate ca) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException, IOException, CMSException, InvalidAlgorithmParameterException, CertStoreException, CertificateEncodingException, IllegalStateException {
        this.cacert = ca;
        this.reqdn = dn;
        X509Name name = CertTools.stringToBcX509Name(cacert.getIssuerDN().getName());
        IssuerAndSerialNumber ias = new IssuerAndSerialNumber(name, cacert.getSerialNumber());
        // Create self signed cert, validity 1 day
        cert = CertTools.genSelfCert(reqdn,24*60*60*1000,null,keys.getPrivate(),keys.getPublic(),AlgorithmConstants.SIGALG_SHA1_WITH_RSA,false);
        
        // wrap message in pkcs#7
        byte[] msg = wrap(ias.getEncoded(), "22");        
        return msg;
    }

    /** Generates a SCEP CertReq. Keys must have been set in the generator for this to succeed 
     * 
     */
    public byte[] generateCertReq(String dn, String password, X509Certificate ca) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException, IOException, CMSException, InvalidAlgorithmParameterException, CertStoreException, CertificateEncodingException, IllegalStateException {
        this.cacert = ca;
        this.reqdn = dn;

        // Create challenge password attribute for PKCS10
        // Attributes { ATTRIBUTE:IOSet } ::= SET OF Attribute{{ IOSet }}
        //
        // Attribute { ATTRIBUTE:IOSet } ::= SEQUENCE {
        //    type    ATTRIBUTE.&id({IOSet}),
        //    values  SET SIZE(1..MAX) OF ATTRIBUTE.&Type({IOSet}{\@type})
        // }
        ASN1EncodableVector challpwdattr = new ASN1EncodableVector();
        // Challenge password attribute
        challpwdattr.add(PKCSObjectIdentifiers.pkcs_9_at_challengePassword); 
        ASN1EncodableVector pwdvalues = new ASN1EncodableVector();
        pwdvalues.add(new DERUTF8String(password));
        challpwdattr.add(new DERSet(pwdvalues));
        // Requested extensions attribute
        ASN1EncodableVector extensionattr = new ASN1EncodableVector();
        extensionattr.add(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);
        // AltNames
        GeneralNames san = CertTools.getGeneralNamesFromAltName("dNSName=foo.bar.com,iPAddress=10.0.0.1");
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        DEROutputStream dOut = new DEROutputStream(bOut);
        try {
            dOut.writeObject(san);
        } catch (IOException e) {
            throw new IllegalArgumentException("error encoding value: " + e);
        }
        Vector oidvec = new Vector();
        oidvec.add(X509Extensions.SubjectAlternativeName);
        Vector valuevec = new Vector();
        valuevec.add(new X509Extension(false, new DEROctetString(bOut.toByteArray())));
        X509Extensions exts = new X509Extensions(oidvec,valuevec);
        extensionattr.add(new DERSet(exts));
        // Complete the Attribute section of the request, the set (Attributes) contains two sequences (Attribute)
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(new DERSequence(challpwdattr));
        v.add(new DERSequence(extensionattr));
        DERSet attributes = new DERSet(v);
        // Create PKCS#10 certificate request
        p10request = new PKCS10CertificationRequest("SHA1WithRSA",
                CertTools.stringToBcX509Name(reqdn), keys.getPublic(), attributes, keys.getPrivate());
        
        // Create self signed cert, validity 1 day
        cert = CertTools.genSelfCert(reqdn,24*60*60*1000,null,keys.getPrivate(),keys.getPublic(),AlgorithmConstants.SIGALG_SHA1_WITH_RSA,false);
        
        // wrap message in pkcs#7
        byte[] msg = wrap(p10request.getEncoded(), "19");
        return msg;        
    }
    
    public byte[] generateGetCertInitial(String dn, X509Certificate ca) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException, CertStoreException, IOException, CMSException  {
        this.cacert = ca;
        this.reqdn = dn;


        ASN1EncodableVector vec = new ASN1EncodableVector();
        vec.add(new DERUTF8String(ca.getIssuerDN().getName()));
        vec.add(new DERUTF8String(dn));
        DERSequence seq = new DERSequence(vec);
              
        // wrap message in pkcs#7
        byte[] msg = wrap(seq.getEncoded(), "20");
        return msg;        
    }
    
    private CMSEnvelopedData envelope(CMSProcessable envThis) throws NoSuchAlgorithmException, NoSuchProviderException, CMSException {
        CMSEnvelopedDataGenerator edGen = new CMSEnvelopedDataGenerator();
        // Envelope the CMS message
        edGen.addKeyTransRecipient(cacert);
        CMSEnvelopedData ed = edGen.generate(envThis, SMIMECapability.dES_CBC.getId(), "BC");
        return ed;
    }
    
    private CMSSignedData sign(CMSProcessable signThis, String messageType) throws NoSuchAlgorithmException, NoSuchProviderException, CMSException, InvalidAlgorithmParameterException, CertStoreException {
        CMSSignedDataGenerator gen1 = new CMSSignedDataGenerator();

        // add authenticated attributes...status, transactionId, sender- and more...
        Hashtable attributes = new Hashtable();
        DERObjectIdentifier oid;
        Attribute attr;
        DERSet value;
        
        // Message type (certreq)
        oid = new DERObjectIdentifier(ScepRequestMessage.id_messageType);
        value = new DERSet(new DERPrintableString(messageType));
        attr = new Attribute(oid, value);
        attributes.put(attr.getAttrType(), attr);

        // TransactionId
        byte[] digest = CertTools.generateMD5Fingerprint(cert.getPublicKey().getEncoded());
        transactionId = new String(Base64.encode(digest));
        oid = new DERObjectIdentifier(ScepRequestMessage.id_transId);
        value = new DERSet(new DERPrintableString(Base64.encode(digest)));
        attr = new Attribute(oid, value);
        attributes.put(attr.getAttrType(), attr);

        // senderNonce
        byte[] nonce = new byte[16];
        SecureRandom randomSource = SecureRandom.getInstance("SHA1PRNG");
        randomSource.nextBytes(nonce);
        senderNonce = new String(Base64.encode(nonce));
        if (nonce != null) {
            oid = new DERObjectIdentifier(ScepRequestMessage.id_senderNonce);
            log.debug("Added senderNonce: " + senderNonce);
            value = new DERSet(new DEROctetString(nonce));
            attr = new Attribute(oid, value);
            attributes.put(attr.getAttrType(), attr);
        }

        // Add our signer info and sign the message
        ArrayList certList = new ArrayList();
        certList.add(cert);
        CertStore certs = CertStore.getInstance("Collection", new CollectionCertStoreParameters(certList), "BC");
        gen1.addCertificatesAndCRLs(certs);
        gen1.addSigner(keys.getPrivate(), cert, digestOid,
                new AttributeTable(attributes), null);
        // The signed data to be enveloped
        CMSSignedData s = gen1.generate(signThis, true, "BC");
        return s;
    }
    private byte[] wrap(byte[] envBytes, String messageType) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, CMSException, InvalidAlgorithmParameterException, CertStoreException {

        // 
        // Create inner enveloped data
        //
        CMSEnvelopedData ed = envelope(new CMSProcessableByteArray(envBytes));
        log.debug("Enveloped data is " + ed.getEncoded().length + " bytes long");
        CMSProcessable msg = new CMSProcessableByteArray(ed.getEncoded());
        //
        // Create the outer signed data
        //
        CMSSignedData s = sign(msg, messageType);
        
        byte[] ret = s.getEncoded();
        return ret;
        
    }
}
