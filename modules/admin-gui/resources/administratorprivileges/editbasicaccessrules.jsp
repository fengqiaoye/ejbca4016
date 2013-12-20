<%
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
 
 // Original version by Philip Vendil.
 
%>
<%@ taglib uri="http://java.sun.com/jsf/html" prefix="h" %>
<%@ taglib uri="http://java.sun.com/jsf/core" prefix="f" %>
<%@ page pageEncoding="ISO-8859-1"%>
<% response.setContentType("text/html; charset="+org.ejbca.config.WebConfiguration.getWebContentEncoding()); %>
<%@page errorPage="/errorpage.jsp" import="org.ejbca.ui.web.admin.configuration.EjbcaWebBean,org.ejbca.config.GlobalConfiguration,
	org.ejbca.core.model.authorization.BasicAccessRuleSet"%>
 
<jsp:useBean id="ejbcawebbean" scope="session" class="org.ejbca.ui.web.admin.configuration.EjbcaWebBean" />
 
<%
	GlobalConfiguration globalconfiguration = ejbcawebbean.initialize(request, "/system_functionality/edit_administrator_privileges"); 
%>
 
<html>
<f:view>
<head>
  <title><h:outputText value="#{web.ejbcaWebBean.globalConfiguration.ejbcaTitle}" /></title>
  <base href="<%= ejbcawebbean.getBaseUrl() %>" />
  <link rel="stylesheet" type="text/css" href="<%= ejbcawebbean.getCssFile() %>" />
  <script type="text/javascript" src="<%= globalconfiguration.getAdminWebPath() %>ejbcajslib.js"></script>
<script type="text/javascript">
<!--  
 
function roleupdated(){
  var selectcas = document.getElementById('basicRules:selectcas');
  var selectrole = document.getElementById('basicRules:selectrole');
  var selectendentityrules = document.getElementById('basicRules:selectendentityrules');
  var selectendentityprofiles = document.getElementById('basicRules:selectendentityprofiles');
  var selectother = document.getElementById('basicRules:selectother');
	
  var currentrole = selectrole.options[selectrole.options.selectedIndex].value;  
 
  if(currentrole == <%=BasicAccessRuleSet.ROLE_NONE %>){
    selectcas.disabled = true;
    selectendentityrules.disabled = true;
    selectendentityprofiles.disabled = true;
    selectother.disabled = true;
 
    numofcas = selectcas.length;
    for( i=numofcas-1; i >= 0; i-- ){          
         selectcas.options[i].selected=false;
    }
 
    numofendentity = selectendentityrules.length;
    for( i=numofendentity-1; i >= 0; i-- ){          
         selectendentityrules.options[i].selected=false;
    }
 
    numofprofiles = selectendentityprofiles.length;
    for( i=numofprofiles-1; i >= 0; i-- ){          
         selectendentityprofiles.options[i].selected=false;
    }
 
    numofother = selectother.length;
    for( i=numofother-1; i >= 0; i-- ){
       selectother.options[i]=null;
    }
  }
  
  if(currentrole == <%=BasicAccessRuleSet.ROLE_SUPERADMINISTRATOR %>){
    selectcas.disabled = true;
    selectendentityrules.disabled = true;
    selectendentityprofiles.disabled = true;
    selectother.disabled = true;
 
    numofcas = selectcas.length;
    for( i=numofcas-1; i >= 0; i-- ){          
         selectcas.options[i].selected=false;
    }
 
    numofendentity = selectendentityrules.length;
    for( i=numofendentity-1; i >= 0; i-- ){          
         selectendentityrules.options[i].selected=false;
    }
 
    numofprofiles = selectendentityprofiles.length;
    for( i=numofprofiles-1; i >= 0; i-- ){          
         selectendentityprofiles.options[i].selected=false;
    }
 
    numofother = selectother.length;
    for( i=numofother-1; i >= 0; i-- ){
       selectother.options[i]=null;
    }
 
  }
  if(currentrole == <%= BasicAccessRuleSet.ROLE_CAADMINISTRATOR%>){
    selectcas.disabled = false;
    selectendentityrules.disabled = true;
    selectendentityprofiles.disabled = true;
    selectother.disabled = false;
 
    numofendentity = selectendentityrules.length;
    for( i=numofendentity-1; i >= 0; i-- ){          
         selectendentityrules.options[i].selected=false;
    }
 
    numofprofiles = selectendentityprofiles.length;
    for( i=numofprofiles-1; i >= 0; i-- ){          
         selectendentityprofiles.options[i].selected=false;
    }
     
    if (selectother.length == 0) {
	    selectother.options[0]=new Option("<%= ejbcawebbean.getText(BasicAccessRuleSet.OTHERTEXTS[BasicAccessRuleSet.OTHER_VIEWLOG]) %>",<%= BasicAccessRuleSet.OTHER_VIEWLOG %>);
	    <% if(globalconfiguration.getIssueHardwareTokens()){ %>
	      selectother.options[1]=new Option("<%= ejbcawebbean.getText(BasicAccessRuleSet.OTHERTEXTS[BasicAccessRuleSet.OTHER_ISSUEHARDTOKENS]) %>",<%= BasicAccessRuleSet.OTHER_ISSUEHARDTOKENS %>);
	    <% } %>
    }
  }
  if(currentrole == <%= BasicAccessRuleSet.ROLE_RAADMINISTRATOR%>){
    selectcas.disabled = false;
    selectendentityrules.disabled = false;
    selectendentityprofiles.disabled = false;
    selectother.disabled = false;

    // Earlier there was a loop here that set some end entity rules to "selected", this made it impossible to edit the rules. ECA-1189.

    if (selectother.length == 0) {
	    selectother.options[0]=new Option("<%= ejbcawebbean.getText(BasicAccessRuleSet.OTHERTEXTS[BasicAccessRuleSet.OTHER_VIEWLOG]) %>",<%= BasicAccessRuleSet.OTHER_VIEWLOG %>);
	    <% if(globalconfiguration.getIssueHardwareTokens()){ %>
	      selectother.options[1]=new Option("<%= ejbcawebbean.getText(BasicAccessRuleSet.OTHERTEXTS[BasicAccessRuleSet.OTHER_ISSUEHARDTOKENS]) %>",<%= BasicAccessRuleSet.OTHER_ISSUEHARDTOKENS %>);
	    <% } %>
    }
  }  
  if(currentrole == <%= BasicAccessRuleSet.ROLE_SUPERVISOR%>){
    selectcas.disabled = false;
    selectendentityrules.disabled = false;
    selectendentityprofiles.disabled = false;
    selectother.disabled = true;
 
    numofendentity = selectendentityrules.length;
    for( i=numofendentity-1; i >= 0; i-- ){
       if(selectendentityrules.options[i].value == <%=BasicAccessRuleSet.ENDENTITY_VIEW %> ||
          selectendentityrules.options[i].value == <%=BasicAccessRuleSet.ENDENTITY_VIEWHISTORY %>)
         selectendentityrules.options[i].selected=true;
       else
         selectendentityrules.options[i].selected=false;
    }
  }
}
 
function checkallfields(){ 
	var selectcas = document.getElementById('basicRules:selectcas');
	var selectrole = document.getElementById('basicRules:selectrole');
	var selectendentityrules = document.getElementById('basicRules:selectendentityrules');
	var selectendentityprofiles = document.getElementById('basicRules:selectendentityprofiles');
	var selectother = document.getElementById('selectother');

    var illegalfields = 0;
    var illegalselection = false;
 
    selectcas.disabled = false;
    selectendentityrules.disabled = false;
    selectendentityprofiles.disabled = false;
    selectother.disabled = false;
 
    var currentrole = selectrole.options[selectrole.options.selectedIndex].value;        
 
    if(currentrole == <%= BasicAccessRuleSet.ROLE_NONE%>){
      alert("<%= ejbcawebbean.getText("SELECTAROLE", true) %>");
    }
 
    if(currentrole == <%= BasicAccessRuleSet.ROLE_SUPERVISOR%>){
      var numofendentity = selectendentityrules.length;
      for( i=numofendentity-1; i >= 0; i-- ){
       if(selectendentityrules.options[i].selected){
         if(!(selectendentityrules.options[i].value==<%= BasicAccessRuleSet.ENDENTITY_VIEW%> ||
              selectendentityrules.options[i].value==<%= BasicAccessRuleSet.ENDENTITY_VIEWHISTORY%> ||
              selectendentityrules.options[i].value==<%= BasicAccessRuleSet.ENDENTITY_VIEWHARDTOKENS%>)){
            illegalselection = true;
         }
       }
      }
    }
    return illegalfields == 0;  
} 

-->
</script>
</head>


<body onload='roleupdated()'>

<div align="center">

	<h2><h:outputText value="#{web.text.EDITACCESSRULES}" /></h2>
	<h3><h:outputText value="#{web.text.ADMINGROUP} : #{adminGroupsManagedBean.currentAdminGroup}" /></h3>

	<h:outputText value="#{web.text.AUTHORIZATIONDENIED}" rendered="#{!adminGroupsManagedBean.authorizedToGroup}"/>

</div>


	<h:panelGroup rendered="#{adminGroupsManagedBean.authorizedToGroup}">
	<div><h:outputText styleClass="alert" value="#{web.text.ADVANCEDMODEREQUIRED}" rendered="#{adminGroupsManagedBean.basicRuleSet.forceAdvanced}" /></div>
	<h:messages layout="table" errorClass="alert"/>

  
	<h:panelGroup rendered="#{!adminGroupsManagedBean.basicRuleSet.forceAdvanced}">
 
 	<h:form id="basicRules">
	<h:inputHidden id="currentAdminGroup" value="#{adminGroupsManagedBean.currentAdminGroup}" />
	<h:panelGrid styleClass="edit" width="100%" columns="2" rowClasses="Row0,Row1" columnClasses="label,field">

		<h:panelGroup>
			&nbsp;
		</h:panelGroup>
		<h:panelGroup>
			<h:outputLink value="#{web.ejbcaWebBean.globalConfiguration.authorizationPath}/administratorprivileges.jsf" title="#{web.text.BACKTOADMINGROUPS}">
				<h:outputText value="#{web.text.BACKTOADMINGROUPS}"/>
			</h:outputLink>
		</h:panelGroup>

		<h:panelGroup>
			&nbsp;
		</h:panelGroup>
		<h:panelGroup style="display: block; text-align: right;">
			<h:outputLink value="#{web.ejbcaWebBean.globalConfiguration.authorizationPath}/editadminentities.jsf?currentAdminGroup=#{adminGroupsManagedBean.currentAdminGroup}"
				title="#{web.text.EDITADMINS}" rendered="#{not empty adminGroupsManagedBean.currentAdminGroup}">
				<h:outputText value="#{web.text.EDITADMINS}"/>
			</h:outputLink>
		</h:panelGroup>

		<h:panelGroup>
			&nbsp;
		</h:panelGroup>
		<h:panelGroup style="display: block; text-align: right;">
			<h:outputLink value="#{web.ejbcaWebBean.globalConfiguration.authorizationPath}/editadvancedaccessrules.jsf?currentAdminGroup=#{adminGroupsManagedBean.currentAdminGroup}"
				title="#{web.text.ADVANCEDMODE}" rendered="#{not empty adminGroupsManagedBean.currentAdminGroup}">
				<h:outputText value="#{web.text.ADVANCEDMODE}"/>
			</h:outputLink>
		</h:panelGroup>

		<h:outputText value="#{web.text.ROLE}"/>
		<h:selectOneMenu id="selectrole" value="#{adminGroupsManagedBean.currentRole}" onchange='roleupdated()'>
			<f:selectItems value="#{adminGroupsManagedBean.availableRoles}" />
		</h:selectOneMenu> 
		
		<h:outputText value="#{web.text.AUTHORIZEDCAS}"/>
		<h:selectManyListbox id="selectcas" value="#{adminGroupsManagedBean.currentCAs}" size="8">
			<f:selectItems value="#{adminGroupsManagedBean.availableCasAndAll}" />
		</h:selectManyListbox> 

		<h:outputText value="#{web.text.ENDENTITYRULES}"/>
		<h:selectManyListbox id="selectendentityrules" value="#{adminGroupsManagedBean.currentEndEntityRules}" size="10">
			<f:selectItems value="#{adminGroupsManagedBean.availableEndEntityRules}" />
		</h:selectManyListbox> 
 
		<h:outputText value="#{web.text.ENDENTITYPROFILES}"/>
		<h:selectManyListbox id="selectendentityprofiles" value="#{adminGroupsManagedBean.currentEndEntityProfiles}" size="8">
			<f:selectItems value="#{adminGroupsManagedBean.availableEndEntityProfiles}" />
		</h:selectManyListbox> 

		<h:outputText value="#{web.text.OTHERRULES}"/>
		<h:selectManyListbox id="selectother" value="#{adminGroupsManagedBean.currentOtherRules}" size="3">
			<f:selectItems value="#{adminGroupsManagedBean.availableOtherRules}" />
		</h:selectManyListbox> 


		<%-- Form buttons --%>

		<h:panelGroup>
			&nbsp;
		</h:panelGroup>
		<h:panelGroup>
			<h:commandButton action="#{adminGroupsManagedBean.saveAccessRules}" onclick="return checkallfields();" value="#{web.text.SAVE}"/>
			<f:verbatim>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</f:verbatim>
			<h:commandButton action="cancel" value="#{web.text.RESTORE}"/>
		</h:panelGroup>

	</h:panelGrid>
	</h:form>

	</h:panelGroup>
	</h:panelGroup>


<%	// Include Footer 
	String footurl = globalconfiguration.getFootBanner(); %>
	<jsp:include page="<%= footurl %>" />

</body>
</f:view>
</html>
