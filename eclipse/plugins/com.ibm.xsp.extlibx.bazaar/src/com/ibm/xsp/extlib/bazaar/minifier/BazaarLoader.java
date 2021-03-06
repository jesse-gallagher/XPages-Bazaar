/*
 * � Copyright IBM Corp. 2010
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied. See the License for the specific language governing 
 * permissions and limitations under the License.
 */

package com.ibm.xsp.extlib.bazaar.minifier;

import java.net.URL;

import javax.servlet.http.HttpServletRequest;

import org.osgi.framework.Bundle;

import com.ibm.commons.util.DoubleMap;
import com.ibm.xsp.extlib.library.BazaarActivator;
import com.ibm.xsp.extlib.minifier.ExtLibLoaderExtension;
import com.ibm.xsp.extlib.resources.ExtlibResourceProvider;
import com.ibm.xsp.extlib.util.ExtLibUtil;


/**
 * Resource Loader that loads the resource from sbt plug-in.
 */
public class BazaarLoader extends ExtLibLoaderExtension {

	public BazaarLoader() {
	}
    
    @Override
    public Bundle getOSGiBundle() {
        return BazaarActivator.instance.getBundle();
    }
	
	
	// ========================================================
	//	Handling Dojo
	// ========================================================
    
    @Override
    public void loadDojoShortcuts(DoubleMap<String, String> aliases, DoubleMap<String, String> prefixes) {
        /// ALIASES
        if(aliases!=null) {
            //aliases.put("XEa","extlib.dijit.Accordion"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        
        /// PREFIXES
        if(prefixes!=null) {
            prefixes.put("3Z0a","extlib.codemirror"); //$NON-NLS-1$ //$NON-NLS-2$
            prefixes.put("3Z0b","extlib.codemirror.lib"); //$NON-NLS-1$ //$NON-NLS-2$
            prefixes.put("3Z0c","extlib.codemirror.lib.codemirror"); //$NON-NLS-1$ //$NON-NLS-2$
            prefixes.put("3Z0d","extlib.codemirror.mode"); //$NON-NLS-1$ //$NON-NLS-2$
            prefixes.put("3Z0e","extlib.codemirror.mode.xml"); //$NON-NLS-1$ //$NON-NLS-2$
            prefixes.put("3Z0f","extlib.codemirror.mode.javascript"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }
    
    // ========================================================
    //  Handling CSS
    // ========================================================
    
    @Override
    public void loadCSSShortcuts(DoubleMap<String, String> aliases, DoubleMap<String, String> prefixes) {
        /// ALIASES
        if(aliases!=null) {
            //aliases.put("@Ea","/.ibmxspres/.extlib/css/tagcloud.css"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        /// PREFIXES
        if(prefixes!=null) {
            prefixes.put("3Z0a","/.ibmxspres/.extlib/codemirror"); //$NON-NLS-1$ //$NON-NLS-2$
            prefixes.put("3Z0b","/.ibmxspres/.extlib/codemirror/theme"); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    
    // ========================================================
    // Serving resources
    // ========================================================
    
    @Override
    public URL getResourceURL(HttpServletRequest request, String name) {
        String path = ExtlibResourceProvider.BUNDLE_RES_PATH_EXTLIB+name;
        return ExtLibUtil.getResourceURL(BazaarActivator.instance.getBundle(), path);
    }
}
