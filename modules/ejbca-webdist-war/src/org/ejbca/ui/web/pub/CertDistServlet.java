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
 
package org.ejbca.ui.web.pub;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509CRL;
import java.util.Collection;
import java.util.Date;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.cesecore.core.ejb.ca.crl.CrlSessionLocal;
import org.ejbca.core.ejb.ca.sign.SignSessionLocal;
import org.ejbca.core.ejb.ca.store.CertificateStatus;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionLocal;
import org.ejbca.core.model.ca.crl.RevokedCertInfo;
import org.ejbca.core.model.log.Admin;
import org.ejbca.cvc.CardVerifiableCertificate;
import org.ejbca.ui.web.RequestHelper;
import org.ejbca.util.Base64;
import org.ejbca.util.CertTools;
import org.ejbca.util.HTMLTools;

/**
 * Servlet used to distribute certificates and CRLs.<br>
 *
 * The servlet is called with method GET or POST and syntax
 * <code>command=&lt;command&gt;</code>.
 * <p>The following commands are supported:<br>
 * <ul>
 * <li>crl - gets the latest CRL.
 * <li>deltacrl - gets the latest delta CRL.
 * <li>lastcert - gets latest certificate of a user, takes argument 'subject=<subjectDN>'.
 * <li>listcerts - lists all certificates of a user, takes argument 'subject=<subjectDN>'.
 * <li>revoked - checks if a certificate is revoked, takes arguments 'subject=<subjectDN>&serno=<serial number>'.
 * <li>cacert - returns ca certificate in PEM-format, takes argument 'issuer=<issuerDN>&level=<ca-level, 0=root>'
 * <li>nscacert - returns ca certificate for Firefox, same args as above
 * <li>iecacert - returns ca certificate for Internet Explorer, same args as above
 * </ul>
 * cacert, nscacert and iecacert also takes optional parameter level=<int 1,2,...>, where the level is
 * which ca certificate in a hierachy should be returned. 0=root (default), 1=sub to root etc.
 *
 * @version $Id: CertDistServlet.java 15525 2012-09-12 14:03:42Z anatom $
 */
public class CertDistServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger(CertDistServlet.class);

    private static final String COMMAND_PROPERTY_NAME = "cmd";
    private static final String COMMAND_CRL = "crl";
    private static final String COMMAND_DELTACRL = "deltacrl"; 
    private static final String COMMAND_REVOKED = "revoked";
    private static final String COMMAND_CERT = "lastcert";
    private static final String COMMAND_LISTCERT = "listcerts";
    private static final String COMMAND_NSCACERT = "nscacert";
    private static final String COMMAND_IECACERT = "iecacert";
    private static final String COMMAND_CACERT = "cacert";
    private static final String COMMAND_CACHAIN = "cachain";
    
    private static final String SUBJECT_PROPERTY = "subject";
	private static final String CAID_PROPERTY = "caid";
    private static final String ISSUER_PROPERTY = "issuer";
    private static final String SERNO_PROPERTY = "serno";
    private static final String LEVEL_PROPERTY = "level";
    private static final String MOZILLA_PROPERTY = "moz";
    private static final String FORMAT_PROPERTY = "format";

    @EJB
    private CertificateStoreSessionLocal storesession;
    @EJB
    private CrlSessionLocal createCrlSession;
    @EJB
    private SignSessionLocal signSession;

    /**
     * init servlet
     *
     * @param config servlet configuration
     *
     * @throws ServletException error
     */
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    /**
     * handles http post
     *
     * @param req servlet request
     * @param res servlet response
     *
     * @throws IOException input/output error
     * @throws ServletException error
     */
    public void doPost(HttpServletRequest req, HttpServletResponse res)
        throws IOException, ServletException {
        log.trace(">doPost()");
        doGet(req, res);
        log.trace("<doPost()");
    } //doPost

	/**
	 * handles http get
	 *
	 * @param req servlet request
	 * @param res servlet response
	 *
	 * @throws IOException input/output error
	 * @throws ServletException error
	 */
    public void doGet(HttpServletRequest req,  HttpServletResponse res) throws java.io.IOException, ServletException {
        log.trace(">doGet()");

        String command;
        // Keep this for logging.
        String remoteAddr = req.getRemoteAddr();
        Admin administrator = new Admin(Admin.TYPE_PUBLIC_WEB_USER, remoteAddr);

        RequestHelper.setDefaultCharacterEncoding(req);
        String issuerdn = null; 
        if(req.getParameter(ISSUER_PROPERTY) != null){
            // HttpServetRequets.getParameter URLDecodes the value for you
            // No need to do it manually, that will cause problems with + characters
            issuerdn = req.getParameter(ISSUER_PROPERTY);
            issuerdn = CertTools.stringToBCDNString(issuerdn);
        }    
		int caid = 0; 
		if(req.getParameter(CAID_PROPERTY) != null){
		  caid = Integer.parseInt(req.getParameter(CAID_PROPERTY));
		}    
        // See if the client wants the response cert or CRL in PEM format (default is DER)
        String format = req.getParameter(FORMAT_PROPERTY); 
        command = req.getParameter(COMMAND_PROPERTY_NAME);
        if (command == null) {
            command = "";
        }
        if ((command.equalsIgnoreCase(COMMAND_CRL) || command.equalsIgnoreCase(COMMAND_DELTACRL)) && issuerdn != null) {
            try {
                byte[] crl = null;
                if (command.equalsIgnoreCase(COMMAND_CRL)) {
                	crl = createCrlSession.getLastCRL(administrator, issuerdn, false); // CRL
                } else {
                	crl = createCrlSession.getLastCRL(administrator, issuerdn, true); // deltaCRL
                } 
                X509CRL x509crl = CertTools.getCRLfromByteArray(crl);
                String dn = CertTools.getIssuerDN(x509crl);
                // We must remove cache headers for IE
                ServletUtils.removeCacheHeaders(res);
                String moz = req.getParameter(MOZILLA_PROPERTY);
                String filename = CertTools.getPartFromDN(dn,"CN")+".crl";
                if (command.equalsIgnoreCase(COMMAND_DELTACRL)) {
                	filename = "delta_"+filename;
                }                 
                if ((moz == null) || !moz.equalsIgnoreCase("y")) {
                    res.setHeader("Content-disposition", "attachment; filename=\"" +  filename+"\"");                    
                }
                res.setContentType("application/x-x509-crl");
                if (StringUtils.equals(format, "PEM")) {
                    RequestHelper.sendNewB64File(Base64.encode(crl, true), res, filename, RequestHelper.BEGIN_CRL_WITH_NL, RequestHelper.END_CRL_WITH_NL);
                } else {
                    res.setContentLength(crl.length);
                    res.getOutputStream().write(crl);                    
                }
                log.debug("Sent latest CRL to client at " + remoteAddr);
            } catch (Exception e) {
                log.debug("Error sending latest CRL to " + remoteAddr+": ", e);
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "Error getting latest CRL.");
                return;
            }
        } else if (command.equalsIgnoreCase(COMMAND_CERT) || command.equalsIgnoreCase(COMMAND_LISTCERT)) {
            // HttpServetRequets.getParameter URLDecodes the value for you
            // No need to do it manually, that will cause problems with + characters
        	String dn = req.getParameter(SUBJECT_PROPERTY);
            if (dn == null) {
                log.debug("Bad request, no 'subject' arg to 'lastcert' or 'listcert' command.");
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Usage command=lastcert/listcert?subject=<subjectdn>.");
                return;
            }
            try {
                log.debug("Looking for certificates for '"+dn+"'.");
                Collection<Certificate> certcoll = storesession.findCertificatesBySubject(administrator, dn);
                Object[] certs = certcoll.toArray();
                int latestcertno = -1;
                if (command.equalsIgnoreCase(COMMAND_CERT)) {
                    long maxdate = 0;
                    for (int i=0;i<certs.length;i++) {
                        if (i == 0) {
                            maxdate = CertTools.getNotBefore((Certificate)certs[i]).getTime();
                            latestcertno = 0;
                        }
                        else if ( CertTools.getNotBefore((Certificate)certs[i]).getTime() > maxdate ) {
                            maxdate = CertTools.getNotBefore(((Certificate)certs[i])).getTime();
                            latestcertno = i;
                        }
                    }
                    if (latestcertno > -1) {
                    	Certificate certcert = (Certificate)certs[latestcertno];
                        byte[] cert = certcert.getEncoded();
                    	String ending = ".cer";
                    	if (certcert instanceof CardVerifiableCertificate) {
                    		ending = ".cvcert";
                    	}
                        String filename = RequestHelper.getFileNameFromCertNoEnding(certcert, "ca");
                        filename = filename+ending;                        
                        // We must remove cache headers for IE
                        ServletUtils.removeCacheHeaders(res);
                        res.setHeader("Content-disposition", "attachment; filename=\"" +  filename+"\"");
                        res.setContentType("application/octet-stream");
                        if (StringUtils.equals(format, "PEM")) {
                            RequestHelper.sendNewB64File(Base64.encode(cert, true), res, filename, RequestHelper.BEGIN_CERTIFICATE_WITH_NL, RequestHelper.END_CERTIFICATE_WITH_NL);
                        } else {
                            res.setContentLength(cert.length);
                            res.getOutputStream().write(cert);
                        }
                        log.debug("Sent latest certificate for '"+dn+"' to client at " + remoteAddr);

                    } else {
                        log.debug("No certificate found for '"+dn+"'.");
                        res.sendError(HttpServletResponse.SC_NOT_FOUND, "No certificate found for requested subject '"+HTMLTools.htmlescape(dn)+"'.");
                    }
                }
                if (command.equalsIgnoreCase(COMMAND_LISTCERT)) {
                    res.setContentType("text/html");
                    PrintWriter pout = new PrintWriter(res.getOutputStream());
                    printHtmlHeader("Certificates for "+HTMLTools.htmlescape(dn), pout);
                    for (int i=0;i<certs.length;i++) {
                        Date notBefore = CertTools.getNotBefore((Certificate)certs[i]);
                        Date notAfter = CertTools.getNotAfter((Certificate)certs[i]);
                        String subject = CertTools.getSubjectDN((Certificate)certs[i]);
                        String issuer = CertTools.getIssuerDN((Certificate)certs[i]);
                        BigInteger serno = CertTools.getSerialNumber((Certificate)certs[i]);
                        pout.println("<pre>Subject:"+subject);
                        pout.println("Issuer:"+issuer);
                        pout.println("NotBefore:"+notBefore.toString());
                        pout.println("NotAfter:"+notAfter.toString());
                        pout.println("Serial number:"+serno.toString());
                        pout.println("</pre>");
                        pout.println("<a href=\"certdist?cmd=revoked&issuer="+URLEncoder.encode(issuer, "UTF-8")+"&serno="+serno.toString()+"\">Check if certificate is revoked</a>");
                        pout.println("<hr>");

                    }
                    if (certs.length == 0) {
                        pout.println("No certificates exists for '"+HTMLTools.htmlescape(dn)+"'.");
                    }
                    printHtmlFooter(pout);
                    pout.close();
                }
            } catch (Exception e) {
                log.debug("Error getting certificates for '"+dn+"' for "+remoteAddr+": ", e);
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "Error getting certificates.");
                return;
            }
        } else if ((command.equalsIgnoreCase(COMMAND_NSCACERT) || command.equalsIgnoreCase(COMMAND_IECACERT) || command.equalsIgnoreCase(COMMAND_CACERT)) && ( issuerdn != null || caid != 0)) {
            String lev = req.getParameter(LEVEL_PROPERTY);
            int level = 0;
            boolean pkcs7 = false;
            if (lev != null) {
                level = Integer.parseInt(lev);
            }else {
                pkcs7 = true;
            }// CA is level 0, next over root level 1 etc etc, -1 returns chain as PKCS7
            try {
                Certificate[] chain = null;
                chain = getCertificateChain(administrator, caid, issuerdn);
                // chain.length-1 is last cert in chain (root CA)
                if (chain.length < level) {
                    PrintStream ps = new PrintStream(res.getOutputStream());
                    ps.println("No CA certificate of level "+level+" exist.");
                    log.debug("No CA certificate of level "+level+" exist.");
                    return;
                }
                Certificate cacert = (Certificate)chain[level];
                String filename = RequestHelper.getFileNameFromCertNoEnding(cacert, "ca");
                byte[] enccert = null;
                if (pkcs7) {
                    enccert = signSession.createPKCS7(administrator, cacert, true);
                } else {
                    enccert = cacert.getEncoded();
                }
                if (command.equalsIgnoreCase(COMMAND_NSCACERT)) {
                    res.setContentType("application/x-x509-ca-cert");
                    res.setContentLength(enccert.length);
                    res.getOutputStream().write(enccert);
                    log.debug("Sent CA cert to NS client, len="+enccert.length+".");
                } else if (command.equalsIgnoreCase(COMMAND_IECACERT)) {
                    // We must remove cache headers for IE
                    ServletUtils.removeCacheHeaders(res);
                    if (pkcs7){
                        res.setHeader("Content-disposition", "attachment; filename=\""+filename+".p7c\"");
                    } else {
                    	String ending = ".crt";
                    	if (cacert instanceof CardVerifiableCertificate) {
                    		ending = ".cvcert";
                    	}
                        res.setHeader("Content-disposition", "attachment; filename=\""+filename+ending+"\"");
                    }
                    res.setContentType("application/octet-stream");
                    res.setContentLength(enccert.length);
                    res.getOutputStream().write(enccert);
                    log.debug("Sent CA cert to IE client, len="+enccert.length+".");
                } else if (command.equalsIgnoreCase(COMMAND_CACERT)) {
                    byte[] b64cert = Base64.encode(enccert);
                    String out;
                    if (pkcs7) {
                        out = "-----BEGIN PKCS7-----\n";
                    } else {
                        out = "-----BEGIN CERTIFICATE-----\n";
                    }
                    out += new String(b64cert);
                    if (pkcs7) {
                        out += "\n-----END PKCS7-----\n";
                    } else {
                        out += "\n-----END CERTIFICATE-----\n";
                    }
                    // We must remove cache headers for IE
                    ServletUtils.removeCacheHeaders(res);
                    res.setHeader("Content-disposition", "attachment; filename=\""+filename+".pem\"");
                    res.setContentType("application/octet-stream");
                    res.setContentLength(out.length());
                    res.getOutputStream().write(out.getBytes());
                    log.debug("Sent CA cert to client, len="+out.length()+".");
                } else {
                    res.setContentType("text/plain");
                    res.getOutputStream().println("Commands="+COMMAND_NSCACERT+" || "+COMMAND_IECACERT+" || "+COMMAND_CACERT);
                    return;
                }
            } catch (Exception e) {
                log.debug("Error getting CA certificates: ", e);
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "Error getting CA certificates.");
                return;
            }
        } else if (command.equalsIgnoreCase(COMMAND_CACHAIN) && ( issuerdn != null || caid != 0)) {
        	handleCaChainCommands(administrator, issuerdn, caid, format, res);
        } else if (command.equalsIgnoreCase(COMMAND_REVOKED)) {
            String dn = req.getParameter(ISSUER_PROPERTY);
            if (dn == null) {
                log.debug("Bad request, no 'issuer' arg to 'revoked' command.");
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Usage command=revoked?issuer=<issuerdn>&serno=<serialnumber>.");
                return;
            }
            String serno = req.getParameter(SERNO_PROPERTY);
            if (serno == null) {
                log.debug("Bad request, no 'serno' arg to 'revoked' command.");
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Usage command=revoked?issuer=<issuerdn>&serno=<serialnumber>.");
                return;
            }
            log.debug("Looking for certificate for '"+dn+"' and serno='"+serno+"'.");
            try {
                CertificateStatus revinfo = storesession.getStatus(dn, new BigInteger(serno));
                PrintWriter pout = new PrintWriter(res.getOutputStream());
                res.setContentType("text/html");
                printHtmlHeader("Check revocation", pout);
                if (revinfo != null) {
                    if (revinfo.revocationReason == RevokedCertInfo.NOT_REVOKED) {
                        pout.println("<h1>NOT REVOKED</h1>");
                        pout.println("Certificate with issuer '"+HTMLTools.htmlescape(dn)+"' and serial number '"+HTMLTools.htmlescape(serno)+"' is NOT revoked.");
                    } else {
                        pout.println("<h1>REVOKED</h1>");
                        pout.println("Certificate with issuer '"+HTMLTools.htmlescape(dn)+"' and serial number '"+HTMLTools.htmlescape(serno)+"' is revoked.");
                        pout.println("RevocationDate is '"+revinfo.revocationDate+"' and reason '"+revinfo.revocationReason+"'.");
                    }
                } else {
                    pout.println("<h1>CERTIFICATE DOES NOT EXIST</h1>");
                    pout.println("Certificate with issuer '"+HTMLTools.htmlescape(dn)+"' and serial number '"+HTMLTools.htmlescape(serno)+"' does not exist.");
                }
                printHtmlFooter(pout);
                pout.close();
            } catch (Exception e) {
                log.debug("Error checking revocation for '"+dn+"' with serno '"+serno+"': ", e);
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "Error checking revocation.");
                return;
            }
        } else {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Commands=cacert | lastcert | listcerts | crl | deltacrl | revoked && issuer=<issuerdn>");
            return;
        }

    } // doGet

	private Certificate[] getCertificateChain(Admin administrator,
			int caid, String issuerdn) {
		Certificate[] chain;
		if(caid != 0) {
		    chain = (Certificate[]) signSession.getCertificateChain(administrator, caid).toArray(new Certificate[0]);
		}
		else {
		    chain = (Certificate[]) signSession.getCertificateChain(administrator, issuerdn.hashCode()).toArray(new Certificate[0]);
		}
		return chain;
	}

	private void handleCaChainCommands(Admin administrator, String issuerdn, int caid, String format, HttpServletResponse res) throws IOException {
			try {
				Certificate[] chain = getCertificateChain(administrator, caid, issuerdn);
				// This one gets it in the wrong order for a chain file...so reverse it
				ArrayUtils.reverse(chain);
				String filename = "chain.pem";
				byte[] outbytes = new byte[0];
				// Encode and send back
				if ((format == null) || StringUtils.equalsIgnoreCase(format, "pem")) {
					final StringBuilder out = new StringBuilder();
					for (int i = 0; i < chain.length; i++) {
						out.append("-----BEGIN CERTIFICATE-----\n");
						out.append(new String(Base64.encode(chain[i].getEncoded())));
						out.append("\n-----END CERTIFICATE-----\n");
					}
					outbytes = out.toString().getBytes();
				} else {
					filename = "chain.jks";
					// Create a JKS truststore with the CA certificates in
			        final KeyStore store = KeyStore.getInstance("JKS");
			        store.load(null, null);
			        for (int i = 0; i < chain.length; i++) {
				        String cadn = CertTools.getSubjectDN(chain[i]);
			        	String alias = CertTools.getPartFromDN(cadn, "CN");
			        	if (alias == null) {
			        		alias = CertTools.getPartFromDN(cadn, "O");
			        	}
			        	if (alias == null) {
			        		alias = "cacert"+i;
			        	}
			        	alias = StringUtils.replaceChars(alias, ' ', '_');
			        	alias = StringUtils.substring(alias, 0, 15);
			            store.setCertificateEntry(alias, chain[i]);
			            ByteArrayOutputStream out = new ByteArrayOutputStream();
			            store.store(out, "changeit".toCharArray());
			            out.close();
			            outbytes = out.toByteArray();
			        }
				}
				// We must remove cache headers for IE
				ServletUtils.removeCacheHeaders(res);
				res.setHeader("Content-disposition", "attachment; filename=\""+filename+"\"");
				res.setContentType("application/octet-stream");
				res.setContentLength(outbytes.length);
				res.getOutputStream().write(outbytes);
				log.debug("Sent CA certificate chain to client, len="+outbytes.length+".");						
            } catch (CertificateEncodingException e) {
                log.debug("Error getting CA certificate chain: ", e);
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "Error getting CA certificate chain.");
            } catch (KeyStoreException e) {
                log.debug("Error creating JKS with CA certificate chain: ", e);
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "Error creating JKS with CA certificate chain.");
			} catch (NoSuchAlgorithmException e) {
                log.debug("Error creating JKS with CA certificate chain: ", e);
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "Error creating JKS with CA certificate chain.");
			} catch (CertificateException e) {
                log.debug("Error creating JKS with CA certificate chain: ", e);
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "Error creating JKS with CA certificate chain.");
			} catch (EJBException e) {
                log.debug("CA does not exist: ", e);
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "CA does not exist: "+HTMLTools.htmlescape(e.getMessage()));
			}
	}
    
    private void printHtmlHeader(String title, PrintWriter pout) {
                pout.println("<html><head>");
                pout.println("<title>"+title+"</title>");
                pout.println("<META HTTP-EQUIV=\"Pragma\" CONTENT=\"no-cache\">");
                pout.println("<META HTTP-EQUIV=\"Expires\" CONTENT=\"-1\">");
                pout.println("</head>");
                pout.println("<body><p>");
    }
    private void printHtmlFooter(PrintWriter pout) {
                pout.println("</body>");
                pout.println("<head>");
                pout.println("<META HTTP-EQUIV=\"Pragma\" CONTENT=\"no-cache\">");
                pout.println("<META HTTP-EQUIV=\"Expires\" CONTENT=\"-1\">");
                pout.println("</head>");
                pout.println("</html>");
    }

}
