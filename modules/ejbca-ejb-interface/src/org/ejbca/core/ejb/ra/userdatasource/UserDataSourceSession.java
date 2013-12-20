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
package org.ejbca.core.ejb.ra.userdatasource;

import java.util.Collection;
import java.util.HashMap;

import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.userdatasource.BaseUserDataSource;
import org.ejbca.core.model.ra.userdatasource.MultipleMatchException;
import org.ejbca.core.model.ra.userdatasource.UserDataSourceConnectionException;
import org.ejbca.core.model.ra.userdatasource.UserDataSourceException;
import org.ejbca.core.model.ra.userdatasource.UserDataSourceExistsException;
import org.ejbca.core.model.ra.userdatasource.UserDataSourceVO;

/**
 * @version $Id: UserDataSourceSession.java 11337 2011-02-10 22:37:15Z jeklund $
 */
public interface UserDataSourceSession {

	/**
     * Main method used to fetch userdata from the given user data sources See
     * BaseUserDataSource class for further documentation about function Checks
     * that the administrator is authorized to fetch userdata.
     * 
     * @param userdatasourceids a Collection (Integer) of userdatasource Ids.
     * @return Collection of UserDataSourceVO, empty if no userdata could be
     *         found.
     * @see org.ejbca.core.model.ra.userdatasource.BaseUserDataSource
     */
    public Collection<UserDataSourceVO> fetch(Admin admin, Collection<Integer> userdatasourceids, String searchstring) throws AuthorizationDeniedException, UserDataSourceException;

    /**
     * method used to remove userdata from the given user data sources.
     * This functionality is optianal of a user data implementation and
     * is not certain it is implemented
     * See BaseUserDataSource class for further documentation about function
     *
     * Checks that the administrator is authorized to remove userdata.
     * 
     * @param userdatasourceids a Collection (Integer) of userdatasource Ids.
     * @return true if the user was remove successfully from at least one of the user data sources.
     * @see org.ejbca.core.model.ra.userdatasource.BaseUserDataSource
     */
    public boolean removeUserData(Admin admin, Collection<Integer> userdatasourceids, String searchstring, boolean removeMultipleMatch) throws AuthorizationDeniedException,
            MultipleMatchException, UserDataSourceException;

	/**
     * Test the connection to a user data source.
     *
     * @param userdatasourceid the id of the userdatasource to test.
     * @see org.ejbca.core.model.ra.userdatasource.BaseUserDataSource
     */
    public void testConnection(org.ejbca.core.model.log.Admin admin, int userdatasourceid) throws UserDataSourceConnectionException;

    /**
     * Adds a user data source to the database.
     * @throws UserDataSourceExistsException if user data source already exists
     */
    public void addUserDataSource(Admin admin, String name, BaseUserDataSource userdatasource) throws UserDataSourceExistsException;

    /**
     * Adds a user data source to the database. Used for importing and exporting
     * profiles from xml-files.
     * 
     * @throws UserDataSourceExistsException if user data source already exists.
     */
    public void addUserDataSource(Admin admin, int id, String name, BaseUserDataSource userdatasource) throws UserDataSourceExistsException;

    /** Updates user data source data. */
    public void changeUserDataSource(Admin admin, String name, BaseUserDataSource userdatasource);

    /**
     * Adds a user data source with the same content as the original.
     * @throws UserDataSourceExistsException if user data source already exists
     */
    public void cloneUserDataSource(Admin admin, String oldname, String newname) throws UserDataSourceExistsException;

    /** Removes a user data source. */
    public boolean removeUserDataSource(Admin admin, String name);

    /**
     * Renames a user data source
     * @throws UserDataSourceExistsException if user data source already exists
     */
    public void renameUserDataSource(Admin admin, String oldname, String newname) throws UserDataSourceExistsException;

    /**
     * Retrieves a Collection of id:s (Integer) to authorized user data sources.
     *
     * @param indicates if sources with anyca set should be included
     * @return Collection of id:s (Integer)
     */
    public Collection<Integer> getAuthorizedUserDataSourceIds(Admin admin, boolean includeAnyCA);

    /**
     * Method creating a hashmap mapping user data source id (Integer) to user
     * data source name (String).
     */
    public HashMap<Integer,String> getUserDataSourceIdToNameMap(Admin admin);

    /** Retrieves a named user data source. */
    public BaseUserDataSource getUserDataSource(Admin admin, String name);

    /** Finds a user data source by id. */
    public BaseUserDataSource getUserDataSource(Admin admin, int id);

    /**
     * Help method used by user data source proxys to indicate if it is time to
     * update it's data.
     */
    public int getUserDataSourceUpdateCount(Admin admin, int userdatasourceid);

    /**
     * Returns a user data source id, given it's user data source name
     * @return the id or 0 if the user data source cannot be found.
     */
    public int getUserDataSourceId(Admin admin, String name);

    /**
     * Returns a user data source name given its id.
     * @return the name or null if id doesnt exists
     */
    public String getUserDataSourceName(Admin admin, int id);
}
