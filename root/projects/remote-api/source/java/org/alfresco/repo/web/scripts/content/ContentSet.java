/*
 * Copyright (C) 2005-2007 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.repo.web.scripts.content;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.alfresco.cmis.CMISObjectReference;
import org.alfresco.cmis.reference.ObjectPathReference;
import org.alfresco.cmis.reference.ReferenceFactory;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.encoding.ContentCharsetFinder;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.web.scripts.AbstractWebScript;
import org.alfresco.web.scripts.WebScriptException;
import org.alfresco.web.scripts.WebScriptRequest;
import org.alfresco.web.scripts.WebScriptResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Content Write Service
 * 
 * Stream content to the Repository.
 * 
 * @author davidc
 */
public class ContentSet extends AbstractWebScript
{
    // Logger
    @SuppressWarnings("unused")
    private static final Log logger = LogFactory.getLog(ContentSet.class);
    
    // Component dependencies
    private ReferenceFactory referenceFactory;
    private DictionaryService dictionaryService;
    private NamespaceService namespaceService;
    private ContentService contentService;
    private MimetypeService mimetypeService;
    
    /**
     * @param reference factory
     */
    public void setReferenceFactory(ReferenceFactory referenceFactory)
    {
        this.referenceFactory = referenceFactory; 
    }

    /**
     * @param dictionaryService
     */
    public void setDictionaryService(DictionaryService dictionaryService)
    {
        this.dictionaryService = dictionaryService; 
    }

    /**
     * @param namespaceService
     */
    public void setNamespaceService(NamespaceService namespaceService)
    {
        this.namespaceService = namespaceService; 
    }

    /**
     * @param contentService
     */
    public void setContentService(ContentService contentService)
    {
        this.contentService = contentService; 
    }

    /**
     * @param mimetypeService
     */
    public void setMimetypeService(MimetypeService mimetypeService)
    {
        this.mimetypeService = mimetypeService; 
    }

    /**
     * @see org.alfresco.web.scripts.WebScript#execute(org.alfresco.web.scripts.WebScriptRequest, org.alfresco.web.scripts.WebScriptResponse)
     */
    public void execute(WebScriptRequest req, WebScriptResponse res)
        throws IOException
    {   
        // create map of args
        String[] names = req.getParameterNames();
        Map<String, String> args = new HashMap<String, String>(names.length, 1.0f);
        for (String name : names)
        {
            args.put(name, req.getParameter(name));
        }
        
        // create map of template vars
        Map<String, String> templateVars = req.getServiceMatch().getTemplateVars();
        
        // create object reference from url
        CMISObjectReference reference = referenceFactory.createObjectReferenceFromUrl(args, templateVars);
        NodeRef nodeRef = reference.getNodeRef();
        if (nodeRef == null)
        {
            throw new WebScriptException(HttpServletResponse.SC_NOT_FOUND, "Unable to find " + reference.toString());
        }
        
        // determine content property
        QName propertyQName = ContentModel.PROP_CONTENT;
        String contentPart = templateVars.get("property");
        if (contentPart.length() > 0 && contentPart.charAt(0) == ';')
        {
            if (contentPart.length() < 2)
            {
                throw new WebScriptException(HttpServletResponse.SC_BAD_REQUEST, "Content property malformed");
            }
            String propertyName = contentPart.substring(1);
            if (propertyName.length() > 0)
            {
                propertyQName = QName.createQName(propertyName, namespaceService);
            }
        }
        PropertyDefinition propertyDef = dictionaryService.getProperty(propertyQName);
        if (propertyDef == null)
        {
            throw new WebScriptException(HttpServletResponse.SC_NOT_FOUND, "Unable to find content property " + propertyQName + " of " + reference.toString());
        }
        if (!propertyDef.getDataType().getName().equals(DataTypeDefinition.CONTENT))
        {
            throw new WebScriptException(HttpServletResponse.SC_NOT_FOUND, "Content stream not found");
        }

        // ensure content can be overwritten
        // TODO: check parameter name
        String overwrite = req.getParameter("overwriteFlag");
        if (overwrite != null && overwrite.equalsIgnoreCase("false"))
        {
            ContentReader reader = contentService.getReader(nodeRef, propertyQName);
            if (reader != null)
            {
                // error code as per CMIS specification
                throw new WebScriptException(HttpServletResponse.SC_CONFLICT, "Content already exists.");
            }
        }
        
        // setup content writer
        ContentWriter writer = contentService.getWriter(nodeRef, propertyQName, true);
        
        // establish mimetype
        String mimetype = req.getContentType();
        if (mimetype == null)
        {
            if (reference instanceof ObjectPathReference)
            {
                mimetype = mimetypeService.guessMimetype(((ObjectPathReference)reference).getPath());
            }
        }
        if (mimetype != null)
        {
            writer.setMimetype(mimetype);
        }
        
        // get the input stream from the request data
        InputStream is = req.getContent().getInputStream();
        is = is.markSupported() ? is : new BufferedInputStream(is);
        
        // establish content encoding
        ContentCharsetFinder charsetFinder = mimetypeService.getContentCharsetFinder();
        Charset encoding = charsetFinder.getCharset(is, mimetype);
        writer.setEncoding(encoding.name());

        // write the new data
        writer.putContent(is);
    }
}