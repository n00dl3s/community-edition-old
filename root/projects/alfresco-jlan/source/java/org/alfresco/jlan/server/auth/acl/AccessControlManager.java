package org.alfresco.jlan.server.auth.acl;

/*
 * AccessControlManager.java
 *
 * Copyright (c) 2004 Starlasoft. All rights reserved.
 */
 
import org.alfresco.jlan.server.SrvSession;
import org.alfresco.jlan.server.config.InvalidConfigurationException;
import org.alfresco.jlan.server.config.ServerConfiguration;
import org.alfresco.jlan.server.core.SharedDevice;
import org.alfresco.jlan.server.core.SharedDeviceList;
import org.alfresco.config.ConfigElement;


/**
 * Access Control Manager Interface
 * 
 * <p>Used to control access to shared filesystems.
 */
public interface AccessControlManager {

	/**
	 * Initialize the access control manager
	 * 
	 * @param config ServerConfiguration
	 * @param params ConfigElement
	 * @exception InvalidConfigurationException
	 */
	public void initialize(ServerConfiguration config, ConfigElement params)
		throws InvalidConfigurationException;
		
	/**
	 * Check access to the shared filesystem for the specified session
	 *
	 * @param sess SrvSession
	 * @param share SharedDevice
	 * @return int
	 */
	public int checkAccessControl(SrvSession sess, SharedDevice share);
	
	/**
	 * Filter a shared device list to remove shares that are not visible or the session does
	 * not have access to.
	 *
	 * @param sess SrvSession
	 * @param shares SharedDeviceList
	 * @return SharedDeviceList
	 */
	public SharedDeviceList filterShareList(SrvSession sess, SharedDeviceList shares);
	
	/**
	 * Create an access control
	 * 
	 * @param type String
	 * @param params ConfigElement
	 * @return AccessControl
	 * @exception ACLParseException
	 * @exception InvalidACLTypeException
	 */
	public AccessControl createAccessControl(String type, ConfigElement params)
		throws ACLParseException, InvalidACLTypeException;
		
	/**
	 * Add an access control parser to the list of available access control types.
	 * 
	 * @param parser AccessControlParser
	 */
	public void addAccessControlType(AccessControlParser parser);
}
