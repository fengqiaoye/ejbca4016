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

package org.ejbca.ui.web.admin.approval;

import java.io.UnsupportedEncodingException;
import java.security.cert.Certificate;

import javax.ejb.EJBException;

import org.ejbca.core.model.approval.Approval;
import org.ejbca.ui.web.admin.configuration.EjbcaJSFHelper;
import org.ejbca.ui.web.admin.configuration.EjbcaWebBean;
import org.ejbca.util.CertTools;

/**
 * Class used to represent the view of an approval
 * 
 * @author Philip Vendil
 * @version $Id: ApprovalView.java 11951 2011-05-11 11:22:00Z jeklund $
 */
public class ApprovalView {
	
	private Approval approval;
	
	public ApprovalView(Approval approval){
		this.approval=approval; 
	}

	public Approval getApproval() {
		return approval;
	}
	
	public String getApprovalDate(){
		return EjbcaJSFHelper.getBean().getEjbcaWebBean().formatAsISO8601(approval.getApprovalDate());
	}
	
	public String getApprovalAdmin(){
		return approval.getAdmin().getUsername();
	}
	
	public String getAdminAction(){
		EjbcaWebBean ejbcawebbean = EjbcaJSFHelper.getBean().getEjbcaWebBean();
		
		if(approval.isApproved()){
			return ejbcawebbean.getText("APPROVED");
		}
		return ejbcawebbean.getText("REJECTED");
	}
	
	public String getViewApproverCertLink(){
		String link;
		try {
			Certificate adminCertificate = approval.getAdmin().getAdminInformation().getX509Certificate();
			String certificateSerialNumber = CertTools.getSerialNumberAsString(adminCertificate);
			String adminIssuerDN = CertTools.getIssuerDN(adminCertificate);
			link = EjbcaJSFHelper.getBean().getEjbcaWebBean().getBaseUrl() + EjbcaJSFHelper.getBean().getEjbcaWebBean().getGlobalConfiguration().getAdminWebPath()
			            + "viewcertificate.jsp?certsernoparameter=" + java.net.URLEncoder.encode(certificateSerialNumber + "," + adminIssuerDN,"UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new EJBException(e);
		}
		
		return "viewcert('" + link + "')";
	}
	
	public String getComment(){
		return approval.getComment();
	}
}
