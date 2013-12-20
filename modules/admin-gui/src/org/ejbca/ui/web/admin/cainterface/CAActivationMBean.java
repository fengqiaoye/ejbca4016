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
package org.ejbca.ui.web.admin.cainterface;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.cesecore.core.ejb.ca.crl.CrlCreateSession;
import org.cesecore.core.ejb.ca.store.CertificateProfileSession;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSession;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.authorization.AuthorizationSession;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSession;
import org.ejbca.core.ejb.ca.caadmin.CaSession;
import org.ejbca.core.ejb.ca.store.CertificateStoreSession;
import org.ejbca.core.ejb.config.GlobalConfigurationSession;
import org.ejbca.core.ejb.ra.UserAdminSession;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.ca.catoken.CATokenAuthenticationFailedException;
import org.ejbca.core.model.ca.catoken.CATokenOfflineException;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.util.EjbLocalHelper;
import org.ejbca.ui.web.admin.BaseManagedBean;
import org.ejbca.ui.web.admin.configuration.EjbcaJSFHelper;
import org.ejbca.ui.web.admin.configuration.EjbcaWebBean;
import org.ejbca.ui.web.admin.configuration.InformationMemory;

/**
 * @author Tham Wickenberg
 * @version $Id: CAActivationMBean.java 11492 2011-03-09 16:34:38Z netmackan $
 */
public class CAActivationMBean extends BaseManagedBean implements Serializable {

	private static final Logger log = Logger.getLogger(CAActivationMBean.class);

	private static final long serialVersionUID = -2660384552215596717L;

	private EjbcaJSFHelper jsfHelper;
	private EjbcaWebBean webBean;
	private CADataHandler cadatahandler;
	private CAInterfaceBean caBean;
	private String authenticationcode;
	private List<CAWrapper> caInfoList;
	private Admin administrator;
	private CaSession caSession;
	private CertificateStoreSession certificatesession;
	private CertificateProfileSession certificateProfileSession;
	private CAAdminSession caadminsession;
	private EndEntityProfileSession endEntityProfileSession;
	private AuthorizationSession authorizationsession;
	private UserAdminSession adminsession;
	private GlobalConfigurationSession globalconfigurationsession;
	private CrlCreateSession crlCreateSession;
	private InformationMemory                  informationmemory;
	public static final String MAKEOFFLINE = "makeoffline";
	public static final String ACTIVATE    = "activate";
	public static final String KEEPCURRENT = "keepcurrent";

	public CAActivationMBean () {
		jsfHelper = EjbcaJSFHelper.getBean();
		webBean = jsfHelper.getEjbcaWebBean();
		new ViewCAInfoJSPHelper();
		caBean = new CAInterfaceBean();
		try {
			caBean.initialize(webBean);
		} catch (Exception e) {
			log.error("Error initializing bean: ", e);
		}
		try {
			administrator = webBean.getAdminObject();
			EjbLocalHelper ejb = new EjbLocalHelper();
			certificatesession = ejb.getCertificateStoreSession();
			caadminsession = ejb.getCaAdminSession();
			caSession = ejb.getCaSession();
			authorizationsession = ejb.getAuthorizationSession();
			adminsession = ejb.getUserAdminSession();
			globalconfigurationsession = ejb.getGlobalConfigurationSession();
			certificateProfileSession = ejb.getCertificateProfileSession();
			endEntityProfileSession = ejb.getEndEntityProfileSession();
			crlCreateSession = ejb.getCrlCreateSession();
			this.informationmemory = webBean.getInformationMemory();

			new CertificateProfileDataHandler(administrator, authorizationsession, caSession, certificateProfileSession, informationmemory);
			cadatahandler = new CADataHandler(administrator, caadminsession, ejb.getCaSession(), endEntityProfileSession, adminsession, globalconfigurationsession, certificatesession, certificateProfileSession, crlCreateSession, authorizationsession, webBean);
			caInfoList = new ArrayList<CAWrapper>();
			initializeWrappers();
		} catch (Exception e){
			log.error("Error initializing bean: ", e);
		}
	}

	/** Returns list of authorized CAs
	 * 
	 * @return List of CAWrapper
	 */
	public List<CAWrapper> getAuthorizedCAWrappers() {
		initializeWrappers();
		Iterator<CAWrapper> it = caInfoList.iterator();
		while ( it.hasNext() ) {
			CAWrapper temp = it.next();
			try {
				temp.setCAInfo(caBean.getCAInfo(temp.getID()).getCAInfo());
			} catch (Exception e) {
				log.error(e);
			}
		}
		return caInfoList;
	}

	public void initializeWrappers() {
		Collection<Integer> idList = webBean.getAuthorizedCAIds();
		Iterator<Integer> it = idList.iterator();
		while ( it.hasNext() ) {
			Integer caid = it.next();
			boolean inList = false;
			Iterator<CAWrapper> tempIt = caInfoList.iterator();
			while (tempIt.hasNext()) {
				CAWrapper wrapper = tempIt.next();
				if (wrapper.getID() == caid.intValue() ) {
					inList = true;
				}
			}
			if (!inList) {
				try {
					caInfoList.add(new CAWrapper(caBean.getCAInfo( caid.intValue()).getCAInfo(), webBean, this));
				} catch (Exception e) {
					log.error(e);
				}
			}
		}
	}

	public void setAuthenticationCode(String authenticationcode) {
		this.authenticationcode = authenticationcode;
	}

	public String getAuthenticationCode() {
		return "";
	}

	public CAInfo activateCAToken(int caid ) throws CATokenAuthenticationFailedException, CATokenOfflineException, AuthorizationDeniedException, ApprovalException, WaitingForApprovalException, Exception {
		cadatahandler.activateCAToken(caid, authenticationcode);
		log.debug("Successfully activated token");
		return caBean.getCAInfo(caid).getCAInfo();
	}

	public CAInfo deactivateCAToken(int caid) throws AuthorizationDeniedException, EjbcaException, Exception {
		cadatahandler.deactivateCAToken(caid);
		log.debug("Successfully de-activated token");
		return caBean.getCAInfo(caid).getCAInfo();
	}

	/** Updates the IncludeInChealthCheck flag in the database for the CA
	 */
	public CAInfo updateMonitored(int caid, boolean monitored) throws Exception {
		CAInfoView cv = caBean.getCAInfo(caid);
		if (cv != null) {
			CAInfo cainfo = cv.getCAInfo();
			cainfo.setIncludeInHealthCheck(monitored);
			cadatahandler.editCA(cainfo);
			return cainfo;			
		} else {
			log.debug("No CA with id: "+caid);
		}
		return null;
	}
	
	public void apply() {
		log.trace(">apply");
		List<CAWrapper> list = caInfoList;
		for (Iterator<CAWrapper> iterator = list.iterator(); iterator.hasNext();) {
			CAWrapper wrapper = iterator.next();
			try {
				String option = wrapper.getActivateOption();
				if (option.equals(CAActivationMBean.ACTIVATE)) {
					wrapper.activateCAToken();
				}
				if (option.equals(CAActivationMBean.MAKEOFFLINE)) { 
					wrapper.deactivateCAToken();
				}
				if (option.equals(CAActivationMBean.KEEPCURRENT)) {
					wrapper.setCAActivationMessage("");			
				}
				// Update the monitored flag in the DB if it changed
				CAInfoView cv = caBean.getCAInfo(wrapper.getID());
				if (cv != null) {
					CAInfo cainfo = cv.getCAInfo();
					if (wrapper.getMonitored() != cainfo.getIncludeInHealthCheck()) {
						wrapper.updateMonitored();
					}					
				} else {
					log.debug("No CA with id: "+wrapper.getID());
				}
			} catch (Exception e) {
				log.error(e);
			}			
		}
		log.trace("<apply");
	}

	public String getMakeoffline() {
		return MAKEOFFLINE;
	}
	public String getActivate() {
		return ACTIVATE;
	}
	public String getKeepcurrent() {
		return KEEPCURRENT;
	}
	
	public List<CAWrapper> getHasMessages() {
		log.trace(">getHasMessages");
		List<CAWrapper> list = caInfoList;
		List<CAWrapper> hasMessages = new ArrayList<CAWrapper>();
		for (Iterator<CAWrapper> iterator = list.iterator(); iterator.hasNext();) {
			CAWrapper wrapper = iterator.next();
			String msg = wrapper.getCAActivationMessage();
			if ( (msg != null) && (!msg.equals("")) ) {
				hasMessages.add(wrapper);
			}			
		}
		log.trace("<getHasMessages");
		return hasMessages;
	}
}
