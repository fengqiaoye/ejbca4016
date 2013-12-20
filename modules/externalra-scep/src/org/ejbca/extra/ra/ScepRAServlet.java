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
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertStore;
import java.security.cert.CertStoreException;
import java.security.cert.Certificate;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import javax.persistence.Persistence;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessable;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.ejbca.core.model.ca.SignRequestException;
import org.ejbca.core.model.ca.SignRequestSignatureException;
import org.ejbca.core.model.ra.NotFoundException;
import org.ejbca.core.protocol.FailInfo;
import org.ejbca.core.protocol.IRequestMessage;
import org.ejbca.core.protocol.IResponseMessage;
import org.ejbca.core.protocol.ResponseStatus;
import org.ejbca.core.protocol.scep.ScepRequestMessage;
import org.ejbca.core.protocol.scep.ScepResponseMessage;
import org.ejbca.extra.db.ISubMessage;
import org.ejbca.extra.db.Message;
import org.ejbca.extra.db.MessageHome;
import org.ejbca.extra.db.PKCS10Request;
import org.ejbca.extra.db.PKCS10Response;
import org.ejbca.extra.db.SubMessages;
import org.ejbca.extra.util.ExtraConfiguration;
import org.ejbca.extra.util.RAKeyStore;
import org.ejbca.util.Base64;
import org.ejbca.util.CertTools;
import org.ejbca.util.CryptoProviderTools;

/**
 * Servlet implementing the RA interface of the Simple Certificate Enrollment Protocol (SCEP)
 * It have three functions:
 *   * Return the CA certificate
 *   * Save certificate requests to RA database and respond pending
 *   * Send back SCEP Success or Failed upon certificate poll request if request have
 *   been processed by CA, otherwise respond with pending
 * 
 * 
 * @version $Id: ScepRAServlet.java 11271 2011-01-27 09:04:46Z anatom $
 */
public class ScepRAServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Logger log = Logger.getLogger(ScepRAServlet.class);   
    
	private SecureRandom randomSource;
	private RAKeyStore scepraks;
	private String keyStoreNumber;
	private String cryptProvider;
	private MessageHome msgHome = null;

    /**
     * Inits the SCEP Servlet
     *
     * @param config Servlet configuration
     *
     * @throws ServletException on error during initialization
     */
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        try {
        	// Initialize configuration, not really needed but it prints some debug info that might 
        	// be interesting.
        	ExtraConfiguration.instance();
        	
            // Install BouncyCastle provider
            log.debug("Re-installing BC-provider");
            CryptoProviderTools.removeBCProvider();
            CryptoProviderTools.installBCProvider();
            
            cryptProvider = getInitParameter("cryptProvider");

            keyStoreNumber = "."+getInitParameter("keyStoreNumber");
            String kspath = ExtraConfiguration.instance().getString(ExtraConfiguration.SCEPKEYSTOREPATH+keyStoreNumber);
            String kspwd = ExtraConfiguration.instance().getString(ExtraConfiguration.SCEPKEYSTOREPWD+keyStoreNumber);
            scepraks = new RAKeyStore(kspath, kspwd);
            
            String randomAlgorithm = "SHA1PRNG";
            randomSource = SecureRandom.getInstance(randomAlgorithm);
            
            msgHome = new MessageHome(Persistence.createEntityManagerFactory("ScepRAMessageDS"), MessageHome.MESSAGETYPE_SCEPRA, true);	//false);

        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    /**
     * Handles HTTP post
     *
     * @param request java standard arg
     * @param response java standard arg
     *
     * @throws IOException input/output error
     * @throws ServletException if the post could not be handled
     */
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        log.debug(">doPost()");
        /* 
         If the remote CA supports it, any of the PKCS#7-encoded SCEP messages
         may be sent via HTTP POST instead of HTTP GET.   This is allowed for
         any SCEP message except GetCACert, GetCACertChain, GetNextCACert,
         or GetCACaps.  In this form of the message, Base 64 encoding is not
         used.
         
         POST /cgi-bin/pkiclient.exe?operation=PKIOperation
         <binary PKCS7 data>
         */
        String operation = "PKIOperation";
        ServletInputStream sin = request.getInputStream();
        // This small code snippet is inspired/copied by apache IO utils to Tomas Gustavsson...
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n = 0;
        while (-1 != (n = sin.read(buf))) {
            output.write(buf, 0, n);
        }
        String message = new String(Base64.encode(output.toByteArray()));
        service(operation, message, request.getRemoteAddr(), response);
        log.debug("<doPost()");
    } //doPost

    /**
     * Handles HTTP get
     *
     * @param request java standard arg
     * @param response java standard arg
     *
     * @throws IOException input/output error
     * @throws ServletException if the post could not be handled
     */
    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws java.io.IOException, ServletException {
    	log.debug(">doGet()");

    	log.debug("query string=" + request.getQueryString());

    	// These are mandatory in SCEP GET
    	/*
             GET /cgi-bin/pkiclient.exe?operation=PKIOperation&message=MIAGCSqGSIb3D
             QEHA6CAMIACAQAxgDCBzAIBADB2MGIxETAPBgNVBAcTCE ......AAAAAA== 
    	 */
    	String operation = request.getParameter("operation");
    	String message = request.getParameter("message");
    	// Some clients don't url encode the + sign in the request
        if (message != null) {
        	message = message.replace(' ', '+');
        }

    	service(operation, message, request.getRemoteAddr(), response);

    	log.debug("<doGet()");
    } // doGet

    private void service(String operation, String message, String remoteAddr, HttpServletResponse response) throws IOException {
        try {
            if ((operation == null) || (message == null)) {
                log.error("Got request missing operation and/or message parameters.");
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "Parameters 'operation' and 'message' must be supplied!");
                return;
            }
            log.debug("Got request '" + operation + "'");
            log.debug("Message: " + message);
        	log.debug("Operation is : " + operation);
        	
            String alias = scepraks.getAlias();
        	log.debug("SCEP RA Keystore alias : " + alias);
            KeyStore raks = scepraks.getKeyStore();
            Certificate[] chain = raks.getCertificateChain(alias);
            X509Certificate cacert = null;
            if (chain.length > 1) {
            	// This should absolutely be more than one!
                cacert = (X509Certificate)chain[1];            	
            } else {
            	log.error("Certificate chain in RA keystore is only 1 certificate long! This is en error, because there should also be CA certificates.");
            }
            X509Certificate racert = (X509Certificate) raks.getCertificate(alias);
            String kspwd = ExtraConfiguration.instance().getString(ExtraConfiguration.SCEPKEYSTOREPWD+keyStoreNumber);
            PrivateKey rapriv = (PrivateKey) raks.getKey(alias, kspwd.toCharArray());

        	
            if (operation.equals("PKIOperation")) {
                byte[] scepmsg = Base64.decode(message.getBytes());

                // Read the message end get the cert, this also checks authorization
                boolean includeCACert = true;
                if (StringUtils.equals("0", getInitParameter("includeCACert"))) {
                	includeCACert = false;
                }

                byte[] reply = null;                                
                ScepRequestMessage reqmsg = new ScepRequestMessage(scepmsg, includeCACert);
                String transId = reqmsg.getTransactionId();
                log.debug("Received a message of type: "+reqmsg.getMessageType());
                if(reqmsg.getMessageType() == ScepRequestMessage.SCEP_TYPE_GETCERTINITIAL) {
                	log.info("Received a GetCertInitial message from host: "+remoteAddr);
                	Message msg = null;
                	try {
                		msg = msgHome.findByMessageId(transId);                		
                	} catch (Exception e) {
                		// TODO: internal resources
                		log.info("Error looking for message with transId "+transId+" :", e);
                	}
                	if(msg != null) {
                		if(msg.getStatus().equals(Message.STATUS_PROCESSED)) {
                			log.debug("Request is processed with status: "+msg.getStatus());
                			SubMessages submessagesresp = msg.getSubMessages(null,null,null);
                			Iterator<ISubMessage> iter =  submessagesresp.getSubMessages().iterator();
                			PKCS10Response resp = (PKCS10Response)iter.next();
                			// create proper ScepResponseMessage
                			IResponseMessage ret = reqmsg.createResponseMessage(org.ejbca.core.protocol.scep.ScepResponseMessage.class, reqmsg, racert, rapriv, cryptProvider);
                			ret.setCACert(cacert);
            				X509Certificate respCert = resp.getCertificate();
                			if ( resp.isSuccessful() && (respCert != null) ) {
                				ret.setCertificate(respCert);                					
                			} else {
                				ret.setStatus(ResponseStatus.FAILURE);
                				ret.setFailInfo(FailInfo.BAD_REQUEST);
                				String failText = resp.getFailInfo();
                				ret.setFailText(failText);
                			}
                			ret.create();
                			reply = ret.getResponseMessage();                				
                		} else {
                			log.debug("Request is not yet processed, status: "+msg.getStatus());
                    		reply = createPendingResponseMessage(reqmsg, racert, rapriv, cryptProvider).getResponseMessage();
                    		log.debug("Responding with pending response, still pending.");               			
                		}                		
                	}else{
                		// User doesn't exist
                	}
                } else {         
                	if(reqmsg.getMessageType() == ScepRequestMessage.SCEP_TYPE_PKCSREQ) {  
                    	log.debug("Received a PKCSReq message from host: "+remoteAddr);
                    	// Decrypt the Scep message and extract the pkcs10 request
                        if (reqmsg.requireKeyInfo()) {
                            // scep encrypts message with the RAs certificate
                            reqmsg.setKeyInfo(racert, rapriv, cryptProvider);
                        }
                        // Verify the request
                        if (reqmsg.verify() == false) {
                        	String msg = "POPO verification failed.";
                            log.error(msg);
                            throw new SignRequestSignatureException(msg);
                        }
                        String username = reqmsg.getUsername(); 
                        if (username == null) {
                        	String msg = "No username in request, request DN: "+reqmsg.getRequestDN();
                            log.error(msg);
                            throw new SignRequestException(msg);
                        }
                        log.info("Received a SCEP/PKCS10 request for user: "+username+", from host: "+remoteAddr);
                        String authPwd = ExtraConfiguration.instance().getString(ExtraConfiguration.SCEPAUTHPWD);
                        if (StringUtils.isNotEmpty(authPwd) && !StringUtils.equals(authPwd, "none")) {
                        	log.debug("Requiring authPwd in order to precess SCEP requests");
                        	String pwd = reqmsg.getPassword();
                        	if (!StringUtils.equals(authPwd, pwd)) {
                        		log.error("Wrong auth password received in SCEP request: "+pwd);
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Auth pwd missmatch");
                                return;
                        	}
                        	log.debug("Request passed authPwd test.");
                        } else {
                        	log.debug("Not requiring authPwd in order to precess SCEP requests");                        	
                        }
                        // Try to find the CA name from the issuerDN, if we can't find it (i.e. not defined in web.xml) we use the default
                        String issuerDN = CertTools.stringToBCDNString(reqmsg.getIssuerDN());
                        String caName = ExtraConfiguration.instance().getString(issuerDN);
                        if (StringUtils.isEmpty(caName)) {
                        	caName = ExtraConfiguration.instance().getString(ExtraConfiguration.SCEPDEFAULTCA);
                        	log.info("Did not find a CA name from issuerDN: "+issuerDN+", using the default CA '"+caName+"'");
                        } else {
                        	log.debug("Found a CA name '"+caName+"' from issuerDN: "+issuerDN);
                        }
                        // Get altNames if we can find them
                        String altNames = reqmsg.getRequestAltNames();

                        byte[] encoded = reqmsg.getCertificationRequest().getEncoded();
                        String pkcs10 = new String(Base64.encode(encoded, false));
                        
                    	// Create a pkcs10 request
                        String certificateProfile = ExtraConfiguration.instance().getString(ExtraConfiguration.SCEPCERTPROFILEKEY);
                        String entityProfile = ExtraConfiguration.instance().getString(ExtraConfiguration.SCEPENTITYPROFILEKEY);
                		boolean createOrEditUser = ExtraConfiguration.instance().getBoolean(ExtraConfiguration.SCEPEDITUSER);
                		PKCS10Request req = new PKCS10Request(100,username, reqmsg.getRequestDN(), altNames, null, null, entityProfile, certificateProfile, caName, pkcs10);
                		req.setCreateOrEditUser(createOrEditUser);
                		SubMessages submessages = new SubMessages();
                		submessages.addSubMessage(req);
                		msgHome.create(transId, submessages);
                		reply = createPendingResponseMessage(reqmsg, racert, rapriv, cryptProvider).getResponseMessage();
                	}
                }
                
                if (reply == null) {
                    // This is probably a getCert message?
                	log.debug("Sending HttpServletResponse.SC_NOT_IMPLEMENTED (501) response");
                    response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Can not handle request");
                    return;
                }
                // Send back SCEP response, PKCS#7 which contains the end entity's certificate, or pending, or failure
                sendBinaryBytes(reply, response, "application/x-pki-message", null);
            } else if (operation.equals("GetCACert")) {
                // The response has the content type tagged as application/x-x509-ca-cert. 
                // The body of the response is a DER encoded binary X.509 certificate. 
                // For example: "Content-Type:application/x-x509-ca-cert\n\n"<BER-encoded X509>
            	// IF we are not an RA, which in case we should return the same thing as GetCACertChain
                log.info("Got SCEP cert request for CA '" + message + "'");
                if (chain != null) {
                	if (chain.length > 1) {
                		// We are an RA, so return the same as GetCACertChain, but with other content type
                        getCACertChain(message, remoteAddr, response, alias, raks, false);
                	} else {
                    	// The CA certificate is no 0
                    	X509Certificate cert = (X509Certificate)chain[0];
                    	if (chain.length > 1) {
                    		cert = (X509Certificate)chain[1];
                    	}
                    	log.debug("Found cert with DN '" + cert.getSubjectDN().toString() + "'");
                        log.info("Sent certificate for CA '" + message + "' to SCEP client with ip " + remoteAddr);
                        sendBinaryBytes(cert.getEncoded(), response, "application/x-x509-ca-cert", null);
                	}
                } else {
                    log.error("No CA certificates found");
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, "No CA certificates found.");
                }                
            } else if (operation.equals("GetCACertChain")) {                
                // The response for GetCACertChain is a certificates-only PKCS#7 
                // SignedDatato carry the certificates to the end entity, with a 
                // Content-Type of application/x-x509-ca-ra-cert-chain.
                log.info("Got SCEP cert chain request for CA '" + message + "'");
                getCACertChain(message, remoteAddr, response, alias, raks, true);
            } else if (operation.equals("GetCACaps")) {
                // The response for GetCACaps is a <lf> separated list of capabilities

                /*
                 "GetNextCACert"       CA Supports the GetNextCACert message.
                 "POSTPKIOperation"    PKIOPeration messages may be sent via HTTP POST.
                 "SHA-1"               CA Supports the SHA-1 hashing algorithm in 
                                       signatures and fingerprints.  If present, the
                                       client SHOULD use SHA-1.  If absent, the client
                                       MUST use MD5 to maintain backward compatability.
                 "Renewal"             Clients may use current certificate and key to
                                       authenticate an enrollment request for a new
                                       certificate.  
                 */
                log.info("Got SCEP CACaps request for CA '" + message + "'");
                response.setContentType("text/plain");
                response.getOutputStream().print("POSTPKIOperation\nSHA-1");
            }	
        } catch (java.lang.ArrayIndexOutOfBoundsException ae) {
            log.error("Empty or invalid request received.", ae);            
            // TODO: Send back proper Failure Response
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, ae.getMessage());
        } catch (Exception e) {
            log.error("Error in ScepRAServlet:", e);
            // TODO: Send back proper Failure Response
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        }
    }

	private void getCACertChain(String message, String remoteAddr, HttpServletResponse response, String alias, KeyStore raks, boolean getcaracertchain) throws KeyStoreException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, CertStoreException, CMSException, IOException, Exception {
		Certificate[] chain = raks.getCertificateChain(alias);
		log.debug("CACertChain is of length: "+chain.length);
		if (chain != null) {
			X509Certificate cert = (X509Certificate) raks.getCertificateChain(alias)[0];
			log.debug("Found cert with DN '" + cert.getSubjectDN().toString() + "'");
//            X509Certificate racert = (X509Certificate) raks.getCertificate(alias);
//            PrivateKey rapriv = (PrivateKey) raks.getKey(alias, keystorepwd.toCharArray());
			byte[] pkcs7response = createPKCS7(chain, null, null);                               
			String ctype = "application/x-x509-ca-ra-cert";
			if (getcaracertchain) {
				ctype = "application/x-x509-ca-ra-cert-chain";				
			}
			log.debug("Sent certificate(s) for CA/RA '" + message + "' to SCEP client with ip "+remoteAddr+". Using content-type: "+ctype);
			sendBinaryBytes(pkcs7response, response, ctype, null);                						
		} else {
		    log.error("No CA certificates found");
		    response.sendError(HttpServletResponse.SC_NOT_FOUND, "No CA certificates found.");
		}
	}
    
    private ScepResponseMessage createPendingResponseMessage(IRequestMessage req, X509Certificate racert, PrivateKey rakey, String cryptProvider) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, IOException, SignRequestException, NotFoundException {
    	ScepResponseMessage ret = new ScepResponseMessage();
    	// Create the response message and set all required fields
    	if (ret.requireSignKeyInfo()) {
    		log.debug("Signing message with cert: "+racert.getSubjectDN().getName());
    		ret.setSignKeyInfo(racert, rakey, cryptProvider);
    	}
    	if (req.getSenderNonce() != null) {
    		ret.setRecipientNonce(req.getSenderNonce());
    	}
    	if (req.getTransactionId() != null) {
    		ret.setTransactionId(req.getTransactionId());
    	}
    	// Sendernonce is a random number
    	byte[] senderNonce = new byte[16];
    	randomSource.nextBytes(senderNonce);
    	ret.setSenderNonce(new String(Base64.encode(senderNonce)));
    	// If we have a specified request key info, use it in the reply
    	if (req.getRequestKeyInfo() != null) {
    		ret.setRecipientKeyInfo(req.getRequestKeyInfo());
    	}
    	// Which digest algorithm to use to create the response, if applicable
    	ret.setPreferredDigestAlg(req.getPreferredDigestAlg());
    	// Include the CA cert or not in the response, if applicable for the response type
    	ret.setIncludeCACert(req.includeCACert());         
        ret.setStatus(ResponseStatus.PENDING);
        ret.create();
    	return ret;
    }
    
    private byte[] createPKCS7(Certificate[] chain, PrivateKey pk, X509Certificate cert) throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, CertStoreException, CMSException, IOException {
    	Collection<Certificate> certList = Arrays.asList(chain);
    	CMSProcessable msg = new CMSProcessableByteArray(new byte[0]);
    	CertStore certs = CertStore.getInstance("Collection", new CollectionCertStoreParameters(certList), "BC");
    	CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
    	gen.addCertificatesAndCRLs(certs);
    	// it is possible to sign the pkcs7, but it's not currently used
    	CMSSignedData s = null;
    	if ( (pk != null) && (cert != null) ) {
    		gen.addSigner(pk, cert, CMSSignedDataGenerator.DIGEST_MD5);
        	s = gen.generate(msg, true, "BC");
    	} else {
        	s = gen.generate(msg, "BC");    		
    	}
    	return s.getEncoded();
    }    

    //
    // Methods that were shamelessly ripped from ServletUtils and RequestHelper to avoid dependencies
    //
    
    /**
     * Sends back a number of bytes
     *
     * @param bytes DER encoded certificate to be returned
     * @param out output stream to send to
     * @param contentType mime type to send back bytes as
     * @param fileName to call the file in a Content-disposition, can be null to leave out this header
     *
     * @throws Exception on error
     */
    private void sendBinaryBytes(byte[] bytes, HttpServletResponse out, String contentType, String filename)
        throws Exception {
        if ( (bytes == null) || (bytes.length == 0) ) {
            log.error("0 length can not be sent to client!");
            return;
        }
        if (filename != null) {
            // We must remove cache headers for IE
            removeCacheHeaders(out);
            out.setHeader("Content-disposition", "filename=\""+filename+"\"");        	
        }
        // Set content-type to general file
        out.setContentType(contentType);
        out.setContentLength(bytes.length);
        // Write the certificate
        ServletOutputStream os = out.getOutputStream();
        os.write(bytes);
        out.flushBuffer();
        log.debug("Sent " + bytes.length + " bytes to client");
    }
    
    /** Helper methods that removes no-cache headers from a response. No-cache headers 
     * makes IE refuse to save a file that is sent (for example a certificate). 
     * No-cache headers are also automatically added by Tomcat by default, so we better
     * make sure they are set to a harmless value.
     * 
     * @param res HttpServletResponse parameter as taken from the doGet, doPost methods in a Servlet.
     */
    private void removeCacheHeaders(HttpServletResponse res) {
        if (res.containsHeader("Pragma")) {
            log.debug("Removing Pragma header to avoid caching issues in IE");
            res.setHeader("Pragma","null");
        }
        if (res.containsHeader("Cache-Control")) {
            log.debug("Removing Cache-Control header to avoid caching issues in IE");
            res.setHeader("Cache-Control","null");
        }
    }

}
